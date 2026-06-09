package dev.shaaf.gbuilder.graph.merge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.shaaf.gbuilder.graph.GraphStoreLocation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

@ApplicationScoped
public class GraphMergeService {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    GraphStoreLocation storeLocation;

    public void mergeDatabases(Path primaryCodebase, Path secondaryCodebase, String repoLabel) throws IOException {
        Path primaryDb = primaryCodebase.resolve(".gbuilder").resolve("graph.db");
        Path secondaryDb = secondaryCodebase.resolve(".gbuilder").resolve("graph.db");
        if (!Files.exists(primaryDb) || !Files.exists(secondaryDb)) {
            throw new IOException("Both codebases must have existing .gbuilder/graph.db stores");
        }

        String jdbcPrimary = "jdbc:sqlite:" + primaryDb.toAbsolutePath();
        String jdbcSecondary = "jdbc:sqlite:" + secondaryDb.toAbsolutePath();

        try (Connection target = DriverManager.getConnection(jdbcPrimary);
             Connection source = DriverManager.getConnection(jdbcSecondary)) {
            target.setAutoCommit(false);
            copyNodes(source, target, repoLabel);
            copyEdges(source, target);
            target.commit();
        } catch (Exception e) {
            throw new IOException("Merge failed: " + e.getMessage(), e);
        }
    }

    public void mergeGraphJson(Path targetCodebase, Path... jsonFiles) throws IOException {
        Path out = targetCodebase.resolve(".gbuilder").resolve("graph.json");
        var merged = objectMapper.createObjectNode();
        var nodes = merged.putArray("nodes");
        var edges = merged.putArray("edges");
        for (Path json : jsonFiles) {
            JsonNode root = objectMapper.readTree(json.toFile());
            root.path("nodes").forEach(nodes::add);
            root.path("edges").forEach(edges::add);
        }
        Files.createDirectories(out.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), merged);
    }

    private void copyNodes(Connection source, Connection target, String repoLabel) throws Exception {
        try (Statement st = source.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, kind, fqn, data, community_id, status, updated_at FROM nodes")) {
            while (rs.next()) {
                String id = rs.getString("id");
                String prefixedId = prefixId(id, repoLabel);
                String data = rs.getString("data");
                if (data != null && data.contains("{")) {
                    var node = objectMapper.readTree(data);
                    if (node.isObject()) {
                        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("repo", repoLabel);
                        data = objectMapper.writeValueAsString(node);
                    }
                }
                try (PreparedStatement ps = target.prepareStatement("""
                        INSERT OR IGNORE INTO nodes (id, kind, fqn, data, community_id, status, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    ps.setString(1, prefixedId);
                    ps.setString(2, rs.getString("kind"));
                    ps.setString(3, rs.getString("fqn"));
                    ps.setString(4, data);
                    ps.setObject(5, rs.getObject("community_id"));
                    ps.setString(6, rs.getString("status"));
                    ps.setLong(7, rs.getLong("updated_at"));
                    ps.executeUpdate();
                }
            }
        }
    }

    private void copyEdges(Connection source, Connection target) throws Exception {
        try (Statement st = source.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT type, from_id, to_id, weight, provenance, confidence_score FROM edges")) {
            while (rs.next()) {
                try (PreparedStatement ps = target.prepareStatement("""
                        INSERT OR IGNORE INTO edges (type, from_id, to_id, weight, provenance, confidence_score)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                    ps.setString(1, rs.getString("type"));
                    ps.setString(2, rs.getString("from_id"));
                    ps.setString(3, rs.getString("to_id"));
                    ps.setDouble(4, rs.getDouble("weight"));
                    ps.setString(5, rs.getString("provenance"));
                    ps.setDouble(6, rs.getDouble("confidence_score"));
                    ps.executeUpdate();
                }
            }
        }
    }

    private String prefixId(String id, String repo) {
        return repo + ":" + id;
    }
}
