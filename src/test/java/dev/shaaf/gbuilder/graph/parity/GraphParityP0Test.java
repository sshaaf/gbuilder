package dev.shaaf.gbuilder.graph.parity;

import dev.shaaf.gbuilder.graph.EdgeProvenance;
import dev.shaaf.gbuilder.graph.GraphBuildOptions;
import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.GraphStoreLocation;
import dev.shaaf.gbuilder.graph.SemanticGraphService;
import dev.shaaf.gbuilder.graph.diff.GraphDiffService;
import dev.shaaf.gbuilder.graph.manifest.GraphManifestService;
import dev.shaaf.gbuilder.graph.report.GraphReportGenerator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GraphParityP0Test {

    @Inject SemanticGraphService graphService;
    @Inject GraphRepository graphRepo;
    @Inject GraphStoreLocation storeLocation;
    @Inject GraphManifestService manifestService;
    @Inject GraphDiffService diffService;
    @Inject GraphReportGenerator reportGenerator;

    @BeforeEach
    void clean() {
        storeLocation.reset();
        graphRepo.clearAll();
    }

    @Test
    void incrementalUpdateSkipsParseWhenUnchanged() throws Exception {
        Path root = Files.createTempDirectory("p0-incr-");
        try {
            Files.writeString(root.resolve("A.java"), """
                package p0;
                public class A { public void run() {} }
                """);

            graphService.buildGraph(root, dev.shaaf.gbuilder.lang.ParserBackend.JPARSER,
                    GraphBuildOptions.defaults());
            assertTrue(Files.exists(manifestService.manifestPath(root)));

            var second = graphService.buildGraph(root, dev.shaaf.gbuilder.lang.ParserBackend.JPARSER,
                    GraphBuildOptions.defaults().withIncremental(true));
            assertNotNull(second.result());
            assertEquals(1, second.result().typeCount());
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void incrementalUpdateDetectsAddedType() throws Exception {
        Path root = Files.createTempDirectory("p0-add-");
        try {
            Files.writeString(root.resolve("A.java"), """
                package p0;
                public class A {}
                """);
            graphService.buildGraph(root, dev.shaaf.gbuilder.lang.ParserBackend.JPARSER,
                    GraphBuildOptions.defaults());

            Files.writeString(root.resolve("B.java"), """
                package p0;
                public class B {}
                """);
            var outcome = graphService.buildGraph(root, dev.shaaf.gbuilder.lang.ParserBackend.JPARSER,
                    GraphBuildOptions.defaults().withIncremental(true));

            assertNotNull(outcome.diff());
            assertTrue(outcome.diff().addedTypes().contains("p0.B"));
            assertEquals(2, outcome.result().typeCount());
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void writesGraphReportOnBuild() throws Exception {
        Path root = Files.createTempDirectory("p0-report-");
        try {
            Files.writeString(root.resolve("Svc.java"), """
                package p0;
                public class Svc { public void go() { helper(); } private void helper() {} }
                """);
            graphService.buildGraph(root);
            Path report = reportGenerator.reportPath(root);
            assertTrue(Files.exists(report));
            String content = Files.readString(report);
            assertTrue(content.contains("# gbuilder Graph Report"));
            assertTrue(content.contains("Build summary"));
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void parserEdgesTaggedExtracted() throws Exception {
        Path root = Files.createTempDirectory("p0-prov-");
        try {
            Files.writeString(root.resolve("Caller.java"), """
                package p0;
                import p0.Callee;
                public class Caller {
                    public void run() { new Callee().work(); }
                }
                """);
            Files.writeString(root.resolve("Callee.java"), """
                package p0;
                public class Callee { public void work() {} }
                """);
            graphService.buildGraph(root);
            boolean hasExtracted = graphRepo.listClassLevelEdges().stream()
                    .filter(e -> "DEPENDS_ON".equals(e.type()))
                    .anyMatch(e -> e.provenance() == EdgeProvenance.EXTRACTED);
            assertTrue(hasExtracted);
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void manifestDiffDetectsChangedFile() throws Exception {
        Path root = Files.createTempDirectory("p0-manifest-");
        try {
            Path file = root.resolve("X.java");
            Files.writeString(file, "package p0; public class X {}");
            manifestService.saveManifest(root, manifestService.scanCurrentHashes(root));

            Files.writeString(file, "package p0; public class X { public void m() {} }");
            var diff = manifestService.diff(root);
            assertEquals(1, diff.changed().size());
            assertTrue(diff.hasChanges());
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void graphSnapshotSupportsDiff() throws Exception {
        Path root = Files.createTempDirectory("p0-snap-");
        try {
            Files.writeString(root.resolve("T.java"), "package p0; public class T {}");
            graphService.buildGraph(root);
            var before = diffService.snapshot();
            Files.writeString(root.resolve("U.java"), "package p0; public class U {}");
            graphService.buildGraph(root, dev.shaaf.gbuilder.lang.ParserBackend.JPARSER,
                    GraphBuildOptions.defaults().withIncremental(true));
            var after = diffService.snapshot();
            var diff = diffService.compute(before, after);
            assertTrue(diff.addedTypes().contains("p0.U"));
        } finally {
            deleteRecursive(root);
        }
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
