package com.ambitious.migration.agent.graph.detection;

import dev.shaaf.gbuilder.graph.detection.JavaNamespaceDetector;
import dev.shaaf.gbuilder.lang.Language;
import dev.shaaf.gbuilder.graph.model.ClassKind;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import dev.shaaf.gbuilder.graph.model.MigrationStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JavaNamespaceDetectorTest {

    JavaNamespaceDetector detector = new JavaNamespaceDetector();

    private ClassNode classWithImports(String name, List<String> imports, List<String> annotations) {
        return new ClassNode(name, "com.test", name, "/test/" + name + ".java",
                Language.JAVA, ClassKind.CLASS, List.of(), List.of(), annotations,
                List.of(), imports, List.of(),
                null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                Map.of(), null, null, MigrationStatus.PENDING);
    }

    @Test
    void shouldDetectJavaxImports() {
        var nodes = List.of(
                classWithImports("A",
                    List.of("javax.annotation.PostConstruct", "jakarta.inject.Inject"),
                    List.of()),
                classWithImports("B",
                    List.of("jakarta.enterprise.context.ApplicationScoped"),
                    List.of())
        );

        var hits = detector.detectDeprecatedNamespaces(nodes);
        assertEquals(1, hits.size());
        assertEquals("javax.annotation.PostConstruct", hits.get(0).importValue());
    }

    @Test
    void shouldReturnEmptyWhenNoJavaxImports() {
        var nodes = List.of(
                classWithImports("C", List.of("jakarta.inject.Inject"), List.of())
        );
        assertTrue(detector.detectDeprecatedNamespaces(nodes).isEmpty());
    }

    @Test
    void shouldDetectEjbStatelessAnnotation() {
        var nodes = List.of(
                classWithImports("Ejb", List.of(), List.of("@Stateless")),
                classWithImports("Spring", List.of(), List.of("@RestController"))
        );

        var ejbs = detector.detectEjbBoundaries(nodes);
        assertEquals(1, ejbs.size());
        assertEquals("Ejb", ejbs.get(0).simpleName());
    }

    @Test
    void shouldDetectAllEjbAnnotationTypes() {
        var nodes = List.of(
                classWithImports("A", List.of(), List.of("@Stateless")),
                classWithImports("B", List.of(), List.of("@Stateful")),
                classWithImports("C", List.of(), List.of("@Singleton")),
                classWithImports("D", List.of(), List.of("@MessageDriven")),
                classWithImports("E", List.of(), List.of("@Service"))
        );

        var ejbs = detector.detectEjbBoundaries(nodes);
        assertEquals(4, ejbs.size());
    }
}
