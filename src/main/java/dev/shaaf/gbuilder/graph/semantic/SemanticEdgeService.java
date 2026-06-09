package dev.shaaf.gbuilder.graph.semantic;

import dev.shaaf.gbuilder.graph.EdgeProvenance;
import dev.shaaf.gbuilder.graph.EmbeddingService;
import dev.shaaf.gbuilder.graph.GraphNodeIds;
import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class SemanticEdgeService {

    private static final int MAX_SEMANTIC_EDGES = 50;

    @Inject
    GraphRepository graphRepository;

    @Inject
    EmbeddingService embeddingService;

    public int addSemanticSimilarityEdges(List<ClassNode> classNodes) {
        List<GraphRepository.StoredEmbedding> classEmbeddings = graphRepository.listStoredEmbeddings().stream()
                .filter(e -> e.kind() == GraphRepository.EmbeddingKind.CLASS)
                .toList();
        if (classEmbeddings.size() >= 2) {
            return addEmbeddingSimilarityEdges(classEmbeddings);
        }
        if (classNodes.size() < 2) {
            return 0;
        }
        return addHeuristicSimilarityEdges(classNodes);
    }

    private int addEmbeddingSimilarityEdges(List<GraphRepository.StoredEmbedding> embeddings) {
        Set<String> internal = new HashSet<>(graphRepository.listInternalClassFqns());
        List<SimilarPair> pairs = new ArrayList<>();
        double threshold = embeddingService.similarityThreshold();

        for (int i = 0; i < embeddings.size(); i++) {
            for (int j = i + 1; j < embeddings.size(); j++) {
                GraphRepository.StoredEmbedding a = embeddings.get(i);
                GraphRepository.StoredEmbedding b = embeddings.get(j);
                if (!internal.contains(a.classFqn()) || !internal.contains(b.classFqn())) {
                    continue;
                }
                if (a.classFqn().equals(b.classFqn())) {
                    continue;
                }
                double score = embeddingService.cosineSimilarity(a.vector(), b.vector());
                if (score >= threshold) {
                    pairs.add(new SimilarPair(a.classFqn(), b.classFqn(), score));
                }
            }
        }

        pairs.sort(Comparator.comparingDouble(SimilarPair::score).reversed());
        int added = 0;
        Set<String> seen = new HashSet<>();
        for (SimilarPair pair : pairs) {
            if (added >= MAX_SEMANTIC_EDGES) {
                break;
            }
            String key = pairKey(pair.a(), pair.b());
            if (!seen.add(key)) {
                continue;
            }
            graphRepository.insertEdge(
                    "SEMANTICALLY_SIMILAR",
                    GraphNodeIds.classId(pair.a()),
                    GraphNodeIds.classId(pair.b()),
                    pair.score(),
                    EdgeProvenance.INFERRED,
                    pair.score());
            added++;
        }
        return added;
    }

    private int addHeuristicSimilarityEdges(List<ClassNode> classNodes) {
        int added = 0;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < classNodes.size(); i++) {
            for (int j = i + 1; j < classNodes.size(); j++) {
                ClassNode a = classNodes.get(i);
                ClassNode b = classNodes.get(j);
                if (a.kind() == b.kind() && a.methods().size() == b.methods().size()
                        && !a.fullyQualifiedName().equals(b.fullyQualifiedName())) {
                    String key = pairKey(a.fullyQualifiedName(), b.fullyQualifiedName());
                    if (seen.add(key)) {
                        graphRepository.insertEdge(
                                "SEMANTICALLY_SIMILAR",
                                GraphNodeIds.classId(a.fullyQualifiedName()),
                                GraphNodeIds.classId(b.fullyQualifiedName()),
                                0.5,
                                EdgeProvenance.INFERRED,
                                0.65);
                        added++;
                    }
                }
            }
        }
        return Math.min(added, MAX_SEMANTIC_EDGES);
    }

    public void addTechnologyHyperedges(List<ClassNode> classNodes) {
        Map<String, Set<String>> classToTech = graphRepository.findAllClassTechnologyCategories();
        Map<String, Set<String>> techToClasses = new java.util.HashMap<>();
        for (var entry : classToTech.entrySet()) {
            for (String category : entry.getValue()) {
                techToClasses.computeIfAbsent(category, k -> new HashSet<>()).add(entry.getKey());
            }
        }
        for (var entry : techToClasses.entrySet()) {
            if (entry.getValue().size() >= 3) {
                graphRepository.persistHyperedge(
                        "tech-" + entry.getKey().toLowerCase().replace(' ', '-'),
                        entry.getKey() + " users",
                        "participate_in",
                        entry.getValue().stream().sorted().toList(),
                        EdgeProvenance.INFERRED,
                        0.85);
            }
        }
    }

    private String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private record SimilarPair(String a, String b, double score) {}
}
