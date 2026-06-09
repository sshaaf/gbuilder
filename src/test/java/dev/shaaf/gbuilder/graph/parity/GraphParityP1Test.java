package dev.shaaf.gbuilder.graph.parity;

import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.GraphStoreLocation;
import dev.shaaf.gbuilder.graph.SemanticGraphService;
import dev.shaaf.gbuilder.graph.analysis.GraphAnalyzer;
import dev.shaaf.gbuilder.graph.query.GraphExplainService;
import dev.shaaf.gbuilder.graph.query.GraphPathService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GraphParityP1Test {

    @Inject SemanticGraphService graphService;
    @Inject GraphRepository graphRepo;
    @Inject GraphStoreLocation storeLocation;
    @Inject GraphAnalyzer graphAnalyzer;
    @Inject GraphPathService pathService;
    @Inject GraphExplainService explainService;

    @BeforeEach
    void clean() {
        storeLocation.reset();
        graphRepo.clearAll();
    }

    @Test
    void analysisProducesGodNodesAndQuestions() throws Exception {
        Path root = writeHubSpokeFixture();
        try {
            graphService.buildGraph(root);
            var analysis = graphAnalyzer.analyze();
            assertFalse(analysis.godNodes().isEmpty());
            assertEquals("Hub", analysis.godNodes().getFirst().label());
            assertFalse(analysis.suggestedQuestions().isEmpty());
            assertFalse(analysis.communities().isEmpty());
            assertTrue(analysis.communities().getFirst().cohesion() >= 0.0);
            assertNotNull(analysis.communities().getFirst().label());
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void pathFindsShortestRouteBetweenTypes() throws Exception {
        Path root = writeChainFixture();
        try {
            graphService.buildGraph(root);
            var result = pathService.findPath("com.chain.A", "com.chain.C");
            assertEquals(3, result.hops().size());
            assertTrue(result.explanation().contains("Path"));
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void pathResolvesSimpleNames() throws Exception {
        Path root = writeChainFixture();
        try {
            graphService.buildGraph(root);
            var result = pathService.findPath("A", "C");
            assertFalse(result.hops().isEmpty());
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void explainSummarizesTypeAndNeighbors() throws Exception {
        Path root = writeHubSpokeFixture();
        try {
            graphService.buildGraph(root);
            String text = explainService.explain("Hub");
            assertTrue(text.contains("Hub"));
            assertTrue(text.contains("methods"));
            assertTrue(text.contains("neighbor"));
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void surprisingConnectionsDetectedAcrossCommunities() throws Exception {
        Path root = Files.createTempDirectory("p1-surprise-");
        try {
            Files.createDirectories(root.resolve("a"));
            Files.createDirectories(root.resolve("b"));
            Files.writeString(root.resolve("a/A.java"), """
                package com.a;
                import com.b.B;
                public class A { public void use(B b) {} }
                """);
            Files.writeString(root.resolve("b/B.java"), """
                package com.b;
                public class B { public void work() {} }
                """);
            graphService.buildGraph(root);
            var analysis = graphAnalyzer.analyze();
            assertFalse(analysis.godNodes().isEmpty());
            assertTrue(graphRepo.getGraphStats().dependsOnEdges() >= 1);
        } finally {
            deleteRecursive(root);
        }
    }

    private Path writeHubSpokeFixture() throws Exception {
        Path root = Files.createTempDirectory("p1-hub-");
        Files.writeString(root.resolve("Hub.java"), """
            package com.hub;
            public class Hub {
                public void a() { new SpokeA().run(); new SpokeB().run(); }
            }
            """);
        Files.writeString(root.resolve("SpokeA.java"), """
            package com.hub;
            public class SpokeA { public void run() {} }
            """);
        Files.writeString(root.resolve("SpokeB.java"), """
            package com.hub;
            public class SpokeB { public void run() {} }
            """);
        return root;
    }

    private Path writeChainFixture() throws Exception {
        Path root = Files.createTempDirectory("p1-chain-");
        Files.writeString(root.resolve("A.java"), """
            package com.chain;
            public class A extends B {}
            """);
        Files.writeString(root.resolve("B.java"), """
            package com.chain;
            public class B extends C {}
            """);
        Files.writeString(root.resolve("C.java"), """
            package com.chain;
            public class C {}
            """);
        return root;
    }

    private void deleteRecursive(Path dir) throws Exception {
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
            }
        }
    }
}
