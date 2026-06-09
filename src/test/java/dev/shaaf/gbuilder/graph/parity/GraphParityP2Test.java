package dev.shaaf.gbuilder.graph.parity;

import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.GraphStoreLocation;
import dev.shaaf.gbuilder.graph.SemanticGraphService;
import dev.shaaf.gbuilder.graph.query.GraphQueryService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GraphParityP2Test {

    @Inject SemanticGraphService graphService;
    @Inject GraphRepository graphRepo;
    @Inject GraphStoreLocation storeLocation;
    @Inject GraphQueryService queryService;

    @BeforeEach
    void clean() {
        storeLocation.reset();
        graphRepo.clearAll();
    }

    @Test
    void bfsQueryReturnsNeighborsWithinDepth() throws Exception {
        Path root = writeFixture();
        try {
            graphService.buildGraph(root);
            var result = queryService.query("Service", false, 3000);
            assertFalse(result.visitedFqns().isEmpty());
            assertTrue(result.answerText().contains("Query:"));
            assertTrue(result.answerText().contains("Service"));
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void dfsQueryTracesCallChain() throws Exception {
        Path root = writeFixture();
        try {
            graphService.buildGraph(root);
            var result = queryService.query("Service", true, 3000);
            assertFalse(result.visitedFqns().isEmpty());
            assertTrue(result.answerText().contains("[depth"));
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void queryRespectsTokenBudget() throws Exception {
        Path root = writeFixture();
        try {
            graphService.buildGraph(root);
            var small = queryService.query("Service", false, 50);
            var large = queryService.query("Service", false, 5000);
            assertTrue(small.answerText().length() <= large.answerText().length());
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void queryHandlesUnknownType() throws Exception {
        Path root = writeFixture();
        try {
            graphService.buildGraph(root);
            var result = queryService.query("NonExistentType", false, 1000);
            assertTrue(result.answerText().contains("No matching types"));
        } finally {
            deleteRecursive(root);
        }
    }

    private Path writeFixture() throws Exception {
        Path root = Files.createTempDirectory("p2-query-");
        Files.writeString(root.resolve("Config.java"), """
            package com.app;
            public class Config { public String v() { return "x"; } }
            """);
        Files.writeString(root.resolve("Service.java"), """
            package com.app;
            import com.app.Config;
            public class Service {
                public void run() { new Config().v(); helper(); }
                private void helper() {}
            }
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
