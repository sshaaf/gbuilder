package dev.shaaf.gbuilder.graph.query;

import dev.shaaf.gbuilder.graph.GraphRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jgrapht.Graph;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultUndirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class GraphPathService {

    private static final Set<String> PATH_EDGE_TYPES = Set.of(
            "DEPENDS_ON", "EXTENDS", "IMPLEMENTS", "USES");

    @Inject
    GraphRepository graphRepository;

    public PathResult findPath(String fromQuery, String toQuery) {
        List<String> fqns = graphRepository.listInternalClassFqns();
        String from = resolveFqn(fromQuery, fqns).orElse(null);
        String to = resolveFqn(toQuery, fqns).orElse(null);
        if (from == null || to == null) {
            return new PathResult(fromQuery, toQuery, List.of(), "Could not resolve one or both types.");
        }
        if (from.equals(to)) {
            return new PathResult(from, to, List.of(from), "Start and end are the same type.");
        }

        Graph<String, DefaultWeightedEdge> graph = buildClassGraph();
        var dijkstra = new DijkstraShortestPath<>(graph);
        var path = dijkstra.getPath(from, to);
        if (path == null) {
            return new PathResult(from, to, List.of(), "No path found between types.");
        }

        List<String> hops = path.getVertexList();
        String explanation = explainPath(hops);
        return new PathResult(from, to, hops, explanation);
    }

    private Graph<String, DefaultWeightedEdge> buildClassGraph() {
        Graph<String, DefaultWeightedEdge> graph =
                new DefaultUndirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (String fqn : graphRepository.listInternalClassFqns()) {
            graph.addVertex(fqn);
        }
        Map<String, DefaultWeightedEdge> edgeCache = new HashMap<>();
        for (var edge : graphRepository.listClassLevelEdges()) {
            if (!PATH_EDGE_TYPES.contains(edge.type())) {
                continue;
            }
            String from = fqnFromId(edge.fromId());
            String to = fqnFromId(edge.toId());
            if (from == null || to == null || !graph.containsVertex(from) || !graph.containsVertex(to)) {
                continue;
            }
            String key = from.compareTo(to) <= 0 ? from + "|" + to : to + "|" + from;
            if (!edgeCache.containsKey(key)) {
                DefaultWeightedEdge e = graph.addEdge(from, to);
                if (e != null) {
                    graph.setEdgeWeight(e, 1.0);
                    edgeCache.put(key, e);
                }
            }
        }
        return graph;
    }

    private String explainPath(List<String> hops) {
        StringBuilder sb = new StringBuilder("Path (").append(hops.size()).append(" hops): ");
        for (int i = 0; i < hops.size(); i++) {
            if (i > 0) {
                sb.append(" → ");
            }
            sb.append(simpleName(hops.get(i)));
        }
        sb.append(". Each hop is a structural relationship in the Java graph.");
        return sb.toString();
    }

    private Optional<String> resolveFqn(String query, List<String> fqns) {
        return fqns.stream()
                .filter(f -> f.equals(query) || f.endsWith("." + query) || simpleName(f).equals(query))
                .findFirst();
    }

    private String fqnFromId(String nodeId) {
        if (nodeId != null && nodeId.startsWith("class:")) {
            return nodeId.substring("class:".length());
        }
        return null;
    }

    private String simpleName(String fqn) {
        return fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
    }

    public record PathResult(String fromFqn, String toFqn, List<String> hops, String explanation) {}
}
