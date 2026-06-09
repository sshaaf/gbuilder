package dev.shaaf.gbuilder.analyzer.model;

import java.util.List;

public record TechEntry(
    String technologyId,
    String technologyName,
    String category,
    long usageCount,
    List<String> exampleFqns
) {}
