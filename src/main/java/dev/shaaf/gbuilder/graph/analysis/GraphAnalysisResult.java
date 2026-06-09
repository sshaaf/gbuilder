package dev.shaaf.gbuilder.graph.analysis;

import java.util.List;

public record GraphAnalysisResult(
        List<GodNode> godNodes,
        List<SurprisingConnection> surprisingConnections,
        List<String> suggestedQuestions,
        List<CommunityInsight> communities
) {
    public static GraphAnalysisResult empty() {
        return new GraphAnalysisResult(List.of(), List.of(), List.of(), List.of());
    }

    public record GodNode(String fqn, String label, int degree) {}

    public record SurprisingConnection(String fromFqn, String toFqn, String edgeType, double score) {}

    public record CommunityInsight(int id, String label, double cohesion, List<String> typeFqns) {}
}
