package io.axoniq.axonserver.enterprise.storage;

import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.localstorage.AggregateReader;
import io.axoniq.axonserver.localstorage.SerializedEvent;
import io.axoniq.axonserver.localstorage.SnapshotReader;
import io.axoniq.axonserver.localstorage.SnapshotWriteStorage;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Marc Gathier
 */
public class AggregateReaderTest {

    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();

    private static TestStorageContainer testStorageContainer;
    private AggregateReader testSubject;

    @BeforeClass
    public static void init() throws Exception {
        testStorageContainer = new TestStorageContainer(tempFolder.getRoot());
        testStorageContainer.createDummyEvents(1000, 100);

        SnapshotWriteStorage snapshotWriteStorage = new SnapshotWriteStorage(testStorageContainer.getTransactionManager(testStorageContainer.getSnapshotManagerChain()));
        snapshotWriteStorage.store(Event.newBuilder().setAggregateIdentifier("55").setAggregateSequenceNumber(5)
                                        .setAggregateType("Snapshot")
                                        .setPayload(SerializedObject
                                                            .newBuilder().build()).build());
        snapshotWriteStorage.store(Event.newBuilder().setAggregateIdentifier("55").setAggregateSequenceNumber(15)
                                        .setAggregateType("Snapshot")
                                        .setPayload(SerializedObject
                                                            .newBuilder().build()).build());
        snapshotWriteStorage.store(Event.newBuilder().setAggregateIdentifier("55").setAggregateSequenceNumber(25)
                                        .setAggregateType("Snapshot")
                                        .setPayload(SerializedObject
                                                            .newBuilder().build()).build());
        snapshotWriteStorage.store(Event.newBuilder().setAggregateIdentifier("55").setAggregateSequenceNumber(75)
                                        .setAggregateType("Snapshot")
                                        .setPayload(SerializedObject
                                                            .newBuilder().build()).build());

    }
    @AfterClass
    public static void close() {
        testStorageContainer.close();
    }

    @Before
    public void setUp() {
        testSubject = new AggregateReader(testStorageContainer.getDatafileManagerChain(), new SnapshotReader(testStorageContainer.getSnapshotManagerChain()));
    }

    @Test
    public void readEventsFromOldSegment() {
        AtomicLong sequenceNumber = new AtomicLong();
        testSubject.readEvents("1", true, 0,
                               event -> sequenceNumber.set(event.getAggregateSequenceNumber()));
        Assert.assertEquals(99, sequenceNumber.intValue());
    }

    @Test
    public void readEventsFromCurrentSegment() {
        AtomicLong sequenceNumber = new AtomicLong();
        testSubject.readEvents("999", true, 0,
                               event -> sequenceNumber.set(event.getAggregateSequenceNumber()));
        Assert.assertEquals(99, sequenceNumber.intValue());
    }

    @Test
    public void readSnapshots() {
        List<Long> sequenceNumbers = new ArrayList<>();
        testSubject.readSnapshots("55", 10, 50, 10,
                               event -> sequenceNumbers.add(event.getAggregateSequenceNumber()));
        Assert.assertEquals(2, sequenceNumbers.size());
    }

    @Test
    public void readAllSnapshots() {
        List<Long> sequenceNumbers = new ArrayList<>();
        testSubject.readSnapshots("55", 0, Long.MAX_VALUE, 0,
                                  event -> sequenceNumbers.add(event.getAggregateSequenceNumber()));
        Assert.assertEquals(4, sequenceNumbers.size());
    }

    @Test
    public void readEventsWithSnapshot() {
        List<Event> events = new ArrayList<>();
        testSubject.readEvents("55", true, 0, event -> {
            events.add(event.asEvent());
        });

        Assert.assertEquals(25, events.size());
        Assert.assertEquals("Snapshot", events.get(0).getAggregateType());
    }

    @Test
    public void readEventsWithSnapshotBeforeMin() {
        List<Event> events = new ArrayList<>();
        testSubject.readEvents("55", true, 90, event -> {
            events.add(event.asEvent()  );
        });

        Assert.assertEquals(10, events.size());
        Assert.assertEquals("Demo", events.get(0).getAggregateType());
    }

}
