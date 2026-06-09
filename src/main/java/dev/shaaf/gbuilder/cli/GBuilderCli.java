package dev.shaaf.gbuilder.cli;

import dev.shaaf.gbuilder.graph.GraphBuildOptions;
import dev.shaaf.gbuilder.graph.SemanticGraphService;
import dev.shaaf.gbuilder.lang.ParserBackend;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@TopCommand
@Command(
        name = "gbuilder",
        description = "Parse a Java codebase and build the semantic knowledge graph",
        mixinStandardHelpOptions = true,
        version = "1.0.0-SNAPSHOT",
        subcommands = {
                QueryCommand.class,
                PathCommand.class,
                ExplainCommand.class,
                ExportCommand.class,
                WatchCommand.class,
                HookCommand.class,
                ClusterOnlyCommand.class,
                MergeCommand.class,
                SaveResultCommand.class
        }
)
public class GBuilderCli implements Callable<Integer> {

    @Option(names = {"-p", "--path"}, description = "Root of the source tree to parse", defaultValue = ".")
    Path codebasePath;

    @Option(names = "--parser-backend", defaultValue = "jparser", converter = ParserBackendConverter.class)
    ParserBackend parserBackend;

    @Option(names = "--update", description = "Incremental update (graphify parity)")
    boolean update;

    @Option(names = "--mode", description = "Build mode: deep enables inferred semantic edges")
    String mode;

    @Option(names = "--no-viz", description = "Skip HTML graph generation")
    boolean noViz;

    @Option(names = "--wiki", description = "Generate agent wiki under .gbuilder/wiki")
    boolean wiki;

    @Option(names = "--svg", description = "Export graph.svg")
    boolean svg;

    @Option(names = "--graphml", description = "Export graph.graphml")
    boolean graphml;

    @Option(names = "--neo4j", description = "Export cypher.txt for Neo4j")
    boolean neo4j;

    @Option(names = "--cluster-only", description = "Re-cluster existing graph only")
    boolean clusterOnly;

    @Option(names = "--benchmark", description = "Print token benchmark after build")
    boolean benchmark;

    @Option(names = "--clustering", defaultValue = "label-propagation",
            description = "Clustering algorithm: label-propagation | girvan-newman")
    String clustering;

    @Inject SemanticGraphService graphService;
    @Inject GraphBuildRenderer renderer;

    @Override
    public Integer call() {
        renderer.banner();

        if (!Files.isDirectory(codebasePath)) {
            renderer.error("Path does not exist or is not a directory: " + codebasePath);
            return 1;
        }

        GraphBuildOptions options = GraphBuildOptions.defaults()
                .withIncremental(update)
                .withDeepMode("deep".equalsIgnoreCase(mode))
                .withSkipVisualization(noViz)
                .withGenerateWiki(wiki)
                .withExportSvg(svg)
                .withExportGraphml(graphml)
                .withExportNeo4j(neo4j)
                .withClusterOnly(clusterOnly)
                .withBenchmark(benchmark)
                .withClusteringAlgorithm(clustering);

        renderer.section("Building Knowledge Graph");
        renderer.keyValue("Source root:", codebasePath.toAbsolutePath());
        renderer.keyValue("Parser backend:", parserBackend.cliValue());
        renderer.keyValue("Mode:", update ? "incremental" : (clusterOnly ? "cluster-only" : "full"));
        renderer.blank();

        try {
            var outcome = graphService.buildGraph(codebasePath, parserBackend, options);
            renderer.renderBuildOutcome(outcome);
            return 0;
        } catch (Exception e) {
            renderer.error("Graph build failed: " + e.getMessage());
            return 1;
        }
    }
}
