package dev.shaaf.gbuilder.graph.model;

import java.util.List;

public record ParameterNode(
    String type,
    String name,
    List<String> annotations
) {}
