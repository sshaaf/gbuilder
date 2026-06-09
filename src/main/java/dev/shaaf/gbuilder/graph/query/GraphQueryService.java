package dev.shaaf.gbuilder.graph.query;

import dev.shaaf.gbuilder.graph.EmbeddingService;
import dev.shaaf.gbuilder.graph.GraphRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class GraphQueryService {

    private static final Set<String> DEFAULT_EDGE_TYPES = Set.of(
            "DEPENDS_ON", "CALLS", "EXTENDS", "IMPLEMENTS", "USES", "SEMANTICALLY_SIMILAR");

    @Inject
    GraphRepository graphRepository;

    @Inject
    EmbeddingService embeddingService;

    public GraphQueryResult query(String question, boolean dfs, int tokenBudget) {
        List<String> seeds = resolveSeeds(question);
        if (seeds.isEmpty()) {
            return new GraphQueryResult(question, List.of(), "No matching types found for query.");
        }

        LinkedHashSet<String> visited = new LinkedHashSet<>();
        List<GraphQueryResult.TraversalStep> steps = new ArrayList<>();

        if (dfs) {
            dfsVisit(seeds.get(0), visited, steps, 0, 4);
        } else {
            bfsVisit(seeds, visited, steps, 3);
        }

        StringBuilder answer = new StringBuilder();
        answer.append("Query: ").append(question).append("\n\n");
        for (var step : steps) {
            answer.append("- [depth ").append(step.depth()).append("] ")
                    .append(step.fqn());
            if (step.viaEdge() != null) {
                answer.append(" (via ").append(step.viaEdge()).append(" from ")
                        .append(step.fromFqn()).append(")");
            }
            answer.append("\n");
            for (String neighbor : graphRepository.getNeighbors(step.fqn(), 1, DEFAULT_EDGE_TYPES)) {
                answer.append("    → ").append(neighbor).append("\n");
            }
        }

        String text = truncateToTokenBudget(answer.toString(), tokenBudget);
        return new GraphQueryResult(question, new ArrayList<>(visited), text);
    }

    /**
     * Resolve seed types from a pre-computed query embedding (used when embeddings are stored
     * without requiring a live embedding API call).
     */
    public List<String> resolveSeedsFromVector(float[] queryVector, int limit) {
        if (queryVector == null || queryVector.length == 0) {
            return List.of();
        }
        return embeddingService.rankAgainstStoredEmbeddings(
                        queryVector,
                        graphRepository.listStoredEmbeddings(),
                        limit,
                        embeddingService.queryMinSimilarity())
                .stream()
                .map(EmbeddingService.SimilarityHit::classFqn)
                .toList();
    }

    private void bfsVisit(List<String> seeds, Set<String> visited,
                          List<GraphQueryResult.TraversalStep> steps, int maxDepth) {
        record QueueItem(String fqn, int depth, String from, String edge) {}
        Deque<QueueItem> queue = new ArrayDeque<>();
        for (String seed : seeds) {
            queue.add(new QueueItem(seed, 0, null, null));
        }
        while (!queue.isEmpty()) {
            QueueItem item = queue.removeFirst();
            if (!visited.add(item.fqn())) {
                continue;
            }
            steps.add(new GraphQueryResult.TraversalStep(
                    item.fqn(), item.depth(), item.from(), item.edge()));
            if (item.depth() >= maxDepth) {
                continue;
            }
            for (String edgeType : DEFAULT_EDGE_TYPES) {
                for (String neighbor : graphRepository.getNeighbors(item.fqn(), 1, Set.of(edgeType))) {
                    if (!visited.contains(neighbor)) {
                        queue.addLast(new QueueItem(neighbor, item.depth() + 1, item.fqn(), edgeType));
                    }
                }
            }
        }
    }

    private void dfsVisit(String start, Set<String> visited, List<GraphQueryResult.TraversalStep> steps,
                          int depth, int maxDepth) {
        if (!visited.add(start) || depth > maxDepth) {
            return;
        }
        steps.add(new GraphQueryResult.TraversalStep(start, depth, null, null));
        for (String edgeType : DEFAULT_EDGE_TYPES) {
            for (String neighbor : graphRepository.getNeighbors(start, 1, Set.of(edgeType))) {
                dfsVisit(neighbor, visited, steps, depth + 1, maxDepth);
            }
        }
    }

    private List<String> resolveSeeds(String question) {
        List<String> byName = resolveSeedsByName(question);
        if (!byName.isEmpty()) {
            return byName;
        }
        return resolveSeedsByEmbedding(question);
    }

    private List<String> resolveSeedsByName(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        return graphRepository.listInternalClassFqns().stream()
                .filter(fqn -> normalized.contains(fqn.toLowerCase(Locale.ROOT))
                        || normalized.contains(simpleName(fqn).toLowerCase(Locale.ROOT)))
                .limit(3)
                .toList();
    }

    private List<String> resolveSeedsByEmbedding(String question) {
        if (!embeddingService.isEnabled()) {
            return List.of();
        }
        float[] queryVector = embeddingService.embedSingle(question);
        if (queryVector.length == 0) {
            return List.of();
        }
        return resolveSeedsFromVector(queryVector, 3);
    }

    private String simpleName(String fqn) {
        return fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
    }

    private String truncateToTokenBudget(String text, int tokenBudget) {
        int maxChars = Math.max(200, tokenBudget * 4);
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n… (truncated to token budget)";
    }

    public record GraphQueryResult(String question, List<String> visitedFqns, String answerText) {
        public record TraversalStep(String fqn, int depth, String fromFqn, String viaEdge) {}
    }
}
