package io.axoniq.axonserver.enterprise.cluster;

import io.axoniq.axonserver.grpc.Confirmation;
import io.axoniq.axonserver.grpc.internal.Application;
import io.axoniq.axonserver.grpc.internal.Context;
import io.axoniq.axonserver.grpc.internal.ContextMember;
import io.axoniq.axonserver.grpc.internal.ContextName;
import io.axoniq.axonserver.grpc.internal.ContextNames;
import io.axoniq.axonserver.grpc.internal.LoadBalanceStrategy;
import io.axoniq.axonserver.grpc.internal.NodeContext;
import io.axoniq.axonserver.grpc.internal.NodeInfo;
import io.axoniq.axonserver.grpc.internal.ProcessorLBStrategy;
import io.axoniq.axonserver.grpc.internal.RaftConfigServiceGrpc;
import io.axoniq.axonserver.grpc.internal.User;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Author: marc
 */
public class RemoteRaftConfigService implements RaftConfigService {

    private final RaftConfigServiceGrpc.RaftConfigServiceStub raftConfigServiceStub;

    public RemoteRaftConfigService(RaftConfigServiceGrpc.RaftConfigServiceStub raftConfigServiceStub) {
        this.raftConfigServiceStub = raftConfigServiceStub;
    }

    @Override
    public void addNodeToContext(String context, String node) {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        raftConfigServiceStub.addNodeToContext(NodeContext.newBuilder().setNode(node).setContext(context).build(),
                                               new CompletableStreamObserver(completableFuture));
        wait(completableFuture);
    }

    @Override
    public void deleteContext(String context) {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        raftConfigServiceStub.deleteContext(ContextName.newBuilder().setContext(context).build(), new CompletableStreamObserver(completableFuture));
        wait(completableFuture);
    }

    private void wait(CompletableFuture<?> completableFuture) {
        try {
            completableFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteNodeFromContext(String context, String node) {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        raftConfigServiceStub.deleteNodeFromContext(NodeContext.newBuilder().setNode(node).setContext(context).build(),
                                               new CompletableStreamObserver(completableFuture));
        wait(completableFuture);
    }

    @Override
    public void addContext(String context, List<String> nodes) {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        raftConfigServiceStub.createContext(Context
                                                    .newBuilder()
                                                    .setName(context)
                                                    .addAllMembers(nodes.stream().map(n -> ContextMember.newBuilder().setNodeId(n).build()).collect(
                                                            Collectors.toList()))
                                                    .build(), new CompletableStreamObserver(completableFuture));
        wait(completableFuture);
    }

    @Override
    public void join(NodeInfo nodeInfo) {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        raftConfigServiceStub.joinCluster(nodeInfo, new CompletableStreamObserver(completableFuture));
        wait(completableFuture);
    }

    @Override
    public void init(List<String> contexts) {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        raftConfigServiceStub.initCluster(ContextNames.newBuilder().addAllContexts(contexts).build(), new CompletableStreamObserver(completableFuture));
        wait(completableFuture);

    }

    @Override
    public CompletableFuture<Void> updateApplication(Application application) {
        CompletableFuture<Void> confirmation = new CompletableFuture<>();
        raftConfigServiceStub.updateApplication(application, new StreamObserver<Confirmation>() {
            @Override
            public void onNext(Confirmation o) {
                confirmation.complete(null);
            }

            @Override
            public void onError(Throwable throwable) {
                confirmation.completeExceptionally(throwable);
            }

            @Override
            public void onCompleted() {

            }
        });
        return confirmation;
    }

    @Override
    public CompletableFuture<Void> updateUser(User request) {
        CompletableFuture<Void> confirmation = new CompletableFuture<>();
        raftConfigServiceStub.updateUser(request, new CompletableStreamObserver(confirmation));
        return confirmation;
    }

    @Override
    public CompletableFuture<Void> updateLoadBalancingStrategy(LoadBalanceStrategy loadBalancingStrategy) {
        CompletableFuture<Void> confirmation = new CompletableFuture<>();
        raftConfigServiceStub.updateLoadBalanceStrategy(loadBalancingStrategy, new CompletableStreamObserver(confirmation));
        return confirmation;
    }

    @Override
    public CompletableFuture<Void> deleteLoadBalancingStrategy(LoadBalanceStrategy loadBalancingStrategy) {
        CompletableFuture<Void> confirmation = new CompletableFuture<>();
        raftConfigServiceStub.deleteLoadBalanceStrategy(loadBalancingStrategy, new CompletableStreamObserver(confirmation));
        return confirmation;
    }

    @Override
    public CompletableFuture<Void> updateProcessorLoadBalancing(ProcessorLBStrategy processorLBStrategy) {
        CompletableFuture<Void> confirmation = new CompletableFuture<>();
        raftConfigServiceStub.updateProcessorLBStrategy(processorLBStrategy, new CompletableStreamObserver(confirmation));
        return confirmation;
    }

    @Override
    public CompletableFuture<Void> deleteUser(User request) {
        CompletableFuture<Void> confirmation = new CompletableFuture<>();
        raftConfigServiceStub.deleteUser(request, new CompletableStreamObserver(confirmation));
        return confirmation;
    }

    @Override
    public CompletableFuture<Void> deleteApplication(Application request) {
        CompletableFuture<Void> confirmation = new CompletableFuture<>();
        raftConfigServiceStub.deleteApplication(request, new CompletableStreamObserver(confirmation));
        return confirmation;
    }
}