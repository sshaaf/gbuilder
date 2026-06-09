package dev.shaaf.gbuilder.analyzer.model;

import java.util.Map;

public record TechnologyNode(
    String id,
    String name,
    String category,
    String version,
    Map<String, String> metadata
) {
    public TechnologyNode(String id, String name, String category) {
        this(id, name, category, "", Map.of());
    }
}
