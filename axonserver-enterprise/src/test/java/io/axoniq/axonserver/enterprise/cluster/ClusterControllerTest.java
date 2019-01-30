package io.axoniq.axonserver.enterprise.cluster;

import io.axoniq.axonserver.AxonServerEnterprise;
import io.axoniq.axonserver.TestSystemInfoProvider;
import io.axoniq.axonserver.access.modelversion.ModelVersionController;
import io.axoniq.axonserver.config.AccessControlConfiguration;
import io.axoniq.axonserver.config.ClusterConfiguration;
import io.axoniq.axonserver.config.MessagingPlatformConfiguration;
import io.axoniq.axonserver.enterprise.cluster.internal.MessagingClusterServiceInterface;
import io.axoniq.axonserver.enterprise.cluster.internal.RemoteConnection;
import io.axoniq.axonserver.enterprise.cluster.internal.StubFactory;
import io.axoniq.axonserver.enterprise.jpa.ClusterNode;
import io.axoniq.axonserver.enterprise.jpa.Context;
import io.axoniq.axonserver.features.FeatureChecker;
import io.axoniq.axonserver.grpc.internal.NodeInfo;
import io.axoniq.axonserver.licensing.Limits;
import io.axoniq.axonserver.message.command.CommandDispatcher;
import io.axoniq.axonserver.message.query.QueryDispatcher;
import io.axoniq.axonserver.topology.Topology;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.persistence.EntityManager;

import static io.axoniq.axonserver.util.AssertUtils.assertWithin;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Marc Gathier
 */
@RunWith(SpringRunner.class)
@DataJpaTest
@ComponentScan(basePackages = "io.axoniq.axonserver.enterprise.context", lazyInit = true)
@ContextConfiguration(classes = AxonServerEnterprise.class)
public class ClusterControllerTest {
    private ClusterController testSubject;
    @Mock
    private NodeSelectionStrategy nodeSelectionStrategy;
    @Mock
    private Limits limits;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private EntityManager entityManager;
    @Mock
    private CommandDispatcher commandDispatcher;

    @Mock
    private QueryDispatcher queryDispatcher;

    @Mock
    private ModelVersionController modelVersionController;

    @Before
    public void setUp()  {
        Context context = new Context(Topology.DEFAULT_CONTEXT);
        FeatureChecker limits = new FeatureChecker() {
            @Override
            public boolean isEnterprise() {
                return true;
            }

            @Override
            public int getMaxClusterSize() {
                return 5;
            }
        };
        ClusterNode clusterNode = new ClusterNode("MyName", "LAPTOP-1QH9GIHL.axoniq.io", "LAPTOP-1QH9GIHL.axoniq.net", 8124, 8224, 8024);
        clusterNode.addContext(context, true, true);
        entityManager.persist(clusterNode);

        MessagingPlatformConfiguration messagingPlatformConfiguration = new MessagingPlatformConfiguration(new TestSystemInfoProvider());
        messagingPlatformConfiguration.setAccesscontrol(new AccessControlConfiguration());
        messagingPlatformConfiguration.setName("MyName");

        messagingPlatformConfiguration.setHostname("LAPTOP-1QH9GIHL");
        messagingPlatformConfiguration.setDomain("axoniq.io");
        messagingPlatformConfiguration.setInternalDomain("axoniq.net");
        messagingPlatformConfiguration.setCluster(new ClusterConfiguration());

        StubFactory stubFactory = new StubFactory() {
            @Override
            public MessagingClusterServiceInterface messagingClusterServiceStub(
                    ClusterNode clusterNode) {
                return new TestMessagingClusterService();
            }

            @Override
            public MessagingClusterServiceInterface messagingClusterServiceStub(
                    String host, int port) {
                return new TestMessagingClusterService();
            }

        };

        testSubject = new ClusterController(messagingPlatformConfiguration, entityManager,
                                            stubFactory,
                                            nodeSelectionStrategy, queryDispatcher, commandDispatcher,
                                            modelVersionController,
                                            eventPublisher, limits);
    }

    @Test
    public void startAndStop()  {
        assertTrue(testSubject.isAutoStartup());
        assertFalse(testSubject.isRunning());
        testSubject.start();
        assertTrue(testSubject.isRunning());
        AtomicBoolean stopped = new AtomicBoolean(false);
        testSubject.stop(() -> stopped.set(true));
        assertFalse(testSubject.isRunning());
        assertTrue(stopped.get());
    }

    @Test
    public void getNodes() throws InterruptedException {
        entityManager.persist(new ClusterNode("name", "hostName", "localhost", 0, 1000, 0));
        testSubject.start();
        Thread.sleep(250);
        Collection<RemoteConnection> nodes = testSubject.getRemoteConnections();

        assertEquals(1, nodes.size());
        Iterator<RemoteConnection> remoteConnectionIterator = nodes.iterator();
        RemoteConnection remoteConnection = remoteConnectionIterator.next();
        assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(remoteConnection.isConnected()));
    }

    @Test
    public void addConnection()  {
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        testSubject.addNodeListener(event -> listenerCalled.set(true));
        testSubject.addConnection(NodeInfo.newBuilder()
                .setNodeName("newName")
                .setInternalHostName("newHostName")
                .setGrpcInternalPort(0)
                .build(), 5);

        Collection<RemoteConnection> nodes = testSubject.getRemoteConnections();
        assertEquals(1, nodes.size());
        assertFalse(nodes.iterator().next().isConnected());
        assertTrue(listenerCalled.get());
    }

    @Test
    public void getMe()  {
        ClusterNode me = testSubject.getMe();
        assertEquals("MyName", me.getName());
        assertEquals("LAPTOP-1QH9GIHL.axoniq.io", me.getHostName());
        assertEquals("LAPTOP-1QH9GIHL.axoniq.net", me.getInternalHostName());
    }

    @Test
    public void findNodeForClient() {
        List<ClusterNode> clusterNodes = new ArrayList<>();
        clusterNodes.add(new ClusterNode("MyName", "hostName", "internalHostName", 0, 0, 0));
        Context context = new Context(Topology.DEFAULT_CONTEXT);
        clusterNodes.get(0).addContext(context, true, true);
        testSubject.start();
        when(nodeSelectionStrategy.selectNode(any(), any(), any())).thenReturn("Dummy");
        ClusterNode node = testSubject.findNodeForClient("client", "component", Topology.DEFAULT_CONTEXT);
        assertEquals(testSubject.getMe(), node);
    }

    @Test
    public void messagingNodes() {
        assertEquals(1, testSubject.nodes().count());
        testSubject.addConnection(NodeInfo.newBuilder()
                .setNodeName("newName")
                .setInternalHostName("newHostName")
                .setGrpcInternalPort(0)
                .build(), 10);
        assertEquals(2, testSubject.nodes().count());
        testSubject.addConnection(NodeInfo.newBuilder()
                .setNodeName("newName")
                .setInternalHostName("newHostName")
                .setGrpcInternalPort(0)
                .build(), 11);
        assertEquals(2, testSubject.nodes().count());
    }

    @Test
    public void canRebalance()  {
        assertFalse(testSubject.canRebalance("client", "component", Topology.DEFAULT_CONTEXT));
    }

    @Test
    public void sendDeleteNode() {
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        testSubject.addNodeListener(event -> {
            if(ClusterEvent.EventType.NODE_DELETED.equals(event.getEventType())) listenerCalled.set(true);
        });
        testSubject.addConnection(NodeInfo.newBuilder()
                .setNodeName("newName")
                .setInternalHostName("newHostName")
                .setGrpcInternalPort(0)
                .build(), 10);
        testSubject.addConnection(NodeInfo.newBuilder()
                .setNodeName("deletedNode")
                .setInternalHostName("newHostName")
                .setGrpcInternalPort(0)
                .build(), 11);


        testSubject.sendDeleteNode("deletedNode");
        assertTrue(listenerCalled.get());
    }

}
