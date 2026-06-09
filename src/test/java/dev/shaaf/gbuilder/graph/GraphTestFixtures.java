package dev.shaaf.gbuilder.graph;

import dev.shaaf.gbuilder.graph.model.*;
import dev.shaaf.gbuilder.lang.Language;

import java.util.List;
import java.util.Map;

public final class GraphTestFixtures {

    private GraphTestFixtures() {}

    public static ClassNode classNode(String fqn, String simpleName,
                                      List<String> annotations, List<String> imports) {
        return classNode(fqn, simpleName, annotations, imports, List.of(), List.of());
    }

    public static ClassNode classNode(String fqn, String simpleName,
                                      List<String> annotations, List<String> imports,
                                      List<MethodNode> methods, List<FieldNode> fields) {
        var annNodes = annotations.stream()
                .map(a -> new AnnotationNode(a, List.of()))
                .toList();
        var importNodes = imports.stream()
                .map(i -> new ImportNode(i, false, false))
                .toList();
        return new ClassNode(
                fqn, fqn.contains(".") ? fqn.substring(0, fqn.lastIndexOf('.')) : "",
                simpleName, simpleName + ".java",
                Language.JAVA, ClassKind.CLASS,
                List.of("public"), annNodes, annotations,
                importNodes, imports,
                List.of(), null, List.of(), List.of(),
                methods, fields, List.of(), List.of(),
                List.of(), Map.of(), null, null, MigrationStatus.PENDING
        );
    }

    public static MethodNode method(String name, String signature, String returnType, String rawBody) {
        return new MethodNode(
                name, signature, returnType, List.of("public"), List.of(), List.of(),
                List.of(), List.of(), List.of(), false, rawBody,
                List.of(), List.of(), null, null
        );
    }

    public static MethodNode methodWithCalls(String name, String signature, String returnType,
                                             String rawBody, List<String> internalCalls) {
        return new MethodNode(
                name, signature, returnType, List.of("public"), List.of(), List.of(),
                List.of(), List.of(), List.of(), false, rawBody,
                internalCalls, List.of(), null, null
        );
    }
}
