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
}
