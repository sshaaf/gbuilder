package dev.shaaf.gbuilder.graph.events;

import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.GraphStoreLocation;
import dev.shaaf.gbuilder.graph.GraphTestFixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GraphEventProcessorIntegrationTest {

    @Inject
    Event<NodeMigratedEvent> migratedEventBus;

    @Inject
    Event<NodeSplitEvent> splitEventBus;

    @Inject
    Event<SignatureChangedEvent> signatureEventBus;

    @Inject
    GraphRepository graphRepo;

    @Inject
    GraphStoreLocation storeLocation;

    @BeforeEach
    void cleanGraph() {
        storeLocation.reset();
        graphRepo.clearAll();
    }

    @Test
    void firingNodeMigratedEventShouldUpdateStatus() {
        graphRepo.persistClassNode(GraphTestFixtures.classNode(
                "com.test.EventTarget", "EventTarget", List.of(), List.of()));

        migratedEventBus.fire(new NodeMigratedEvent(
                "com.test.EventTarget", "/new/path.java", Instant.now()));

        assertEquals("MIGRATED", graphRepo.getClassStatus("com.test.EventTarget").orElseThrow());
    }

    @Test
    void firingNodeSplitEventShouldDeleteOldNode() {
        var method = GraphTestFixtures.method("doWork", "public void doWork()", "void", "{}");
        graphRepo.persistClassNode(GraphTestFixtures.classNode(
                "com.test.SplitTarget", "SplitTarget", List.of(), List.of(),
                List.of(method), List.of()));

        splitEventBus.fire(new NodeSplitEvent(
                "com.test.SplitTarget",
                List.of("com.test.SplitA", "com.test.SplitB"),
                Instant.now()));

        assertFalse(graphRepo.classExists("com.test.SplitTarget"));
        assertFalse(graphRepo.methodExists("com.test.SplitTarget", "public void doWork()"));
    }

    @Test
    void firingSignatureChangedEventShouldUpdateSignature() {
        var method = GraphTestFixtures.method(
                "oldMethod", "public void oldMethod()", "void", "{}");
        graphRepo.persistClassNode(GraphTestFixtures.classNode(
                "com.test.SigClass", "SigClass", List.of(), List.of(),
                List.of(method), List.of()));

        signatureEventBus.fire(new SignatureChangedEvent(
                "com.test.SigClass#oldMethod",
                "public void oldMethod()",
                "public void newMethod()",
                Instant.now()));

        assertFalse(graphRepo.methodExists("com.test.SigClass", "public void oldMethod()"));
        assertTrue(graphRepo.methodExists("com.test.SigClass", "public void newMethod()"));
    }
}
