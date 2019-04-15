package io.axoniq.axonserver.enterprise.storage;

import io.axoniq.axonserver.localstorage.EventStreamController;
import io.axoniq.axonserver.localstorage.EventStreamExecutor;
import io.axoniq.axonserver.localstorage.EventStreamReader;
import io.axoniq.axonserver.util.AssertUtils;
import org.junit.*;
import org.junit.rules.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Marc Gathier
 */
@Ignore
public class EventStreamReaderTest {
    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();
    private static TestStorageContainer testStorageContainer;

    private EventStreamReader testSubject;

    @BeforeClass
    public static void init() throws Exception {
        testStorageContainer = new TestStorageContainer(tempFolder.getRoot());
        testStorageContainer.createDummyEvents(1000, 100);
    }

    @AfterClass
    public static void close() {
        testStorageContainer.close();
    }

    @Before
    public void setUp() {
        testSubject = new EventStreamReader(testStorageContainer.getDatafileManagerChain(),
                                            testStorageContainer.getEventWriter(), new EventStreamExecutor(1));

    }

    @Test
    public void readEventsFromStart() throws InterruptedException {
        AtomicLong counter = new AtomicLong();
        EventStreamController controller = testSubject.createController(eventWithToken -> {
            counter.incrementAndGet();
        }, Throwable::printStackTrace);

        controller.update(0, 100);
        AssertUtils.assertWithin(1000, TimeUnit.MILLISECONDS, () -> Assert.assertEquals(100, counter.get()));

        controller.update(0, 100);
        AssertUtils.assertWithin(1000, TimeUnit.MILLISECONDS, () -> Assert.assertEquals(200, counter.get()));
    }

    @Test
    public void readEventsFromEnd() throws InterruptedException {
        AtomicLong counter = new AtomicLong();
        EventStreamController controller = testSubject.createController(eventWithToken -> {
            counter.incrementAndGet();
        }, Throwable::printStackTrace);

        controller.update(testStorageContainer.getDatafileManagerChain().getLastToken()-1, 100);
        AssertUtils.assertWithin(1000, TimeUnit.MILLISECONDS, () -> Assert.assertEquals(2, counter.get()));
    }

    @Test
    @Ignore("Test is unstable when running from Jenkins")
    public void readEventsWhileWriting() throws InterruptedException {
        AtomicLong counter = new AtomicLong();
        EventStreamController controller = testSubject.createController(eventWithToken -> counter.incrementAndGet(),
                                                                        Throwable::printStackTrace);

        controller.update(95000, 100000);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        Future<?> task = executor.submit(() -> {
            testStorageContainer.createDummyEvents(5000, 1, "live-");
        });

        AssertUtils.assertWithin(2000, TimeUnit.MILLISECONDS, () -> Assert.assertEquals(10000, counter.get()));
        if( ! task.isDone()) task.cancel(true);
    }


}
