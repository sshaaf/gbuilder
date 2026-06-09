package dev.shaaf.gbuilder.lang.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import dev.shaaf.gbuilder.graph.model.*;
import dev.shaaf.gbuilder.lang.ClassNodeExtractor;
import dev.shaaf.gbuilder.lang.Language;
import dev.shaaf.gbuilder.lang.ParserBackend;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class JavaParserExtractor implements ClassNodeExtractor {

    private static final Logger LOG = Logger.getLogger(JavaParserExtractor.class);

    private static final ParserConfiguration PARSER_CONFIGURATION = new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);

    private static final ThreadLocal<JavaParser> THREAD_LOCAL_PARSER =
            ThreadLocal.withInitial(() -> new JavaParser(PARSER_CONFIGURATION));

    @PostConstruct
    void configureParser() {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    @Override
    public ParserBackend backend() {
        return ParserBackend.JPARSER;
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

    public List<ClassNode> parseDirectory(Path rootDir) throws IOException {
        return extractDirectory(rootDir);
    }

    public List<ClassNode> extractDirectory(Path rootDir) throws IOException {
        List<ClassNode> nodes = new ArrayList<>();
        for (Path javaFile : JavaSourceScanner.scanJavaFiles(rootDir)) {
            try {
                nodes.addAll(parseFile(javaFile));
            } catch (IOException e) {
                LOG.warnf("Skipping unparseable file: %s — %s", javaFile, e.getMessage());
            }
        }
        JavaSourceScanner.logParseSummary(rootDir, nodes.size(), backend().cliValue());
        return nodes;
    }

    public List<ClassNode> parseFile(Path filePath) throws IOException {
        CompilationUnit cu = THREAD_LOCAL_PARSER.get()
                .parse(filePath)
                .getResult()
                .orElseThrow(() -> new IOException("Failed to parse: " + filePath));
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        List<ImportNode> importNodes = cu.getImports().stream()
                .map(i -> new ImportNode(
                        i.getNameAsString(),
                        i.isStatic(),
                        i.isAsterisk()))
                .toList();

        List<String> importStrings = cu.getImports().stream()
                .map(i -> i.getNameAsString())
                .toList();

        List<ClassNode> results = new ArrayList<>();
        for (TypeDeclaration<?> typeDecl : cu.findAll(TypeDeclaration.class)) {
            results.add(buildClassNode(typeDecl, packageName, importNodes, importStrings, filePath.toString()));
        }
        return results;
    }

    public Map<String, Object> parseModuleInfo(Path moduleInfoPath) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(moduleInfoPath);
        return cu.getModule().map(mod -> {
            Map<String, Object> info = new java.util.LinkedHashMap<>();
            info.put("name", mod.getNameAsString());
            info.put("isOpen", mod.isOpen());
            info.put("requires", mod.findAll(com.github.javaparser.ast.modules.ModuleRequiresDirective.class)
                    .stream().map(r -> r.getNameAsString()).toList());
            info.put("exports", mod.findAll(com.github.javaparser.ast.modules.ModuleExportsDirective.class)
                    .stream().map(e -> e.getNameAsString()).toList());
            info.put("opens", mod.findAll(com.github.javaparser.ast.modules.ModuleOpensDirective.class)
                    .stream().map(o -> o.getNameAsString()).toList());
            info.put("uses", mod.findAll(com.github.javaparser.ast.modules.ModuleUsesDirective.class)
                    .stream().map(u -> u.getNameAsString()).toList());
            info.put("provides", mod.findAll(com.github.javaparser.ast.modules.ModuleProvidesDirective.class)
                    .stream().map(p -> p.getNameAsString()).toList());
            return info;
        }).orElse(null);
    }

    private ClassNode buildClassNode(TypeDeclaration<?> typeDecl, String packageName,
                                     List<ImportNode> importNodes, List<String> importStrings,
                                     String filePath) {
        String simpleName = typeDecl.getNameAsString();
        String fqn = resolveFullyQualifiedName(typeDecl, packageName);

        ClassKind kind = resolveKind(typeDecl);
        List<String> modifiers = extractModifiers(typeDecl);
        List<AnnotationNode> annotationNodes = extractAnnotationNodes(typeDecl);
        List<String> annotations = annotationNodes.stream().map(AnnotationNode::name).toList();
        List<String> typeParameters = extractTypeParameters(typeDecl);

        String superClass = null;
        List<String> interfaces = List.of();
        if (typeDecl instanceof ClassOrInterfaceDeclaration cid) {
            superClass = cid.getExtendedTypes().stream()
                    .findFirst()
                    .map(t -> JavaFqnResolver.resolveToFqn(t.getNameAsString(), importNodes, packageName))
                    .orElse(null);
            interfaces = cid.getImplementedTypes().stream()
                    .map(t -> JavaFqnResolver.resolveToFqn(t.getNameAsString(), importNodes, packageName))
                    .toList();
        } else if (typeDecl instanceof EnumDeclaration ed) {
            interfaces = ed.getImplementedTypes().stream()
                    .map(t -> JavaFqnResolver.resolveToFqn(t.getNameAsString(), importNodes, packageName))
                    .toList();
        } else if (typeDecl instanceof RecordDeclaration rd) {
            interfaces = rd.getImplementedTypes().stream()
                    .map(t -> JavaFqnResolver.resolveToFqn(t.getNameAsString(), importNodes, packageName))
                    .toList();
        }

        List<MethodNode> methods = new ArrayList<>();
        methods.addAll(extractMethods(typeDecl));
        methods.addAll(extractConstructors(typeDecl));

        return new ClassNode(
                fqn, packageName, simpleName, filePath, Language.JAVA,
                kind, modifiers, annotationNodes, annotations,
                importNodes, importStrings, typeParameters,
                superClass, interfaces, List.of(),
                methods, extractFields(typeDecl), extractEnumConstants(typeDecl),
                extractRecordComponents(typeDecl), extractStaticInitializers(typeDecl),
                Map.of("parserBackend", backend().cliValue()), null, null, MigrationStatus.PENDING
        );
    }

    private String resolveFullyQualifiedName(TypeDeclaration<?> typeDecl, String packageName) {
        List<String> nameParts = new ArrayList<>();
        nameParts.add(typeDecl.getNameAsString());

        var parent = typeDecl.getParentNode().orElse(null);
        while (parent instanceof TypeDeclaration<?> enclosing) {
            nameParts.addFirst(enclosing.getNameAsString());
            parent = enclosing.getParentNode().orElse(null);
        }

        return JavaFqnResolver.buildFqn(packageName, nameParts.subList(0, nameParts.size() - 1), typeDecl.getNameAsString());
    }

    private ClassKind resolveKind(TypeDeclaration<?> typeDecl) {
        if (typeDecl instanceof EnumDeclaration) return ClassKind.ENUM;
        if (typeDecl instanceof RecordDeclaration) return ClassKind.RECORD;
        if (typeDecl instanceof AnnotationDeclaration) return ClassKind.ANNOTATION;
        if (typeDecl instanceof ClassOrInterfaceDeclaration cid && cid.isInterface()) return ClassKind.INTERFACE;
        return ClassKind.CLASS;
    }

    private List<String> extractModifiers(NodeWithModifiers<?> node) {
        return node.getModifiers().stream()
                .map(m -> m.getKeyword().asString())
                .toList();
    }

    private List<AnnotationNode> extractAnnotationNodes(NodeWithAnnotations<?> node) {
        return node.getAnnotations().stream()
                .map(this::parseAnnotation)
                .toList();
    }

    private AnnotationNode parseAnnotation(AnnotationExpr expr) {
        String name = expr.getNameAsString();
        List<AnnotationAttribute> attributes = new ArrayList<>();

        if (expr instanceof SingleMemberAnnotationExpr single) {
            attributes.add(new AnnotationAttribute("value", single.getMemberValue().toString()));
        } else if (expr instanceof NormalAnnotationExpr normal) {
            for (var pair : normal.getPairs()) {
                attributes.add(new AnnotationAttribute(pair.getNameAsString(), pair.getValue().toString()));
            }
        }
        return new AnnotationNode(name, attributes);
    }

    private List<String> extractAnnotationStrings(NodeWithAnnotations<?> node) {
        return node.getAnnotations().stream()
                .map(AnnotationExpr::toString)
                .toList();
    }

    private List<String> extractTypeParameters(TypeDeclaration<?> typeDecl) {
        if (typeDecl instanceof ClassOrInterfaceDeclaration cid) {
            return cid.getTypeParameters().stream().map(Object::toString).toList();
        }
        if (typeDecl instanceof RecordDeclaration rd) {
            return rd.getTypeParameters().stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private List<String> extractMethodTypeParameters(CallableDeclaration<?> decl) {
        return decl.getTypeParameters().stream().map(Object::toString).toList();
    }

    private List<MethodNode> extractMethods(TypeDeclaration<?> typeDecl) {
        return typeDecl.getMethods().stream().map(md -> {
            List<AnnotationNode> annotNodes = extractAnnotationNodes(md);
            List<String> annotStrings = annotNodes.stream().map(AnnotationNode::name).toList();
            List<ParameterNode> params = md.getParameters().stream()
                    .map(p -> new ParameterNode(
                            p.getTypeAsString(),
                            p.getNameAsString(),
                            extractAnnotationStrings(p)))
                    .toList();
            List<String> thrown = md.getThrownExceptions().stream().map(t -> t.asString()).toList();
            List<String> internalCalls = md.findAll(MethodCallExpr.class).stream()
                    .map(MethodCallExpr::getNameAsString).toList();
            List<String> methodRefs = md.findAll(MethodReferenceExpr.class).stream()
                    .map(ref -> ref.getScope().toString() + "::" + ref.getIdentifier())
                    .toList();
            String body = md.getBody().map(Object::toString).orElse("");

            return new MethodNode(
                    md.getNameAsString(),
                    md.getDeclarationAsString(),
                    md.getTypeAsString(),
                    extractModifiers(md),
                    annotNodes, annotStrings, extractMethodTypeParameters(md),
                    params, thrown, false,
                    body, internalCalls, methodRefs, null, null
            );
        }).toList();
    }

    private List<MethodNode> extractConstructors(TypeDeclaration<?> typeDecl) {
        return typeDecl.getConstructors().stream().map(cd -> {
            List<AnnotationNode> annotNodes = extractAnnotationNodes(cd);
            List<String> annotStrings = annotNodes.stream().map(AnnotationNode::name).toList();
            List<ParameterNode> params = cd.getParameters().stream()
                    .map(p -> new ParameterNode(
                            p.getTypeAsString(),
                            p.getNameAsString(),
                            extractAnnotationStrings(p)))
                    .toList();
            List<String> thrown = cd.getThrownExceptions().stream().map(t -> t.asString()).toList();
            List<String> internalCalls = cd.findAll(MethodCallExpr.class).stream()
                    .map(MethodCallExpr::getNameAsString).toList();
            List<String> methodRefs = cd.findAll(MethodReferenceExpr.class).stream()
                    .map(ref -> ref.getScope().toString() + "::" + ref.getIdentifier())
                    .toList();

            return new MethodNode(
                    cd.getNameAsString(),
                    cd.getDeclarationAsString(),
                    "<init>",
                    extractModifiers(cd),
                    annotNodes, annotStrings, extractMethodTypeParameters(cd),
                    params, thrown, true,
                    cd.getBody().toString(), internalCalls, methodRefs, null, null
            );
        }).toList();
    }

    private List<FieldNode> extractFields(TypeDeclaration<?> typeDecl) {
        List<FieldNode> fields = new ArrayList<>();
        for (FieldDeclaration fd : typeDecl.getFields()) {
            List<String> modifiers = extractModifiers(fd);
            List<String> annotations = extractAnnotationStrings(fd);
            fd.getVariables().forEach(v -> fields.add(new FieldNode(
                    v.getTypeAsString(), v.getNameAsString(),
                    modifiers, annotations,
                    v.getInitializer().map(Object::toString).orElse(null)
            )));
        }
        return fields;
    }

    private List<EnumConstantNode> extractEnumConstants(TypeDeclaration<?> typeDecl) {
        if (!(typeDecl instanceof EnumDeclaration ed)) return List.of();
        return ed.getEntries().stream().map(entry -> new EnumConstantNode(
                entry.getNameAsString(),
                entry.getArguments().stream().map(Object::toString).toList(),
                extractAnnotationStrings(entry)
        )).toList();
    }

    private List<RecordComponentNode> extractRecordComponents(TypeDeclaration<?> typeDecl) {
        if (!(typeDecl instanceof RecordDeclaration rd)) return List.of();
        return rd.getParameters().stream().map(p -> new RecordComponentNode(
                p.getTypeAsString(),
                p.getNameAsString(),
                extractAnnotationStrings(p)
        )).toList();
    }

    private List<String> extractStaticInitializers(TypeDeclaration<?> typeDecl) {
        return typeDecl.findAll(InitializerDeclaration.class).stream()
                .filter(InitializerDeclaration::isStatic)
                .map(id -> id.getBody().toString())
                .toList();
    }
}
