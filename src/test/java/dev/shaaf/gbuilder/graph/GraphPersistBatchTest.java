package dev.shaaf.gbuilder.graph;

import dev.shaaf.gbuilder.graph.model.ClassKind;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import dev.shaaf.gbuilder.graph.model.MethodNode;
import dev.shaaf.gbuilder.lang.Language;
import dev.shaaf.gbuilder.lang.ParserBackend;
import dev.shaaf.gbuilder.graph.model.MigrationStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class GraphPersistBatchTest {

    @Inject
    GraphRepository graphRepo;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        graphRepo.setStoreRoot(tempDir);
        graphRepo.clearAll();
    }

    @Test
    void shouldPersistClassesAndEdgesInChunks() {
        ClassNode classA = sampleClass("com.batch.A", "run", "helper");
        List<ClassNode> classes = List.of(classA, sampleClass("com.batch.B", "execute"));

        graphRepo.persistClassNodesChunk(classes);
        graphRepo.createCallEdgesChunk(List.of(
                new GraphRepository.PendingCallEdge("void run()", "com.batch.A", "helper")));
        graphRepo.createStructuralEdgesChunk(List.of(
                new GraphRepository.PendingStructuralEdge("DEPENDS_ON", "com.batch.A", "com.batch.B", 3.0)));

        assertEquals(2, graphRepo.countClassNodes());
        assertEquals(1, graphRepo.countRelationships("CALLS"));
        assertEquals(1, graphRepo.countRelationships("DEPENDS_ON"));
    }

    @Test
    void shouldStoreEmbeddingsInChunks() {
        graphRepo.persistClassNodesChunk(List.of(sampleClass("com.embed.Target", "run")));
        graphRepo.storeEmbeddingsChunk(List.of(
                new GraphRepository.PendingEmbedding(
                        "com.embed.Target", "void run()", new float[] {0.1f, 0.2f},
                        GraphRepository.EmbeddingKind.METHOD),
                new GraphRepository.PendingEmbedding(
                        "com.embed.Target", null, new float[] {0.3f, 0.4f},
                        GraphRepository.EmbeddingKind.CLASS)));

        assertEquals(2, graphRepo.listStoredEmbeddings().size());
    }

    private ClassNode sampleClass(String fqn, String... methodNames) {
        List<MethodNode> methods = new ArrayList<>();
        for (String methodName : methodNames) {
            methods.add(new MethodNode(
                    methodName, "void " + methodName + "()", "void",
                    List.of("public"), List.of(), List.of(), List.of(),
                    List.of(), List.of(), false,
                    "{}", List.of(), List.of(), null, null));
        }
        return new ClassNode(
                fqn, "com.batch", fqn.substring(fqn.lastIndexOf('.') + 1), "/tmp/X.java",
                Language.JAVA, ClassKind.CLASS, List.of("public"), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, List.of(), List.of(),
                methods, List.of(), List.of(), List.of(), List.of(),
                java.util.Map.of("parserBackend", ParserBackend.JPARSER.cliValue()),
                null, null, MigrationStatus.PENDING);
    }
}
