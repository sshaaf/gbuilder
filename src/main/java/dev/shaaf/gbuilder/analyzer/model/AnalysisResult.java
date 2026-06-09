package dev.shaaf.gbuilder.analyzer.model;

import java.time.Duration;

public record AnalysisResult(
    TechBOM techBom,
    int rulesApplied,
    int nodesTagged,
    Duration elapsed
) {}
