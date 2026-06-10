package dev.shaaf.gbuilder.lang.benchmark;

import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.GraphStoreLocation;
import dev.shaaf.gbuilder.graph.SemanticGraphService;
import dev.shaaf.gbuilder.lang.ParserBackend;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ParserBackendComparisonTest {

    private static final String FIXTURE_RESOURCE = "/parser-benchmark/coolstore-mini";

    @Inject
    SemanticGraphService graphService;

    @Inject
    GraphRepository graphRepo;

    @Inject
    GraphStoreLocation storeLocation;

    @Test
    void shouldCompareParserBackendsAndWriteMarkdownReport() throws Exception {
        Path fixtureRoot = ParserBenchmarkSupport.materializeFixture(FIXTURE_RESOURCE);
        try {
            int sourceFiles = ParserBenchmarkSupport.countJavaFiles(fixtureRoot);

            ParserBenchmarkRun jparser = ParserBenchmarkSupport.runBackend(
                    graphService, graphRepo, storeLocation, fixtureRoot, ParserBackend.JPARSER);
            ParserBenchmarkRun treesitter = ParserBenchmarkSupport.runBackend(
                    graphService, graphRepo, storeLocation, fixtureRoot, ParserBackend.TREESITTER);

            ParserBenchmarkComparison comparison = ParserBenchmarkComparison.compare(
                    "coolstore-mini", sourceFiles, jparser, treesitter);

            String markdown = ParserBenchmarkReporter.toMarkdown(
                    System.getProperty("gbuilder.expected.version", "unknown"),
                    System.getProperty("java.version", "unknown"),
                    System.getProperty("os.name", "unknown"),
                    List.of(comparison));

            Path reportPath = Paths.get(System.getProperty(
                    "gbuilder.parser-comparison.report-path",
                    "target/parser-comparison/PARSER_COMPARISON.md"));
            ParserBenchmarkSupport.writeReport(reportPath, markdown);

            String publishPath = System.getProperty("gbuilder.parser-comparison.publish-path");
            if (publishPath != null && !publishPath.isBlank()) {
                ParserBenchmarkSupport.writeReport(Paths.get(publishPath), markdown);
            }

            assertTrue(comparison.structurallyAligned(),
                    "Structural parser mismatch. See " + reportPath.toAbsolutePath());
        } finally {
            ParserBenchmarkSupport.deleteRecursive(fixtureRoot);
        }
    }
}
