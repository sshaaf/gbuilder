package dev.shaaf.gbuilder.graph.model;

import java.util.List;

public record EnumConstantNode(
    String name,
    List<String> arguments,
    List<String> annotations
) {}
