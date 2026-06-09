package dev.shaaf.gbuilder.lang;

import dev.shaaf.gbuilder.graph.model.ClassKind;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import dev.shaaf.gbuilder.graph.model.MethodNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static dev.shaaf.gbuilder.lang.ParserConformanceFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ParserConformanceTest {

    @Inject
    ParserFacade parserFacade;

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @EnumSource(ParserBackend.class)
    void shouldParseSimpleClass(ParserBackend backend) throws Exception {
        write(tempDir, "HelloService.java", SIMPLE_CLASS);
        List<ClassNode> nodes = parserFacade.extract(tempDir, backend);

        assertEquals(1, nodes.size());
        ClassNode node = nodes.get(0);
        assertEquals("com.example.HelloService", node.fullyQualifiedName());
        assertEquals("com.example", node.packageName());
        assertEquals("HelloService", node.simpleName());
        assertHasMethod(node, "greet");
        assertTrue(node.annotations().stream().anyMatch(a -> a.contains("ApplicationScoped")));
        assertParserBackend(nodes, backend);
    }

    @ParameterizedTest
    @EnumSource(ParserBackend.class)
    void shouldParseInterface(ParserBackend backend) throws Exception {
        write(tempDir, "Greeter.java", INTERFACE_SOURCE);
        List<ClassNode> nodes = parserFacade.extract(tempDir, backend);

        ClassNode node = nodes.get(0);
        assertKind(node, ClassKind.INTERFACE);
        assertEquals("com.api.Greeter", node.fullyQualifiedName());
        assertHasMethod(node, "greet");
    }

    @ParameterizedTest
    @EnumSource(ParserBackend.class)
    void shouldParseEnum(ParserBackend backend) throws Exception {
        write(tempDir, "Priority.java", ENUM_SOURCE);
        List<ClassNode> nodes = parserFacade.extract(tempDir, backend);

        ClassNode node = nodes.get(0);
        assertKind(node, ClassKind.ENUM);
        assertEquals(3, node.enumConstants().size());
        assertEquals("LOW", node.enumConstants().get(0).name());
        assertEquals("HIGH", node.enumConstants().get(2).name());
        assertHasMethod(node, "label");
    }

    @ParameterizedTest
    @EnumSource(ParserBackend.class)
    void shouldParseRecord(ParserBackend backend) throws Exception {
        write(tempDir, "Point.java", RECORD_SOURCE);
        List<ClassNode> nodes = parserFacade.extract(tempDir, backend);

        ClassNode node = nodes.get(0);
        assertKind(node, ClassKind.RECORD);
        assertEquals(2, node.recordComponents().size());
        assertEquals("x", node.recordComponents().get(0).name());
        assertEquals("y", node.recordComponents().get(1).name());
        assertHasMethod(node, "distance");
    }

    @ParameterizedTest
    @EnumSource(ParserBackend.class)
    void shouldParseAnnotationType(ParserBackend backend) throws Exception {
        write(tempDir, "Audited.java", ANNOTATION_SOURCE);
        List<ClassNode> nodes = parserFacade.extract(tempDir, backend);

        assertKind(nodes.get(0), ClassKind.ANNOTATION);
        assertEquals("com.meta.Audited", nodes.get(0).fullyQualifiedName());
    }

    @ParameterizedTest
    @EnumSource(ParserBackend.class)
    void shouldExtractFieldsWithModifiers(ParserBackend backend) throws Exception {
        write(tempDir, "Config.java", FIELDS_SOURCE);
        List<ClassNode> nodes = parserFacade.extract(tempDir, backend);

        ClassNode node = nodes.get(0);
        assertEquals(2, node.fields().size());
        assertTrue(node.fields().stream().anyMatch(f -> f.name().equals("MAX_RETRIES")
                && f.modifiers().contains("static") && f.modifiers().contains("final")));
        assertTrue(node.fields().stream().anyMatch(f -> f.name().equals("name")
                && f.modifiers().contains("private")));
    }

    @ParameterizedTest
    @EnumSource(ParserBackend.class)
    void shouldExtractConstructorsAndMethods(ParserBackend backend) throws Exception {
        write(tempDir, "Person.java", CONSTRUCTOR_SOURCE);
        List<ClassNode> nodes = parserFacade.extract(tempDir, backend);

        ClassNode node = nodes.get(0);
        List<MethodNode> constructors = node.methods().stream().filter(MethodNode::constructor).toList();
        assertEquals(1, constructors.size());
        assertEquals("Person", constructors.get(0).name());
        assertEquals("<init>", constructors.get(0).returnType());
        assertEquals(1, constructors.get(0).parameters().size());
        assertEquals("String", constructors.get(0).parameters().get(0).type());

        assertHasMethod(node, "getName");
    }

    @ParameterizedTest
    @EnumSource(ParserBackend.class)
    void shouldExtractMethodParameters(ParserBackend backend) throws Exception {
        write(tempDir, "MathUtils.java", """
                package com.math;
                public class MathUtils {
                    public int add(int a, int b) { return a + b; }
                }
                """);
        List<ClassNode> nodes = parserFacade.extract(tempDir, backend);

        MethodNode add = findMethod(nodes.get(0), "add");
        assertEquals(2, add.parameters().size());
        assertEquals("int", add.parameters().get(0).type());
        assertEquals("a", add.parameters().get(0).name());
        assertEquals("b", add.parameters().get(1).name());
    }

    @ParameterizedTest
    @EnumSource(ParserBackend.class)
    void shouldExtractSuperclassAndInterfaces(ParserBackend backend) throws Exception {
        write(tempDir, "Dog.java", INHERITANCE_SOURCE);
        List<ClassNode> nodes = parserFacade.extract(tempDir, backend);

        ClassNode dog = findBySimpleName(nodes, "Dog");
        assertEquals("com.inherit.Animal", dog.superClass());
        assertTrue(dog.interfaces().size() >= 2);
        assertTrue(dog.interfaces().contains("java.io.Serializable"));
        assertTrue(dog.interfaces().stream().anyMatch(i -> i.contains("Comparable")));
    }

    @ParameterizedTest
    @EnumSource(ParserBackend.class)
    void shouldResolveInnerClassFqn(ParserBackend backend) throws Exception {
        write(tempDir, "Outer.java", INNER_CLASS_SOURCE);
        List<ClassNode> nodes = parserFacade.extract(tempDir, backend);

        assertEquals(2, nodes.size());
        assertAnyMatch(nodes, n -> n.fullyQualifiedName().equals("com.inner.Outer"));
        assertAnyMatch(nodes, n -> n.fullyQualifiedName().equals("com.inner.Outer.Inner"));
    }

    @ParameterizedTest
    @EnumSource(ParserBackend.class)
    void shouldExtractInternalMethodCalls(ParserBackend backend) throws Exception {
        write(tempDir, "Processor.java", METHOD_CALLS_SOURCE);
        List<ClassNode> nodes = parserFacade.extract(tempDir, backend);

        ClassNode node = nodes.get(0);
        assertMethodCalls(node, "process", "validate", "transform");
    }

    @ParameterizedTest
    @EnumSource(ParserBackend.class)
    void shouldScanDirectoryAndSkipModuleInfo(ParserBackend backend) throws Exception {
        Path sub = tempDir.resolve("pkg");
        Files.createDirectories(sub);
        write(sub, "Service.java", """
                package pkg;
                public class Service { public void run() {} }
                """);
        Files.writeString(tempDir.resolve("module-info.java"), "module app {}");

        List<ClassNode> nodes = parserFacade.extract(tempDir, backend);
        assertEquals(1, nodes.size());
        assertEquals("pkg.Service", nodes.get(0).fullyQualifiedName());
    }

    @ParameterizedTest
    @EnumSource(ParserBackend.class)
    void shouldExtractMultipleTypesInOneFile(ParserBackend backend) throws Exception {
        write(tempDir, "Types.java", """
                package com.multi;
                public class Alpha { }
                interface Beta { void go(); }
                """);
        List<ClassNode> nodes = parserFacade.extract(tempDir, backend);

        assertEquals(2, nodes.size());
        assertAnyMatch(nodes, n -> n.simpleName().equals("Alpha") && n.kind() == ClassKind.CLASS);
        assertAnyMatch(nodes, n -> n.simpleName().equals("Beta") && n.kind() == ClassKind.INTERFACE);
    }

    @ParameterizedTest
    @EnumSource(ParserBackend.class)
    void shouldRecordImports(ParserBackend backend) throws Exception {
        write(tempDir, "UserService.java", """
                package com.svc;
                import java.util.List;
                import com.svc.Config;
                public class UserService {
                    public void load() {}
                }
                class Config {}
                """);
        List<ClassNode> nodes = parserFacade.extract(tempDir, backend);

        ClassNode service = findBySimpleName(nodes, "UserService");
        assertEquals(2, service.importNodes().size());
        assertTrue(service.imports().contains("java.util.List"));
    }

    @ParameterizedTest
    @EnumSource(ParserBackend.class)
    void shouldExtractMethodModifiers(ParserBackend backend) throws Exception {
        write(tempDir, "Utility.java", """
                package com.util;
                public abstract class Utility {
                    public static synchronized void doWork() { }
                    protected abstract void hook();
                }
                """);
        List<ClassNode> nodes = parserFacade.extract(tempDir, backend);

        ClassNode node = nodes.get(0);
        assertTrue(node.modifiers().contains("abstract"));
        assertTrue(node.modifiers().contains("public"));

        MethodNode doWork = findMethod(node, "doWork");
        assertTrue(doWork.modifiers().contains("public"));
        assertTrue(doWork.modifiers().contains("static"));
        assertTrue(doWork.modifiers().contains("synchronized"));

        MethodNode hook = findMethod(node, "hook");
        assertTrue(hook.modifiers().contains("protected"));
        assertTrue(hook.modifiers().contains("abstract"));
    }
}
