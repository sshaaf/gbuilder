package dev.shaaf.gbuilder.cli;

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
        version = "1.0.0-SNAPSHOT"
)
public class GBuilderCli implements Callable<Integer> {

    @Option(
            names = {"-p", "--path"},
            description = "Root of the source tree to parse",
            required = true
    )
    Path codebasePath;

    @Option(
            names = "--parser-backend",
            description = "Parser backend: jparser (default), treesitter (tree-sitter4j)",
            defaultValue = "jparser",
            converter = ParserBackendConverter.class
    )
    ParserBackend parserBackend;

    @Inject
    SemanticGraphService graphService;

    @Inject
    GraphBuildRenderer renderer;

    @Override
    public Integer call() {
        renderer.banner();

        if (!Files.isDirectory(codebasePath)) {
            renderer.error("Path does not exist or is not a directory: " + codebasePath);
            return 1;
        }

        renderer.section("Building Knowledge Graph");
        renderer.keyValue("Source root:", codebasePath.toAbsolutePath());
        renderer.keyValue("Parser backend:", parserBackend.cliValue());
        renderer.blank();

        try {
            long start = System.currentTimeMillis();
            var result = graphService.buildGraph(codebasePath, parserBackend);
            long duration = System.currentTimeMillis() - start;

            renderer.renderBuildResult(result, duration);
            return 0;
        } catch (Exception e) {
            renderer.error("Graph build failed: " + e.getMessage());
            return 1;
        }
    }
}
