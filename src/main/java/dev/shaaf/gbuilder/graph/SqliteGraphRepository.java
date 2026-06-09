package dev.shaaf.gbuilder.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.shaaf.gbuilder.analyzer.model.TechnologyNode;
import dev.shaaf.gbuilder.graph.model.ClassKind;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import dev.shaaf.gbuilder.graph.model.EnumConstantNode;
import dev.shaaf.gbuilder.graph.model.MethodNode;
import dev.shaaf.gbuilder.graph.model.MigrationStatus;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

@ApplicationScoped
public class SqliteGraphRepository implements GraphRepository {

    private static final Logger LOG = Logger.getLogger(SqliteGraphRepository.class);

    @Inject
    GraphStoreLocation storeLocation;

    @Inject
    ObjectMapper objectMapper;

    private final Object initLock = new Object();

    @PostConstruct
    void init() {
        initializeSchema(storeLocation.resolveDefault());
    }

    @Override
    public void setStoreRoot(Path codebaseRoot) {
        Path dbPath = storeLocation.resolveForCodebase(codebaseRoot);
        initializeSchema(dbPath);
    }

    @Override
    public void persistClassNode(ClassNode node) {
        withWriteConnection(conn -> {
            upsertClassNode(conn, node);
            for (MethodNode method : node.methods()) {
                upsertMethodNode(conn, node.fullyQualifiedName(), method);
                insertEdge(conn, "CONTAINS",
                        GraphNodeIds.classId(node.fullyQualifiedName()),
                        GraphNodeIds.methodId(node.fullyQualifiedName(), method.signature()),
                        1.0);
            }
            return null;
        });
    }

    @Override
    public void createExtendsEdge(String childFqn, String parentFqn) {
        withWriteConnection(conn -> {
            ensureExternalClass(conn, parentFqn, ClassKind.CLASS);
            insertEdge(conn, "EXTENDS", GraphNodeIds.classId(childFqn), GraphNodeIds.classId(parentFqn), 1.0);
            return null;
        });
    }

    @Override
    public void createImplementsEdge(String classFqn, String interfaceFqn) {
        withWriteConnection(conn -> {
            ensureExternalClass(conn, interfaceFqn, ClassKind.INTERFACE);
            insertEdge(conn, "IMPLEMENTS", GraphNodeIds.classId(classFqn), GraphNodeIds.classId(interfaceFqn), 1.0);
            return null;
        });
    }

    @Override
    public void createCallEdge(String callerSignature, String callerClassName, String calleeName) {
        withWriteConnection(conn -> {
            String callerId = GraphNodeIds.methodId(callerClassName, callerSignature);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT id FROM nodes
                    WHERE kind = 'METHOD' AND fqn = ? AND json_extract(data, '$.name') = ?
                    """)) {
                ps.setString(1, callerClassName);
                ps.setString(2, calleeName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        insertEdge(conn, "CALLS", callerId, rs.getString("id"), 2.0);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public void createDependsOnEdge(String fromFqn, String toFqn) {
        withWriteConnection(conn -> {
            insertEdge(conn, "DEPENDS_ON",
                    GraphNodeIds.classId(fromFqn), GraphNodeIds.classId(toFqn), 3.0);
            return null;
        });
    }

    @Override
    public void updateNodeStatus(String fqn, MigrationStatus status) {
        withWriteConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE nodes SET status = ?, updated_at = ? WHERE id = ?")) {
                ps.setString(1, status.name());
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, GraphNodeIds.classId(fqn));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public long countClassNodes() {
        return countNodes("CLASS", false);
    }

    @Override
    public long countExternalNodes() {
        return withReadConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM nodes WHERE kind = 'CLASS' AND json_extract(data, '$.external') = true")) {
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    @Override
    public long countMethodNodes() {
        return countNodes("METHOD", null);
    }

    @Override
    public long countRelationships(String type) {
        return withReadConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM edges WHERE type = ?")) {
                ps.setString(1, type);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    @Override
    public String getClusterJson(String clusterId) {
        int communityId;
        try {
            communityId = Integer.parseInt(clusterId);
        } catch (NumberFormatException e) {
            return "[]";
        }

        return withReadConnection(conn -> {
            List<Map<String, Object>> classes = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT id, fqn, data FROM nodes
                    WHERE kind = 'CLASS' AND community_id = ?
                    ORDER BY fqn
                    """)) {
                ps.setInt(1, communityId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("fqn", rs.getString("fqn"));
                        ClassNode classNode = readClassNode(rs.getString("data"));
                        if (classNode != null) {
                            entry.put("simpleName", classNode.simpleName());
                            entry.put("kind", classNode.kind().name());
                            entry.put("status", classNode.status().name());
                            entry.put("methods", classNode.methods().stream()
                                    .map(MethodNode::signature)
                                    .toList());
                        }
                        classes.add(entry);
                    }
                }
            }
            try {
                return objectMapper.writeValueAsString(classes);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize cluster JSON", e);
            }
        });
    }

    @Override
    public void persistTechnologyNode(TechnologyNode node) {
        withWriteConnection(conn -> {
            upsertTechnologyNode(conn, node);
            return null;
        });
    }

    @Override
    public void createUsesEdge(String classFqn, String techId) {
        withWriteConnection(conn -> {
            insertEdge(conn, "USES",
                    GraphNodeIds.classId(classFqn), GraphNodeIds.technologyId(techId), 1.0);
            return null;
        });
    }

    @Override
    public void storeClassEmbedding(String fqn, float[] vector) {
        if (vector == null || vector.length == 0) {
            return;
        }
        withWriteConnection(conn -> {
            upsertEmbedding(conn, GraphNodeIds.classId(fqn), vector, "class");
            return null;
        });
    }

    @Override
    public void storeMethodEmbedding(String signature, String className, float[] vector) {
        if (vector == null || vector.length == 0) {
            return;
        }
        withWriteConnection(conn -> {
            upsertEmbedding(conn, GraphNodeIds.methodId(className, signature), vector, "method");
            return null;
        });
    }

    @Override
    public Map<String, Object> findMethodContext(String className, String methodSignature) {
        return withReadConnection(conn -> {
            String methodId = GraphNodeIds.methodId(className, methodSignature);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT data FROM nodes WHERE id = ? AND kind = 'METHOD'")) {
                ps.setString(1, methodId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return findMethodContextByName(conn, className, methodSignature);
                    }
                    MethodNode method = readMethodNode(rs.getString("data"));
                    if (method == null) {
                        return Map.of();
                    }
                    return buildMethodContext(conn, className, method);
                }
            }
        });
    }

    @Override
    public List<Map<String, String>> findInternalCallSignatures(String className, List<String> callNames) {
        if (callNames == null || callNames.isEmpty()) {
            return List.of();
        }
        return withReadConnection(conn -> {
            List<Map<String, String>> results = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT json_extract(data, '$.name') AS name,
                           json_extract(data, '$.signature') AS signature,
                           json_extract(data, '$.returnType') AS returnType
                    FROM nodes
                    WHERE kind = 'METHOD' AND fqn = ?
                      AND json_extract(data, '$.name') = ?
                    """)) {
                for (String callName : callNames) {
                    ps.setString(1, className);
                    ps.setString(2, callName);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            results.add(Map.of(
                                    "method_name", rs.getString("name"),
                                    "signature", rs.getString("signature"),
                                    "return_type", rs.getString("returnType")
                            ));
                        }
                    }
                }
            }
            return results;
        });
    }

    @Override
    public void updateMethodSignature(String className, String oldSignature, String newSignature) {
        withWriteConnection(conn -> {
            String oldId = GraphNodeIds.methodId(className, oldSignature);
            String newId = GraphNodeIds.methodId(className, newSignature);

            try (PreparedStatement ps = conn.prepareStatement("SELECT data FROM nodes WHERE id = ?")) {
                ps.setString(1, oldId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    MethodNode method = readMethodNode(rs.getString("data"));
                    if (method == null) {
                        return null;
                    }
                    MethodNode updated = new MethodNode(
                            method.name(), newSignature, method.returnType(), method.modifiers(),
                            method.annotationNodes(), method.annotations(), method.typeParameters(),
                            method.parameters(), method.thrownExceptions(), method.constructor(),
                            method.rawBody(), method.internalMethodCalls(), method.methodReferences(),
                            method.semanticSummary(), method.embeddingVector()
                    );
                    upsertMethodNode(conn, className, updated);
                    insertEdge(conn, "CONTAINS",
                            GraphNodeIds.classId(className), newId, 1.0);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM nodes WHERE id = ?")) {
                ps.setString(1, oldId);
                ps.executeUpdate();
            }

            rewireEdges(conn, oldId, newId);
            return null;
        });
    }

    @Override
    public void deleteNodeAndEdges(String fqn) {
        withWriteConnection(conn -> {
            String classId = GraphNodeIds.classId(fqn);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM nodes WHERE kind = 'METHOD' AND fqn = ?")) {
                ps.setString(1, fqn);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        deleteNode(conn, rs.getString("id"));
                    }
                }
            }
            deleteNode(conn, classId);
            return null;
        });
    }

    @Override
    public Map<String, Set<String>> findAllClassTechnologyCategories() {
        return withReadConnection(conn -> {
            Map<String, Set<String>> map = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT c.fqn AS fqn, json_extract(t.data, '$.category') AS category
                    FROM edges e
                    JOIN nodes c ON c.id = e.from_id
                    JOIN nodes t ON t.id = e.to_id
                    WHERE e.type = 'USES'
                      AND c.kind = 'CLASS'
                      AND (json_extract(c.data, '$.external') IS NULL
                           OR json_extract(c.data, '$.external') = false)
                    """)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        map.computeIfAbsent(rs.getString("fqn"), k -> new HashSet<>())
                                .add(rs.getString("category"));
                    }
                }
            }
            return map;
        });
    }

    @Override
    public long countTechnologyNodes() {
        return countNodes("TECHNOLOGY", null);
    }

    @Override
    public void clearAll() {
        withWriteConnection(conn -> {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM embeddings");
                st.executeUpdate("DELETE FROM summaries");
                st.executeUpdate("DELETE FROM edges");
                st.executeUpdate("DELETE FROM nodes");
            }
            return null;
        });
    }

    @Override
    public GraphStats getGraphStats() {
        return new GraphStats(
                countClassNodes(),
                countExternalNodes(),
                countMethodNodes(),
                countRelationships("CONTAINS"),
                countRelationships("CALLS"),
                countRelationships("DEPENDS_ON"),
                countRelationships("EXTENDS"),
                countRelationships("IMPLEMENTS"),
                countTechnologyNodes(),
                countRelationships("USES"),
                countCommunities()
        );
    }

    @Override
    public void assignCommunities(Map<String, Integer> classFqnToCommunity) {
        withWriteConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE nodes SET community_id = ?, updated_at = ? WHERE id = ?")) {
                for (var entry : classFqnToCommunity.entrySet()) {
                    ps.setInt(1, entry.getValue());
                    ps.setLong(2, System.currentTimeMillis());
                    ps.setString(3, GraphNodeIds.classId(entry.getKey()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            regenerateCommunitySummaries(conn, classFqnToCommunity);
            return null;
        });
    }

    @Override
    public Optional<Integer> getCommunityId(String classFqn) {
        return withReadConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT community_id FROM nodes WHERE id = ?")) {
                ps.setString(1, GraphNodeIds.classId(classFqn));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getObject("community_id") == null) {
                        return Optional.empty();
                    }
                    return Optional.of(rs.getInt("community_id"));
                }
            }
        });
    }

    @Override
    public List<String> getCommunityClassFqns(int communityId) {
        return withReadConnection(conn -> {
            List<String> fqns = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT fqn FROM nodes
                    WHERE kind = 'CLASS' AND community_id = ?
                    ORDER BY fqn
                    """)) {
                ps.setInt(1, communityId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        fqns.add(rs.getString("fqn"));
                    }
                }
            }
            return fqns;
        });
    }

    @Override
    public List<String> getNeighbors(String classFqn, int depth, Set<String> edgeTypes) {
        if (depth <= 0) {
            return List.of();
        }
        return withReadConnection(conn -> {
            Set<String> visited = new HashSet<>();
            Queue<String> queue = new ArrayDeque<>();
            queue.add(classFqn);
            visited.add(classFqn);

            for (int level = 0; level < depth && !queue.isEmpty(); level++) {
                int size = queue.size();
                for (int i = 0; i < size; i++) {
                    String current = queue.poll();
                    for (String neighbor : loadClassNeighbors(conn, current, edgeTypes)) {
                        if (visited.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
            visited.remove(classFqn);
            return new ArrayList<>(visited);
        });
    }

    @Override
    public Optional<String> getClassStatus(String fqn) {
        return withReadConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status FROM nodes WHERE id = ?")) {
                ps.setString(1, GraphNodeIds.classId(fqn));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.ofNullable(rs.getString("status"));
                }
            }
        });
    }

    @Override
    public boolean classExists(String fqn) {
        return withReadConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM nodes WHERE id = ?")) {
                ps.setString(1, GraphNodeIds.classId(fqn));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    @Override
    public boolean methodExists(String className, String signature) {
        return withReadConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM nodes WHERE id = ?")) {
                ps.setString(1, GraphNodeIds.methodId(className, signature));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    @Override
    public long countTechnologyById(String techId) {
        return withReadConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM nodes WHERE id = ?")) {
                ps.setString(1, GraphNodeIds.technologyId(techId));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    @Override
    public long countUsesEdgesForTechnology(String techId) {
        return withReadConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM edges WHERE type = 'USES' AND to_id = ?")) {
                ps.setString(1, GraphNodeIds.technologyId(techId));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    @Override
    public int countCommunities() {
        return withReadConnection(conn -> {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT COUNT(DISTINCT community_id) FROM nodes WHERE community_id IS NOT NULL")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }

    @Override
    public Optional<String> getCommunitySummary(int communityId) {
        return withReadConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT summary FROM summaries WHERE id = ?")) {
                ps.setString(1, "community:" + communityId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.ofNullable(rs.getString("summary"));
                }
            }
        });
    }

    @Override
    public Optional<ClassNode> findClassNode(String fqn) {
        return withReadConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT data FROM nodes WHERE id = ? AND kind = 'CLASS'")) {
                ps.setString(1, GraphNodeIds.classId(fqn));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.ofNullable(readClassNode(rs.getString("data")));
                }
            }
        });
    }

    private void initializeSchema(Path dbPath) {
        synchronized (initLock) {
            storeLocation.ensureParentDirectory(dbPath);
            try (Connection conn = openConnection(dbPath)) {
                try (Statement st = conn.createStatement()) {
                    st.execute("PRAGMA journal_mode=WAL");
                    st.execute("PRAGMA foreign_keys=ON");
                    st.execute("""
                            CREATE TABLE IF NOT EXISTS nodes (
                              id TEXT PRIMARY KEY,
                              kind TEXT NOT NULL,
                              fqn TEXT,
                              data TEXT NOT NULL,
                              community_id INTEGER,
                              status TEXT,
                              updated_at INTEGER
                            )
                            """);
                    st.execute("""
                            CREATE TABLE IF NOT EXISTS edges (
                              id INTEGER PRIMARY KEY AUTOINCREMENT,
                              type TEXT NOT NULL,
                              from_id TEXT NOT NULL,
                              to_id TEXT NOT NULL,
                              weight REAL DEFAULT 1.0,
                              UNIQUE(type, from_id, to_id)
                            )
                            """);
                    st.execute("""
                            CREATE TABLE IF NOT EXISTS summaries (
                              id TEXT PRIMARY KEY,
                              level TEXT NOT NULL,
                              summary TEXT NOT NULL,
                              token_est INTEGER
                            )
                            """);
                    st.execute("""
                            CREATE TABLE IF NOT EXISTS embeddings (
                              node_id TEXT PRIMARY KEY,
                              vector BLOB NOT NULL,
                              model TEXT
                            )
                            """);
                    st.execute("CREATE INDEX IF NOT EXISTS idx_edges_from ON edges(from_id, type)");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_edges_to ON edges(to_id, type)");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_nodes_community ON nodes(community_id)");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_nodes_fqn ON nodes(fqn)");
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to initialize graph store at " + dbPath, e);
            }
        }
    }

    private Connection openConnection(Path dbPath) throws SQLException {
        storeLocation.ensureParentDirectory(dbPath);
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        return DriverManager.getConnection(jdbcUrl);
    }

    private <T> T withReadConnection(SqlFunction<T> action) {
        try (Connection conn = openConnection(storeLocation.current())) {
            return action.apply(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Graph read failed", e);
        }
    }

    private <T> T withWriteConnection(SqlFunction<T> action) {
        try (Connection conn = openConnection(storeLocation.current())) {
            conn.setAutoCommit(false);
            try {
                T result = action.apply(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Graph write failed", e);
        }
    }

    private long countNodes(String kind, Boolean externalOnly) {
        return withReadConnection(conn -> {
            String sql = "SELECT COUNT(*) FROM nodes WHERE kind = ?";
            if (externalOnly != null) {
                sql += externalOnly
                        ? " AND json_extract(data, '$.external') = true"
                        : " AND (json_extract(data, '$.external') IS NULL OR json_extract(data, '$.external') = false)";
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, kind);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    private void upsertClassNode(Connection conn, ClassNode node) throws SQLException {
        String json = writeJson(node);
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO nodes (id, kind, fqn, data, status, updated_at)
                VALUES (?, 'CLASS', ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                  kind = excluded.kind,
                  fqn = excluded.fqn,
                  data = excluded.data,
                  status = excluded.status,
                  updated_at = excluded.updated_at
                """)) {
            ps.setString(1, GraphNodeIds.classId(node.fullyQualifiedName()));
            ps.setString(2, node.fullyQualifiedName());
            ps.setString(3, json);
            ps.setString(4, node.status().name());
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private void upsertMethodNode(Connection conn, String classFqn, MethodNode method) throws SQLException {
        String json = writeJson(method);
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO nodes (id, kind, fqn, data, updated_at)
                VALUES (?, 'METHOD', ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                  kind = excluded.kind,
                  fqn = excluded.fqn,
                  data = excluded.data,
                  updated_at = excluded.updated_at
                """)) {
            ps.setString(1, GraphNodeIds.methodId(classFqn, method.signature()));
            ps.setString(2, classFqn);
            ps.setString(3, json);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private void upsertTechnologyNode(Connection conn, TechnologyNode node) throws SQLException {
        Map<String, Object> payload = Map.of(
                "id", node.id(),
                "name", node.name(),
                "category", node.category(),
                "version", node.version()
        );
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO nodes (id, kind, fqn, data, updated_at)
                VALUES (?, 'TECHNOLOGY', ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                  data = excluded.data,
                  updated_at = excluded.updated_at
                """)) {
            ps.setString(1, GraphNodeIds.technologyId(node.id()));
            ps.setString(2, node.id());
            ps.setString(3, writeJson(payload));
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private void ensureExternalClass(Connection conn, String fqn, ClassKind kind) throws SQLException {
        String simpleName = fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fullyQualifiedName", fqn);
        payload.put("simpleName", simpleName);
        payload.put("external", true);
        payload.put("kind", kind.name());
        payload.put("status", MigrationStatus.EXTERNAL.name());

        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO nodes (id, kind, fqn, data, status, updated_at)
                VALUES (?, 'CLASS', ?, ?, ?, ?)
                ON CONFLICT(id) DO NOTHING
                """)) {
            ps.setString(1, GraphNodeIds.classId(fqn));
            ps.setString(2, fqn);
            ps.setString(3, writeJson(payload));
            ps.setString(4, MigrationStatus.EXTERNAL.name());
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private void insertEdge(Connection conn, String type, String fromId, String toId, double weight)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR IGNORE INTO edges (type, from_id, to_id, weight)
                VALUES (?, ?, ?, ?)
                """)) {
            ps.setString(1, type);
            ps.setString(2, fromId);
            ps.setString(3, toId);
            ps.setDouble(4, weight);
            ps.executeUpdate();
        }
    }

    private void deleteNode(Connection conn, String nodeId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM edges WHERE from_id = ? OR to_id = ?")) {
            ps.setString(1, nodeId);
            ps.setString(2, nodeId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM embeddings WHERE node_id = ?")) {
            ps.setString(1, nodeId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM nodes WHERE id = ?")) {
            ps.setString(1, nodeId);
            ps.executeUpdate();
        }
    }

    private void rewireEdges(Connection conn, String oldId, String newId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, type, from_id, to_id, weight FROM edges WHERE from_id = ? OR to_id = ?")) {
            ps.setString(1, oldId);
            ps.setString(2, oldId);
            List<EdgeRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new EdgeRow(
                            rs.getLong("id"),
                            rs.getString("type"),
                            rs.getString("from_id"),
                            rs.getString("to_id"),
                            rs.getDouble("weight")
                    ));
                }
            }
            for (EdgeRow row : rows) {
                try (PreparedStatement delete = conn.prepareStatement("DELETE FROM edges WHERE id = ?")) {
                    delete.setLong(1, row.id());
                    delete.executeUpdate();
                }
                String from = row.fromId().equals(oldId) ? newId : row.fromId();
                String to = row.toId().equals(oldId) ? newId : row.toId();
                insertEdge(conn, row.type(), from, to, row.weight());
            }
        }
    }

    private Map<String, Object> findMethodContextByName(Connection conn, String className, String methodName)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT data FROM nodes
                WHERE kind = 'METHOD' AND fqn = ? AND json_extract(data, '$.name') = ?
                LIMIT 1
                """)) {
            ps.setString(1, className);
            ps.setString(2, methodName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Map.of();
                }
                MethodNode method = readMethodNode(rs.getString("data"));
                if (method == null) {
                    return Map.of();
                }
                return buildMethodContext(conn, className, method);
            }
        }
    }

    private Map<String, Object> buildMethodContext(Connection conn, String className, MethodNode method)
            throws SQLException {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("name", method.name());
        ctx.put("signature", method.signature());
        ctx.put("returnType", method.returnType());
        ctx.put("annotations", method.annotations());
        ctx.put("rawBody", method.rawBody() != null ? method.rawBody() : "");
        ctx.put("parameters", method.parameters().stream()
                .map(p -> p.type() + " " + p.name())
                .toList());
        ctx.put("internalCalls", method.internalMethodCalls());
        ctx.put("methodRefs", method.methodReferences());

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT data FROM nodes WHERE id = ? AND kind = 'CLASS'")) {
            ps.setString(1, GraphNodeIds.classId(className));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ClassNode classNode = readClassNode(rs.getString("data"));
                    if (classNode != null) {
                        ctx.put("filePath", classNode.filePath());
                        ctx.put("packageName", classNode.packageName());
                        ctx.put("simpleName", classNode.simpleName());
                        ctx.put("classAnnotations", classNode.annotations());
                        ctx.put("kind", classNode.kind().name());
                    }
                }
            }
        }
        return ctx;
    }

    private List<String> loadClassNeighbors(Connection conn, String classFqn, Set<String> edgeTypes)
            throws SQLException {
        Set<String> neighbors = new HashSet<>();
        String classId = GraphNodeIds.classId(classFqn);

        try (PreparedStatement out = conn.prepareStatement("""
                SELECT to_id, type FROM edges WHERE from_id = ?
                """)) {
            out.setString(1, classId);
            try (ResultSet rs = out.executeQuery()) {
                while (rs.next()) {
                    if (edgeTypes.contains(rs.getString("type"))) {
                        neighbors.add(extractClassFqn(rs.getString("to_id")));
                    }
                }
            }
        }

        try (PreparedStatement in = conn.prepareStatement("""
                SELECT from_id, type FROM edges WHERE to_id = ?
                """)) {
            in.setString(1, classId);
            try (ResultSet rs = in.executeQuery()) {
                while (rs.next()) {
                    if (edgeTypes.contains(rs.getString("type"))) {
                        neighbors.add(extractClassFqn(rs.getString("from_id")));
                    }
                }
            }
        }

        neighbors.remove(classFqn);
        return new ArrayList<>(neighbors);
    }

    private String extractClassFqn(String nodeId) {
        if (nodeId.startsWith("class:")) {
            return GraphNodeIds.fqnFromClassId(nodeId);
        }
        if (nodeId.startsWith("method:")) {
            return GraphNodeIds.classFqnFromMethodId(nodeId);
        }
        return nodeId;
    }

    private void regenerateCommunitySummaries(Connection conn, Map<String, Integer> classFqnToCommunity)
            throws SQLException {
        Map<Integer, List<String>> grouped = new HashMap<>();
        for (var entry : classFqnToCommunity.entrySet()) {
            grouped.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        try (PreparedStatement delete = conn.prepareStatement("DELETE FROM summaries WHERE level = 'COMMUNITY'")) {
            delete.executeUpdate();
        }

        try (PreparedStatement insert = conn.prepareStatement("""
                INSERT INTO summaries (id, level, summary, token_est)
                VALUES (?, 'COMMUNITY', ?, ?)
                """)) {
            for (var entry : grouped.entrySet()) {
                int communityId = entry.getKey();
                List<String> fqns = entry.getValue();
                String summary = "Community " + communityId + ": " + fqns.size()
                        + " classes — " + String.join(", ", fqns.stream().limit(5).toList());
                insert.setString(1, "community:" + communityId);
                insert.setString(2, summary);
                insert.setInt(3, estimateTokens(summary));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void upsertEmbedding(Connection conn, String nodeId, float[] vector, String model) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO embeddings (node_id, vector, model)
                VALUES (?, ?, ?)
                ON CONFLICT(node_id) DO UPDATE SET vector = excluded.vector, model = excluded.model
                """)) {
            ps.setString(1, nodeId);
            ps.setBytes(2, toBytes(vector));
            ps.setString(3, model);
            ps.executeUpdate();
        }
    }

    private byte[] toBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    private ClassNode readClassNode(String json) {
        try {
            return objectMapper.readValue(json, ClassNode.class);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to deserialize ClassNode");
            return null;
        }
    }

    private MethodNode readMethodNode(String json) {
        try {
            return objectMapper.readValue(json, MethodNode.class);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to deserialize MethodNode");
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize graph node", e);
        }
    }

    @FunctionalInterface
    private interface SqlFunction<T> {
        T apply(Connection conn) throws SQLException;
    }

    private record EdgeRow(long id, String type, String fromId, String toId, double weight) {}
}
