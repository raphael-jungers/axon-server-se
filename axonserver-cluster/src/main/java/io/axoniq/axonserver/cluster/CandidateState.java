package io.axoniq.axonserver.cluster;

import io.axoniq.axonserver.grpc.cluster.AppendEntriesRequest;
import io.axoniq.axonserver.grpc.cluster.AppendEntriesResponse;
import io.axoniq.axonserver.grpc.cluster.AppendEntryFailure;
import io.axoniq.axonserver.grpc.cluster.InstallSnapshotRequest;
import io.axoniq.axonserver.grpc.cluster.InstallSnapshotResponse;
import io.axoniq.axonserver.grpc.cluster.Node;
import io.axoniq.axonserver.grpc.cluster.RequestVoteRequest;
import io.axoniq.axonserver.grpc.cluster.RequestVoteResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;

/**
 * @author Sara Pellegrini
 * @since 4.0
 */
public class CandidateState extends AbstractMembershipState {

    private final RaftGroup raftGroup;
    private final Consumer<MembershipState> transitionHandler;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<ScheduledFuture<?>> currentElectionTimeoutTask = new AtomicReference<>();
    private final Map<String, Boolean> receivedVotes = new ConcurrentHashMap<>();

    public static class Builder extends AbstractMembershipState.Builder {

        private Consumer<MembershipState> transitionHandler;

        public Builder transitionHandler(Consumer<MembershipState> transitionHandler) {
            this.transitionHandler = transitionHandler;
            return this;
        }

        protected void validate() {
            super.validate();

            if (transitionHandler == null) {
                throw new IllegalStateException("The transitionHandler must be provided");
            }
        }

        public CandidateState build() {
            return new CandidateState(this);
        }
    }

    private CandidateState(Builder builder) {
        super(builder);
        this.raftGroup = builder.raftGroup;
        this.transitionHandler = builder.transitionHandler;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void start() {
        onElectionTimeout();
    }

    @Override
    public void stop() {
        executorService.shutdown();
    }

    @Override
    public AppendEntriesResponse appendEntries(AppendEntriesRequest request) {
        if (request.getTerm() >= currentTerm()) {
            FollowerState followerState = new FollowerState(raftGroup, transitionHandler);
            transitionHandler.accept(followerState);
            return followerState.appendEntries(request);
        } else {
            return AppendEntriesResponse.newBuilder()
                                        .setGroupId(request.getGroupId())
                                        .setTerm(currentTerm())
                                        .setFailure(AppendEntryFailure.newBuilder()
                                                                      .setLastAppliedIndex(lastLogAppliedIndex())
//                                                                      .setLastAppliedEventSequence() //TODO
                                                                      .build())
                                        .build();
        }
    }

    @Override
    public RequestVoteResponse requestVote(RequestVoteRequest request) {
        if (request.getTerm() > currentTerm()) {
            FollowerState followerState = new FollowerState(raftGroup, transitionHandler);
            transitionHandler.accept(followerState);
            return followerState.requestVote(request);
        }

        return RequestVoteResponse.newBuilder()
                                  .setGroupId(request.getGroupId())
                                  .setTerm(currentTerm())
                                  .setVoteGranted(false)
                                  .build();
    }

    @Override
    public InstallSnapshotResponse installSnapshot(InstallSnapshotRequest request) {
        if (request.getTerm() > currentTerm()) {
            FollowerState followerState = new FollowerState(raftGroup, transitionHandler);
            transitionHandler.accept(followerState);
            return followerState.installSnapshot(request);
        }
        return InstallSnapshotResponse.newBuilder()
                                      .setGroupId(request.getGroupId())
                                      .setTerm(currentTerm())
                                      .build();
    }

    private void resetElectionTimeout() {
        Optional.ofNullable(currentElectionTimeoutTask.get()).ifPresent(task -> task.cancel(true));
        long timeout = ThreadLocalRandom.current().nextLong(minElectionTimeout(), maxElectionTimeout());
        ScheduledFuture<?> newTimeoutTask = executorService.schedule(this::onElectionTimeout, timeout, MILLISECONDS);
        currentElectionTimeoutTask.set(newTimeoutTask);
    }

    private void onElectionTimeout() {
        updateCurrentTerm(currentTerm() + 1);
        markVotedFor(me());
        resetElectionTimeout();
        RequestVoteRequest request = RequestVoteRequest.newBuilder()
                                                       //.setGroupId(groupId) //TODO
                                                       .setCandidateId(me())
                                                       .setTerm(currentTerm())
                                                       .setLastLogIndex(lastLogIndex())
                                                       .setLastLogTerm(lastLogTerm())
                                                       .build();
        otherNodes().forEach(node -> requestVote(request, node));
    }

    Iterable<RaftPeer> otherNodes() {
        List<Node> nodes = raftGroup.raftConfiguration().groupMembers();
        return nodes.stream()
                    .map(Node::getNodeId)
                    .filter(id -> !id.equals(me()))
                    .map(raftGroup::peer)
                    .collect(toList());
    }

    private void requestVote(RequestVoteRequest request, RaftPeer node) {
        node.requestVote(request).thenAccept(response -> onVoteResponse(node.nodeId(), response));
    }

    private void onVoteResponse(String voter, RequestVoteResponse response) {
        if (response.getTerm() > currentTerm()) {
            transitionHandler.accept(new FollowerState(raftGroup, transitionHandler));
            return;
        }
        this.receivedVotes.put(voter, response.getVoteGranted());
        if (electionWon()) {
            LeaderState leaderState = LeaderState.builder().build();
            stop();
            transitionHandler.accept(leaderState);
            leaderState.start();
        }
    }

    private boolean electionWon() {
        return false; //TODO
    }
}
