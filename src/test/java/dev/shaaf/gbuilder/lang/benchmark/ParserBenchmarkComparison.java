package dev.shaaf.gbuilder.lang.benchmark;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public record ParserBenchmarkComparison(
        String fixtureName,
        int sourceFileCount,
        ParserBenchmarkRun jparser,
        ParserBenchmarkRun treesitter,
        List<String> structuralMismatches,
        List<String> warnings) {

    public boolean structurallyAligned() {
        return structuralMismatches.isEmpty();
    }

    public static ParserBenchmarkComparison compare(
            String fixtureName,
            int sourceFileCount,
            ParserBenchmarkRun jparser,
            ParserBenchmarkRun treesitter) {
        List<String> mismatches = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        compareMetric(mismatches, "types", jparser.typeCount(), treesitter.typeCount());
        compareMetric(mismatches, "methods", jparser.methodCount(), treesitter.methodCount());
        compareMetric(mismatches, "fields", jparser.fieldCount(), treesitter.fieldCount());
        compareMetric(mismatches, "imports", jparser.importCount(), treesitter.importCount());

        compareMetric(mismatches, "CALLS edges", jparser.graphStats().callsEdges(), treesitter.graphStats().callsEdges());
        compareMetric(mismatches, "DEPENDS_ON edges", jparser.graphStats().dependsOnEdges(),
                treesitter.graphStats().dependsOnEdges());
        compareMetric(mismatches, "EXTENDS edges", jparser.graphStats().extendsEdges(),
                treesitter.graphStats().extendsEdges());
        compareMetric(mismatches, "IMPLEMENTS edges", jparser.graphStats().implementsEdges(),
                treesitter.graphStats().implementsEdges());

        Set<String> jpFqns = new TreeSet<>(jparser.internalClassFqns());
        Set<String> tsFqns = new TreeSet<>(treesitter.internalClassFqns());
        Set<String> onlyJp = new TreeSet<>(jpFqns);
        onlyJp.removeAll(tsFqns);
        Set<String> onlyTs = new TreeSet<>(tsFqns);
        onlyTs.removeAll(jpFqns);

        for (String fqn : onlyTs) {
            if (fqn.contains("..")) {
                warnings.add("Tree-sitter produced malformed FQN: " + fqn);
            } else {
                mismatches.add("FQN only in treesitter: " + fqn);
            }
        }
        for (String fqn : onlyJp) {
            mismatches.add("FQN only in jparser: " + fqn);
        }

        if (jparser.annotationCount() != treesitter.annotationCount()) {
            warnings.add("Annotation count differs (jparser="
                    + jparser.annotationCount() + ", treesitter=" + treesitter.annotationCount() + ")");
        }
        if (jparser.communityCount() != treesitter.communityCount()) {
            warnings.add("Community count differs (jparser="
                    + jparser.communityCount() + ", treesitter=" + treesitter.communityCount() + ")");
        }
        if (jparser.graphStats().usesEdges() != treesitter.graphStats().usesEdges()) {
            warnings.add("USES edges differ (jparser="
                    + jparser.graphStats().usesEdges() + ", treesitter=" + treesitter.graphStats().usesEdges() + ")");
        }

        Map<String, Long> jpTech = jparser.technologyClassCounts();
        Map<String, Long> tsTech = treesitter.technologyClassCounts();
        for (String tech : unionKeys(jpTech, tsTech)) {
            long jp = jpTech.getOrDefault(tech, 0L);
            long ts = tsTech.getOrDefault(tech, 0L);
            if (jp != ts) {
                warnings.add("Technology '" + tech + "' class count differs (jparser=" + jp + ", treesitter=" + ts + ")");
            }
        }

        return new ParserBenchmarkComparison(fixtureName, sourceFileCount, jparser, treesitter, mismatches, warnings);
    }

    private static void compareMetric(List<String> mismatches, String name, long jparser, long treesitter) {
        if (jparser != treesitter) {
            mismatches.add(name + " (jparser=" + jparser + ", treesitter=" + treesitter + ")");
        }
    }

    private static Set<String> unionKeys(Map<String, Long> a, Map<String, Long> b) {
        Set<String> keys = new TreeSet<>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        return keys;
    }
}
