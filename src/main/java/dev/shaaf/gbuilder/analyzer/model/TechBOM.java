package dev.shaaf.gbuilder.analyzer.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TechBOM(
    List<TechEntry> entries,
    Map<String, Long> categorySummary,
    Instant generatedAt
) {}
