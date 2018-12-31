package io.axoniq.axonserver.cluster.replication;

import io.axoniq.axonserver.cluster.Registration;
import io.axoniq.axonserver.cluster.TermIndex;
import io.axoniq.axonserver.grpc.cluster.Config;
import io.axoniq.axonserver.grpc.cluster.Entry;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public interface LogEntryStore {

    void appendEntry(List<Entry> entries) throws IOException;

    boolean contains(long logIndex, long logTerm);

    Entry getEntry(long index);

    CompletableFuture<Entry> createEntry(long currentTerm, String entryType, byte[] entryData);

    CompletableFuture<Entry> createEntry(long currentTerm, Config config);

    TermIndex lastLog();

    EntryIterator createIterator(long index);

    default EntryIterator createIterator(){
        return createIterator(1);
    }

    void clear();

    void clearOlderThan(long time, TimeUnit timeUnit);

    long lastLogIndex();

    Registration registerLogAppendListener(Consumer<Entry> listener);

    Registration registerLogRollbackListener(Consumer<Entry> listener);

}