package dev.shaaf.gbuilder.graph.model;

import java.util.List;

public record MethodNode(
    String name,
    String signature,
    String returnType,
    List<String> modifiers,
    List<AnnotationNode> annotationNodes,
    List<String> annotations,
    List<String> typeParameters,
    List<ParameterNode> parameters,
    List<String> thrownExceptions,
    boolean constructor,
    String rawBody,
    List<String> internalMethodCalls,
    List<String> methodReferences,
    String semanticSummary,
    float[] embeddingVector
) {}
