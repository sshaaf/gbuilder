package dev.shaaf.gbuilder.graph;

import dev.shaaf.gbuilder.graph.query.GraphQueryService;
import dev.shaaf.gbuilder.graph.semantic.SemanticEdgeService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class EmbeddingGraphEnhancementTest {

    @Inject GraphRepository graphRepo;
    @Inject GraphStoreLocation storeLocation;
    @Inject SemanticEdgeService semanticEdgeService;
    @Inject GraphQueryService queryService;
    @Inject EmbeddingService embeddingService;

    @BeforeEach
    void clean() {
        storeLocation.reset();
        graphRepo.clearAll();
    }

    @Test
    void semanticEdgesUseStoredEmbeddingSimilarity() {
        persistPair("sim.Alpha", "sim.Beta", "sim.Gamma");

        float[] alpha = {1f, 0f, 0f};
        float[] beta = {0.99f, 0.1f, 0f};
        float[] gamma = {0f, 1f, 0f};
        graphRepo.storeClassEmbedding("sim.Alpha", alpha);
        graphRepo.storeClassEmbedding("sim.Beta", beta);
        graphRepo.storeClassEmbedding("sim.Gamma", gamma);

        int added = semanticEdgeService.addSemanticSimilarityEdges(graphRepo.findAllClassNodes());

        assertTrue(added >= 1);
        boolean alphaBeta = graphRepo.listClassLevelEdges().stream()
                .anyMatch(e -> "SEMANTICALLY_SIMILAR".equals(e.type())
                        && e.provenance() == EdgeProvenance.INFERRED
                        && connects(e, "sim.Alpha", "sim.Beta")
                        && e.confidenceScore() >= 0.80);
        assertTrue(alphaBeta);

        boolean alphaGamma = graphRepo.listClassLevelEdges().stream()
                .anyMatch(e -> "SEMANTICALLY_SIMILAR".equals(e.type())
                        && connects(e, "sim.Alpha", "sim.Gamma"));
        assertFalse(alphaGamma);
    }

    @Test
    void queryResolvesSeedsFromStoredEmbeddingsWhenNameMisses() throws Exception {
        persistPair("search.TargetService", "search.Other", "search.Unrelated");

        float[] target = {1f, 0f, 0f};
        float[] other = {0f, 1f, 0f};
        graphRepo.storeClassEmbedding("search.TargetService", target);
        graphRepo.storeMethodEmbedding("run()", "search.TargetService", target);
        graphRepo.storeClassEmbedding("search.Other", other);

        float[] query = {0.98f, 0.05f, 0f};
        List<String> seeds = queryService.resolveSeedsFromVector(query, 2);

        assertFalse(seeds.isEmpty());
        assertEquals("search.TargetService", seeds.getFirst());
        assertFalse(seeds.contains("search.Unrelated"));

        var result = queryService.query("billing invoice processor", false, 2000);
        assertTrue(result.answerText().contains("No matching types")
                || !result.visitedFqns().isEmpty());
    }

    @Test
    void rankAgainstStoredEmbeddingsPicksBestClassPerFqn() {
        float[] classVec = {1f, 0f, 0f};
        float[] methodVec = {0.95f, 0.05f, 0f};
        graphRepo.storeClassEmbedding("pkg.Service", classVec);
        graphRepo.storeMethodEmbedding("handle()", "pkg.Service", methodVec);

        float[] query = {0.99f, 0.01f, 0f};
        var hits = embeddingService.rankAgainstStoredEmbeddings(
                query, graphRepo.listStoredEmbeddings(), 3, 0.5);

        assertEquals(1, hits.size());
        assertEquals("pkg.Service", hits.getFirst().classFqn());
        assertTrue(hits.getFirst().score() > 0.9);
    }

    @Test
    void listStoredEmbeddingsRoundTripsVectors() {
        graphRepo.storeClassEmbedding("rt.Type", new float[]{1f, 2f, 3f});
        var stored = graphRepo.listStoredEmbeddings();
        assertEquals(1, stored.size());
        assertEquals("rt.Type", stored.getFirst().classFqn());
        assertEquals(GraphRepository.EmbeddingKind.CLASS, stored.getFirst().kind());
        assertArrayEquals(new float[]{1f, 2f, 3f}, stored.getFirst().vector(), 0.0001f);
    }

    private void persistPair(String... fqns) {
        for (String fqn : fqns) {
            String simple = fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
            graphRepo.persistClassNode(GraphTestFixtures.classNode(fqn, simple, List.of(), List.of()));
        }
    }

    private boolean connects(GraphRepository.GraphEdgeRecord edge, String a, String b) {
        String from = edge.fromId().substring("class:".length());
        String to = edge.toId().substring("class:".length());
        return (from.equals(a) && to.equals(b)) || (from.equals(b) && to.equals(a));
    }
}
