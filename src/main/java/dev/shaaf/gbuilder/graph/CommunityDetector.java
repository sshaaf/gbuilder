package dev.shaaf.gbuilder.graph;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jgrapht.Graph;
import org.jgrapht.alg.clustering.LabelPropagationClustering;
import org.jgrapht.graph.DefaultUndirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CommunityDetector {

    private static final Logger LOG = Logger.getLogger(CommunityDetector.class);

    @Inject
    GraphRepository graphRepository;

    @Inject
    GraphStoreLocation storeLocation;

    public Map<String, Integer> detectAndAssign() {
        Map<String, Integer> communities = detectCommunities();
        if (!communities.isEmpty()) {
            graphRepository.assignCommunities(communities);
            LOG.infof("Assigned %d classes to %d communities",
                    communities.size(), new HashSet<>(communities.values()).size());
        }
        return communities;
    }

    public Map<String, Integer> detectCommunities() {
        Set<String> internalClasses = loadInternalClassFqns();
        if (internalClasses.isEmpty()) {
            return Map.of();
        }

        Graph<String, DefaultWeightedEdge> graph =
                new DefaultUndirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (String fqn : internalClasses) {
            graph.addVertex(fqn);
        }

        Map<String, Double> edgeWeights = loadClassLevelEdgeWeights(internalClasses);
        for (var entry : edgeWeights.entrySet()) {
            String[] parts = entry.getKey().split("\u0000");
            String a = parts[0];
            String b = parts[1];
            if (!graph.containsVertex(a)) {
                graph.addVertex(a);
            }
            if (!graph.containsVertex(b)) {
                graph.addVertex(b);
            }
            DefaultWeightedEdge edge = graph.addEdge(a, b);
            if (edge != null) {
                graph.setEdgeWeight(edge, entry.getValue());
            }
        }

        LabelPropagationClustering<String, DefaultWeightedEdge> clustering =
                new LabelPropagationClustering<>(graph);
        LabelPropagationClustering.Clustering<String> result = clustering.getClustering();

        Map<String, Integer> communities = new HashMap<>();
        int communityIndex = 0;
        for (Set<String> community : result.getClusters()) {
            for (String fqn : community) {
                communities.put(fqn, communityIndex);
            }
            communityIndex++;
        }
        return communities;
    }

    private Set<String> loadInternalClassFqns() {
        Set<String> classes = new HashSet<>();
        try (Connection conn = openConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT fqn FROM nodes
                    WHERE kind = 'CLASS'
                      AND (json_extract(data, '$.external') IS NULL
                           OR json_extract(data, '$.external') = false)
                    """)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        classes.add(rs.getString("fqn"));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load internal classes for community detection", e);
        }
        return classes;
    }

    private Map<String, Double> loadClassLevelEdgeWeights(Set<String> internalClasses) {
        Map<String, Double> weights = new HashMap<>();
        try (Connection conn = openConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT type, from_id, to_id, weight FROM edges")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String type = rs.getString("type");
                        String fromFqn = classFqnForEdgeEndpoint(rs.getString("from_id"), internalClasses);
                        String toFqn = classFqnForEdgeEndpoint(rs.getString("to_id"), internalClasses);
                        if (fromFqn == null || toFqn == null || fromFqn.equals(toFqn)) {
                            continue;
                        }
                        if (!isCommunityEdgeType(type)) {
                            continue;
                        }
                        double weight = communityEdgeWeight(type, rs.getDouble("weight"));
                        String key = undirectedKey(fromFqn, toFqn);
                        weights.merge(key, weight, Double::sum);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load edges for community detection", e);
        }
        return weights;
    }

    private String classFqnForEdgeEndpoint(String nodeId, Set<String> internalClasses) {
        if (nodeId.startsWith("class:")) {
            String fqn = GraphNodeIds.fqnFromClassId(nodeId);
            return internalClasses.contains(fqn) ? fqn : null;
        }
        if (nodeId.startsWith("method:")) {
            String fqn = GraphNodeIds.classFqnFromMethodId(nodeId);
            return internalClasses.contains(fqn) ? fqn : null;
        }
        return null;
    }

    private boolean isCommunityEdgeType(String type) {
        return switch (type) {
            case "DEPENDS_ON", "CALLS", "EXTENDS", "IMPLEMENTS" -> true;
            default -> false;
        };
    }

    private double communityEdgeWeight(String type, double storedWeight) {
        return switch (type) {
            case "DEPENDS_ON" -> 3.0;
            case "CALLS" -> 2.0;
            case "EXTENDS", "IMPLEMENTS" -> 1.0;
            default -> storedWeight;
        };
    }

    private String undirectedKey(String a, String b) {
        if (a.compareTo(b) <= 0) {
            return a + "\u0000" + b;
        }
        return b + "\u0000" + a;
    }

    private Connection openConnection() throws SQLException {
        Path dbPath = storeLocation.current();
        storeLocation.ensureParentDirectory(dbPath);
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        return java.sql.DriverManager.getConnection(jdbcUrl);
    }
}
