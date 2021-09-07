package io.axoniq.axonserver.component.processor;

import io.axoniq.axonserver.component.processor.listener.ClientProcessor;
import io.axoniq.axonserver.component.processor.listener.ClientProcessors;
import org.springframework.stereotype.Component;


/**
 * Provides the token store identifier for a given tracking event processor instance.
 *
 * @author Sara Pellegrini
 * @since 4.4
 */
@Component
public class EventProcessorIdentifierProvider {

    /*All event processor instances running in connected clients*/
    private final ClientProcessors clientProcessors;

    /**
     * Create an instance base on the provided event processor instances running in connected clients.
     *
     * @param clientProcessors all the event processor instances running in connected clients.
     */
    public EventProcessorIdentifierProvider(ClientProcessors clientProcessors) {
        this.clientProcessors = clientProcessors;
    }

    /**
     * Finds the first event processor instance that runs in the specified client and has the specified name.
     * Returns the token store identifier of that instance.
     *
     * @param clientIdentifier the client running the event processor instance
     * @param processorName    the name of the event processor
     * @return the token store identifier of the event processor
     */
    public EventProcessorIdentifier get(String clientIdentifier, String processorName) {
        for (ClientProcessor clientProcessor : clientProcessors) {
            if (clientProcessor.clientId().equals(clientIdentifier) &&
                    clientProcessor.eventProcessorInfo().getProcessorName().equals(processorName)) {
                String tokenStoreIdentifier = clientProcessor.eventProcessorInfo().getTokenStoreIdentifier();
                return new EventProcessorIdentifier(processorName, tokenStoreIdentifier);
            }
        }
        throw new IllegalArgumentException("Event processor not found.");
    }
}
