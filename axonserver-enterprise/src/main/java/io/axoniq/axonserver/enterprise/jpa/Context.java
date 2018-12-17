package io.axoniq.axonserver.enterprise.jpa;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.PreRemove;

/**
 * Author: marc
 */
@Entity(name = "Context")
public class Context implements Serializable {
    @Id
    private String name;

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "key.context", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ContextClusterNode> nodes = new HashSet<>();

    public Context() {
    }

    public Context(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<ClusterNode> getNodes() {
        return nodes.stream().map(ContextClusterNode::getClusterNode).collect(Collectors.toSet());
    }

    public Collection<String> getNodeNames() {
        return nodes.stream().filter(ContextClusterNode::isMessaging).map(t -> t.getClusterNode().getName()).collect(Collectors.toSet());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Context context1 = (Context) o;
        return Objects.equals(name, context1.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @PreRemove
    public void clearContexts() {
        nodes.forEach(ccn -> ccn.getClusterNode().remove(ccn));
        nodes.clear();
    }

    public void remove(ContextClusterNode ccn) {
        nodes.remove(ccn);
    }

    public Set<ContextClusterNode> getAllNodes() {
        return nodes;
    }

    public void addClusterNode(ContextClusterNode contextClusterNode) {
        nodes.add(contextClusterNode);
    }
}
