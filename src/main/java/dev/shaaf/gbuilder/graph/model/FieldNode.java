package dev.shaaf.gbuilder.graph.model;

import java.util.List;

public record FieldNode(
    String type,
    String name,
    List<String> modifiers,
    List<String> annotations,
    String value
) {}
