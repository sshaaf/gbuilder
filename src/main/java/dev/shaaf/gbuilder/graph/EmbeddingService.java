package dev.shaaf.gbuilder.graph;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import dev.shaaf.gbuilder.graph.model.MethodNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Wraps LangChain4j EmbeddingModel for single/batch embedding and cosine similarity.
 * When no embedding model is configured (gbuilder.embedding.enabled=false or no API key),
 * all embed* methods return zero-length arrays, keeping the graph pipeline functional.
 */
@ApplicationScoped
public class EmbeddingService {

    private static final Logger LOG = Logger.getLogger(EmbeddingService.class);

    @ConfigProperty(name = "gbuilder.embedding.enabled", defaultValue = "false")
    boolean embeddingEnabled;

    @ConfigProperty(name = "gbuilder.embedding.batch-size", defaultValue = "50")
    int batchSize;

    @ConfigProperty(name = "gbuilder.embedding.parallel-threshold", defaultValue = "8")
    int parallelThreshold;

    @ConfigProperty(name = "gbuilder.embedding.max-parallelism", defaultValue = "0")
    int maxParallelism;

    @ConfigProperty(name = "gbuilder.embedding.similarity-threshold", defaultValue = "0.80")
    double similarityThreshold;

    @ConfigProperty(name = "gbuilder.embedding.query-min-similarity", defaultValue = "0.65")
    double queryMinSimilarity;

    @Inject
    Instance<EmbeddingModel> embeddingModelInstance;

    public double similarityThreshold() {
        return similarityThreshold;
    }

    public double queryMinSimilarity() {
        return queryMinSimilarity;
    }

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

    public List<ClassEmbeddingVectors> embedClassNodes(List<ClassNode> nodes) {
        if (!isEnabled() || nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        if (nodes.size() < Math.max(1, parallelThreshold)) {
            return nodes.stream().map(this::embedClassNode).toList();
        }

        int parallelism = maxParallelism > 0 ? maxParallelism : Math.max(1, Runtime.getRuntime().availableProcessors());
        Semaphore limit = new Semaphore(parallelism);
        List<ClassEmbeddingVectors> results = new ArrayList<>(nodes.size());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<ClassEmbeddingVectors>> tasks = new ArrayList<>(nodes.size());
            for (ClassNode node : nodes) {
                tasks.add(() -> {
                    limit.acquire();
                    try {
                        return embedClassNode(node);
                    } finally {
                        limit.release();
                    }
                });
            }
            List<Future<ClassEmbeddingVectors>> futures = executor.invokeAll(tasks);
            for (Future<ClassEmbeddingVectors> future : futures) {
                results.add(future.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Parallel embedding interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Parallel embedding failed", e.getCause());
        }

        return results;
    }

    public ClassEmbeddingVectors embedClassNode(ClassNode node) {
        List<String> methodBodies = node.methods().stream()
                .map(MethodNode::rawBody)
                .map(b -> b != null ? b : "")
                .toList();

        List<float[]> vectors = embedBatch(methodBodies);
        List<MethodEmbeddingVector> methodVectors = new ArrayList<>(node.methods().size());
        for (int i = 0; i < node.methods().size(); i++) {
            MethodNode method = node.methods().get(i);
            methodVectors.add(new MethodEmbeddingVector(method.signature(), vectors.get(i)));
        }

        String classBody = String.join("\n", methodBodies);
        float[] classVector = embedSingle(classBody);
        return new ClassEmbeddingVectors(node.fullyQualifiedName(), methodVectors, classVector);
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

    /**
     * Rank stored class embeddings by cosine similarity to a query vector.
     * Method embeddings contribute to their enclosing class (best score wins).
     */
    public List<SimilarityHit> rankAgainstStoredEmbeddings(
            float[] queryVector,
            List<GraphRepository.StoredEmbedding> embeddings,
            int limit,
            double minSimilarity) {
        if (queryVector == null || queryVector.length == 0 || embeddings == null || embeddings.isEmpty()) {
            return List.of();
        }

        Map<String, Double> bestPerClass = new HashMap<>();
        for (GraphRepository.StoredEmbedding embedding : embeddings) {
            double score = cosineSimilarity(queryVector, embedding.vector());
            if (score >= minSimilarity) {
                bestPerClass.merge(embedding.classFqn(), score, Math::max);
            }
        }

        return bestPerClass.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(Math.max(1, limit))
                .map(e -> new SimilarityHit(e.getKey(), e.getValue()))
                .toList();
    }

    public record SimilarityHit(String classFqn, double score) {}

    public record MethodEmbeddingVector(String signature, float[] vector) {}

    public record ClassEmbeddingVectors(
            String classFqn,
            List<MethodEmbeddingVector> methods,
            float[] classVector) {}
}
