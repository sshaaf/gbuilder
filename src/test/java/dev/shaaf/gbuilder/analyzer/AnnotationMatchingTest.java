package dev.shaaf.gbuilder.analyzer;

import dev.shaaf.gbuilder.graph.model.*;
import dev.shaaf.gbuilder.lang.Language;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationMatchingTest {

    @Test
    void shouldMatchClassMethodAndFieldAnnotations() {
        ClassNode node = new ClassNode(
                "com.example.Service", "com.example", "Service", "Service.java", Language.JAVA,
                ClassKind.CLASS, List.of("public"),
                List.of(new AnnotationNode("Stateless", List.of())),
                List.of("@Stateless"),
                List.of(), List.of(), List.of(), null, List.of(), List.of(),
                List.of(new MethodNode(
                        "onMessage", "void onMessage()", "void", List.of("public"),
                        List.of(new AnnotationNode("Override", List.of())),
                        List.of("@Override"),
                        List.of(), List.of(), List.of(), false,
                        "{}", List.of(), List.of(), null, null)),
                List.of(new FieldNode("String", "topic", List.of("private"),
                        List.of("@jakarta.jms.Queue(name=\"orders\")"), null)),
                List.of(), List.of(), List.of(), java.util.Map.of(), null, null, MigrationStatus.PENDING);

        assertTrue(AnnotationMatching.matchesAny(Set.of("Stateless"), node));
        assertTrue(AnnotationMatching.matchesAny(Set.of("Override"), node));
        assertFalse(AnnotationMatching.matchesAny(Set.of("Entity"), node));
    }

    @Test
    void shouldExtractSimpleAnnotationNames() {
        assertEquals("Table", AnnotationMatching.simpleName("@Table(name = \"ORDERS\")"));
        assertEquals("Entity", AnnotationMatching.simpleName("javax.persistence.Entity"));
    }
}
