package dev.shaaf.gbuilder.graph.model;

import dev.shaaf.gbuilder.lang.Language;

import java.util.List;
import java.util.Map;

public record ClassNode(
    String fullyQualifiedName,
    String packageName,
    String simpleName,
    String filePath,
    Language language,
    ClassKind kind,
    List<String> modifiers,
    List<AnnotationNode> annotationNodes,
    List<String> annotations,
    List<ImportNode> importNodes,
    List<String> imports,
    List<String> typeParameters,
    String superClass,
    List<String> interfaces,
    List<String> permittedSubclasses,
    List<MethodNode> methods,
    List<FieldNode> fields,
    List<EnumConstantNode> enumConstants,
    List<RecordComponentNode> recordComponents,
    List<String> staticInitializers,
    Map<String, String> languageMetadata,
    String semanticSummary,
    float[] embeddingVector,
    MigrationStatus status
) {}
