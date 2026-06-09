package dev.shaaf.gbuilder.graph;

import dev.shaaf.gbuilder.graph.EmbeddingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EmbeddingService — cosine similarity math only (no LLM needed).
 * Embedding/batch methods tested via integration when an API key is available.
 */
class EmbeddingServiceTest {

    private final EmbeddingService service = new EmbeddingService();

    @Test
    void cosineSimilarityOfIdenticalVectorsShouldBeOne() {
        float[] v = {1.0f, 2.0f, 3.0f};
        assertEquals(1.0, service.cosineSimilarity(v, v), 0.0001);
    }

    @Test
    void cosineSimilarityOfOrthogonalVectorsShouldBeZero() {
        float[] a = {1.0f, 0.0f};
        float[] b = {0.0f, 1.0f};
        assertEquals(0.0, service.cosineSimilarity(a, b), 0.0001);
    }

    @Test
    void cosineSimilarityOfOppositeVectorsShouldBeNegativeOne() {
        float[] a = {1.0f, 0.0f};
        float[] b = {-1.0f, 0.0f};
        assertEquals(-1.0, service.cosineSimilarity(a, b), 0.0001);
    }

    @Test
    void shouldThrowOnMismatchedVectorLengths() {
        float[] a = {1.0f, 2.0f};
        float[] b = {1.0f};
        assertThrows(IllegalArgumentException.class,
                () -> service.cosineSimilarity(a, b));
    }

    @Test
    void shouldReturnZeroForNullVectors() {
        assertEquals(0.0, service.cosineSimilarity(null, new float[]{1.0f}));
        assertEquals(0.0, service.cosineSimilarity(new float[]{1.0f}, null));
        assertEquals(0.0, service.cosineSimilarity(null, null));
    }

    @Test
    void shouldReturnZeroForEmptyVectors() {
        assertEquals(0.0, service.cosineSimilarity(new float[0], new float[]{1.0f}));
        assertEquals(0.0, service.cosineSimilarity(new float[]{1.0f}, new float[0]));
        assertEquals(0.0, service.cosineSimilarity(new float[0], new float[0]));
    }

    @Test
    void shouldReturnZeroForZeroVectors() {
        float[] zero = {0.0f, 0.0f, 0.0f};
        float[] other = {1.0f, 2.0f, 3.0f};
        assertEquals(0.0, service.cosineSimilarity(zero, other));
    }

    @Test
    void shouldComputeSimilarityForSimilarVectors() {
        float[] a = {1.0f, 2.0f, 3.0f};
        float[] b = {1.1f, 2.1f, 3.1f};
        double similarity = service.cosineSimilarity(a, b);
        assertTrue(similarity > 0.99, "Similar vectors should have similarity > 0.99, got: " + similarity);
    }
}
