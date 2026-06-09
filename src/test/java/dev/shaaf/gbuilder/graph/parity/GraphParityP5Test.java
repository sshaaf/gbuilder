package dev.shaaf.gbuilder.graph.parity;

import dev.shaaf.gbuilder.graph.EdgeProvenance;
import dev.shaaf.gbuilder.graph.GraphBuildOptions;
import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.GraphStoreLocation;
import dev.shaaf.gbuilder.graph.SemanticGraphService;
import dev.shaaf.gbuilder.graph.export.GraphExportService;
import dev.shaaf.gbuilder.graph.merge.GraphMergeService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GraphParityP5Test {

    @Inject SemanticGraphService graphService;
    @Inject GraphRepository graphRepo;
    @Inject GraphStoreLocation storeLocation;
    @Inject GraphMergeService mergeService;
    @Inject GraphExportService exportService;

    @BeforeEach
    void clean() {
        storeLocation.reset();
        graphRepo.clearAll();
    }

    @Test
    void deepModeAddsInferredSemanticEdges() throws Exception {
        Path root = writeSimilarTypesFixture();
        try {
            graphService.buildGraph(root, dev.shaaf.gbuilder.lang.ParserBackend.JPARSER,
                    GraphBuildOptions.defaults().withDeepMode(true));
            boolean inferred = graphRepo.listClassLevelEdges().stream()
                    .anyMatch(e -> "SEMANTICALLY_SIMILAR".equals(e.type())
                            && e.provenance() == EdgeProvenance.INFERRED);
            assertTrue(inferred);
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void deepModeAddsTechnologyHyperedges() throws Exception {
        Path root = writeSpringFixture();
        try {
            graphService.buildGraph(root, dev.shaaf.gbuilder.lang.ParserBackend.JPARSER,
                    GraphBuildOptions.defaults().withDeepMode(true));
            assertFalse(graphRepo.listHyperedges().isEmpty());
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void neo4jExportContainsMergeStatements() throws Exception {
        Path root = writeChainFixture();
        try {
            graphService.buildGraph(root);
            exportService.exportNeo4jCypher(root);
            String cypher = Files.readString(root.resolve(".gbuilder/cypher.txt"));
            assertTrue(cypher.contains("MERGE (n:Type"));
            assertTrue(cypher.contains("EXTENDS"));
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void saveQueryResultPersistsQaNode() throws Exception {
        Path root = writeSimilarTypesFixture();
        try {
            graphService.buildGraph(root);
            graphRepo.setStoreRoot(root);
            graphService.saveQueryResult("What calls Worker?", "Worker.run calls step1", java.util.List.of("deep.Worker"));
            assertFalse(graphRepo.listQaResults().isEmpty());
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void mergeCombinesTwoGraphDatabases() throws Exception {
        Path primary = Files.createTempDirectory("p5-primary-");
        Path secondary = Files.createTempDirectory("p5-secondary-");
        try {
            Files.writeString(primary.resolve("P.java"), "package p; public class P {}");
            Files.writeString(secondary.resolve("S.java"), "package s; public class S {}");
            graphRepo.setStoreRoot(primary);
            graphService.buildGraph(primary);
            graphRepo.setStoreRoot(secondary);
            graphService.buildGraph(secondary);

            mergeService.mergeDatabases(primary, secondary, "secondary-repo");
            graphRepo.setStoreRoot(primary);
            var fqns = graphRepo.listInternalClassFqns();
            assertTrue(fqns.contains("p.P"));
            assertTrue(fqns.contains("s.S"));
        } finally {
            deleteRecursive(primary);
            deleteRecursive(secondary);
        }
    }

    @Test
    void girvanNewmanClusteringRuns() throws Exception {
        Path root = writeSimilarTypesFixture();
        try {
            var outcome = graphService.buildGraph(root, dev.shaaf.gbuilder.lang.ParserBackend.JPARSER,
                    GraphBuildOptions.defaults().withClusteringAlgorithm("girvan-newman"));
            assertTrue(outcome.result().communityCount() >= 1);
        } finally {
            deleteRecursive(root);
        }
    }

    private Path writeSimilarTypesFixture() throws Exception {
        Path root = Files.createTempDirectory("p5-sem-");
        Files.writeString(root.resolve("Worker.java"), """
            package deep;
            public class Worker { public void run() {} public void stop() {} }
            """);
        Files.writeString(root.resolve("Helper.java"), """
            package deep;
            public class Helper { public void run() {} public void stop() {} }
            """);
        return root;
    }

    private Path writeSpringFixture() throws Exception {
        Path root = Files.createTempDirectory("p5-jaxrs-");
        for (String name : List.of("R1", "R2", "R3")) {
            Files.writeString(root.resolve(name + ".java"), """
                package api;
                import javax.ws.rs.Path;
                import javax.ws.rs.GET;
                @Path("/%s")
                public class %s {
                    @GET public String get() { return "ok"; }
                }
                """.formatted(name.toLowerCase(), name));
        }
        return root;
    }

    private Path writeChainFixture() throws Exception {
        Path root = Files.createTempDirectory("p5-chain-");
        Files.writeString(root.resolve("A.java"), """
            package chain;
            public class A extends B {}
            """);
        Files.writeString(root.resolve("B.java"), """
            package chain;
            public class B {}
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
