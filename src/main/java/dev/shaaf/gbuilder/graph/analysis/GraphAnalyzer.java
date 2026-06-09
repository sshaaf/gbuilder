package dev.shaaf.gbuilder.graph.analysis;

import dev.shaaf.gbuilder.graph.GraphRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class GraphAnalyzer {

    private static final Set<String> CLASS_EDGE_TYPES = Set.of(
            "DEPENDS_ON", "CALLS", "EXTENDS", "IMPLEMENTS", "USES", "SEMANTICALLY_SIMILAR");

    @Inject
    GraphRepository graphRepository;

    public GraphAnalysisResult analyze() {
        List<String> fqns = graphRepository.listInternalClassFqns();
        Map<String, Integer> degrees = computeDegrees(fqns);
        List<GraphAnalysisResult.GodNode> godNodes = degrees.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(e -> new GraphAnalysisResult.GodNode(
                        e.getKey(), simpleName(e.getKey()), e.getValue()))
                .toList();

        Map<String, Integer> communities = loadCommunities(fqns);
        List<GraphAnalysisResult.SurprisingConnection> surprises = findSurprisingConnections(communities);
        List<String> questions = suggestQuestions(godNodes, surprises, communities);
        List<GraphAnalysisResult.CommunityInsight> communityInsights = buildCommunityInsights(communities);

        persistCommunityInsights(communityInsights);

        return new GraphAnalysisResult(godNodes, surprises, questions, communityInsights);
    }

    private Map<String, Integer> computeDegrees(List<String> fqns) {
        Map<String, Set<String>> neighbors = new HashMap<>();
        for (String fqn : fqns) {
            neighbors.put(fqn, new HashSet<>());
        }
        for (GraphRepository.GraphEdgeRecord edge : graphRepository.listClassLevelEdges()) {
            String from = classFqn(edge.fromId());
            String to = classFqn(edge.toId());
            if (from != null && to != null && neighbors.containsKey(from) && neighbors.containsKey(to)) {
                neighbors.get(from).add(to);
                neighbors.get(to).add(from);
            }
        }
        Map<String, Integer> degrees = new HashMap<>();
        for (var entry : neighbors.entrySet()) {
            degrees.put(entry.getKey(), entry.getValue().size());
        }
        return degrees;
    }

    private List<GraphAnalysisResult.SurprisingConnection> findSurprisingConnections(
            Map<String, Integer> communities) {
        List<GraphAnalysisResult.SurprisingConnection> results = new ArrayList<>();
        for (GraphRepository.GraphEdgeRecord edge : graphRepository.listClassLevelEdges()) {
            String from = classFqn(edge.fromId());
            String to = classFqn(edge.toId());
            if (from == null || to == null) {
                continue;
            }
            Integer fromCommunity = communities.get(from);
            Integer toCommunity = communities.get(to);
            double score = 0.0;
            if (fromCommunity != null && toCommunity != null && !fromCommunity.equals(toCommunity)) {
                score += 0.5;
            }
            if ("DEPENDS_ON".equals(edge.type()) || "IMPLEMENTS".equals(edge.type())) {
                score += 0.2;
            }
            if (edge.provenance() == dev.shaaf.gbuilder.graph.EdgeProvenance.INFERRED) {
                score += 0.15;
            }
            if (score >= 0.5) {
                results.add(new GraphAnalysisResult.SurprisingConnection(
                        from, to, edge.type(), score));
            }
        }
        return results.stream()
                .sorted(Comparator.comparingDouble(GraphAnalysisResult.SurprisingConnection::score).reversed())
                .limit(10)
                .toList();
    }

    private List<String> suggestQuestions(
            List<GraphAnalysisResult.GodNode> godNodes,
            List<GraphAnalysisResult.SurprisingConnection> surprises,
            Map<String, Integer> communities) {
        List<String> questions = new ArrayList<>();
        if (!godNodes.isEmpty()) {
            questions.add("What depends on " + godNodes.get(0).label() + " and why is it a hub?");
        }
        if (!surprises.isEmpty()) {
            var s = surprises.get(0);
            questions.add("Why does " + simpleName(s.fromFqn()) + " connect to "
                    + simpleName(s.toFqn()) + " across communities?");
        }
        long communityCount = communities.values().stream().distinct().count();
        if (communityCount > 1) {
            questions.add("Which types bridge the " + communityCount + " detected communities?");
        }
        questions.add("What are the strongest DEPENDS_ON chains in this codebase?");
        questions.add("Which interfaces are implemented by multiple unrelated classes?");
        return questions.stream().limit(5).toList();
    }

    private List<GraphAnalysisResult.CommunityInsight> buildCommunityInsights(
            Map<String, Integer> communities) {
        Map<Integer, List<String>> grouped = new HashMap<>();
        for (var entry : communities.entrySet()) {
            grouped.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        List<GraphAnalysisResult.CommunityInsight> insights = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            int id = entry.getKey();
            List<String> members = entry.getValue();
            double cohesion = computeCohesion(members);
            String label = labelCommunity(members);
            insights.add(new GraphAnalysisResult.CommunityInsight(id, label, cohesion, members));
        }
        insights.sort(Comparator.comparingInt(GraphAnalysisResult.CommunityInsight::id));
        return insights;
    }

    private double computeCohesion(List<String> members) {
        if (members.size() < 2) {
            return 1.0;
        }
        Set<String> memberSet = new HashSet<>(members);
        int internal = 0;
        int possible = members.size() * (members.size() - 1) / 2;
        for (GraphRepository.GraphEdgeRecord edge : graphRepository.listClassLevelEdges()) {
            String from = classFqn(edge.fromId());
            String to = classFqn(edge.toId());
            if (from != null && to != null && memberSet.contains(from) && memberSet.contains(to)) {
                internal++;
            }
        }
        return possible == 0 ? 1.0 : Math.min(1.0, (double) internal / possible);
    }

    private String labelCommunity(List<String> members) {
        Map<String, Long> packageCounts = new HashMap<>();
        for (String fqn : members) {
            String pkg = fqn.contains(".") ? fqn.substring(0, fqn.lastIndexOf('.')) : "default";
            String top = pkg.contains(".") ? pkg.substring(pkg.lastIndexOf('.') + 1) : pkg;
            packageCounts.merge(top, 1L, Long::sum);
        }
        String dominant = packageCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Mixed");
        return dominant + " (" + members.size() + " types)";
    }

    private void persistCommunityInsights(List<GraphAnalysisResult.CommunityInsight> insights) {
        for (var insight : insights) {
            graphRepository.setCommunityLabel(insight.id(), insight.label(), insight.cohesion());
        }
    }

    private Map<String, Integer> loadCommunities(List<String> fqns) {
        Map<String, Integer> map = new HashMap<>();
        for (String fqn : fqns) {
            graphRepository.getCommunityId(fqn).ifPresent(id -> map.put(fqn, id));
        }
        return map;
    }

    private String classFqn(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        if (nodeId.startsWith("class:")) {
            return nodeId.substring("class:".length());
        }
        return null;
    }

    private String simpleName(String fqn) {
        return fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
    }
}
