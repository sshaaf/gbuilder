package dev.shaaf.gbuilder.lang.benchmark;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public final class ParserBenchmarkReporter {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private ParserBenchmarkReporter() {}

    public static String toMarkdown(
            String projectVersion,
            String javaVersion,
            String osName,
            List<ParserBenchmarkComparison> comparisons) {
        StringBuilder md = new StringBuilder();
        md.append("# Parser backend comparison\n\n");
        md.append("Generated: ").append(TIMESTAMP.format(Instant.now())).append("\n\n");
        md.append("| Build context | Value |\n");
        md.append("|---------------|-------|\n");
        md.append("| gbuilder version | ").append(projectVersion).append(" |\n");
        md.append("| Java | ").append(javaVersion).append(" |\n");
        md.append("| OS | ").append(osName).append(" |\n\n");

        for (ParserBenchmarkComparison comparison : comparisons) {
            appendComparison(md, comparison);
        }

        md.append("## Summary\n\n");
        long aligned = comparisons.stream().filter(ParserBenchmarkComparison::structurallyAligned).count();
        md.append("- Fixtures: **").append(comparisons.size()).append("**\n");
        md.append("- Structurally aligned: **").append(aligned).append("** / ")
                .append(comparisons.size()).append("\n");
        if (aligned == comparisons.size()) {
            md.append("- Status: **PASS** (structural parity)\n");
        } else {
            md.append("- Status: **FAIL** (structural mismatches detected)\n");
        }

        return md.toString();
    }

    private static void appendComparison(StringBuilder md, ParserBenchmarkComparison comparison) {
        ParserBenchmarkRun jp = comparison.jparser();
        ParserBenchmarkRun ts = comparison.treesitter();

        md.append("## ").append(comparison.fixtureName()).append("\n\n");
        md.append("Source files: **").append(comparison.sourceFileCount()).append("**\n\n");

        md.append("| Metric | JavaParser | Tree-sitter | Match |\n");
        md.append("|--------|------------|-------------|-------|\n");
        appendRow(md, "Duration (ms)", jp.durationMs(), ts.durationMs(), false);
        appendRow(md, "Types", jp.typeCount(), ts.typeCount(), true);
        appendRow(md, "Methods", jp.methodCount(), ts.methodCount(), true);
        appendRow(md, "Fields", jp.fieldCount(), ts.fieldCount(), true);
        appendRow(md, "Imports", jp.importCount(), ts.importCount(), true);
        appendRow(md, "Annotations", jp.annotationCount(), ts.annotationCount(), false);
        appendRow(md, "Communities", jp.communityCount(), ts.communityCount(), false);
        appendRow(md, "CALLS edges", jp.graphStats().callsEdges(), ts.graphStats().callsEdges(), true);
        appendRow(md, "DEPENDS_ON edges", jp.graphStats().dependsOnEdges(), ts.graphStats().dependsOnEdges(), true);
        appendRow(md, "EXTENDS edges", jp.graphStats().extendsEdges(), ts.graphStats().extendsEdges(), true);
        appendRow(md, "IMPLEMENTS edges", jp.graphStats().implementsEdges(), ts.graphStats().implementsEdges(), true);
        appendRow(md, "USES edges", jp.graphStats().usesEdges(), ts.graphStats().usesEdges(), false);
        md.append("\n");

        appendTechnologyTable(md, jp.technologyClassCounts(), ts.technologyClassCounts());

        if (!comparison.structuralMismatches().isEmpty()) {
            md.append("### Structural mismatches\n\n");
            for (String mismatch : comparison.structuralMismatches()) {
                md.append("- ").append(mismatch).append("\n");
            }
            md.append("\n");
        }

        if (!comparison.warnings().isEmpty()) {
            md.append("### Informational differences\n\n");
            for (String warning : comparison.warnings()) {
                md.append("- ").append(warning).append("\n");
            }
            md.append("\n");
        }
    }

    private static void appendRow(StringBuilder md, String name, long jp, long ts, boolean structural) {
        String match = jp == ts ? "yes" : (structural ? "**no**" : "no");
        md.append("| ").append(name).append(" | ").append(jp).append(" | ").append(ts).append(" | ")
                .append(match).append(" |\n");
    }

    private static void appendTechnologyTable(
            StringBuilder md,
            Map<String, Long> jpTech,
            Map<String, Long> tsTech) {
        md.append("### Technology detection\n\n");
        md.append("| Technology | JavaParser classes | Tree-sitter classes |\n");
        md.append("|------------|-------------------|---------------------|\n");
        for (String tech : unionKeys(jpTech, tsTech)) {
            md.append("| ").append(tech).append(" | ")
                    .append(jpTech.getOrDefault(tech, 0L)).append(" | ")
                    .append(tsTech.getOrDefault(tech, 0L)).append(" |\n");
        }
        md.append("\n");
    }

    private static Iterable<String> unionKeys(Map<String, Long> a, Map<String, Long> b) {
        java.util.Set<String> keys = new java.util.TreeSet<>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        return keys;
    }
}
