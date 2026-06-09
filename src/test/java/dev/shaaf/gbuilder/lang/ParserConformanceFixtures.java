package dev.shaaf.gbuilder.lang;

import dev.shaaf.gbuilder.graph.model.ClassKind;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import dev.shaaf.gbuilder.graph.model.MethodNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

public final class ParserConformanceFixtures {

    private ParserConformanceFixtures() {}

    public static final String SIMPLE_CLASS = """
            package com.example;
            import jakarta.enterprise.context.ApplicationScoped;
            @ApplicationScoped
            public class HelloService {
                public String greet(String name) {
                    return "Hello " + name;
                }
            }
            """;

    public static final String INTERFACE_SOURCE = """
            package com.api;
            public interface Greeter {
                String greet(String name);
            }
            """;

    public static final String ENUM_SOURCE = """
            package com.enums;
            public enum Priority {
                LOW, MEDIUM, HIGH;
                public String label() { return name(); }
            }
            """;

    public static final String RECORD_SOURCE = """
            package com.records;
            public record Point(int x, int y) {
                public double distance() { return Math.sqrt(x * x + y * y); }
            }
            """;

    public static final String ANNOTATION_SOURCE = """
            package com.meta;
            public @interface Audited {
                String value() default "";
            }
            """;

    public static final String FIELDS_SOURCE = """
            package com.fields;
            public class Config {
                private static final int MAX_RETRIES = 3;
                private String name;
            }
            """;

    public static final String CONSTRUCTOR_SOURCE = """
            package com.ctors;
            public class Person {
                private String name;
                public Person(String name) { this.name = name; }
                public String getName() { return name; }
            }
            """;

    public static final String INHERITANCE_SOURCE = """
            package com.inherit;
            import java.io.Serializable;
            public class Dog extends Animal implements Serializable, Comparable<Dog> {
                public int compareTo(Dog other) { return 0; }
            }
            class Animal { }
            """;

    public static final String INNER_CLASS_SOURCE = """
            package com.inner;
            public class Outer {
                public static class Inner {
                    public void work() { }
                }
            }
            """;

    public static final String METHOD_CALLS_SOURCE = """
            package com.calls;
            public class Processor {
                public void process() {
                    validate();
                    transform();
                }
                private void validate() { }
                private void transform() { }
            }
            """;

    public static void write(Path dir, String filename, String source) throws Exception {
        Files.writeString(dir.resolve(filename), source);
    }

    public static ClassNode findBySimpleName(List<ClassNode> nodes, String simpleName) {
        return nodes.stream()
                .filter(n -> n.simpleName().equals(simpleName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing type: " + simpleName));
    }

    public static MethodNode findMethod(ClassNode node, String name) {
        return node.methods().stream()
                .filter(m -> m.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method: " + name));
    }

    public static void assertParserBackend(List<ClassNode> nodes, ParserBackend backend) {
        nodes.forEach(n -> assertEquals(backend.cliValue(), n.languageMetadata().get("parserBackend")));
    }

    public static void assertKind(ClassNode node, ClassKind kind) {
        assertEquals(kind, node.kind());
    }

    public static void assertHasMethod(ClassNode node, String name) {
        assertTrue(node.methods().stream().anyMatch(m -> m.name().equals(name)),
                () -> "Expected method " + name + " on " + node.simpleName());
    }

    public static void assertMethodCalls(ClassNode node, String methodName, String... calls) {
        MethodNode method = findMethod(node, methodName);
        for (String call : calls) {
            assertTrue(method.internalMethodCalls().contains(call),
                    () -> methodName + " should call " + call + " but had " + method.internalMethodCalls());
        }
    }

    public static void assertAnyMatch(List<ClassNode> nodes, Predicate<ClassNode> predicate) {
        assertTrue(nodes.stream().anyMatch(predicate), "No node matched predicate");
    }
}
