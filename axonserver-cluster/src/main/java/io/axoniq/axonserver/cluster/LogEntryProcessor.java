package io.axoniq.axonserver.cluster;

import io.axoniq.axonserver.cluster.replication.EntryIterator;
import io.axoniq.axonserver.grpc.cluster.Entry;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Author: marc
 */
public class LogEntryProcessor {
    private final AtomicBoolean applyRunning = new AtomicBoolean(false);
    private final ProcessorStore processorStore;
    private volatile Thread commitListenerThread;
    private volatile boolean running;

    public LogEntryProcessor(ProcessorStore processorStore) {
        this.processorStore = processorStore;
    }


    public void start(Function<Long, EntryIterator> entryIteratorSupplier, Consumer<Entry> consumer) {
        commitListenerThread = Thread.currentThread();
        running = true;
        while (running) {
            int retries = 1;
            while (retries > 0) {
                int applied = applyEntries(entryIteratorSupplier, consumer);
                if (applied > 0) {
                    retries = 0;
                } else {
                    retries--;
                }

                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
        }
    }

    public int applyEntries(Function<Long, EntryIterator> entryIteratorSupplier, Consumer<Entry> consumer) {
        int count = 0;
        if( applyRunning.compareAndSet(false, true)) {
            if( processorStore.lastApplied() < processorStore.commitIndex()) {
                try(EntryIterator iterator = entryIteratorSupplier.apply(processorStore.lastApplied() + 1)) {
                    boolean beforeCommit = true;
                    while (beforeCommit && iterator.hasNext()) {
                        Entry entry = iterator.next();
                        beforeCommit = entry.getIndex() <= processorStore.commitIndex();
                        if (beforeCommit) {
                            consumer.accept(entry);
                            processorStore.updateLastApplied(entry.getIndex());
                            count++;
                        }
                    }
                }
            }
            applyRunning.set(false);
        }
        return count;
    }

    public void markCommitted(long committedIndex) {
        if( committedIndex > processorStore.commitIndex()) {
            processorStore.updateCommitIndex(committedIndex);
            if( commitListenerThread != null) {
                LockSupport.unpark(commitListenerThread);
            }
        }
    }

    public long commitIndex() {
        return processorStore.commitIndex();
    }

    public long lastAppliedIndex() {
        return processorStore.lastApplied();
    }

    public void stop() {
        running = false;
    }
}