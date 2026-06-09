package dev.shaaf.gbuilder.graph.parity;

import dev.shaaf.gbuilder.graph.GraphBuildOptions;
import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.GraphStoreLocation;
import dev.shaaf.gbuilder.graph.SemanticGraphService;
import dev.shaaf.gbuilder.graph.benchmark.TokenBenchmarkService;
import dev.shaaf.gbuilder.graph.hook.GraphHookService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GraphParityP4Test {

    @Inject SemanticGraphService graphService;
    @Inject GraphRepository graphRepo;
    @Inject GraphStoreLocation storeLocation;
    @Inject GraphHookService hookService;
    @Inject TokenBenchmarkService benchmarkService;

    @BeforeEach
    void clean() {
        storeLocation.reset();
        graphRepo.clearAll();
    }

    @Test
    void clusterOnlyReclustersWithoutReparsing() throws Exception {
        Path root = writeFixture();
        try {
            graphService.buildGraph(root);
            long typesBefore = graphRepo.getGraphStats().typeCount();
            var outcome = graphService.buildGraph(root, dev.shaaf.gbuilder.lang.ParserBackend.JPARSER,
                    GraphBuildOptions.defaults().withClusterOnly(true));
            assertEquals(typesBefore, outcome.result().typeCount());
            assertTrue(outcome.result().communityCount() >= 1);
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void hookInstallAndStatus() throws Exception {
        Path root = Files.createTempDirectory("p4-hook-");
        Path git = root.resolve(".git");
        Path hooks = git.resolve("hooks");
        try {
            Files.createDirectories(hooks);
            hookService.install(root);
            Path postCommit = hooks.resolve("post-commit");
            assertTrue(Files.exists(postCommit));
            assertTrue(Files.readString(postCommit).contains("gbuilder"));
            assertEquals(GraphHookService.HookStatus.INSTALLED, hookService.status(root));
            hookService.uninstall(root);
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void tokenBenchmarkProducesRatio() throws Exception {
        Path root = writeFixture();
        try {
            graphService.buildGraph(root);
            var nodes = graphRepo.findAllClassNodes();
            var bench = benchmarkService.benchmark(root, nodes);
            assertTrue(bench.rawTokens() > 0);
            assertTrue(bench.graphTokens() > 0);
            assertNotNull(bench.summary());
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void buildWithBenchmarkFlagReturnsBenchmark() throws Exception {
        Path root = writeMultiTypeFixture();
        try {
            var outcome = graphService.buildGraph(root, dev.shaaf.gbuilder.lang.ParserBackend.JPARSER,
                    GraphBuildOptions.defaults().withBenchmark(true));
            assertNotNull(outcome.benchmark());
            assertTrue(outcome.benchmark().reductionRatio() > 0);
        } finally {
            deleteRecursive(root);
        }
    }

    private Path writeFixture() throws Exception {
        Path root = Files.createTempDirectory("p4-fix-");
        Files.writeString(root.resolve("Worker.java"), """
            package auto;
            public class Worker {
                public void run() { step1(); step2(); }
                private void step1() {}
                private void step2() {}
            }
            """);
        return root;
    }

    private Path writeMultiTypeFixture() throws Exception {
        Path root = Files.createTempDirectory("p4-bench-");
        for (int i = 0; i < 6; i++) {
            Files.writeString(root.resolve("T" + i + ".java"), """
                package bench;
                public class T%d {
                    public void m() { helper(); }
                    private void helper() {}
                }
                """.formatted(i));
        }
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
