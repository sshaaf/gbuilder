package dev.shaaf.gbuilder.graph;

import dev.shaaf.gbuilder.analyzer.model.TechnologyNode;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import dev.shaaf.gbuilder.graph.model.MigrationStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface GraphRepository {

    void setStoreRoot(Path codebaseRoot);

    void persistClassNode(ClassNode node);

    void persistClassNodesChunk(List<ClassNode> nodes);

    void createCallEdgesChunk(List<PendingCallEdge> edges);

    void createStructuralEdgesChunk(List<PendingStructuralEdge> edges);

    void storeEmbeddingsChunk(List<PendingEmbedding> embeddings);

    record PendingCallEdge(String callerSignature, String callerClassName, String calleeName) {}

    record PendingStructuralEdge(String type, String fromFqn, String toFqn, double weight) {}

    record PendingEmbedding(String classFqn, String methodSignature, float[] vector, EmbeddingKind kind) {}

    void createExtendsEdge(String childFqn, String parentFqn);

    void createImplementsEdge(String classFqn, String interfaceFqn);

    void createCallEdge(String callerSignature, String callerClassName, String calleeName);

    void createDependsOnEdge(String fromFqn, String toFqn);

    void updateNodeStatus(String fqn, MigrationStatus status);

    long countClassNodes();

    long countExternalNodes();

    long countMethodNodes();

    long countRelationships(String type);

    String getClusterJson(String clusterId);

    void persistTechnologyNode(TechnologyNode node);

    void createUsesEdge(String classFqn, String techId);

    void storeClassEmbedding(String fqn, float[] vector);

    void storeMethodEmbedding(String signature, String className, float[] vector);

    List<StoredEmbedding> listStoredEmbeddings();

    record StoredEmbedding(String nodeId, String classFqn, EmbeddingKind kind, float[] vector) {}

    enum EmbeddingKind { CLASS, METHOD }

    Map<String, Object> findMethodContext(String className, String methodSignature);

    List<Map<String, String>> findInternalCallSignatures(String className, List<String> callNames);

    void updateMethodSignature(String className, String oldSignature, String newSignature);

    void deleteNodeAndEdges(String fqn);

    Map<String, Set<String>> findAllClassTechnologyCategories();

    long countTechnologyNodes();

    void clearAll();

    GraphStats getGraphStats();

    void assignCommunities(Map<String, Integer> classFqnToCommunity);

    Optional<Integer> getCommunityId(String classFqn);

    List<String> getCommunityClassFqns(int communityId);

    List<String> getNeighbors(String classFqn, int depth, Set<String> edgeTypes);

    Optional<String> getClassStatus(String fqn);

    boolean classExists(String fqn);

    boolean methodExists(String className, String signature);

    long countTechnologyById(String techId);

    long countUsesEdgesForTechnology(String techId);

    int countCommunities();

    Optional<String> getCommunitySummary(int communityId);

    Optional<ClassNode> findClassNode(String fqn);

    List<ClassNode> findAllClassNodes();

    List<String> listInternalClassFqns();

    void removeNodesByFilePaths(List<String> absoluteFilePaths);

    void removeClassAndRelatedNodes(String fqn);

    void setCommunityLabel(int communityId, String label, double cohesion);

    Optional<String> getCommunityLabel(int communityId);

    Optional<Double> getCommunityCohesion(int communityId);

    List<GraphEdgeRecord> listClassLevelEdges();

    List<GraphEdgeRecord> listAllEdges();

    void insertEdge(String type, String fromId, String toId, double weight,
                    EdgeProvenance provenance, double confidenceScore);

    void persistQaResult(String question, String answer, List<String> nodeFqns);

    List<QaResultRecord> listQaResults();

    void persistHyperedge(String id, String label, String relation,
                          List<String> nodeIds, EdgeProvenance provenance, double confidence);

    List<HyperedgeRecord> listHyperedges();

    record GraphEdgeRecord(String type, String fromId, String toId, double weight,
                           EdgeProvenance provenance, double confidenceScore) {}

    record QaResultRecord(long id, String question, String answer, List<String> nodeFqns) {}

    record HyperedgeRecord(String id, String label, String relation, List<String> nodeIds,
                           EdgeProvenance provenance, double confidenceScore) {}
}
