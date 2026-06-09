package dev.shaaf.gbuilder.graph.model;

public record ImportNode(
    String name,
    boolean isStatic,
    boolean isWildcard
) {}
