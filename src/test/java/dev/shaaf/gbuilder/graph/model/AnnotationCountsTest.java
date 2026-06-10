package dev.shaaf.gbuilder.graph.model;

import dev.shaaf.gbuilder.lang.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnnotationCountsTest {

    @Test
    void shouldCountAnnotationsAcrossClassMembers() {
        ClassNode node = new ClassNode(
                "com.example.Entity", "com.example", "Entity", "Entity.java", Language.JAVA,
                ClassKind.CLASS, List.of("public"),
                List.of(new AnnotationNode("Entity", List.of())),
                List.of("@Entity"),
                List.of(), List.of(), List.of(), null, List.of(), List.of(),
                List.of(new MethodNode(
                        "getId", "Long getId()", "Long", List.of("public"),
                        List.of(), List.of(),
                        List.of(), List.of(), List.of(), false,
                        "{}", List.of(), List.of(), null, null)),
                List.of(new FieldNode("Long", "id", List.of("private"),
                        List.of("@Id", "@GeneratedValue"), null)),
                List.of(), List.of(), List.of(), java.util.Map.of(), null, null, MigrationStatus.PENDING);

        assertEquals(3, AnnotationCounts.totalForClass(node));
    }
}
