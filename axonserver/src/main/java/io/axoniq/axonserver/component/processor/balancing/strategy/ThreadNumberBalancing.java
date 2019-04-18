/*
 * Copyright (c) 2017-2019 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.component.processor.balancing.strategy;

import io.axoniq.axonserver.component.processor.ProcessorEventPublisher;
import io.axoniq.axonserver.component.processor.balancing.DefaultOperationFactory;
import io.axoniq.axonserver.component.processor.balancing.LoadBalancingOperation;
import io.axoniq.axonserver.component.processor.balancing.LoadBalancingStrategy;
import io.axoniq.axonserver.component.processor.balancing.OperationFactory;
import io.axoniq.axonserver.component.processor.balancing.TrackingEventProcessor;
import io.axoniq.axonserver.component.processor.balancing.strategy.MoveSegmentAlgorithm.MoveOperationFactory;
import io.axoniq.axonserver.component.processor.listener.ClientProcessors;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;

import static java.util.stream.StreamSupport.stream;

/**
 * Created by Sara Pellegrini on 07/08/2018.
 * sara.pellegrini@gmail.com
 */
public class ThreadNumberBalancing implements LoadBalancingStrategy {

    public interface InstancesRepo {

        Iterable<Application> findFor(TrackingEventProcessor processor);
    }

    private final InstancesRepo instances;

    private final OperationFactory operationFactory;

    ThreadNumberBalancing(InstancesRepo instances, OperationFactory operationFactory) {
        this.instances = instances;
        this.operationFactory = operationFactory;
    }

    @Override
    public LoadBalancingOperation balance(TrackingEventProcessor processor) {
        return new MoveSegmentAlgorithm<>(new Move(processor)).balance(instanceClones(processor));
    }

    private Iterable<Application> instanceClones(TrackingEventProcessor processor) {
        return () -> stream(instances.findFor(processor).spliterator(), false).iterator();
    }

    public static final class Application implements MoveSegmentAlgorithm.Instance<Application> {

        private final String identifier;
        private final int threadPoolSize;
        private final Collection<Integer> segments = new ArrayList<>();

        public Application(String identifier, int threadPoolSize, Iterable<Integer> segments) {
            this.identifier = identifier;
            this.threadPoolSize = threadPoolSize;
            segments.forEach(this.segments::add);
        }

        @Override
        public boolean canAcceptThreadFrom(Application source) {
            return this.threadPoolSize > this.segments.size() &&
                    this.segments.size() + 1 <= source.segments.size() - 1;
        }

        @Override
        public Integer acceptThreadFrom(Application source) {
            Integer segment = source.segments.iterator().next();
            source.segments.remove(segment);
            this.segments.add(segment);
            return segment;
        }

        @Override
        public int compareTo(Application o) {
            int lessSegments = this.segments.size() - o.segments.size();
            int moreThreads = o.threadPoolSize - this.threadPoolSize;
            return lessSegments != 0 ? lessSegments : moreThreads;
        }

        @Override
        public String toString() {
            return "Instance{" +
                    "identifier='" + identifier + '\'' +
                    ", threadPoolSize=" + threadPoolSize +
                    ", segments=" + segments +
                    '}';
        }
    }

    private class Move implements MoveOperationFactory<Application> {

        private final TrackingEventProcessor trackingProcessor;

        Move(TrackingEventProcessor trackingProcessor) {
            this.trackingProcessor = trackingProcessor;
        }

        @Override
        public LoadBalancingOperation apply(Integer segment, Application source, Application target) {
            return operationFactory.move(segment, trackingProcessor, source.identifier, target.identifier);
        }
    }

    @Component("ThreadNumberBalancingStrategy")
    public static class ThreadNumberBalancingStrategyFactory implements LoadBalancingStrategy.Factory {

        private final ProcessorEventPublisher processorEventsSource;

        private final ClientProcessors processors;

        public ThreadNumberBalancingStrategyFactory(ProcessorEventPublisher processorEventsSource,
                                                    ClientProcessors processors) {
            this.processorEventsSource = processorEventsSource;
            this.processors = processors;
        }

        @Override
        public LoadBalancingStrategy create() {
            return new ThreadNumberBalancing(new DefaultInstancesRepository(processors),
                                             new DefaultOperationFactory(processorEventsSource, processors));
        }
    }
}
