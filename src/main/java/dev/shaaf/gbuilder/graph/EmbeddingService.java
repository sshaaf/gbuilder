package dev.shaaf.gbuilder.graph;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps LangChain4j EmbeddingModel for single/batch embedding and cosine similarity.
 * When no embedding model is configured (ambitious.embedding.enabled=false or no API key),
 * all embed* methods return zero-length arrays, keeping the graph pipeline functional.
 */
@ApplicationScoped
public class EmbeddingService {

    private static final Logger LOG = Logger.getLogger(EmbeddingService.class);

    @ConfigProperty(name = "ambitious.embedding.enabled", defaultValue = "false")
    boolean embeddingEnabled;

    @ConfigProperty(name = "ambitious.embedding.batch-size", defaultValue = "50")
    int batchSize;

    @Inject
    Instance<EmbeddingModel> embeddingModelInstance;

    public boolean isEnabled() {
        return embeddingEnabled && embeddingModelInstance.isResolvable();
    }

    public float[] embedSingle(String text) {
        if (!isEnabled() || text == null || text.isBlank()) {
            return new float[0];
        }
        try {
            var response = embeddingModelInstance.get().embed(text);
            return response.content().vector();
        } catch (Exception e) {
            LOG.warnf("Embedding failed for text (length=%d): %s", text.length(), e.getMessage());
            return new float[0];
        }
    }

    public List<float[]> embedBatch(List<String> texts) {
        if (!isEnabled() || texts == null || texts.isEmpty()) {
            return texts == null ? List.of() : texts.stream().map(t -> new float[0]).toList();
        }

        List<float[]> results = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
            try {
                var segments = batch.stream()
                        .map(TextSegment::from)
                        .toList();
                var response = embeddingModelInstance.get().embedAll(segments);
                for (var embedding : response.content()) {
                    results.add(embedding.vector());
                }
            } catch (Exception e) {
                LOG.warnf("Batch embedding failed (batch %d-%d): %s",
                        i, Math.min(i + batchSize, texts.size()), e.getMessage());
                for (int j = 0; j < batch.size(); j++) {
                    results.add(new float[0]);
                }
            }
        }
        return results;
    }

    /**
     * Cosine similarity between two vectors. Returns 0.0 if either vector is empty or null.
     */
    public double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return 0.0;
        }
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vectors must have equal length: " + a.length + " vs " + b.length);
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator == 0.0) {
            return 0.0;
        }
        return dotProduct / denominator;
    }
}
