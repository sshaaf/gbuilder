package dev.shaaf.gbuilder.graph;

import dev.shaaf.gbuilder.graph.model.ClassNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CommunityDetectorTest {

    @Inject
    GraphRepository graphRepo;

    @Inject
    GraphStoreLocation storeLocation;

    @Inject
    CommunityDetector communityDetector;

    @BeforeEach
    void cleanGraph() {
        storeLocation.reset();
        graphRepo.clearAll();
    }

    @Test
    void shouldAssignConnectedClassesToSameCommunity() {
        ClassNode billing = GraphTestFixtures.classNode(
                "com.app.BillingService", "BillingService", List.of(), List.of("com.app.OrderDao"));
        ClassNode orderDao = GraphTestFixtures.classNode(
                "com.app.OrderDao", "OrderDao", List.of(), List.of());
        ClassNode unrelated = GraphTestFixtures.classNode(
                "com.other.OtherService", "OtherService", List.of(), List.of());

        graphRepo.persistClassNode(billing);
        graphRepo.persistClassNode(orderDao);
        graphRepo.persistClassNode(unrelated);
        graphRepo.createDependsOnEdge("com.app.BillingService", "com.app.OrderDao");

        Map<String, Integer> communities = communityDetector.detectAndAssign();

        assertEquals(3, communities.size());
        assertEquals(communities.get("com.app.BillingService"), communities.get("com.app.OrderDao"));
        assertNotEquals(communities.get("com.app.BillingService"), communities.get("com.other.OtherService"));
        assertTrue(graphRepo.getCommunitySummary(communities.get("com.app.BillingService")).isPresent());
    }

    @Test
    void shouldExposeCommunityClassList() {
        graphRepo.persistClassNode(GraphTestFixtures.classNode(
                "com.slice.A", "A", List.of(), List.of("com.slice.B")));
        graphRepo.persistClassNode(GraphTestFixtures.classNode(
                "com.slice.B", "B", List.of(), List.of()));
        graphRepo.createDependsOnEdge("com.slice.A", "com.slice.B");

        communityDetector.detectAndAssign();
        int communityId = graphRepo.getCommunityId("com.slice.A").orElseThrow();

        assertEquals(Set.of("com.slice.A", "com.slice.B"),
                Set.copyOf(graphRepo.getCommunityClassFqns(communityId)));
    }
}
