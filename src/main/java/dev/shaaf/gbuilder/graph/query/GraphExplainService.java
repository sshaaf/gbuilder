package dev.shaaf.gbuilder.graph.query;

import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class GraphExplainService {

    private static final Set<String> NEIGHBOR_TYPES = Set.of(
            "DEPENDS_ON", "CALLS", "EXTENDS", "IMPLEMENTS", "USES");

    @Inject
    GraphRepository graphRepository;

    public String explain(String nodeQuery) {
        Optional<ClassNode> node = resolveNode(nodeQuery);
        if (node.isEmpty()) {
            return "No type found matching: " + nodeQuery;
        }
        ClassNode type = node.get();
        List<String> neighbors = graphRepository.getNeighbors(type.fullyQualifiedName(), 1, NEIGHBOR_TYPES);
        int communityId = graphRepository.getCommunityId(type.fullyQualifiedName()).orElse(-1);
        String communityLabel = communityId >= 0
                ? graphRepository.getCommunityLabel(communityId).orElse("Community " + communityId)
                : "unassigned";

        return String.format(
                "%s (%s) in package %s is a %s with %d methods and %d fields. "
                        + "It belongs to %s and connects to %d neighbor types: %s.",
                type.simpleName(),
                type.fullyQualifiedName(),
                type.packageName().isBlank() ? "(default)" : type.packageName(),
                type.kind().name().toLowerCase(),
                type.methods().size(),
                type.fields().size(),
                communityLabel,
                neighbors.size(),
                neighbors.stream().limit(5).map(this::simpleName).reduce((a, b) -> a + ", " + b).orElse("none"));
    }

    private Optional<ClassNode> resolveNode(String query) {
        return graphRepository.listInternalClassFqns().stream()
                .filter(f -> f.equals(query) || f.endsWith("." + query) || simpleName(f).equals(query))
                .findFirst()
                .flatMap(graphRepository::findClassNode);
    }

    private String simpleName(String fqn) {
        return fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
    }
}
