package io.axoniq.axonserver.cluster;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Milan Savic
 */
public class DefaultScheduler implements Scheduler {

    private final ScheduledExecutorService scheduledExecutorService;
    private final Clock clock = Clock.systemUTC();

    public DefaultScheduler() {
        this(Executors.newSingleThreadScheduledExecutor());
    }

    public DefaultScheduler(ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
    }

    @Override
    public Clock clock() {
        return clock;
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                                                     TimeUnit timeUnit) {
        return scheduledExecutorService.scheduleWithFixedDelay(command, initialDelay, delay, timeUnit);
    }

    @Override
    public ScheduledRegistration schedule(Runnable command, long delay, TimeUnit timeUnit) {
        ScheduledFuture<?> schedule = scheduledExecutorService.schedule(command, delay, timeUnit);
        return new ScheduledRegistration() {
            @Override
            public long getDelay(TimeUnit unit) {
                return schedule.getDelay(unit);
            }

            @Override
            public long getElapsed(TimeUnit unit) {
                long millisElapsed = timeUnit.toMillis(delay) - getDelay(TimeUnit.MILLISECONDS);
                return unit.convert(millisElapsed, TimeUnit.MILLISECONDS);
            }

            @Override
            public void cancel() {
                schedule.cancel(true);
            }
        };
    }

    @Override
    public void shutdownNow() {
        scheduledExecutorService.shutdownNow();
    }
}