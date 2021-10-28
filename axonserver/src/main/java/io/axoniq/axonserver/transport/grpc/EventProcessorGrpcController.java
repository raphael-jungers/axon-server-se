package io.axoniq.axonserver.transport.grpc;

import com.google.protobuf.Empty;
import io.axoniq.axonserver.admin.eventprocessor.api.EventProcessorAdminService;
import io.axoniq.axonserver.config.AuthenticationProvider;
import io.axoniq.axonserver.grpc.AxonServerClientService;
import io.axoniq.axonserver.grpc.admin.EventProcessorAdminServiceGrpc.EventProcessorAdminServiceImplBase;
import io.axoniq.axonserver.grpc.admin.EventProcessorIdentifier;
import io.axoniq.axonserver.transport.grpc.eventprocessor.EventProcessorIdMessage;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Controller;

/**
 * Exposed through GRPC the operations applicable to an Event Processor.
 *
 * @author Sara Pellegrini
 * @since 4.6
 */
@Controller
public class EventProcessorGrpcController extends EventProcessorAdminServiceImplBase
        implements AxonServerClientService {

    private final EventProcessorAdminService service;
    private final AuthenticationProvider authenticationProvider;

    public EventProcessorGrpcController(EventProcessorAdminService service,
                                        AuthenticationProvider authenticationProvider) {
        this.service = service;
        this.authenticationProvider = authenticationProvider;
    }

    /**
     * Processes the request to pause a specific event processor.
     *
     * @param processorId      the identifier of the event processor
     * @param responseObserver the grpc {@link StreamObserver}
     */
    @Override
    public void pauseEventProcessor(EventProcessorIdentifier processorId, StreamObserver<Empty> responseObserver) {
        try {
            service.pause(new EventProcessorIdMessage(processorId), new GrpcAuthentication(authenticationProvider));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    /**
     * Processes the request to start a specific event processor.
     *
     * @param processorId      the identifier of the event processor
     * @param responseObserver the grpc {@link StreamObserver}
     */
    @Override
    public void startEventProcessor(EventProcessorIdentifier processorId, StreamObserver<Empty> responseObserver) {
        try {
            service.start(new EventProcessorIdMessage(processorId), new GrpcAuthentication(authenticationProvider));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }


    /**
     * Processes the request to split the bigger segment of a specific event processor.
     *
     * @param processorId      the identifier of the event processor
     * @param responseObserver the grpc {@link StreamObserver}
     */
    @Override
    public void splitEventProcessor(EventProcessorIdentifier processorId, StreamObserver<Empty> responseObserver) {
        try {
            service.split(new EventProcessorIdMessage(processorId), new GrpcAuthentication(authenticationProvider));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    /**
     * Processes the request to split the bigger segment of a specific event processor.
     *
     * @param processorId      the identifier of the event processor
     * @param responseObserver the grpc {@link StreamObserver}
     */
    @Override
    public void mergeEventProcessor(EventProcessorIdentifier processorId, StreamObserver<Empty> responseObserver) {
        try {
            service.merge(new EventProcessorIdMessage(processorId), new GrpcAuthentication(authenticationProvider));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }
}
