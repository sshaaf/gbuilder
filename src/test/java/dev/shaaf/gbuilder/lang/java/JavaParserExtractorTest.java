package dev.shaaf.gbuilder.lang.java;

import dev.shaaf.gbuilder.lang.java.JavaParserExtractor;
import dev.shaaf.gbuilder.graph.model.*;
import dev.shaaf.gbuilder.lang.Language;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JavaParserExtractorTest {

    JavaParserExtractor parser;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = new JavaParserExtractor();
        parser.configureParser();
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    @Test
    void shouldParseSimpleClass() throws IOException {
        Path javaFile = tempDir.resolve("HelloService.java");
        Files.writeString(javaFile, """
            package com.example;

            import jakarta.enterprise.context.ApplicationScoped;

            @ApplicationScoped
            public class HelloService {
                public String greet(String name) {
                    return "Hello " + name;
                }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);

        assertEquals(1, nodes.size());
        ClassNode node = nodes.get(0);
        assertEquals("com.example.HelloService", node.fullyQualifiedName());
        assertEquals("com.example", node.packageName());
        assertEquals("HelloService", node.simpleName());
        assertEquals(Language.JAVA, node.language());
        assertTrue(node.annotations().stream().anyMatch(a -> a.contains("ApplicationScoped")));
        assertEquals(1, node.methods().size());
        assertEquals("greet", node.methods().get(0).name());
    }

    @Test
    void shouldExtractMethodAnnotations() throws IOException {
        Path javaFile = tempDir.resolve("OrderProcessor.java");
        Files.writeString(javaFile, """
            package com.legacy;

            import javax.ejb.Stateless;
            import javax.ejb.TransactionAttribute;
            import javax.ejb.TransactionAttributeType;

            @Stateless
            public class OrderProcessor {
                @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
                public void process() { }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        ClassNode node = nodes.get(0);

        assertTrue(node.annotations().stream().anyMatch(a -> a.contains("Stateless")));
        MethodNode method = node.methods().get(0);
        assertTrue(method.annotations().stream().anyMatch(a -> a.contains("TransactionAttribute")));
    }

    @Test
    void shouldExtractFieldsWithModifiers() throws IOException {
        Path javaFile = tempDir.resolve("Config.java");
        Files.writeString(javaFile, """
            package com.example;
            public class Config {
                private static final int MAX_RETRIES = 3;
                private String name;
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        assertEquals(2, nodes.get(0).fields().size());
        assertEquals("MAX_RETRIES", nodes.get(0).fields().get(0).name());
        assertTrue(nodes.get(0).fields().get(0).modifiers().contains("static"));
        assertTrue(nodes.get(0).fields().get(0).modifiers().contains("final"));
    }

    @Test
    void shouldExtractInternalMethodCalls() throws IOException {
        Path javaFile = tempDir.resolve("Calculator.java");
        Files.writeString(javaFile, """
            package com.example;
            public class Calculator {
                public int compute(int a, int b) {
                    int tax = calculateTax(a);
                    log("done");
                    return tax + b;
                }
                private int calculateTax(int amount) { return amount; }
                private void log(String msg) { }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        MethodNode compute = nodes.get(0).methods().stream()
                .filter(m -> m.name().equals("compute"))
                .findFirst().orElseThrow();

        assertTrue(compute.internalMethodCalls().contains("calculateTax"));
        assertTrue(compute.internalMethodCalls().contains("log"));
    }

    @Test
    void shouldParseDirectoryRecursively() throws IOException {
        Path subDir = tempDir.resolve("sub");
        Files.createDirectories(subDir);

        Files.writeString(tempDir.resolve("A.java"),
                "package root; public class A { }");
        Files.writeString(subDir.resolve("B.java"),
                "package root.sub; public class B { }");

        List<ClassNode> nodes = parser.parseDirectory(tempDir);
        assertEquals(2, nodes.size());
    }

    @Test
    void shouldHandleClassWithNoMethods() throws IOException {
        Path javaFile = tempDir.resolve("Empty.java");
        Files.writeString(javaFile, """
            package com.example;
            public class Empty { }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        assertEquals(1, nodes.size());
        assertTrue(nodes.get(0).methods().isEmpty());
    }

    @Test
    void shouldExtractMultipleClassesFromSingleFile() throws IOException {
        Path javaFile = tempDir.resolve("Multi.java");
        Files.writeString(javaFile, """
            package com.example;
            public class Multi {
                public void doSomething() { }
            }
            class Helper {
                public void help() { }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        assertEquals(2, nodes.size());
    }

    @Test
    void shouldParseInterfaceWithCorrectKind() throws IOException {
        Path javaFile = tempDir.resolve("Greeter.java");
        Files.writeString(javaFile, """
            package com.example;
            public interface Greeter {
                String greet(String name);
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        assertEquals(1, nodes.size());
        assertEquals(ClassKind.INTERFACE, nodes.get(0).kind());
        assertTrue(nodes.get(0).modifiers().contains("public"));
    }

    @Test
    void shouldParseEnumWithFields() throws IOException {
        Path javaFile = tempDir.resolve("Color.java");
        Files.writeString(javaFile, """
            package com.example;
            public enum Color {
                RED, GREEN, BLUE;
                public String label() { return name().toLowerCase(); }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        assertEquals(1, nodes.size());
        assertEquals(ClassKind.ENUM, nodes.get(0).kind());
        assertEquals(1, nodes.get(0).methods().size());
        assertEquals("label", nodes.get(0).methods().get(0).name());
    }

    @Test
    void shouldParseRecordWithComponents() throws IOException {
        Path javaFile = tempDir.resolve("Point.java");
        Files.writeString(javaFile, """
            package com.example;
            public record Point(int x, int y) {
                public double distance() { return Math.sqrt(x * x + y * y); }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        assertEquals(1, nodes.size());
        assertEquals(ClassKind.RECORD, nodes.get(0).kind());
        assertTrue(nodes.get(0).methods().stream()
                .anyMatch(m -> m.name().equals("distance")));
    }

    @Test
    void shouldParseAnnotationType() throws IOException {
        Path javaFile = tempDir.resolve("Audited.java");
        Files.writeString(javaFile, """
            package com.example;
            public @interface Audited {
                String value() default "";
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        assertEquals(1, nodes.size());
        assertEquals(ClassKind.ANNOTATION, nodes.get(0).kind());
    }

    @Test
    void shouldExtractConstructors() throws IOException {
        Path javaFile = tempDir.resolve("Person.java");
        Files.writeString(javaFile, """
            package com.example;
            public class Person {
                private String name;
                public Person(String name) { this.name = name; }
                public String getName() { return name; }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        ClassNode node = nodes.get(0);

        List<MethodNode> constructors = node.methods().stream()
                .filter(MethodNode::constructor).toList();
        assertEquals(1, constructors.size());
        assertEquals("Person", constructors.get(0).name());
        assertEquals("<init>", constructors.get(0).returnType());
        assertEquals(1, constructors.get(0).parameters().size());
        assertEquals("String", constructors.get(0).parameters().get(0).type());
        assertEquals("name", constructors.get(0).parameters().get(0).name());

        List<MethodNode> regularMethods = node.methods().stream()
                .filter(m -> !m.constructor()).toList();
        assertEquals(1, regularMethods.size());
    }

    @Test
    void shouldExtractMethodParameters() throws IOException {
        Path javaFile = tempDir.resolve("MathUtils.java");
        Files.writeString(javaFile, """
            package com.example;
            public class MathUtils {
                public int add(int a, int b) { return a + b; }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        MethodNode add = nodes.get(0).methods().get(0);
        assertEquals(2, add.parameters().size());
        assertEquals("int", add.parameters().get(0).type());
        assertEquals("a", add.parameters().get(0).name());
        assertEquals("int", add.parameters().get(1).type());
        assertEquals("b", add.parameters().get(1).name());
    }

    @Test
    void shouldExtractMethodModifiers() throws IOException {
        Path javaFile = tempDir.resolve("Utility.java");
        Files.writeString(javaFile, """
            package com.example;
            public abstract class Utility {
                public static synchronized void doWork() { }
                protected abstract void hook();
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        assertTrue(nodes.get(0).modifiers().contains("abstract"));
        assertTrue(nodes.get(0).modifiers().contains("public"));

        MethodNode doWork = nodes.get(0).methods().stream()
                .filter(m -> m.name().equals("doWork")).findFirst().orElseThrow();
        assertTrue(doWork.modifiers().contains("public"));
        assertTrue(doWork.modifiers().contains("static"));
        assertTrue(doWork.modifiers().contains("synchronized"));

        MethodNode hook = nodes.get(0).methods().stream()
                .filter(m -> m.name().equals("hook")).findFirst().orElseThrow();
        assertTrue(hook.modifiers().contains("abstract"));
    }

    @Test
    void shouldExtractThrownExceptions() throws IOException {
        Path javaFile = tempDir.resolve("FileReader.java");
        Files.writeString(javaFile, """
            package com.example;
            import java.io.IOException;
            public class FileReader {
                public String read(String path) throws IOException, IllegalArgumentException {
                    return "";
                }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        MethodNode read = nodes.get(0).methods().get(0);
        assertEquals(2, read.thrownExceptions().size());
        assertTrue(read.thrownExceptions().contains("IOException"));
        assertTrue(read.thrownExceptions().contains("IllegalArgumentException"));
    }

    @Test
    void shouldExtractSuperclassAndInterfaces() throws IOException {
        Path javaFile = tempDir.resolve("Dog.java");
        Files.writeString(javaFile, """
            package com.example;
            import java.io.Serializable;
            public class Dog extends Animal implements Serializable, Comparable<Dog> {
                public int compareTo(Dog other) { return 0; }
            }
            class Animal { }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        ClassNode dog = nodes.stream()
                .filter(n -> n.simpleName().equals("Dog")).findFirst().orElseThrow();
        assertEquals("com.example.Animal", dog.superClass());
        assertEquals(2, dog.interfaces().size());
        assertTrue(dog.interfaces().contains("java.io.Serializable"));
        assertTrue(dog.interfaces().contains("java.lang.Comparable"));
    }

    @Test
    void shouldResolveInnerClassFqn() throws IOException {
        Path javaFile = tempDir.resolve("Outer.java");
        Files.writeString(javaFile, """
            package com.example;
            public class Outer {
                public static class Inner {
                    public void work() { }
                }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        assertEquals(2, nodes.size());
        assertTrue(nodes.stream().anyMatch(
                n -> n.fullyQualifiedName().equals("com.example.Outer")));
        assertTrue(nodes.stream().anyMatch(
                n -> n.fullyQualifiedName().equals("com.example.Outer.Inner")));
    }

    @Test
    void shouldParseEnumImplementingInterface() throws IOException {
        Path javaFile = tempDir.resolve("Status.java");
        Files.writeString(javaFile, """
            package com.example;
            public enum Status implements Displayable {
                ACTIVE, INACTIVE;
                public String display() { return name(); }
            }
            interface Displayable {
                String display();
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        ClassNode status = nodes.stream()
                .filter(n -> n.simpleName().equals("Status")).findFirst().orElseThrow();
        assertEquals(ClassKind.ENUM, status.kind());
        assertTrue(status.interfaces().contains("com.example.Displayable"));

        ClassNode displayable = nodes.stream()
                .filter(n -> n.simpleName().equals("Displayable")).findFirst().orElseThrow();
        assertEquals(ClassKind.INTERFACE, displayable.kind());
    }

    // --- New construct tests ---

    @Test
    void shouldExtractEnumConstants() throws IOException {
        Path javaFile = tempDir.resolve("Priority.java");
        Files.writeString(javaFile, """
            package com.example;
            public enum Priority {
                LOW(1), MEDIUM(5), HIGH(10);
                private final int weight;
                Priority(int weight) { this.weight = weight; }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        ClassNode node = nodes.get(0);
        assertEquals(ClassKind.ENUM, node.kind());
        assertEquals(3, node.enumConstants().size());
        assertEquals("LOW", node.enumConstants().get(0).name());
        assertEquals(List.of("1"), node.enumConstants().get(0).arguments());
        assertEquals("HIGH", node.enumConstants().get(2).name());
        assertEquals(List.of("10"), node.enumConstants().get(2).arguments());
    }

    @Test
    void shouldExtractRecordComponents() throws IOException {
        Path javaFile = tempDir.resolve("Address.java");
        Files.writeString(javaFile, """
            package com.example;
            public record Address(String street, String city, int zip) {}
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        ClassNode node = nodes.get(0);
        assertEquals(ClassKind.RECORD, node.kind());
        assertEquals(3, node.recordComponents().size());
        assertEquals("String", node.recordComponents().get(0).type());
        assertEquals("street", node.recordComponents().get(0).name());
        assertEquals("int", node.recordComponents().get(2).type());
        assertEquals("zip", node.recordComponents().get(2).name());
    }

    @Test
    void shouldExtractGenericTypeParametersOnClass() throws IOException {
        Path javaFile = tempDir.resolve("Repository.java");
        Files.writeString(javaFile, """
            package com.example;
            public class Repository<T extends Comparable<T>, ID> {
                public T find(ID id) { return null; }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        ClassNode node = nodes.get(0);
        assertEquals(2, node.typeParameters().size());
        assertTrue(node.typeParameters().get(0).contains("T"));
        assertTrue(node.typeParameters().get(1).contains("ID"));
    }

    @Test
    void shouldExtractGenericTypeParametersOnMethod() throws IOException {
        Path javaFile = tempDir.resolve("Converter.java");
        Files.writeString(javaFile, """
            package com.example;
            public class Converter {
                public <T, R> R convert(T input) { return null; }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        MethodNode convert = nodes.get(0).methods().get(0);
        assertEquals(2, convert.typeParameters().size());
        assertEquals("T", convert.typeParameters().get(0));
        assertEquals("R", convert.typeParameters().get(1));
    }

    @Test
    void shouldDistinguishStaticImports() throws IOException {
        Path javaFile = tempDir.resolve("TestHelper.java");
        Files.writeString(javaFile, """
            package com.example;
            import java.util.List;
            import static java.util.Collections.emptyList;
            import static org.junit.jupiter.api.Assertions.*;
            public class TestHelper { }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        ClassNode node = nodes.get(0);

        assertEquals(3, node.importNodes().size());
        ImportNode regularImport = node.importNodes().stream()
                .filter(i -> i.name().equals("java.util.List")).findFirst().orElseThrow();
        assertFalse(regularImport.isStatic());
        assertFalse(regularImport.isWildcard());

        ImportNode staticImport = node.importNodes().stream()
                .filter(i -> i.name().contains("emptyList")).findFirst().orElseThrow();
        assertTrue(staticImport.isStatic());
        assertFalse(staticImport.isWildcard());

        ImportNode wildcardImport = node.importNodes().stream()
                .filter(i -> i.name().contains("Assertions")).findFirst().orElseThrow();
        assertTrue(wildcardImport.isStatic());
        assertTrue(wildcardImport.isWildcard());
    }

    @Test
    void shouldExtractMethodReferences() throws IOException {
        Path javaFile = tempDir.resolve("Processor.java");
        Files.writeString(javaFile, """
            package com.example;
            import java.util.List;
            public class Processor {
                public void process(List<String> items) {
                    items.forEach(System.out::println);
                    items.stream().map(String::toUpperCase);
                }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        MethodNode process = nodes.get(0).methods().get(0);
        assertEquals(2, process.methodReferences().size());
        assertTrue(process.methodReferences().contains("System.out::println"));
        assertTrue(process.methodReferences().contains("String::toUpperCase"));
    }

    @Test
    void shouldExtractStaticInitializerBlocks() throws IOException {
        Path javaFile = tempDir.resolve("Registry.java");
        Files.writeString(javaFile, """
            package com.example;
            import java.util.Map;
            import java.util.HashMap;
            public class Registry {
                private static final Map<String, String> TYPES = new HashMap<>();
                static {
                    TYPES.put("a", "Alpha");
                    TYPES.put("b", "Beta");
                }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        ClassNode node = nodes.get(0);
        assertEquals(1, node.staticInitializers().size());
        assertTrue(node.staticInitializers().get(0).contains("TYPES.put"));
    }

    @Test
    void shouldExtractStructuredAnnotationAttributes() throws IOException {
        Path javaFile = tempDir.resolve("Scheduled.java");
        Files.writeString(javaFile, """
            package com.example;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Scheduled {
                String cron() default "";
                int delay() default 0;
            }
            """);

        Path javaFile2 = tempDir.resolve("Worker.java");
        Files.writeString(javaFile2, """
            package com.example;
            public class Worker {
                @Scheduled(cron = "0 * * * *", delay = 5)
                public void run() { }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile2);
        MethodNode run = nodes.get(0).methods().get(0);
        assertEquals(1, run.annotationNodes().size());
        AnnotationNode scheduled = run.annotationNodes().get(0);
        assertEquals("Scheduled", scheduled.name());
        assertEquals(2, scheduled.attributes().size());
        assertTrue(scheduled.attributes().stream()
                .anyMatch(a -> a.name().equals("cron") && a.value().contains("0 * * * *")));
        assertTrue(scheduled.attributes().stream()
                .anyMatch(a -> a.name().equals("delay") && a.value().equals("5")));
    }

    @Test
    void shouldExtractSingleMemberAnnotationAttribute() throws IOException {
        Path javaFile = tempDir.resolve("Named.java");
        Files.writeString(javaFile, """
            package com.example;
            @SuppressWarnings("unchecked")
            public class Named {
                @Deprecated
                public void old() { }
            }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        ClassNode node = nodes.get(0);

        AnnotationNode suppress = node.annotationNodes().get(0);
        assertEquals("SuppressWarnings", suppress.name());
        assertEquals(1, suppress.attributes().size());
        assertEquals("value", suppress.attributes().get(0).name());
        assertTrue(suppress.attributes().get(0).value().contains("unchecked"));

        MethodNode old = node.methods().get(0);
        AnnotationNode deprecated = old.annotationNodes().get(0);
        assertEquals("Deprecated", deprecated.name());
        assertTrue(deprecated.attributes().isEmpty());
    }

    @Test
    void shouldParseModuleInfo() throws IOException {
        Path moduleFile = tempDir.resolve("module-info.java");
        Files.writeString(moduleFile, """
            module com.example.app {
                requires java.sql;
                requires transitive java.logging;
                exports com.example.api;
                opens com.example.internal;
            }
            """);

        Map<String, Object> info = parser.parseModuleInfo(moduleFile);
        assertNotNull(info);
        assertEquals("com.example.app", info.get("name"));
        assertFalse((Boolean) info.get("isOpen"));
        @SuppressWarnings("unchecked")
        List<String> requires = (List<String>) info.get("requires");
        assertEquals(2, requires.size());
        assertTrue(requires.contains("java.sql"));
        assertTrue(requires.contains("java.logging"));
        @SuppressWarnings("unchecked")
        List<String> exports = (List<String>) info.get("exports");
        assertTrue(exports.contains("com.example.api"));
    }

    @Test
    void shouldSkipModuleInfoInDirectoryScan() throws IOException {
        Files.writeString(tempDir.resolve("module-info.java"), """
            module test.module { }
            """);
        Files.writeString(tempDir.resolve("App.java"), """
            package test;
            public class App { }
            """);

        List<ClassNode> nodes = parser.parseDirectory(tempDir);
        assertEquals(1, nodes.size());
        assertEquals("App", nodes.get(0).simpleName());
    }

    @Test
    void shouldHandleEnumWithNoArgConstants() throws IOException {
        Path javaFile = tempDir.resolve("Direction.java");
        Files.writeString(javaFile, """
            package com.example;
            public enum Direction { NORTH, SOUTH, EAST, WEST }
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        assertEquals(4, nodes.get(0).enumConstants().size());
        assertTrue(nodes.get(0).enumConstants().get(0).arguments().isEmpty());
    }

    @Test
    void shouldCaptureRecordWithGenericTypeParameters() throws IOException {
        Path javaFile = tempDir.resolve("Pair.java");
        Files.writeString(javaFile, """
            package com.example;
            public record Pair<A, B>(A first, B second) {}
            """);

        List<ClassNode> nodes = parser.parseFile(javaFile);
        ClassNode node = nodes.get(0);
        assertEquals(ClassKind.RECORD, node.kind());
        assertEquals(2, node.typeParameters().size());
        assertEquals("A", node.typeParameters().get(0));
        assertEquals("B", node.typeParameters().get(1));
        assertEquals(2, node.recordComponents().size());
    }
}
