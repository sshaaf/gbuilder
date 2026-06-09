package dev.shaaf.gbuilder.graph.report;

import dev.shaaf.gbuilder.graph.GraphBuildOptions;
import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.GraphStats;
import dev.shaaf.gbuilder.graph.SemanticGraphService;
import dev.shaaf.gbuilder.graph.analysis.GraphAnalysisResult;
import dev.shaaf.gbuilder.graph.diff.GraphDiffResult;
import dev.shaaf.gbuilder.lang.ParserBackend;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@ApplicationScoped
public class GraphReportGenerator {

    @Inject
    GraphRepository graphRepository;

    public Path reportPath(Path codebaseRoot) {
        return codebaseRoot.resolve(".gbuilder").resolve("GRAPH_REPORT.md");
    }

    public void writeReport(
            Path codebaseRoot,
            SemanticGraphService.GraphBuildResult buildResult,
            GraphBuildOptions options,
            long durationMs,
            GraphDiffResult diff,
            GraphAnalysisResult analysis) throws IOException {
        Path path = reportPath(codebaseRoot);
        Files.createDirectories(path.getParent());

        GraphStats stats = graphRepository.getGraphStats();
        StringBuilder md = new StringBuilder();
        md.append("# gbuilder Graph Report\n\n");
        md.append("Generated: ").append(Instant.now()).append("\n\n");

        md.append("## Build summary\n\n");
        md.append("| Metric | Value |\n|--------|-------|\n");
        md.append("| Parser backend | ").append(buildResult.parserBackend().cliValue()).append(" |\n");
        md.append("| Build mode | ").append(options.incremental() ? "incremental" : "full").append(" |\n");
        md.append("| Duration | ").append(durationMs).append(" ms |\n");
        md.append("| Types | ").append(buildResult.typeCount()).append(" |\n");
        md.append("| Methods | ").append(buildResult.methodCount()).append(" |\n");
        md.append("| Communities | ").append(buildResult.communityCount()).append(" |\n");
        md.append("| CALLS edges | ").append(stats.callsEdges()).append(" |\n");
        md.append("| DEPENDS_ON edges | ").append(stats.dependsOnEdges()).append(" |\n");
        md.append("| EXTENDS edges | ").append(stats.extendsEdges()).append(" |\n");
        md.append("| IMPLEMENTS edges | ").append(stats.implementsEdges()).append(" |\n\n");

        if (diff != null) {
            md.append("## Graph diff\n\n");
            md.append(diff.summary()).append("\n");
            if (!diff.addedTypes().isEmpty()) {
                md.append("\n**Added types:** ").append(String.join(", ", diff.addedTypes())).append("\n");
            }
            if (!diff.removedTypes().isEmpty()) {
                md.append("\n**Removed types:** ").append(String.join(", ", diff.removedTypes())).append("\n");
            }
            md.append("\n");
        }

        if (analysis != null) {
            appendAnalysis(md, analysis);
        }

        Files.writeString(path, md);
    }

    private void appendAnalysis(StringBuilder md, GraphAnalysisResult analysis) {
        md.append("## God nodes\n\n");
        if (analysis.godNodes().isEmpty()) {
            md.append("_None detected._\n\n");
        } else {
            for (var node : analysis.godNodes()) {
                md.append("- **").append(node.label()).append("** (degree ").append(node.degree()).append(")\n");
            }
            md.append("\n");
        }

        md.append("## Surprising connections\n\n");
        if (analysis.surprisingConnections().isEmpty()) {
            md.append("_None detected._\n\n");
        } else {
            for (var edge : analysis.surprisingConnections()) {
                md.append("- ").append(edge.fromFqn()).append(" → ").append(edge.toFqn())
                        .append(" (").append(edge.edgeType()).append(", score ")
                        .append(String.format("%.2f", edge.score())).append(")\n");
            }
            md.append("\n");
        }

        md.append("## Suggested questions\n\n");
        for (String q : analysis.suggestedQuestions()) {
            md.append("- ").append(q).append("\n");
        }
        md.append("\n");

        md.append("## Communities\n\n");
        for (var community : analysis.communities()) {
            md.append("### ").append(community.label()).append(" (id ").append(community.id())
                    .append(", cohesion ").append(String.format("%.2f", community.cohesion())).append(")\n");
            md.append("Types: ").append(String.join(", ", community.typeFqns().stream().limit(8).toList()));
            if (community.typeFqns().size() > 8) {
                md.append(" … (+").append(community.typeFqns().size() - 8).append(" more)");
            }
            md.append("\n\n");
        }
    }

    public void writeMinimalReport(
            Path codebaseRoot,
            ParserBackend backend,
            GraphStats stats,
            int communityCount,
            long durationMs,
            boolean incremental) throws IOException {
        writeReport(
                codebaseRoot,
                new SemanticGraphService.GraphBuildResult(
                        (int) stats.typeCount(),
                        new SemanticGraphService.KindBreakdown(0, 0, 0, 0, 0),
                        (int) stats.methodCount(), 0, 0, 0, 0, 0, 0,
                        null, 0, 0, communityCount, backend),
                GraphBuildOptions.defaults().withIncremental(incremental),
                durationMs,
                null,
                null);
    }
}
