package dev.shaaf.gbuilder.lang.java;

import dev.shaaf.gbuilder.graph.model.*;
import dev.shaaf.gbuilder.lang.ClassNodeExtractor;
import dev.shaaf.gbuilder.lang.Language;
import dev.shaaf.gbuilder.lang.ParserBackend;
import io.roastedroot.treesitter.TreeSitterNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class JavaTreeSitterExtractor implements ClassNodeExtractor {

    private static final Logger LOG = Logger.getLogger(JavaTreeSitterExtractor.class);

    private static final List<String> TYPE_NODE_TYPES = List.of(
            "class_declaration",
            "interface_declaration",
            "enum_declaration",
            "record_declaration",
            "annotation_type_declaration"
    );

    @Inject
    TreeSitterSession treeSitterSession;

    @Inject
    JavaTreeSitterQueries queries;

    @Override
    public ParserBackend backend() {
        return ParserBackend.TREESITTER;
    }

    @Override
    public Language language() {
        return Language.JAVA;
    }

    @Override
    public List<ClassNode> extract(Path sourceRoot) {
        try {
            return extractDirectory(sourceRoot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse directory: " + sourceRoot, e);
        }
    }

    @Override
    public List<ClassNode> extractFile(Path filePath) {
        try {
            return parseFile(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse file: " + filePath, e);
        }
    }

    public List<ClassNode> extractDirectory(Path rootDir) throws IOException {
        List<ClassNode> nodes = new ArrayList<>();
        for (Path javaFile : JavaSourceScanner.scanJavaFiles(rootDir)) {
            try {
                nodes.addAll(parseFile(javaFile));
            } catch (Exception e) {
                LOG.warnf("Skipping unparseable file: %s — %s", javaFile, e.getMessage());
            }
        }
        JavaSourceScanner.logParseSummary(rootDir, nodes.size(), backend().cliValue());
        return nodes;
    }

    public List<ClassNode> parseFile(Path filePath) throws IOException {
        String source = JavaSourceScanner.readSource(filePath);
        try (var parsed = treeSitterSession.parseJava(source)) {
            TreeSitterNode root = parsed.root();
            String packageName = extractPackage(source, root);
            List<ImportNode> importNodes = extractImports(source, root);
            List<String> importStrings = importNodes.stream().map(ImportNode::name).toList();

            List<ClassNode> results = new ArrayList<>();
            collectTypeNodes(root, source, packageName, importNodes, importStrings, filePath.toString(),
                    List.of(), results);
            return results;
        }
    }

    private void collectTypeNodes(TreeSitterNode parent, String source, String packageName,
                                    List<ImportNode> importNodes, List<String> importStrings,
                                    String filePath, List<String> enclosingTypes,
                                    List<ClassNode> out) {
        for (int i = 0; i < parent.namedChildCount(); i++) {
            TreeSitterNode child = parent.namedChild(i);
            if (child == null || !TYPE_NODE_TYPES.contains(child.type())) {
                continue;
            }
            out.add(buildClassNode(child, source, packageName, importNodes, importStrings,
                    filePath, enclosingTypes));
            String simpleName = TreeSitterSupport.typeDeclarationName(source, child);
            List<String> nestedEnclosing = new ArrayList<>(enclosingTypes);
            nestedEnclosing.add(simpleName);
            TreeSitterNode body = TreeSitterSupport.findBody(child);
            if (body != null) {
                collectTypeNodes(body, source, packageName, importNodes, importStrings,
                        filePath, nestedEnclosing, out);
            }
        }
    }

    private ClassNode buildClassNode(TreeSitterNode typeNode, String source, String packageName,
                                     List<ImportNode> importNodes, List<String> importStrings,
                                     String filePath, List<String> enclosingTypes) {
        String simpleName = TreeSitterSupport.typeDeclarationName(source, typeNode);
        String fqn = JavaFqnResolver.buildFqn(packageName, enclosingTypes, simpleName);
        ClassKind kind = resolveKind(typeNode.type());
        List<String> modifiers = TreeSitterSupport.extractModifiers(source, typeNode);
        List<String> annotations = TreeSitterSupport.extractDirectAnnotations(source, typeNode);
        List<AnnotationNode> annotationNodes = annotations.stream()
                .map(a -> new AnnotationNode(a, List.of()))
                .toList();

        return new ClassNode(
                fqn, packageName, simpleName, filePath, Language.JAVA,
                kind, modifiers, annotationNodes, annotations,
                importNodes, importStrings, List.of(),
                extractSuperClass(source, typeNode, importNodes, packageName),
                extractInterfaces(source, typeNode, importNodes, packageName),
                List.of(),
                extractMethods(source, typeNode, simpleName),
                extractFields(source, typeNode),
                extractEnumConstants(source, typeNode),
                extractRecordComponents(source, typeNode),
                List.of(),
                Map.of("parserBackend", backend().cliValue()), null, null, MigrationStatus.PENDING
        );
    }

    private String extractPackage(String source, TreeSitterNode root) {
        TreeSitterNode packageNode = TreeSitterSupport.findFirstChild(root, "package_declaration");
        if (packageNode == null) {
            return "";
        }
        TreeSitterNode scopedId = TreeSitterSupport.findFirstChild(packageNode, "scoped_identifier");
        if (scopedId != null) {
            return TreeSitterSupport.nodeText(source, scopedId).trim();
        }
        TreeSitterNode identifier = TreeSitterSupport.findFirstChild(packageNode, "identifier");
        return identifier == null ? "" : TreeSitterSupport.nodeText(source, identifier).trim();
    }

    private List<ImportNode> extractImports(String source, TreeSitterNode root) {
        List<ImportNode> imports = new ArrayList<>();
        for (TreeSitterNode importNode : TreeSitterSupport.childrenOfType(root, "import_declaration")) {
            String text = TreeSitterSupport.nodeText(source, importNode).trim();
            boolean isStatic = text.contains("import static");
            boolean isWildcard = text.endsWith("*;");
            String name = text.replace("import", "")
                    .replace("static", "")
                    .replace(";", "")
                    .replace("*", "")
                    .trim();
            imports.add(new ImportNode(name, isStatic, isWildcard));
        }
        return imports;
    }

    private ClassKind resolveKind(String nodeType) {
        return switch (nodeType) {
            case "interface_declaration" -> ClassKind.INTERFACE;
            case "enum_declaration" -> ClassKind.ENUM;
            case "record_declaration" -> ClassKind.RECORD;
            case "annotation_type_declaration" -> ClassKind.ANNOTATION;
            default -> ClassKind.CLASS;
        };
    }

    private String extractSuperClass(String source, TreeSitterNode typeNode, List<ImportNode> imports, String packageName) {
        if (!"class_declaration".equals(typeNode.type())) {
            return null;
        }
        TreeSitterNode superclass = TreeSitterSupport.findFirstNamedChildOfTypes(typeNode, "superclass");
        if (superclass == null) {
            return null;
        }
        String typeName = extractTypeName(source, superclass);
        return typeName.isEmpty() ? null : JavaFqnResolver.resolveToFqn(typeName, imports, packageName);
    }

    private List<String> extractInterfaces(String source, TreeSitterNode typeNode, List<ImportNode> imports, String packageName) {
        TreeSitterNode interfacesNode = TreeSitterSupport.findFirstNamedChildOfTypes(
                typeNode, "interfaces", "super_interfaces");
        if (interfacesNode == null) {
            return List.of();
        }
        List<String> interfaces = new ArrayList<>();
        for (TreeSitterNode type : TreeSitterSupport.childrenOfType(interfacesNode, "type_identifier")) {
            String name = TreeSitterSupport.nodeText(source, type).trim();
            interfaces.add(JavaFqnResolver.resolveToFqn(name, imports, packageName));
        }
        for (TreeSitterNode type : TreeSitterSupport.childrenOfType(interfacesNode, "scoped_type_identifier")) {
            String name = TreeSitterSupport.nodeText(source, type).trim();
            interfaces.add(JavaFqnResolver.resolveToFqn(name.substring(name.lastIndexOf('.') + 1), imports, packageName));
        }
        return interfaces;
    }

    private String extractTypeName(String source, TreeSitterNode typeNode) {
        TreeSitterNode identifier = TreeSitterSupport.findFirstChild(typeNode, "type_identifier");
        if (identifier != null) {
            return TreeSitterSupport.nodeText(source, identifier).trim();
        }
        identifier = TreeSitterSupport.findFirstChild(typeNode, "scoped_type_identifier");
        if (identifier != null) {
            String text = TreeSitterSupport.nodeText(source, identifier).trim();
            return text.contains(".") ? text.substring(text.lastIndexOf('.') + 1) : text;
        }
        return TreeSitterSupport.nodeText(source, typeNode).trim();
    }

    private List<MethodNode> extractMethods(String source, TreeSitterNode typeNode, String typeSimpleName) {
        List<MethodNode> methods = new ArrayList<>();
        TreeSitterNode body = TreeSitterSupport.findBody(typeNode);
        if (body == null) {
            return methods;
        }
        for (TreeSitterNode child : TreeSitterQuerySupport.typeBodyMembers(body, "method_declaration")) {
            methods.add(toMethodNodeFromQuery(source, child, typeSimpleName, false));
        }
        for (TreeSitterNode child : TreeSitterQuerySupport.typeBodyMembers(body, "constructor_declaration")) {
            methods.add(toMethodNodeFromQuery(source, child, typeSimpleName, true));
        }
        return methods;
    }

    private MethodNode toMethodNodeFromQuery(
            String source, TreeSitterNode declarationNode, String typeSimpleName, boolean constructor) {
        var captureNodes = TreeSitterQuerySupport.firstCaptureNodes(
                constructor ? queries.constructorDeclaration() : queries.methodDeclaration(),
                declarationNode,
                source);

        String name = resolveMethodName(source, declarationNode, captureNodes, typeSimpleName, constructor);
        List<String> modifiers = TreeSitterQuerySupport.modifiersFromCapture(source, captureNodes, "modifiers");
        List<String> annotations = TreeSitterSupport.extractDirectAnnotations(source, declarationNode);
        List<AnnotationNode> annotationNodes = annotations.stream()
                .map(a -> new AnnotationNode(a, List.of()))
                .toList();
        List<ParameterNode> parameters = extractParameters(source, declarationNode);
        String returnType = constructor
                ? "<init>"
                : TreeSitterQuerySupport.firstCapture(captureNodes, "return_type")
                        .map(node -> TreeSitterSupport.nodeText(source, node).trim())
                        .filter(text -> !text.isEmpty())
                        .orElseGet(() -> extractReturnType(source, declarationNode));
        String signature = buildSignature(modifiers, returnType, name, parameters, constructor);
        String rawBody = Optional.ofNullable(TreeSitterSupport.findBody(declarationNode))
                .map(node -> TreeSitterSupport.nodeText(source, node).trim())
                .orElse("");
        List<String> internalCalls = extractMethodCallsFromQuery(source, declarationNode);

        return new MethodNode(
                name, signature, returnType, modifiers,
                annotationNodes, annotations, List.of(),
                parameters, List.of(), constructor,
                rawBody, internalCalls, List.of(), null, null
        );
    }

    private String resolveMethodName(
            String source,
            TreeSitterNode declarationNode,
            Map<String, TreeSitterNode> captures,
            String typeSimpleName,
            boolean constructor) {
        String captureName = constructor ? "ctor_name" : "method_name";
        Optional<String> queriedName = TreeSitterQuerySupport.firstCapture(captures, captureName)
                .map(node -> TreeSitterSupport.nodeText(source, node).trim())
                .filter(name -> !name.isEmpty());
        if (queriedName.isPresent()) {
            return queriedName.get();
        }
        if (constructor) {
            return typeSimpleName;
        }
        return TreeSitterSupport.methodNameText(source, declarationNode);
    }

    private List<String> extractMethodCallsFromQuery(String source, TreeSitterNode methodNode) {
        return TreeSitterQuerySupport.captureTexts(queries.methodInvocation(), methodNode, source, "call_name");
    }

    private List<ParameterNode> extractParameters(String source, TreeSitterNode methodNode) {
        List<ParameterNode> parameters = new ArrayList<>();
        TreeSitterNode formalParameters = TreeSitterSupport.findFirstNamedChildOfTypes(methodNode, "formal_parameters");
        if (formalParameters == null) {
            return parameters;
        }
        for (TreeSitterNode param : TreeSitterSupport.childrenOfType(formalParameters, "formal_parameter")) {
            parameters.add(new ParameterNode(
                    extractParameterType(source, param),
                    TreeSitterSupport.parameterNameText(source, param),
                    TreeSitterSupport.extractAnnotations(source, param)
            ));
        }
        return parameters;
    }

    private String extractParameterType(String source, TreeSitterNode paramNode) {
        for (int i = 0; i < paramNode.namedChildCount(); i++) {
            TreeSitterNode child = paramNode.namedChild(i);
            if (child == null) {
                continue;
            }
            String type = child.type();
            if (!"identifier".equals(type) && !"modifiers".equals(type)
                    && !"annotation".equals(type) && !"marker_annotation".equals(type)) {
                return TreeSitterSupport.nodeText(source, child).trim();
            }
        }
        return "Object";
    }

    private String extractReturnType(String source, TreeSitterNode methodNode) {
        TreeSitterNode typeNode = TreeSitterSupport.findReturnType(methodNode);
        return typeNode == null ? "void" : TreeSitterSupport.nodeText(source, typeNode).trim();
    }

    private String buildSignature(List<String> modifiers, String returnType, String name,
                                  List<ParameterNode> parameters, boolean constructor) {
        String paramList = String.join(", ", parameters.stream()
                .map(p -> p.type() + " " + p.name())
                .toList());
        String mods = modifiers.isEmpty() ? "" : String.join(" ", modifiers) + " ";
        if (constructor) {
            return mods + name + "(" + paramList + ")";
        }
        return mods + returnType + " " + name + "(" + paramList + ")";
    }

    private List<FieldNode> extractFields(String source, TreeSitterNode typeNode) {
        List<FieldNode> fields = new ArrayList<>();
        TreeSitterNode body = TreeSitterSupport.findBody(typeNode);
        if (body == null) {
            return fields;
        }
        for (TreeSitterNode fieldDecl : TreeSitterQuerySupport.typeBodyMembers(body, "field_declaration")) {
            var captures = TreeSitterQuerySupport.firstCaptureNodes(queries.fieldDeclaration(), fieldDecl, source);
            List<String> modifiers = TreeSitterQuerySupport.modifiersFromCapture(source, captures, "modifiers");
            List<String> annotations = TreeSitterSupport.extractAnnotations(source, fieldDecl);
            String type = extractFieldType(source, fieldDecl);
            String name = TreeSitterQuerySupport.firstCapture(captures, "field_name")
                    .map(node -> TreeSitterSupport.nodeText(source, node).trim())
                    .orElse("");
            if (name.isEmpty()) {
                continue;
            }
            String initializer = extractFieldInitializer(source, fieldDecl, name);
            fields.add(new FieldNode(type, name, modifiers, annotations,
                    initializer.isEmpty() ? null : initializer));
        }
        return fields;
    }

    private String extractFieldInitializer(String source, TreeSitterNode fieldDecl, String fieldName) {
        for (TreeSitterNode declarator : TreeSitterQuerySupport.directChildrenOfType(fieldDecl, "variable_declarator")) {
            String declaratorName = TreeSitterSupport.identifierText(source, declarator);
            if (!fieldName.equals(declaratorName)) {
                continue;
            }
            for (int i = 0; i < declarator.namedChildCount(); i++) {
                TreeSitterNode child = declarator.namedChild(i);
                if (child != null && !"identifier".equals(child.type())) {
                    return TreeSitterSupport.nodeText(source, child).trim();
                }
            }
        }
        return "";
    }

    private String extractFieldType(String source, TreeSitterNode fieldDecl) {
        for (int i = 0; i < fieldDecl.namedChildCount(); i++) {
            TreeSitterNode child = fieldDecl.namedChild(i);
            if (child == null) {
                continue;
            }
            String type = child.type();
            if ("type_identifier".equals(type) || "generic_type".equals(type)
                    || "scoped_type_identifier".equals(type) || "integral_type".equals(type)
                    || "floating_point_type".equals(type) || "boolean_type".equals(type)) {
                return TreeSitterSupport.nodeText(source, child).trim();
            }
        }
        return "Object";
    }

    private List<EnumConstantNode> extractEnumConstants(String source, TreeSitterNode typeNode) {
        if (!"enum_declaration".equals(typeNode.type())) {
            return List.of();
        }
        List<EnumConstantNode> constants = new ArrayList<>();
        TreeSitterNode body = TreeSitterSupport.findBody(typeNode);
        if (body == null) {
            return constants;
        }
        for (TreeSitterNode constant : TreeSitterSupport.childrenOfType(body, "enum_constant")) {
            constants.add(new EnumConstantNode(
                    TreeSitterSupport.identifierText(source, constant),
                    List.of(),
                    TreeSitterSupport.extractAnnotations(source, constant)
            ));
        }
        return constants;
    }

    private List<RecordComponentNode> extractRecordComponents(String source, TreeSitterNode typeNode) {
        if (!"record_declaration".equals(typeNode.type())) {
            return List.of();
        }
        List<RecordComponentNode> components = new ArrayList<>();
        for (TreeSitterNode param : TreeSitterSupport.childrenOfType(typeNode, "formal_parameter")) {
            components.add(new RecordComponentNode(
                    extractParameterType(source, param),
                    TreeSitterSupport.parameterNameText(source, param),
                    TreeSitterSupport.extractAnnotations(source, param)
            ));
        }
        return components;
    }
}
