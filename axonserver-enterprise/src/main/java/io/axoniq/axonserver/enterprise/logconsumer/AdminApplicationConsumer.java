package io.axoniq.axonserver.enterprise.logconsumer;

import io.axoniq.axonserver.enterprise.cluster.GrpcRaftController;
import io.axoniq.axonserver.grpc.ProtoConverter;
import io.axoniq.axonserver.grpc.cluster.Entry;
import io.axoniq.axonserver.grpc.internal.Application;
import io.axoniq.platform.application.ApplicationController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Author: marc
 */
@Component
public class AdminApplicationConsumer implements LogEntryConsumer {
    private final ApplicationController applicationController;
    private final Logger logger = LoggerFactory.getLogger(AdminApplicationConsumer.class);

    public AdminApplicationConsumer(ApplicationController applicationController) {
        this.applicationController = applicationController;
    }

    @Override
    public void consumeLogEntry(String groupId, Entry e) {
        if( ! groupId.equals(GrpcRaftController.ADMIN_GROUP)) return;
        if( entryType(e, Application.class.getName())) {
            Application application = null;
            try {
                application = Application.parseFrom(e.getSerializedObject().getData());
                applicationController.synchronize(ProtoConverter.createJpaApplication(application));
            } catch (Exception e1) {
                logger.warn("Failed to update application: {}", application, e1);
            }
        }

    }


    @Override
    public int priority() {
        return 0;
    }
}
