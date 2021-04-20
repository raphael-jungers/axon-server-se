/*
 * Copyright (c) 2017-2019 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.refactoring.transport.rest.dto;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.refactoring.messaging.api.SerializedObject;
import io.axoniq.axonserver.refactoring.util.StringUtils;

/**
 * @author Marc Gathier
 */
public class SerializedObjectJson {

    private String type;
    private String data;
    private String revision;

    public SerializedObjectJson() {

    }

    public SerializedObjectJson(SerializedObject payload) {
        this.type = payload.type();
        this.data = new String(payload.data());
        this.revision = payload.revision();
    }

    public SerializedObjectJson(io.axoniq.axonserver.grpc.SerializedObject payload) {
        type = payload.getType();
        data = payload.getData().toStringUtf8();
        revision = payload.getRevision();
    }

    public io.axoniq.axonserver.grpc.SerializedObject asSerializedObject() {
        return io.axoniq.axonserver.grpc.SerializedObject.newBuilder()
                                                         .setData(ByteString.copyFromUtf8(data))
                                                         .setType(type)
                                                         .setRevision(StringUtils.getOrDefault(revision, ""))
                                                         .build();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getRevision() {
        return revision;
    }

    public void setRevision(String revision) {
        this.revision = revision;
    }
}
