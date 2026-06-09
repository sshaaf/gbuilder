package dev.shaaf.gbuilder.graph.semantic;

import dev.shaaf.gbuilder.graph.EdgeProvenance;
import dev.shaaf.gbuilder.graph.EmbeddingService;
import dev.shaaf.gbuilder.graph.GraphNodeIds;
import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class SemanticEdgeService {

    @Inject
    GraphRepository graphRepository;

    @Inject
    EmbeddingService embeddingService;

    public int addSemanticSimilarityEdges(List<ClassNode> classNodes) {
        if (!embeddingService.isEnabled() || classNodes.size() < 2) {
            return addHeuristicSimilarityEdges(classNodes);
        }
        int added = 0;
        for (int i = 0; i < classNodes.size(); i++) {
            for (int j = i + 1; j < classNodes.size(); j++) {
                ClassNode a = classNodes.get(i);
                ClassNode b = classNodes.get(j);
                if (samePackage(a, b) && similarShape(a, b)) {
                    graphRepository.insertEdge(
                            "SEMANTICALLY_SIMILAR",
                            GraphNodeIds.classId(a.fullyQualifiedName()),
                            GraphNodeIds.classId(b.fullyQualifiedName()),
                            0.5,
                            EdgeProvenance.INFERRED,
                            0.75);
                    added++;
                }
            }
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
        return Math.min(added, 50);
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

    private boolean samePackage(ClassNode a, ClassNode b) {
        return a.packageName().equals(b.packageName());
    }

    private boolean similarShape(ClassNode a, ClassNode b) {
        return a.kind() == b.kind()
                && Math.abs(a.methods().size() - b.methods().size()) <= 2;
    }

    private String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }
}
