/*
 * Copyright (c) 2017-2019 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.component.processor.listener;

import io.axoniq.axonserver.grpc.control.EventProcessorInfo;
import io.axoniq.axonserver.topology.Topology;

/**
 * {@link ClientProcessor} Fake implementation for test purpose .
 *
 * @author Sara Pellegrini
 */
public class FakeClientProcessor implements ClientProcessor {

    private final String clientId;

    private final boolean belongsToComponent;

    private final String belongsToContext;

    private final EventProcessorInfo eventProcessorInfo;

    public FakeClientProcessor(String clientId, String processorName, String tokenStoreId) {
        this(clientId, true, EventProcessorInfo.newBuilder()
                                               .setProcessorName(processorName)
                                               .setTokenStoreIdentifier(tokenStoreId)
                                               .setRunning(true)
                                               .build());
    }


    public FakeClientProcessor(String clientId, boolean belongsToComponent, String processorName, boolean running) {
        this(clientId, belongsToComponent, EventProcessorInfo.newBuilder()
                                                             .setProcessorName(processorName)
                                                             .setRunning(running)
                                                             .build());
    }

    public FakeClientProcessor(String clientId, boolean belongsToComponent,
                               EventProcessorInfo eventProcessorInfo) {
        this(clientId, belongsToComponent, Topology.DEFAULT_CONTEXT, eventProcessorInfo);
    }

    public FakeClientProcessor(String clientId, boolean belongsToComponent, String belongsToContext,
                               EventProcessorInfo eventProcessorInfo) {
        this.clientId = clientId;
        this.belongsToComponent = belongsToComponent;
        this.belongsToContext = belongsToContext;
        this.eventProcessorInfo = eventProcessorInfo;
    }

    @Override
    public String clientId() {
        return clientId;
    }

    @Override
    public String context() {
        return "default";
    }

    @Override
    public EventProcessorInfo eventProcessorInfo() {
        return eventProcessorInfo;
    }

    @Override
    public Boolean belongsToComponent(String component) {
        return belongsToComponent;
    }

    @Override
    public boolean belongsToContext(String context) {
        return belongsToContext.equals(context);
    }
}
