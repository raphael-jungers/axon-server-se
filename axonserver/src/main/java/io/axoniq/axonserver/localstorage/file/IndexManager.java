/*
 * Copyright (c) 2017-2019 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.localstorage.file;

import io.axoniq.axonserver.exception.ErrorCode;
import io.axoniq.axonserver.exception.MessagingPlatformException;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.io.Closeable;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Marc Gathier
 */
public class IndexManager {

    private static final Logger logger = LoggerFactory.getLogger(IndexManager.class);
    private static final String AGGREGATE_MAP = "aggregateMap";
    private static final ScheduledExecutorService scheduledExecutorService =
            Executors.newScheduledThreadPool(1, new CustomizableThreadFactory("index-manager-"));
    private final StorageProperties storageProperties;
    private final ConcurrentNavigableMap<Long, PersistedBloomFilter> bloomFilterPerSegment = new ConcurrentSkipListMap<>();
    private final ConcurrentSkipListMap<Long, Index> indexMap = new ConcurrentSkipListMap<>();
    private final String context;
    private ScheduledFuture<?> cleanupTask;

    public IndexManager(String context, StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
        this.context = context;
        scheduledExecutorService.scheduleAtFixedRate(this::indexCleanup, 10, 10, TimeUnit.SECONDS);
    }

    public void createIndex(Long segment, Map<String, SortedSet<PositionInfo>> positionsPerAggregate) {
        File tempFile = storageProperties.indexTemp(context, segment);
        if (!FileUtils.delete(tempFile)) {
            throw new MessagingPlatformException(ErrorCode.INDEX_WRITE_ERROR,
                                                 "Failed to delete temp index file:" + tempFile);
        }
        DBMaker.Maker maker = DBMaker.fileDB(tempFile);
        if (storageProperties.isUseMmapIndex()) {
            maker.fileMmapEnable();
            if (storageProperties.isForceCleanMmapIndex()) {
                maker.cleanerHackEnable();
            }
        } else {
            maker.fileChannelEnable();
        }
        DB db = maker.make();
        try (HTreeMap<String, SortedSet<PositionInfo>> map = db.hashMap(AGGREGATE_MAP,
                                                                        Serializer.STRING,
                                                                        PositionInfoSerializer.get())
                                                               .createOrOpen()) {
            map.putAll(positionsPerAggregate);
        }
        db.close();

        if (!tempFile.renameTo(storageProperties.index(context, segment))) {
            throw new MessagingPlatformException(ErrorCode.INDEX_WRITE_ERROR,
                                                 "Failed to rename index file:" + tempFile);
        }

        PersistedBloomFilter filter = new PersistedBloomFilter(storageProperties.bloomFilter(context, segment)
                                                                                .getAbsolutePath(),
                                                               positionsPerAggregate.keySet().size(),
                                                               storageProperties.getBloomIndexFpp());
        filter.create();
        filter.insertAll(positionsPerAggregate.keySet());
        filter.store();
        bloomFilterPerSegment.put(segment, filter);

        getIndex(segment);
    }

    public SortedSet<PositionInfo> getPositions(long segment, String aggregateId) {
        if (notInBloomIndex(segment, aggregateId)) {
            return Collections.emptySortedSet();
        }

        RuntimeException lastError = new RuntimeException();
        for (int retry = 0; retry < 3; retry++) {
            try {
                Index idx = getIndex(segment);
                return idx.getPositions(aggregateId);
            } catch (Throwable ex) {
                lastError = new RuntimeException(
                        "Error happened while trying get positions for " + segment + " segment.", ex);
            }
        }
        throw lastError;
    }

    /**
     * Returns the {@link Index} for the specified segment.
     *
     * @param segment the segment
     * @return the {@link Index} for the specified segment
     *
     * @throws IndexNotFoundException if the attempt to open tha index file fails
     */
    public Index getIndex(long segment) {
        return indexMap.computeIfAbsent(segment, Index::new).ensureReady();
    }

    private void indexCleanup() {
        while (indexMap.size() > storageProperties.getMaxIndexesInMemory()) {
            Map.Entry<Long, Index> entry = indexMap.pollFirstEntry();
            logger.debug("{}: Closing index {}", context, entry.getKey());
            cleanupTask = scheduledExecutorService.schedule(() -> entry.getValue().close(), 2, TimeUnit.SECONDS);
        }

        while (bloomFilterPerSegment.size() > storageProperties.getMaxBloomFiltersInMemory()) {
            Map.Entry<Long, PersistedBloomFilter> removed = bloomFilterPerSegment.pollFirstEntry();
            logger.debug("{}: Removed bloomfilter for {} from memory", context, removed.getKey());
        }
    }

    private boolean notInBloomIndex(Long segment, String aggregateId) {
        PersistedBloomFilter persistedBloomFilter = bloomFilterPerSegment.computeIfAbsent(segment,
                                                                                          i -> loadBloomFilter(segment));
        return persistedBloomFilter != null && !persistedBloomFilter.mightContain(aggregateId);
    }

    private PersistedBloomFilter loadBloomFilter(Long segment) {
        logger.debug("{}: open bloom filter for {}", context, segment);
        PersistedBloomFilter filter = new PersistedBloomFilter(storageProperties.bloomFilter(context, segment)
                                                                                .getAbsolutePath(), 0, 0.03f);
        if (!filter.fileExists()) {
            return null;
        }
        filter.load();
        return filter;
    }

    public boolean validIndex(long segment) {
        try {
            return loadBloomFilter(segment) != null && getIndex(segment) != null;
        } catch (Exception ex) {
            logger.warn("Failed to validate index for segment: {}", segment, ex);
        }
        return false;
    }

    public void cleanup() {
        bloomFilterPerSegment.clear();
        indexMap.forEach((segment, index) -> index.close());
        indexMap.clear();
        if (cleanupTask != null && !cleanupTask.isDone()) {
            cleanupTask.cancel(true);
        }
    }

    public void remove(Long s) {
        Index index = indexMap.remove(s);
        if( index != null) index.close();
        bloomFilterPerSegment.remove(s);
    }


    public class Index implements Closeable {

        private final long segment;
        private final Object initLock = new Object();
        private volatile boolean initialized;
        private Map<String, SortedSet<PositionInfo>> positions;
        private DB db;


        private Index(long segment) {
            this.segment = segment;
        }

        public SortedSet<PositionInfo> getPositions(String aggregateId) {
            SortedSet<PositionInfo> aggregatePositions = positions.get(aggregateId);
            return aggregatePositions == null ? Collections.emptySortedSet() : aggregatePositions;
        }

        @Override
        public void close() {
            logger.debug("{}: close {}", segment, storageProperties.index(context, segment));
            db.close();
        }

        public Index ensureReady() {
            if (initialized && !db.isClosed()) {
                return this;
            }

            synchronized (initLock) {
                if (initialized && !db.isClosed()) {
                    return this;
                }

                if (!storageProperties.index(context, segment).exists()) {
                    throw new IndexNotFoundException("Index not found for segment: " + segment);
                }
                logger.debug("{}: open {}", segment, storageProperties.index(context, segment));
                DBMaker.Maker maker = DBMaker.fileDB(storageProperties.index(context, segment))
                                             .readOnly()
                                             .fileLockDisable();
                if (storageProperties.isUseMmapIndex()) {
                    maker.fileMmapEnable();
                    if (storageProperties.isForceCleanMmapIndex()) {
                        maker.cleanerHackEnable();
                    }
                } else {
                    maker.fileChannelEnable();
                }
                this.db = maker.make();
                this.positions = db.hashMap(AGGREGATE_MAP, Serializer.STRING, PositionInfoSerializer.get())
                                   .createOrOpen();
                initialized = true;
            }
            return this;
        }
    }
}
