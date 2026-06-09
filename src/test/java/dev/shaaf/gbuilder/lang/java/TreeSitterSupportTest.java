package dev.shaaf.gbuilder.lang.java;

import io.roastedroot.treesitter.TreeSitterNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class TreeSitterSupportTest {

    private static final String FIXTURE = """
            package com.support;
            import java.util.List;
            public class Calculator {
                private int value;
                public int compute(int a) {
                    return add(a);
                }
                private int add(int x) { return x + value; }
            }
            """;

    private final TreeSitterSession session = new TreeSitterSession();

    @AfterEach
    void shutdown() {
        session.shutdown();
    }

    private void withRoot(Consumer<WithParsed> test) {
        try (var parsed = session.parseJava(FIXTURE)) {
            test.accept(new WithParsed(FIXTURE, parsed.root()));
        }
    }

    @Test
    void shouldExtractPackageDeclaration() {
        withRoot(ctx -> {
            TreeSitterNode packageNode = TreeSitterSupport.findFirstChild(ctx.root, "package_declaration");
            assertNotNull(packageNode);
            assertTrue(TreeSitterSupport.nodeText(ctx.source, packageNode).contains("com.support"));
        });
    }

    @Test
    void shouldFindClassDeclaration() {
        withRoot(ctx -> {
            List<TreeSitterNode> classes = TreeSitterSupport.childrenOfType(ctx.root, "class_declaration");
            assertEquals(1, classes.size());
            assertEquals("Calculator", TreeSitterSupport.typeDeclarationName(ctx.source, classes.get(0)));
        });
    }

    @Test
    void shouldExtractMethodNamesWithoutReturnType() {
        withRoot(ctx -> {
            TreeSitterNode classNode = TreeSitterSupport.childrenOfType(ctx.root, "class_declaration").get(0);
            TreeSitterNode body = TreeSitterSupport.findBody(classNode);
            List<TreeSitterNode> methods = TreeSitterSupport.childrenOfType(body, "method_declaration");

            assertEquals(2, methods.size());
            assertEquals("compute", TreeSitterSupport.methodNameText(ctx.source, methods.get(0)));
            assertEquals("add", TreeSitterSupport.methodNameText(ctx.source, methods.get(1)));
        });
    }

    @Test
    void shouldExtractInternalMethodCalls() {
        withRoot(ctx -> {
            TreeSitterNode classNode = TreeSitterSupport.childrenOfType(ctx.root, "class_declaration").get(0);
            TreeSitterNode compute = TreeSitterSupport.childrenOfType(
                    TreeSitterSupport.findBody(classNode), "method_declaration").get(0);

            List<String> calls = TreeSitterSupport.extractMethodInvocations(ctx.source, compute);
            assertTrue(calls.contains("add"));
        });
    }

    @Test
    void shouldExtractFieldDeclaration() {
        withRoot(ctx -> {
            TreeSitterNode classNode = TreeSitterSupport.childrenOfType(ctx.root, "class_declaration").get(0);
            List<TreeSitterNode> fields = TreeSitterSupport.childrenOfType(
                    TreeSitterSupport.findBody(classNode), "field_declaration");
            assertEquals(1, fields.size());
        });
    }

    @Test
    void shouldExtractReferenceTypeParameterNames() {
        try (var parsed = session.parseJava("""
                class Box {
                    void put(String label, int count) {}
                }
                """)) {
            TreeSitterNode method = TreeSitterSupport.childrenOfType(
                    parsed.root(), "method_declaration").get(0);
            TreeSitterNode params = TreeSitterSupport.findFirstNamedChildOfTypes(method, "formal_parameters");
            List<TreeSitterNode> formals = TreeSitterSupport.childrenOfType(params, "formal_parameter");
            assertEquals("label", TreeSitterSupport.parameterNameText(parsed.source(), formals.get(0)));
            assertEquals("count", TreeSitterSupport.parameterNameText(parsed.source(), formals.get(1)));
        }
    }

    @Test
    void shouldSliceNodeTextByByteRange() {
        withRoot(ctx -> {
            TreeSitterNode classNode = TreeSitterSupport.childrenOfType(ctx.root, "class_declaration").get(0);
            String text = TreeSitterSupport.nodeText(ctx.source, classNode);
            assertTrue(text.startsWith("public class Calculator"));
        });
    }

    private record WithParsed(String source, TreeSitterNode root) {}
}
