package io.axoniq.axonserver.cluster.configuration.store;

import io.axoniq.axonserver.cluster.configuration.MembersStore;
import io.axoniq.axonserver.cluster.jpa.JpaRaftGroupNode;
import io.axoniq.axonserver.cluster.jpa.JpaRaftGroupNodeRepository;
import io.axoniq.axonserver.grpc.cluster.Node;

import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Sara Pellegrini
 * @since 4.0
 */
public class JpaMembersStore implements MembersStore {

    private final Supplier<String> groupId;

    private final JpaRaftGroupNodeRepository raftGroupNodeRepository;

    public JpaMembersStore(Supplier<String> groupId,
                           JpaRaftGroupNodeRepository raftGroupNodeRepository) {
        this.groupId = groupId;
        this.raftGroupNodeRepository = raftGroupNodeRepository;
    }

    @Override
    public Collection<Node> get() {
        Set<JpaRaftGroupNode> jpaNodes = raftGroupNodeRepository.findByGroupId(this.groupId.get());
        return jpaNodes.stream().map(jpaNode -> Node.newBuilder()
                                                    .setNodeId(jpaNode.getNodeId())
                                                    .setHost(jpaNode.getHost())
                                                    .setPort(jpaNode.getPort())
                                                    .build())
                       .collect(Collectors.toSet());
    }

    @Override
    public void set(Collection<Node> nodes) {
        String group = this.groupId.get();
        Set<JpaRaftGroupNode> oldNodes = raftGroupNodeRepository.findByGroupId(group);
        raftGroupNodeRepository.deleteAll(oldNodes);
        nodes.forEach(node -> raftGroupNodeRepository.save(new JpaRaftGroupNode(group, node)));
    }
}
