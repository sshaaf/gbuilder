package dev.shaaf.gbuilder.lang.benchmark;

import dev.shaaf.gbuilder.analyzer.model.AnalysisResult;
import dev.shaaf.gbuilder.graph.GraphStats;
import dev.shaaf.gbuilder.lang.ParserBackend;

import java.util.List;
import java.util.Map;

public record ParserBenchmarkRun(
        ParserBackend backend,
        long durationMs,
        int typeCount,
        int methodCount,
        int fieldCount,
        int importCount,
        int annotationCount,
        int communityCount,
        GraphStats graphStats,
        AnalysisResult analysis,
        List<String> internalClassFqns,
        Map<String, Long> technologyClassCounts) {}
