package dev.shaaf.gbuilder.graph.model;

import java.util.List;

public record AnnotationNode(
    String name,
    List<AnnotationAttribute> attributes
) {}
