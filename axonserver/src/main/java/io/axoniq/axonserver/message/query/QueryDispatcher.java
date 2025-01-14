/*
 *  Copyright (c) 2017-2022 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 *  under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.message.query;

import io.axoniq.axonserver.ProcessingInstructionHelper;
import io.axoniq.axonserver.applicationevents.TopologyEvents;
import io.axoniq.axonserver.exception.ErrorCode;
import io.axoniq.axonserver.exception.ErrorMessageFactory;
import io.axoniq.axonserver.exception.MessagingPlatformException;
import io.axoniq.axonserver.grpc.SerializedQuery;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.interceptor.DefaultExecutionContext;
import io.axoniq.axonserver.interceptor.QueryInterceptors;
import io.axoniq.axonserver.message.ClientStreamIdentification;
import io.axoniq.axonserver.message.FlowControlQueues;
import io.axoniq.axonserver.message.command.InsufficientBufferCapacityException;
import io.axoniq.axonserver.metric.BaseMetricName;
import io.axoniq.axonserver.metric.MeterFactory;
import io.axoniq.axonserver.util.ConstraintCache;
import io.axoniq.axonserver.util.NonReplacingConstraintCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static io.axoniq.axonserver.util.StringUtils.getOrDefault;
import static java.lang.String.format;
import static java.util.Collections.singleton;

/**
 * Dispatches queries to the correct handlers and dispatches responses back to the caller.
 *
 * @author Marc Gathier
 * @since 4.0
 */
@Component("QueryDispatcher")
public class QueryDispatcher {

    private final Logger logger = LoggerFactory.getLogger(QueryDispatcher.class);
    private final QueryRegistrationCache registrationCache;
    private final NonReplacingConstraintCache<String, ActiveQuery> queryCache;
    private final QueryInterceptors queryInterceptors;
    private final QueryMetricsRegistry queryMetricsRegistry;
    private final FlowControlQueues<QueryInstruction> queryQueue;
    private final Map<String, MeterFactory.RateMeter> queryRatePerContext = new ConcurrentHashMap<>();

    public QueryDispatcher(QueryRegistrationCache registrationCache,
                           NonReplacingConstraintCache<String, ActiveQuery> queryCache,
                           QueryMetricsRegistry queryMetricsRegistry,
                           QueryInterceptors queryInterceptors,
                           MeterFactory meterFactory,
                           @Value("${axoniq.axonserver.query-queue-capacity-per-client:10000}") int queueCapacity) {
        this.registrationCache = registrationCache;
        this.queryMetricsRegistry = queryMetricsRegistry;
        this.queryCache = queryCache;
        this.queryInterceptors = queryInterceptors;
        queryQueue = new FlowControlQueues<>(Comparator.comparing(QueryInstruction::priority).reversed(),
                                             queueCapacity,
                                             BaseMetricName.AXON_APPLICATION_QUERY_QUEUE_SIZE,
                                             meterFactory,
                                             ErrorCode.TOO_MANY_REQUESTS);
        queryMetricsRegistry.gauge(BaseMetricName.AXON_ACTIVE_QUERIES, queryCache, ConstraintCache::size);
    }


    /**
     * Handles a received {@link QueryResponse}.
     *
     * @param queryResponse  the {@link QueryResponse} has been received
     * @param clientStreamId the query long living stream identifier that the client used to send the response
     * @param clientId       the client id of the client that sent the response
     */
    public void handleResponse(QueryResponse queryResponse,
                               String clientStreamId,
                               String clientId) {
        String requestIdentifier = queryResponse.getRequestIdentifier();
        ActiveQuery activeQuery = activeQuery(clientStreamId, requestIdentifier);
        if (activeQuery != null) {
            ClientStreamIdentification clientStream = new ClientStreamIdentification(activeQuery.getContext(),
                                                                                     clientStreamId);
            long responseTime = System.currentTimeMillis() - activeQuery.getTimestamp();
            queryMetricsRegistry.addEndToEndResponseTime(activeQuery.getQuery(),
                                                         clientId,
                                                         clientStream.getContext(),
                                                         responseTime);
            if (activeQuery.forward(queryResponse, clientStreamId) && activeQuery.isStreaming()) {
                activeQuery.removeHandlersNotMatching(clientStreamId)
                           .forEach(h -> dispatchCancellation(h, activeQuery.getKey(), activeQuery.queryName()));
            }
        } else {
            logger.debug("No (more) information for {}", queryResponse.getRequestIdentifier());
        }
    }

    private ActiveQuery activeQuery(String clientStreamId, String requestIdentifier) {
        ActiveQuery activeQuery = queryCache.get(requestIdentifier);
        if (activeQuery == null) {
            requestIdentifier = requestIdentifier + "/" + clientStreamId;
            activeQuery = queryCache.get(requestIdentifier);
        }
        return activeQuery;
    }

    public void handleComplete(String requestId, String clientStreamId, String clientId, boolean proxied) {
        ActiveQuery activeQuery = activeQuery(clientStreamId, requestId);
        if (activeQuery != null) {
            if (activeQuery.complete(clientStreamId)) {
                queryCache.remove(activeQuery.getKey());
            }
            if (!proxied) {
                queryMetricsRegistry.addHandlerResponseTime(activeQuery.getQuery(),
                                                            activeQuery.getSourceClientId(),
                                                            clientId,
                                                            activeQuery.getContext(),
                                                            System.currentTimeMillis() - activeQuery
                                                                    .getTimestamp());
            }
        } else {
            logger.debug("No (more) information for {} on completed", requestId);
        }
    }

    @EventListener
    public void on(TopologyEvents.QueryHandlerDisconnected event) {
        registrationCache.remove(event.clientIdentification());
        if (!event.isProxied()) {
            queryQueue.move(new ClientStreamIdentification(event.getContext(), event.getClientStreamId()).toString(),
                            query -> null);
        }
    }

    /**
     * Removes the query from the cache and completes it with a {@link ErrorCode#COMMAND_TIMEOUT} error.
     *
     * @param client    the client where the query should be handled
     * @param messageId the request id for the query
     */
    public void removeFromCache(String client, String messageId) {
        ActiveQuery query = queryCache.remove(messageId);
        if (query != null) {
            query.completeWithError(client, ErrorCode.QUERY_TIMEOUT, "Query cancelled due to timeout");
        }
    }

    public FlowControlQueues<QueryInstruction> getQueryQueue() {
        return queryQueue;
    }

    /**
     * Cancels the query with given {@code requestId}.
     *
     * @param requestId the identifier of the query to be cancelled
     */
    public void cancel(String requestId) {
        ActiveQuery activeQuery = queryCache.get(requestId);
        if (activeQuery != null) {
            activeQuery.handlers()
                       .forEach(h -> dispatchCancellation(h,
                                                          activeQuery.getKey(),
                                                          activeQuery.queryName()));
            queryCache.remove(requestId);
        }
    }

    /**
     * Asks for more elements of specific query identified by {@code requestId}.
     *
     * @param requestId the idenifier of the query
     * @param permits   how many elements we expect to be sent
     */
    public void flowControl(String requestId, long permits) {
        ActiveQuery activeQuery = queryCache.get(requestId);
        if (activeQuery != null) {
            activeQuery.handlers().forEach(h -> dispatchFlowControl(h,
                                                                    activeQuery.getKey(),
                                                                    activeQuery.queryName(),
                                                                    permits));
        }
    }

    public void query(SerializedQuery serializedQuery, Authentication principal,
                      Consumer<QueryResponse> callback, Consumer<String> onCompleted) {
        queryRate(serializedQuery.context()).mark();
        DefaultExecutionContext executionContext = new DefaultExecutionContext(serializedQuery.context(),
                                                                               principal);

        Consumer<QueryResponse> interceptedCallback = r -> intercept(executionContext, r, callback);
        try {
            SerializedQuery serializedQuery2 = queryInterceptors.queryRequest(serializedQuery, executionContext);

            QueryRequest query = serializedQuery2.query();

            long timeout =
                    System.currentTimeMillis() + ProcessingInstructionHelper
                            .timeout(query.getProcessingInstructionsList());
            Set<QueryHandler<?>> handlers = registrationCache.find(serializedQuery.context(), query);
            if (handlers.isEmpty()) {
                interceptedCallback.accept(QueryResponse.newBuilder()
                                                        .setErrorCode(ErrorCode.NO_HANDLER_FOR_QUERY.getCode())
                                                        .setRequestIdentifier(query.getMessageIdentifier())
                                                        .setMessageIdentifier(UUID.randomUUID().toString())
                                                        .setErrorMessage(ErrorMessageFactory
                                                                                 .build("No handler for query: " + query
                                                                                         .getQuery()))
                                                        .build());
                onCompleted.accept("NoClient");
            } else {
                QueryDefinition queryDefinition = new QueryDefinition(serializedQuery.context(), query.getQuery());
                ActiveQuery activeQuery = new ActiveQuery(query.getMessageIdentifier(),
                                                          query.getClientId(), queryDefinition,
                                                          interceptedCallback,
                                                          onCompleted,
                                                          handlers, isStreamingQuery(query));
                if(queryCache.putIfAbsent(query.getMessageIdentifier(), activeQuery)!=null){
                    callback.accept(QueryResponse.newBuilder()
                                                 .setErrorCode(ErrorCode.QUERY_DUPLICATED.getCode())
                                                 .setRequestIdentifier(serializedQuery.getMessageIdentifier())
                                                 .setMessageIdentifier(UUID.randomUUID().toString())
                                                 .setErrorMessage(ErrorMessageFactory
                                                                          .build("Query with supplied ID already present"))
                                                 .build());
                    onCompleted.accept("DuplicateId");
                }
                else{
                    handlers.forEach(h -> dispatchQuery(h, serializedQuery2, timeout));
                }
            }
        } catch (InsufficientBufferCapacityException insufficientBufferCapacityException) {
            logger.warn("{}: failed to dispatch query {}", serializedQuery.context(),
                        serializedQuery.query().getQuery(), insufficientBufferCapacityException);
            interceptedCallback.accept(QueryResponse.newBuilder()
                                                    .setErrorCode(ErrorCode.TOO_MANY_REQUESTS.getCode())
                                                    .setRequestIdentifier(serializedQuery.getMessageIdentifier())
                                                    .setMessageIdentifier(UUID.randomUUID().toString())
                                                    .setErrorMessage(ErrorMessageFactory
                                                                             .build(insufficientBufferCapacityException
                                                                                            .getMessage()))
                                                    .build());
            onCompleted.accept("NoCapacity");
        } catch (MessagingPlatformException messagingPlatformException) {
            logger.warn("{}: failed to dispatch query {}", serializedQuery.context(),
                        serializedQuery.query().getQuery(), messagingPlatformException);
            interceptedCallback.accept(QueryResponse.newBuilder()
                                                    .setErrorCode(messagingPlatformException.getErrorCode().getCode())
                                                    .setRequestIdentifier(serializedQuery.getMessageIdentifier())
                                                    .setMessageIdentifier(UUID.randomUUID().toString())
                                                    .setErrorMessage(ErrorMessageFactory
                                                                             .build(messagingPlatformException
                                                                                            .getMessage()))
                                                    .build());
            onCompleted.accept("Rejected");
            executionContext.compensate(messagingPlatformException);
        } catch (Exception otherException) {
            logger.warn("{}: failed to dispatch query {}", serializedQuery.context(),
                        serializedQuery.query().getQuery(), otherException);
            interceptedCallback.accept(QueryResponse.newBuilder()
                                                    .setErrorCode(ErrorCode.OTHER.getCode())
                                                    .setRequestIdentifier(serializedQuery.getMessageIdentifier())
                                                    .setMessageIdentifier(UUID.randomUUID().toString())
                                                    .setErrorMessage(ErrorMessageFactory
                                                                             .build(getOrDefault(otherException
                                                                                                         .getMessage(),
                                                                                                 otherException
                                                                                                         .getClass()
                                                                                                         .getName())))
                                                    .build());
            onCompleted.accept("Failed");
            executionContext.compensate(otherException);
        }
    }

    private void intercept(DefaultExecutionContext executionContext, QueryResponse response,
                           Consumer<QueryResponse> callback) {
        try {
            callback.accept(queryInterceptors.queryResponse(response, executionContext));
        } catch (Exception ex) {
            logger.warn("{}: Exception in response interceptor", executionContext.contextName(), ex);
            callback.accept(QueryResponse.newBuilder()
                                         .setErrorCode(ErrorCode.EXCEPTION_IN_INTERCEPTOR.getCode())
                                         .setRequestIdentifier(response.getRequestIdentifier())
                                         .setMessageIdentifier(UUID.randomUUID().toString())
                                         .setErrorMessage(ErrorMessageFactory
                                                                  .build(ex.getMessage()))
                                         .build());
            executionContext.compensate(ex);
        }
    }

    public MeterFactory.RateMeter queryRate(String context) {
        return queryRatePerContext.computeIfAbsent(context,
                                                   c -> queryMetricsRegistry
                                                           .rateMeter(c, BaseMetricName.AXON_QUERY_RATE));
    }

    /**
     * Cancels the query initiated via other node.
     *
     * @param requestId      the query identifier
     * @param queryName      the query name
     * @param context        the context
     * @param clientStreamId the client stream identifier
     */
    public void cancelProxied(String requestId, String queryName, String context, String clientStreamId) {
        dispatchProxied(context, queryName, clientStreamId,
                        handler -> {
                            dispatchCancellation(handler, requestId, queryName);
                            queryCache.remove(proxiedQueryKey(requestId, clientStreamId));
                        });
    }

    private String proxiedQueryKey(String requestId, String clientStreamId) {
        return requestId + "/" + clientStreamId;
    }

    /**
     * Requests more responses for the query. The request is initiated via other node.
     *
     * @param requestId      the query identifier
     * @param queryName      the query name
     * @param permits        the number of responses to be sent
     * @param context        the context
     * @param clientStreamId the client stream identifier
     */
    public void flowControlProxied(String requestId, String queryName, long permits, String context,
                                   String clientStreamId) {
        dispatchProxied(context, queryName, clientStreamId,
                        handler -> dispatchFlowControl(handler, requestId, queryName, permits));
    }

    private void dispatchProxied(String context, String queryName, String clientStreamId,
                                 Consumer<QueryHandler<?>> action) {
        QueryDefinition queryDefinition = new QueryDefinition(context, queryName);
        QueryHandler<?> queryHandler = registrationCache.find(queryDefinition, clientStreamId);
        if (queryHandler != null) {
            action.accept(queryHandler);
        }
    }

    /**
     * Dispatches the query. The request is initiated via other node.
     *
     * @param serializedQuery the serialized query
     * @param callback        the callback to be invoked for each query result when they are ready
     * @param onCompleted     the callback to be invoked when the query is completed
     */
    public void dispatchProxied(SerializedQuery serializedQuery, Consumer<QueryResponse> callback,
                                Consumer<String> onCompleted) {
        dispatchProxied(serializedQuery, callback, onCompleted, true);
    }

    /**
     * Dispatches the query. The request is initiated via other node.
     *
     * @param serializedQuery the serialized query
     * @param callback        the callback to be invoked for each query result when they are ready
     * @param onCompleted     the callback to be invoked when the query is completed
     * @param streaming       indicates whether results of this query are going to be streamed or not
     */
    public void dispatchProxied(SerializedQuery serializedQuery, Consumer<QueryResponse> callback,
                                Consumer<String> onCompleted, boolean streaming) {
        QueryRequest query = serializedQuery.query();
        long timeout =
                System.currentTimeMillis() + ProcessingInstructionHelper.timeout(query.getProcessingInstructionsList());
        String context = serializedQuery.context();
        String clientId = serializedQuery.clientStreamId();
        QueryHandler<?> queryHandler = registrationCache.find(context, query, clientId);
        if (queryHandler == null) {
            callback.accept(QueryResponse.newBuilder()
                                         .setErrorCode(ErrorCode.CLIENT_DISCONNECTED.getCode())
                                         .setRequestIdentifier(query.getMessageIdentifier())
                                         .setMessageIdentifier(UUID.randomUUID().toString())
                                         .setErrorMessage(
                                                 ErrorMessageFactory
                                                         .build(format("Client %s not found while processing: %s"
                                                                 , clientId, query.getQuery())))
                                         .build());
            onCompleted.accept(clientId);
        } else {
            QueryDefinition queryDefinition = new QueryDefinition(context, query.getQuery());
            String key = proxiedQueryKey(query.getMessageIdentifier(), serializedQuery.clientStreamId());
            ActiveQuery activeQuery = new ActiveQuery(key,
                                                      serializedQuery.query().getClientId(),
                                                      queryDefinition,
                                                      callback,
                                                      onCompleted,
                                                      singleton(queryHandler),
                                                      isStreamingQuery(query));
            try {
                if(queryCache.putIfAbsent(key, activeQuery)!=null){
                    callback.accept(QueryResponse.newBuilder()
                                                 .setErrorCode(ErrorCode.QUERY_DUPLICATED.getCode())
                                                 .setRequestIdentifier(serializedQuery.getMessageIdentifier())
                                                 .setMessageIdentifier(UUID.randomUUID().toString())
                                                 .setErrorMessage(ErrorMessageFactory
                                                                          .build("Query with supplied ID already present"))
                                                 .build());
                    onCompleted.accept("DuplicateId");
                }
                else{
                    dispatchQuery(queryHandler, serializedQuery, timeout, streaming);
                }
            } catch (InsufficientBufferCapacityException insufficientBufferCapacityException) {
                activeQuery.completeWithError(queryHandler.getClientId(),
                                              ErrorCode.QUERY_DISPATCH_ERROR,
                                              insufficientBufferCapacityException.getMessage());
            }
        }
    }

    private boolean isStreamingQuery(QueryRequest query) {
        return ProcessingInstructionHelper.clientSupportsQueryStreaming(query.getProcessingInstructionsList());
    }

    private void dispatchQuery(QueryHandler<?> queryHandler, SerializedQuery query, long timeout) {
        dispatchQuery(queryHandler, query, timeout, true);
    }

    private void dispatchQuery(QueryHandler<?> queryHandler, SerializedQuery query, long timeout, boolean streaming) {
        dispatch(query.getMessageIdentifier(),
                 queryHandler.getClientId(),
                 () -> queryHandler.enqueueQuery(query, queryQueue, timeout, streaming));
    }

    private void dispatchCancellation(QueryHandler<?> queryHandler, String requestId, String queryName) {
        dispatch(requestId,
                 queryHandler.getClientId(),
                 () -> queryHandler.enqueueCancellation(requestId, queryName, queryQueue));
    }

    private void dispatchFlowControl(QueryHandler<?> queryHandler, String requestId, String queryName, long permits) {
        dispatch(requestId,
                 queryHandler.getClientId(),
                 () -> queryHandler.enqueueFlowControl(requestId, queryName, permits, queryQueue));
    }

    private void dispatch(String requestId, String clientId, Runnable action) {
        try {
            action.run();
        } catch (MessagingPlatformException mpe) {
            ActiveQuery information = queryCache.remove(requestId);
            if (information != null) {
                information.completeWithError(clientId, mpe.getErrorCode(), mpe.getMessage());
            }
        }
    }
}
