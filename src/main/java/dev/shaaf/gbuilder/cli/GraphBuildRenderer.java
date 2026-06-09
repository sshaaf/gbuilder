package dev.shaaf.gbuilder.cli;

import dev.shaaf.gbuilder.analyzer.model.AnalysisResult;
import dev.shaaf.gbuilder.analyzer.model.TechEntry;
import dev.shaaf.gbuilder.graph.SemanticGraphService;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.PrintStream;

@ApplicationScoped
public class GraphBuildRenderer {

    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String GREEN  = "\u001B[32m";
    private static final String CYAN   = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String DIM    = "\u001B[2m";

    private final PrintStream out;

    public GraphBuildRenderer() {
        this.out = System.out;
    }

    public void banner() {
        out.println();
        out.println(BOLD + CYAN + "  ╔═══════════════════════════════════════════════╗" + RESET);
        out.println(BOLD + CYAN + "  ║   Ambitious Graph — Code Knowledge Graph CLI  ║" + RESET);
        out.println(BOLD + CYAN + "  ╚═══════════════════════════════════════════════╝" + RESET);
        out.println();
    }

    public void section(String title) {
        out.println(BOLD + "  " + title + RESET);
        out.println(DIM + "  " + "─".repeat(44) + RESET);
    }

    public void keyValue(String key, Object value) {
        out.printf("  %-24s %s%s%s%n", key, BOLD, value, RESET);
    }

    public void success(String message) {
        out.println(GREEN + "  ✓ " + message + RESET);
    }

    public void error(String message) {
        out.println(RED + "  ✗ " + message + RESET);
    }

    public void blank() {
        out.println();
    }

    public void renderBuildResult(SemanticGraphService.GraphBuildResult result, long durationMs) {
        section("Graph Build Complete");
        var k = result.kindBreakdown();
        keyValue("Parser backend:", result.parserBackend().cliValue());
        keyValue("Types parsed:", result.typeCount());
        keyValue("  Classes:", k.classes());
        keyValue("  Interfaces:", k.interfaces());
        keyValue("  Enums:", k.enums());
        keyValue("  Records:", k.records());
        keyValue("  Annotation types:", k.annotationTypes());
        keyValue("Methods:", result.methodCount());
        keyValue("  Constructors:", result.constructorCount());
        keyValue("Fields:", result.fieldCount());
        keyValue("Imports:", result.importCount());
        keyValue("Annotations:", result.annotationCount());
        if (result.enumConstantCount() > 0) {
            keyValue("Enum constants:", result.enumConstantCount());
        }
        if (result.recordComponentCount() > 0) {
            keyValue("Record components:", result.recordComponentCount());
        }
        if (result.embeddedMethodCount() > 0 || result.embeddedClassCount() > 0) {
            keyValue("Embedded methods:", result.embeddedMethodCount());
            keyValue("Embedded classes:", result.embeddedClassCount());
        }
        if (result.communityCount() > 0) {
            keyValue("Communities:", result.communityCount());
        }
        keyValue("Duration:", durationMs + " ms");
        blank();

        if (result.analysisResult() != null && result.analysisResult().nodesTagged() > 0) {
            renderAnalysisResult(result.analysisResult());
        }

        success("Knowledge graph persisted to embedded store (.gbuilder/graph.db)");
        blank();
    }

    private void renderAnalysisResult(AnalysisResult analysis) {
        section("Technology Analysis (TechBOM)");
        keyValue("Rules applied:", analysis.rulesApplied());
        keyValue("Classes tagged:", analysis.nodesTagged());
        keyValue("Technologies found:", analysis.techBom().entries().size());
        blank();

        for (TechEntry entry : analysis.techBom().entries()) {
            keyValue("  " + entry.technologyName() + ":", entry.usageCount() + " classes");
        }
        blank();
    }
}
