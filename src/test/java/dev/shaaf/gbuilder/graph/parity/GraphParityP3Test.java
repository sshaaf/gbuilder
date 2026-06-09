package dev.shaaf.gbuilder.graph.parity;

import dev.shaaf.gbuilder.graph.GraphBuildOptions;
import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.GraphStoreLocation;
import dev.shaaf.gbuilder.graph.SemanticGraphService;
import dev.shaaf.gbuilder.graph.analysis.GraphAnalyzer;
import dev.shaaf.gbuilder.graph.export.GraphExportService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GraphParityP3Test {

    @Inject SemanticGraphService graphService;
    @Inject GraphRepository graphRepo;
    @Inject GraphStoreLocation storeLocation;
    @Inject GraphExportService exportService;
    @Inject GraphAnalyzer graphAnalyzer;

    @BeforeEach
    void clean() {
        storeLocation.reset();
        graphRepo.clearAll();
    }

    @Test
    void htmlExportContainsVisNetwork() throws Exception {
        Path root = writeFixture();
        try {
            graphService.buildGraph(root);
            exportService.exportHtml(root, graphAnalyzer.analyze());
            String html = Files.readString(root.resolve(".gbuilder/graph.html"));
            assertTrue(html.contains("vis-network"));
            assertTrue(html.contains("search"));
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void graphmlExportContainsNodesAndEdges() throws Exception {
        Path root = writeFixture();
        try {
            graphService.buildGraph(root);
            exportService.exportGraphMl(root);
            String xml = Files.readString(root.resolve(".gbuilder/graph.graphml"));
            assertTrue(xml.contains("<graphml"));
            assertTrue(xml.contains("id=\"viz.Alpha\""));
            assertTrue(xml.contains("id=\"viz.Beta\""));
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void svgExportWritten() throws Exception {
        Path root = writeFixture();
        try {
            graphService.buildGraph(root);
            exportService.exportSvg(root);
            String svg = Files.readString(root.resolve(".gbuilder/graph.svg"));
            assertTrue(svg.contains("<svg"));
            assertTrue(svg.contains("Alpha"));
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void wikiExportCreatesIndexAndArticles() throws Exception {
        Path root = writeFixture();
        try {
            graphService.buildGraph(root);
            exportService.exportWiki(root, graphAnalyzer.analyze());
            assertTrue(Files.exists(root.resolve(".gbuilder/wiki/index.md")));
            try (var files = Files.list(root.resolve(".gbuilder/wiki"))) {
                assertTrue(files.anyMatch(p -> p.toString().endsWith(".md")));
            }
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void buildWithExportFlagsWritesArtifacts() throws Exception {
        Path root = writeFixture();
        try {
            var options = GraphBuildOptions.defaults()
                    .withExportSvg(true)
                    .withExportGraphml(true);
            graphService.buildGraph(root, dev.shaaf.gbuilder.lang.ParserBackend.JPARSER, options);
            assertTrue(Files.exists(root.resolve(".gbuilder/graph.svg")));
            assertTrue(Files.exists(root.resolve(".gbuilder/graph.graphml")));
            assertTrue(Files.exists(root.resolve(".gbuilder/graph.html")));
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void noVizSkipsHtmlOnRequest() throws Exception {
        Path root = writeFixture();
        try {
            graphService.buildGraph(root, dev.shaaf.gbuilder.lang.ParserBackend.JPARSER,
                    GraphBuildOptions.defaults().withSkipVisualization(true));
            assertFalse(Files.exists(root.resolve(".gbuilder/graph.html")));
            assertTrue(Files.exists(root.resolve(".gbuilder/graph.json")));
        } finally {
            deleteRecursive(root);
        }
    }

    private Path writeFixture() throws Exception {
        Path root = Files.createTempDirectory("p3-export-");
        Files.writeString(root.resolve("Alpha.java"), """
            package viz;
            public class Alpha { public void a() { new Beta().b(); } }
            """);
        Files.writeString(root.resolve("Beta.java"), """
            package viz;
            public class Beta { public void b() {} }
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
