/*
 * Copyright (c) 2017-2019 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.refactoring.transport.rest;

import io.axoniq.axonserver.refactoring.security.AuditLog;
import io.axoniq.axonserver.refactoring.transport.grpc.PlatformService;
import org.slf4j.Logger;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import java.security.Principal;

/**
 * Rest API to send instuction to a client to reconnect to Axon Server.
 *
 * @author Marc Gathier
 * @since 4.0
 */
@RestController("InstructionRestController")
@RequestMapping("/v1/instructions")
public class InstructionRestController {

    private static final Logger auditLog = AuditLog.getLogger();

    private final PlatformService platformService;

    public InstructionRestController(PlatformService platformService) {
        this.platformService = platformService;
    }

    @PatchMapping
    public String requestReconnect(@RequestParam(value = "client") String clientId,
                                   @ApiIgnore final Principal principal) {
        auditLog.info("[{}] Request client \"{}\" to reconnect.", AuditLog.username(principal), clientId);

        if (platformService.requestReconnect(clientId)) {
            return clientId + ": requested reconnect";
        } else {
            return clientId + ": not connected to this server";
        }
    }
}
