package dev.shaaf.gbuilder.graph.model;

import java.util.List;

public record RecordComponentNode(
    String type,
    String name,
    List<String> annotations
) {}
