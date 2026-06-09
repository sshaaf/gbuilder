package dev.shaaf.gbuilder.cli;

import dev.shaaf.gbuilder.graph.GraphBuildOptions;
import dev.shaaf.gbuilder.graph.GraphStoreLocation;
import dev.shaaf.gbuilder.graph.SemanticGraphService;
import dev.shaaf.gbuilder.graph.hook.GraphHookService;
import dev.shaaf.gbuilder.graph.merge.GraphMergeService;
import dev.shaaf.gbuilder.graph.query.GraphExplainService;
import dev.shaaf.gbuilder.graph.query.GraphPathService;
import dev.shaaf.gbuilder.graph.query.GraphQueryService;
import dev.shaaf.gbuilder.lang.ParserBackend;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "query", description = "BFS/DFS graph query (graphify parity)")
class QueryCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Question to query the graph with")
    String question;

    @Option(names = "--dfs", description = "Use DFS instead of BFS")
    boolean dfs;

    @Option(names = "--budget", defaultValue = "3000", description = "Token budget cap")
    int budget;

    @Option(names = {"-p", "--path"}, description = "Codebase root (must have existing graph)")
    Path codebasePath = Path.of(".");

    @Inject GraphQueryService queryService;
    @Inject GraphStoreLocation storeLocation;
    @Inject GraphBuildRenderer renderer;

    @Override
    public Integer call() {
        storeLocation.resolveForCodebase(codebasePath);
        var result = queryService.query(question, dfs, budget);
        renderer.section("Graph query");
        System.out.println(result.answerText());
        return 0;
    }
}

@Command(name = "path", description = "Shortest path between two types")
class PathCommand implements Callable<Integer> {

    @Parameters(index = "0") String from;
    @Parameters(index = "1") String to;

    @Option(names = {"-p", "--path"}, defaultValue = ".")
    Path codebasePath;

    @Inject GraphPathService pathService;
    @Inject GraphStoreLocation storeLocation;
    @Inject GraphBuildRenderer renderer;

    @Override
    public Integer call() {
        storeLocation.resolveForCodebase(codebasePath);
        var result = pathService.findPath(from, to);
        renderer.section("Path");
        System.out.println(result.explanation());
        if (!result.hops().isEmpty()) {
            System.out.println("Hops: " + String.join(" → ", result.hops()));
        }
        return result.hops().isEmpty() ? 1 : 0;
    }
}

@Command(name = "explain", description = "Explain a type node")
class ExplainCommand implements Callable<Integer> {

    @Parameters(index = "0") String node;

    @Option(names = {"-p", "--path"}, defaultValue = ".")
    Path codebasePath;

    @Inject GraphExplainService explainService;
    @Inject GraphStoreLocation storeLocation;

    @Override
    public Integer call() {
        storeLocation.resolveForCodebase(codebasePath);
        System.out.println(explainService.explain(node));
        return 0;
    }
}

@Command(name = "export", subcommands = {ExportHtmlCommand.class, ExportGraphmlCommand.class,
        ExportSvgCommand.class, ExportNeo4jCommand.class, ExportWikiCommand.class})
class ExportCommand {}

abstract class ExportBase implements Callable<Integer> {
    @Option(names = {"-p", "--path"}, defaultValue = ".")
    Path codebasePath;
    @Inject GraphStoreLocation storeLocation;

    protected void initStore() {
        storeLocation.resolveForCodebase(codebasePath);
    }
}

@Command(name = "html")
class ExportHtmlCommand extends ExportBase {
    @Inject dev.shaaf.gbuilder.graph.export.GraphExportService exportService;
    @Inject dev.shaaf.gbuilder.graph.analysis.GraphAnalyzer analyzer;

    @Override
    public Integer call() throws Exception {
        initStore();
        exportService.exportHtml(codebasePath, analyzer.analyze());
        System.out.println("Wrote " + codebasePath.resolve(".gbuilder/graph.html"));
        return 0;
    }
}

@Command(name = "graphml")
class ExportGraphmlCommand extends ExportBase {
    @Inject dev.shaaf.gbuilder.graph.export.GraphExportService exportService;

    @Override
    public Integer call() throws Exception {
        initStore();
        exportService.exportGraphMl(codebasePath);
        System.out.println("Wrote " + codebasePath.resolve(".gbuilder/graph.graphml"));
        return 0;
    }
}

@Command(name = "svg")
class ExportSvgCommand extends ExportBase {
    @Inject dev.shaaf.gbuilder.graph.export.GraphExportService exportService;

    @Override
    public Integer call() throws Exception {
        initStore();
        exportService.exportSvg(codebasePath);
        System.out.println("Wrote " + codebasePath.resolve(".gbuilder/graph.svg"));
        return 0;
    }
}

@Command(name = "neo4j")
class ExportNeo4jCommand extends ExportBase {
    @Inject dev.shaaf.gbuilder.graph.export.GraphExportService exportService;

    @Override
    public Integer call() throws Exception {
        initStore();
        exportService.exportNeo4jCypher(codebasePath);
        System.out.println("Wrote " + codebasePath.resolve(".gbuilder/cypher.txt"));
        return 0;
    }
}

@Command(name = "wiki")
class ExportWikiCommand extends ExportBase {
    @Inject dev.shaaf.gbuilder.graph.export.GraphExportService exportService;
    @Inject dev.shaaf.gbuilder.graph.analysis.GraphAnalyzer analyzer;

    @Override
    public Integer call() throws Exception {
        initStore();
        exportService.exportWiki(codebasePath, analyzer.analyze());
        System.out.println("Wrote " + codebasePath.resolve(".gbuilder/wiki/"));
        return 0;
    }
}

@Command(name = "watch", description = "Watch folder and rebuild graph on Java changes")
class WatchCommand implements Callable<Integer> {

    @Option(names = {"-p", "--path"}, defaultValue = ".")
    Path codebasePath;

    @Option(names = "--debounce", defaultValue = "3")
    int debounceSeconds;

    @Inject SemanticGraphService graphService;
    @Inject GraphBuildRenderer renderer;

    @Override
    public Integer call() throws Exception {
        renderer.banner();
        renderer.keyValue("Watching:", codebasePath.toAbsolutePath());
        try (var watch = java.nio.file.FileSystems.getDefault().newWatchService()) {
            registerAll(codebasePath, watch);
            long lastRun = 0;
            while (true) {
                var key = watch.take();
                Thread.sleep(debounceSeconds * 1000L);
                long now = System.currentTimeMillis();
                if (now - lastRun > debounceSeconds * 1000L) {
                    var outcome = graphService.buildGraph(codebasePath, ParserBackend.JPARSER,
                            GraphBuildOptions.defaults().withIncremental(true));
                    renderer.renderBuildOutcome(outcome);
                    lastRun = now;
                }
                key.pollEvents().clear();
            }
        }
    }

    private void registerAll(Path root, java.nio.file.WatchService watch) throws Exception {
        Files.walk(root).filter(Files::isDirectory).forEach(dir -> {
            try {
                dir.register(watch, java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY,
                        java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                        java.nio.file.StandardWatchEventKinds.ENTRY_DELETE);
            } catch (Exception ignored) {
            }
        });
    }
}

@Command(name = "hook", subcommands = {HookInstallCommand.class, HookUninstallCommand.class, HookStatusCommand.class})
class HookCommand {}

@Command(name = "install")
class HookInstallCommand implements Callable<Integer> {
    @Option(names = {"-p", "--path"}, defaultValue = ".") Path codebasePath;
    @Inject GraphHookService hookService;

    @Override
    public Integer call() throws Exception {
        hookService.install(codebasePath);
        System.out.println("Installed post-commit hook");
        return 0;
    }
}

@Command(name = "uninstall")
class HookUninstallCommand implements Callable<Integer> {
    @Option(names = {"-p", "--path"}, defaultValue = ".") Path codebasePath;
    @Inject GraphHookService hookService;

    @Override
    public Integer call() throws Exception {
        hookService.uninstall(codebasePath);
        System.out.println("Removed gbuilder hook (if present)");
        return 0;
    }
}

@Command(name = "status")
class HookStatusCommand implements Callable<Integer> {
    @Option(names = {"-p", "--path"}, defaultValue = ".") Path codebasePath;
    @Inject GraphHookService hookService;

    @Override
    public Integer call() {
        System.out.println(hookService.status(codebasePath));
        return 0;
    }
}

@Command(name = "cluster-only", description = "Re-cluster existing graph without re-parsing")
class ClusterOnlyCommand implements Callable<Integer> {

    @Option(names = {"-p", "--path"}, required = true)
    Path codebasePath;

    @Option(names = "--parser-backend", defaultValue = "jparser", converter = ParserBackendConverter.class)
    ParserBackend parserBackend;

    @Inject SemanticGraphService graphService;
    @Inject GraphBuildRenderer renderer;

    @Override
    public Integer call() throws Exception {
        renderer.banner();
        var outcome = graphService.buildGraph(codebasePath, parserBackend,
                GraphBuildOptions.defaults().withClusterOnly(true));
        renderer.renderBuildOutcome(outcome);
        return 0;
    }
}

@Command(name = "merge", description = "Merge graph databases from two codebases")
class MergeCommand implements Callable<Integer> {

    @Parameters(index = "0") Path primary;
    @Parameters(index = "1") Path secondary;

    @Option(names = "--repo", defaultValue = "secondary")
    String repoLabel;

    @Inject GraphMergeService mergeService;

    @Override
    public Integer call() throws Exception {
        mergeService.mergeDatabases(primary, secondary, repoLabel);
        System.out.println("Merged graph from " + secondary + " into " + primary);
        return 0;
    }
}

@Command(name = "save-result", description = "Save query/path/explain result into graph")
class SaveResultCommand implements Callable<Integer> {

    @Option(names = "--question", required = true) String question;
    @Option(names = "--answer", required = true) String answer;
    @Option(names = "--nodes", split = ",") List<String> nodes;
    @Option(names = {"-p", "--path"}, defaultValue = ".") Path codebasePath;

    @Inject SemanticGraphService graphService;
    @Inject GraphStoreLocation storeLocation;

    @Override
    public Integer call() {
        storeLocation.resolveForCodebase(codebasePath);
        graphService.saveQueryResult(question, answer, nodes == null ? List.of() : nodes);
        System.out.println("Saved Q&A result");
        return 0;
    }
}
