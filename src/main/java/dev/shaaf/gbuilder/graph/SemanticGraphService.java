package dev.shaaf.gbuilder.graph;

import dev.shaaf.gbuilder.analyzer.AnalyzerService;
import dev.shaaf.gbuilder.analyzer.model.AnalysisResult;
import dev.shaaf.gbuilder.lang.ParserBackend;
import dev.shaaf.gbuilder.lang.ParserFacade;
import dev.shaaf.gbuilder.graph.analysis.GraphAnalysisResult;
import dev.shaaf.gbuilder.graph.analysis.GraphAnalyzer;
import dev.shaaf.gbuilder.graph.benchmark.TokenBenchmarkService;
import dev.shaaf.gbuilder.graph.diff.GraphDiffResult;
import dev.shaaf.gbuilder.graph.diff.GraphDiffService;
import dev.shaaf.gbuilder.graph.export.GraphExportService;
import dev.shaaf.gbuilder.graph.manifest.GraphManifestService;
import dev.shaaf.gbuilder.graph.report.GraphReportGenerator;
import dev.shaaf.gbuilder.graph.semantic.SemanticEdgeService;
import dev.shaaf.gbuilder.graph.model.ClassKind;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import dev.shaaf.gbuilder.graph.model.FieldNode;
import dev.shaaf.gbuilder.graph.model.MethodNode;
import dev.shaaf.gbuilder.graph.model.MigrationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Main facade for the Graph Builder agent. Orchestrates parsing and persistence.
 * Imports and annotations are captured as raw data on each ClassNode —
 * classification (e.g. deprecated, EJB) is deferred to a future detection agent
 * or user-supplied rules.
 * Embedding enrichment is stubbed and wired to LangChain4j in a later phase.
 */
@ApplicationScoped
public class SemanticGraphService {

    private static final Logger LOG = Logger.getLogger(SemanticGraphService.class);

    @Inject
    ParserFacade parserFacade;

    @Inject
    GraphRepository graphRepo;

    @Inject
    AnalyzerService analyzerService;

    @Inject
    EmbeddingService embeddingService;

    @Inject
    CommunityDetector communityDetector;

    @Inject
    ContextCompiler contextCompiler;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    GraphManifestService manifestService;

    @Inject
    GraphDiffService diffService;

    @Inject
    GraphReportGenerator reportGenerator;

    @Inject
    GraphAnalyzer graphAnalyzer;

    @Inject
    GraphExportService exportService;

    @Inject
    SemanticEdgeService semanticEdgeService;

    @Inject
    TokenBenchmarkService tokenBenchmarkService;

    @Inject
    GraphPersistConfig persistConfig;

    private List<ClassNode> lastBuildNodes = List.of();
    private final ConcurrentHashMap<String, ReentrantLock> nodeLocks = new ConcurrentHashMap<>();

    public GraphBuildOutcome buildGraph(Path codebaseRoot) throws IOException {
        return buildGraph(codebaseRoot, ParserBackend.JPARSER, GraphBuildOptions.defaults());
    }

    public GraphBuildOutcome buildGraph(Path codebaseRoot, ParserBackend parserBackend) throws IOException {
        return buildGraph(codebaseRoot, parserBackend, GraphBuildOptions.defaults());
    }

    public GraphBuildOutcome buildGraph(Path codebaseRoot, ParserBackend parserBackend,
                                        GraphBuildOptions options) throws IOException {
        LOG.infof("Building semantic graph from: %s using parser backend: %s (incremental=%s)",
                codebaseRoot, parserBackend.cliValue(), options.incremental());

        graphRepo.setStoreRoot(codebaseRoot);
        long start = System.currentTimeMillis();

        if (options.clusterOnly()) {
            return clusterOnly(codebaseRoot, parserBackend, start);
        }

        Optional<GraphDiffService.GraphSnapshot> snapshotBefore = options.incremental()
                ? Optional.of(diffService.snapshot())
                : Optional.empty();

        List<ClassNode> classNodes;
        if (options.incremental()) {
            classNodes = buildIncremental(codebaseRoot, parserBackend);
        } else {
            graphRepo.clearAll();
            classNodes = parserFacade.extract(codebaseRoot, parserBackend);
            persistGraph(classNodes);
        }

        classNodes = graphRepo.findAllClassNodes();
        java.util.Set<String> knownFqns = classNodes.stream()
                .map(ClassNode::fullyQualifiedName)
                .collect(java.util.stream.Collectors.toSet());

        buildStructuralEdges(classNodes, knownFqns);

        AnalysisResult analysisResult = analyzerService.analyze(classNodes);

        if (options.deepMode()) {
            int semantic = semanticEdgeService.addSemanticSimilarityEdges(classNodes);
            semanticEdgeService.addTechnologyHyperedges(classNodes);
            LOG.infof("Deep mode: added %d semantic similarity edges", semantic);
        }

        communityDetector.setClusteringAlgorithm(options.clusteringAlgorithm());
        var communities = communityDetector.detectAndAssign();
        int communityCount = new java.util.HashSet<>(communities.values()).size();

        int embeddedMethods = 0;
        int embeddedClasses = 0;
        if (embeddingService.isEnabled()) {
            var embeddingStats = persistEmbeddings(classNodes);
            embeddedMethods = embeddingStats.methods;
            embeddedClasses = embeddingStats.classes;
        }

        lastBuildNodes = classNodes;
        manifestService.saveManifest(codebaseRoot, manifestService.scanCurrentHashes(codebaseRoot));

        GraphBuildResult buildResult = toBuildResult(classNodes, analysisResult, embeddedMethods,
                embeddedClasses, communityCount, parserBackend);

        GraphAnalysisResult graphAnalysis = graphAnalyzer.analyze();
        GraphDiffResult diff = snapshotBefore
                .map(before -> diffService.compute(before, diffService.snapshot()))
                .orElse(null);

        long duration = System.currentTimeMillis() - start;
        reportGenerator.writeReport(codebaseRoot, buildResult, options, duration, diff, graphAnalysis);
        exportService.exportGraphJson(codebaseRoot);
        if (!options.skipVisualization()) {
            exportService.exportHtml(codebaseRoot, graphAnalysis);
        }
        if (options.generateWiki()) {
            exportService.exportWiki(codebaseRoot, graphAnalysis);
        }
        if (options.exportSvg()) {
            exportService.exportSvg(codebaseRoot);
        }
        if (options.exportGraphml()) {
            exportService.exportGraphMl(codebaseRoot);
        }
        if (options.exportNeo4j()) {
            exportService.exportNeo4jCypher(codebaseRoot);
        }

        TokenBenchmarkService.BenchmarkResult benchmark = null;
        if (options.benchmark() && classNodes.size() >= 5) {
            benchmark = tokenBenchmarkService.benchmark(codebaseRoot, classNodes);
        }

        return new GraphBuildOutcome(buildResult, diff, graphAnalysis, benchmark, duration);
    }

    public GraphBuildOutcome clusterOnly(Path codebaseRoot, ParserBackend parserBackend, long startTime)
            throws IOException {
        graphRepo.setStoreRoot(codebaseRoot);
        communityDetector.detectAndAssign();
        List<ClassNode> classNodes = graphRepo.findAllClassNodes();
        GraphAnalysisResult graphAnalysis = graphAnalyzer.analyze();
        GraphBuildResult buildResult = toBuildResult(classNodes, null, 0, 0,
                graphAnalysis.communities().size(), parserBackend);
        long duration = System.currentTimeMillis() - startTime;
        GraphBuildOptions options = GraphBuildOptions.defaults().withClusterOnly(true);
        reportGenerator.writeReport(codebaseRoot, buildResult, options, duration, null, graphAnalysis);
        exportService.exportGraphJson(codebaseRoot);
        return new GraphBuildOutcome(buildResult, null, graphAnalysis, null, duration);
    }

    private List<ClassNode> buildIncremental(Path codebaseRoot, ParserBackend parserBackend) throws IOException {
        GraphManifestService.ManifestDiff diff = manifestService.diff(codebaseRoot);
        if (!diff.hasChanges()) {
            LOG.info("No file changes detected — skipping parse");
            return graphRepo.findAllClassNodes();
        }

        List<String> pathsToRemove = new ArrayList<>();
        for (String rel : diff.deleted()) {
            pathsToRemove.add(codebaseRoot.resolve(rel).toAbsolutePath().toString());
        }
        for (String rel : diff.changed()) {
            pathsToRemove.add(codebaseRoot.resolve(rel).toAbsolutePath().toString());
        }
        graphRepo.removeNodesByFilePaths(pathsToRemove);

        List<Path> toParse = manifestService.filesToParse(codebaseRoot, diff);
        if (toParse.isEmpty()) {
            return graphRepo.findAllClassNodes();
        }
        List<ClassNode> parsed = parserFacade.extractFiles(codebaseRoot, toParse, parserBackend);
        persistGraph(parsed);
        return graphRepo.findAllClassNodes();
    }

    private void persistGraph(List<ClassNode> classNodes) {
        flushInChunks(classNodes, persistConfig.persistBatchSize(), graphRepo::persistClassNodesChunk);

        List<GraphRepository.PendingCallEdge> callEdges = new ArrayList<>();
        for (ClassNode classNode : classNodes) {
            collectCallEdges(classNode, callEdges);
        }
        flushInChunks(callEdges, persistConfig.edgeBatchSize(), graphRepo::createCallEdgesChunk);
    }

    private void buildStructuralEdges(List<ClassNode> classNodes, java.util.Set<String> knownFqns) {
        List<GraphRepository.PendingStructuralEdge> edges = new ArrayList<>();
        for (ClassNode classNode : classNodes) {
            if (classNode.superClass() != null) {
                edges.add(new GraphRepository.PendingStructuralEdge(
                        "EXTENDS", classNode.fullyQualifiedName(), classNode.superClass(), 1.0));
            }
            for (String iface : classNode.interfaces()) {
                edges.add(new GraphRepository.PendingStructuralEdge(
                        "IMPLEMENTS", classNode.fullyQualifiedName(), iface, 1.0));
            }
            for (String imp : classNode.imports()) {
                if (knownFqns.contains(imp)) {
                    edges.add(new GraphRepository.PendingStructuralEdge(
                            "DEPENDS_ON", classNode.fullyQualifiedName(), imp, 3.0));
                }
            }
        }
        flushInChunks(edges, persistConfig.edgeBatchSize(), graphRepo::createStructuralEdgesChunk);
    }

    private EmbeddingStats persistEmbeddings(List<ClassNode> classNodes) {
        List<EmbeddingService.ClassEmbeddingVectors> vectors = embeddingService.embedClassNodes(classNodes);
        List<GraphRepository.PendingEmbedding> pending = new ArrayList<>();
        int embeddedMethods = 0;
        int embeddedClasses = 0;

        for (EmbeddingService.ClassEmbeddingVectors classVectors : vectors) {
            for (EmbeddingService.MethodEmbeddingVector methodVector : classVectors.methods()) {
                if (methodVector.vector().length > 0) {
                    pending.add(new GraphRepository.PendingEmbedding(
                            classVectors.classFqn(),
                            methodVector.signature(),
                            methodVector.vector(),
                            GraphRepository.EmbeddingKind.METHOD));
                    embeddedMethods++;
                }
            }
            if (classVectors.classVector().length > 0) {
                pending.add(new GraphRepository.PendingEmbedding(
                        classVectors.classFqn(),
                        null,
                        classVectors.classVector(),
                        GraphRepository.EmbeddingKind.CLASS));
                embeddedClasses++;
            }
        }

        flushInChunks(pending, persistConfig.edgeBatchSize(), graphRepo::storeEmbeddingsChunk);
        return new EmbeddingStats(embeddedMethods, embeddedClasses);
    }

    private <T> void flushInChunks(List<T> items, int chunkSize, java.util.function.Consumer<List<T>> writer) {
        if (items.isEmpty()) {
            return;
        }
        int size = Math.max(1, chunkSize);
        for (int i = 0; i < items.size(); i += size) {
            writer.accept(items.subList(i, Math.min(i + size, items.size())));
        }
    }

    private GraphBuildResult toBuildResult(List<ClassNode> classNodes, AnalysisResult analysisResult,
                                           int embeddedMethods, int embeddedClasses, int communityCount,
                                           ParserBackend parserBackend) {

        int totalMethods = classNodes.stream().mapToInt(c -> c.methods().size()).sum();
        int constructors = classNodes.stream()
                .mapToInt(c -> (int) c.methods().stream().filter(MethodNode::constructor).count()).sum();
        int totalFields = classNodes.stream().mapToInt(c -> c.fields().size()).sum();
        int totalImports = classNodes.stream().mapToInt(c -> c.imports().size()).sum();
        int totalAnnotations = classNodes.stream()
                .mapToInt(c -> c.annotations().size()).sum();
        int enumConstants = classNodes.stream().mapToInt(c -> c.enumConstants().size()).sum();
        int recordComponents = classNodes.stream().mapToInt(c -> c.recordComponents().size()).sum();

        long classes = classNodes.stream().filter(c -> c.kind() == ClassKind.CLASS).count();
        long interfaces = classNodes.stream().filter(c -> c.kind() == ClassKind.INTERFACE).count();
        long enums = classNodes.stream().filter(c -> c.kind() == ClassKind.ENUM).count();
        long records = classNodes.stream().filter(c -> c.kind() == ClassKind.RECORD).count();
        long annotationTypes = classNodes.stream().filter(c -> c.kind() == ClassKind.ANNOTATION).count();

        var kindBreakdown = new KindBreakdown(classes, interfaces, enums, records, annotationTypes);

        LOG.infof("Graph built: %d types (%d classes, %d interfaces, %d enums, %d records, %d @interfaces), " +
                        "%d methods (%d constructors), %d fields, %d imports, %d annotations",
                classNodes.size(), classes, interfaces, enums, records, annotationTypes,
                totalMethods, constructors, totalFields, totalImports, totalAnnotations);

        return new GraphBuildResult(
                classNodes.size(), kindBreakdown,
                totalMethods, constructors, totalFields,
                totalImports, totalAnnotations,
                enumConstants, recordComponents,
                analysisResult,
                embeddedMethods, embeddedClasses,
                communityCount,
                parserBackend
        );
    }

    public void saveQueryResult(String question, String answer, List<String> nodeFqns) {
        graphRepo.persistQaResult(question, answer, nodeFqns);
    }

    public record GraphBuildOutcome(
            GraphBuildResult result,
            GraphDiffResult diff,
            GraphAnalysisResult analysis,
            TokenBenchmarkService.BenchmarkResult benchmark,
            long durationMs
    ) {}

    public ContextCompiler.CompiledContext compileContext(String methodId, int tokenBudget) {
        return contextCompiler.compile(new ContextCompiler.ContextRequest(methodId, tokenBudget));
    }

    private record EmbeddingStats(int methods, int classes) {}

    /**
     * Builds the Surgical AST Context JSON for a specific method, giving the Coder agent
     * exactly the context it needs to rewrite the method safely.
     *
     * @param methodId format: "com.example.MyClass#methodName" or full signature
     * @return JSON string matching the spec.md Surgical Context schema, or empty JSON on not found
     */
    public String buildSurgicalContext(String methodId) {
        String className;
        String methodIdentifier;

        if (methodId.contains("#")) {
            String[] parts = methodId.split("#", 2);
            className = parts[0];
            methodIdentifier = parts[1];
        } else {
            LOG.warnf("Invalid methodId format (expected ClassName#method): %s", methodId);
            return "{}";
        }

        // Wildcard: build a combined context for the entire class
        if ("*".equals(methodIdentifier)) {
            return buildClassWideSurgicalContext(className);
        }

        // Find method node by name first, then try by signature
        Map<String, Object> ctx = graphRepo.findMethodContext(className, methodIdentifier);

        if (ctx.isEmpty()) {
            // Try finding by method name instead of signature
            var callSigs = graphRepo.findInternalCallSignatures(className, List.of(methodIdentifier));
            if (!callSigs.isEmpty()) {
                String fullSig = callSigs.get(0).get("signature");
                ctx = graphRepo.findMethodContext(className, fullSig);
            }
        }

        if (ctx.isEmpty()) {
            LOG.warnf("Method not found in graph: %s", methodId);
            return "{}";
        }

        try {
            return buildContextJson(ctx, className);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to build surgical context for %s", methodId);
            return "{}";
        }
    }

    /**
     * Build surgical context for an entire class — all methods, fields, annotations.
     * Used for paradigm shift slices where the whole class needs rewriting.
     */
    private String buildClassWideSurgicalContext(String className) {
        List<ClassNode> matchingNodes = lastBuildNodes.stream()
                .filter(cn -> cn.fullyQualifiedName().equals(className))
                .toList();

        if (matchingNodes.isEmpty()) {
            LOG.warnf("Class not found in last build: %s", className);
            return "{}";
        }

        ClassNode classNode = matchingNodes.get(0);

        try {
            ObjectNode root = objectMapper.createObjectNode();

            // metadata
            ObjectNode metadata = root.putObject("metadata");
            metadata.put("file_path", classNode.filePath());
            metadata.put("package_name", classNode.packageName());
            metadata.put("class_name", classNode.simpleName());

            // class_context
            ObjectNode classContext = root.putObject("class_context");
            ArrayNode classAnnotations = classContext.putArray("class_annotations");
            for (String ann : classNode.annotations()) {
                classAnnotations.add(ann);
            }

            ArrayNode imports = classContext.putArray("imports");
            for (String imp : classNode.imports()) {
                imports.add(imp);
            }

            // injected dependencies
            ArrayNode injectedDeps = classContext.putArray("injected_dependencies");
            for (FieldNode field : classNode.fields()) {
                boolean isInjected = field.annotations().stream()
                        .anyMatch(a -> a.contains("Inject") || a.contains("EJB") ||
                                a.contains("PersistenceContext") || a.contains("Resource") ||
                                a.contains("Autowired"));
                if (isInjected) {
                    ObjectNode dep = injectedDeps.addObject();
                    dep.put("type", field.type());
                    dep.put("name", field.name());
                    ArrayNode depAnns = dep.putArray("annotations");
                    for (String ann : field.annotations()) {
                        depAnns.add(ann);
                    }
                }
            }

            // all fields
            ArrayNode fieldsArray = classContext.putArray("fields");
            for (FieldNode field : classNode.fields()) {
                ObjectNode fieldNode = fieldsArray.addObject();
                fieldNode.put("type", field.type());
                fieldNode.put("name", field.name());
                ArrayNode mods = fieldNode.putArray("modifiers");
                for (String mod : field.modifiers()) {
                    mods.add(mod);
                }
                ArrayNode anns = fieldNode.putArray("annotations");
                for (String ann : field.annotations()) {
                    anns.add(ann);
                }
            }

            // all methods
            ArrayNode methodsArray = root.putArray("methods");
            for (MethodNode method : classNode.methods()) {
                ObjectNode methodNode = methodsArray.addObject();
                methodNode.put("name", method.name());
                methodNode.put("signature", method.signature());
                methodNode.put("return_type", method.returnType());
                methodNode.put("is_constructor", method.constructor());
                ArrayNode methodAnns = methodNode.putArray("annotations");
                for (String ann : method.annotations()) {
                    methodAnns.add(ann);
                }
                methodNode.put("raw_body", method.rawBody());
                ArrayNode calls = methodNode.putArray("internal_calls");
                for (String call : method.internalMethodCalls()) {
                    calls.add(call);
                }
            }

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);

        } catch (Exception e) {
            LOG.errorf(e, "Failed to build class-wide surgical context for %s", className);
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private String buildContextJson(Map<String, Object> ctx, String className) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();

        // metadata
        ObjectNode metadata = root.putObject("metadata");
        metadata.put("file_path", (String) ctx.get("filePath"));
        metadata.put("package_name", (String) ctx.get("packageName"));
        metadata.put("class_name", (String) ctx.get("simpleName"));

        // class_context
        ObjectNode classContext = root.putObject("class_context");
        ArrayNode classAnnotations = classContext.putArray("class_annotations");
        for (String ann : (List<String>) ctx.get("classAnnotations")) {
            classAnnotations.add(ann);
        }

        // injected_dependencies: fields with @Inject, @EJB, @PersistenceContext, etc.
        ArrayNode injectedDeps = classContext.putArray("injected_dependencies");
        List<ClassNode> matchingNodes = lastBuildNodes.stream()
                .filter(cn -> cn.fullyQualifiedName().equals(className))
                .toList();
        if (!matchingNodes.isEmpty()) {
            ClassNode classNode = matchingNodes.get(0);
            for (FieldNode field : classNode.fields()) {
                boolean isInjected = field.annotations().stream()
                        .anyMatch(a -> a.contains("Inject") || a.contains("EJB") ||
                                       a.contains("PersistenceContext") || a.contains("Resource") ||
                                       a.contains("Autowired"));
                if (isInjected) {
                    ObjectNode dep = injectedDeps.addObject();
                    dep.put("type", field.type());
                    dep.put("name", field.name());
                    ArrayNode depAnns = dep.putArray("annotations");
                    for (String ann : field.annotations()) {
                        depAnns.add(ann);
                    }
                }
            }

            ArrayNode classVars = classContext.putArray("class_variables_accessed_by_target");
            for (FieldNode field : classNode.fields()) {
                boolean isInjected = field.annotations().stream()
                        .anyMatch(a -> a.contains("Inject") || a.contains("EJB") ||
                                       a.contains("PersistenceContext") || a.contains("Resource") ||
                                       a.contains("Autowired"));
                if (!isInjected) {
                    ObjectNode varNode = classVars.addObject();
                    varNode.put("type", field.type());
                    varNode.put("name", field.name());
                    ArrayNode mods = varNode.putArray("modifiers");
                    for (String mod : field.modifiers()) {
                        mods.add(mod);
                    }
                    if (field.value() != null) {
                        varNode.put("value", field.value());
                    }
                }
            }
        }

        // target_method
        ObjectNode targetMethod = root.putObject("target_method");
        targetMethod.put("name", (String) ctx.get("name"));
        targetMethod.put("signature", (String) ctx.get("signature"));
        ArrayNode methodAnns = targetMethod.putArray("method_annotations");
        for (String ann : (List<String>) ctx.get("annotations")) {
            methodAnns.add(ann);
        }
        targetMethod.put("raw_body", (String) ctx.get("rawBody"));

        ArrayNode internalCalls = targetMethod.putArray("internal_method_calls");
        List<String> callNames = (List<String>) ctx.get("internalCalls");
        if (callNames != null && !callNames.isEmpty()) {
            var callDetails = graphRepo.findInternalCallSignatures(className, callNames);
            for (var call : callDetails) {
                ObjectNode callNode = internalCalls.addObject();
                callNode.put("method_name", call.get("method_name"));
                callNode.put("signature", call.get("signature"));
                callNode.put("return_type", call.get("return_type"));
            }
        }

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private void collectCallEdges(ClassNode node, List<GraphRepository.PendingCallEdge> edges) {
        for (MethodNode method : node.methods()) {
            for (String calledMethod : method.internalMethodCalls()) {
                edges.add(new GraphRepository.PendingCallEdge(
                        method.signature(), node.fullyQualifiedName(), calledMethod));
            }
        }
    }

    public List<ClassNode> getLastBuildNodes() {
        return lastBuildNodes;
    }

    public String getClusterJson(String clusterId) {
        return graphRepo.getClusterJson(clusterId);
    }

    public void acquireSliceLock(String nodeId) {
        nodeLocks.computeIfAbsent(nodeId, k -> new ReentrantLock()).lock();
    }

    public void releaseSliceLock(String nodeId) {
        ReentrantLock lock = nodeLocks.get(nodeId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    public void markNodeStatus(String fqn, MigrationStatus status) {
        graphRepo.updateNodeStatus(fqn, status);
    }

    public record KindBreakdown(
        long classes,
        long interfaces,
        long enums,
        long records,
        long annotationTypes
    ) {}

    public record GraphBuildResult(
        int typeCount,
        KindBreakdown kindBreakdown,
        int methodCount,
        int constructorCount,
        int fieldCount,
        int importCount,
        int annotationCount,
        int enumConstantCount,
        int recordComponentCount,
        AnalysisResult analysisResult,
        int embeddedMethodCount,
        int embeddedClassCount,
        int communityCount,
        ParserBackend parserBackend
    ) {}
}
