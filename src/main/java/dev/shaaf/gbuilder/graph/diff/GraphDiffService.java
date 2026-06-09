package dev.shaaf.gbuilder.graph.diff;

import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.GraphStats;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class GraphDiffService {

    @Inject
    GraphRepository graphRepository;

    public GraphDiffResult compute(GraphSnapshot before, GraphSnapshot after) {
        Set<String> beforeTypes = new HashSet<>(before.typeFqns());
        Set<String> afterTypes = new HashSet<>(after.typeFqns());

        List<String> added = afterTypes.stream().filter(t -> !beforeTypes.contains(t)).sorted().toList();
        List<String> removed = beforeTypes.stream().filter(t -> !afterTypes.contains(t)).sorted().toList();

        long addedEdges = Math.max(0, after.edgeCount() - before.edgeCount() + removed.size());
        long removedEdges = Math.max(0, before.edgeCount() - after.edgeCount() + added.size());

        String summary = String.format(
                "+%d types, -%d types, net edges %+d",
                added.size(), removed.size(), after.edgeCount() - before.edgeCount());

        return new GraphDiffResult(
                before.typeCount(),
                after.typeCount(),
                before.edgeCount(),
                after.edgeCount(),
                added,
                removed,
                addedEdges,
                removedEdges,
                summary
        );
    }

    public GraphSnapshot snapshot() {
        GraphStats stats = graphRepository.getGraphStats();
        List<String> fqns = new ArrayList<>(graphRepository.listInternalClassFqns());
        long edgeCount = stats.containsEdges() + stats.callsEdges() + stats.dependsOnEdges()
                + stats.extendsEdges() + stats.implementsEdges() + stats.usesEdges();
        return new GraphSnapshot(stats.typeCount(), edgeCount, fqns);
    }

    public record GraphSnapshot(long typeCount, long edgeCount, List<String> typeFqns) {}
}
