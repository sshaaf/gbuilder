package dev.shaaf.gbuilder.lang.java;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TreeSitterQuerySupportTest {

    @Inject
    JavaTreeSitterQueries queries;

    @Inject
    TreeSitterSession session;

    @Test
    void shouldExtractMethodModifiersAndNamesViaQuery() {
        String source = """
                class MyService {
                  public static synchronized void doWork() {}
                  protected abstract void hook();
                }
                """;

        try (var parsed = session.parseJava(source)) {
            var classBody = TreeSitterSupport.findBody(
                    TreeSitterSupport.childrenOfType(parsed.root(), "class_declaration").get(0));
            var methodNode = TreeSitterQuerySupport.directChildrenOfType(classBody, "method_declaration").get(0);

            Map<String, io.roastedroot.treesitter.TreeSitterNode> captures =
                    TreeSitterQuerySupport.firstCaptureNodes(queries.methodDeclaration(), methodNode, source);

            assertEquals("doWork", TreeSitterSupport.nodeText(source, captures.get("method_name")));
            assertEquals("void", TreeSitterSupport.nodeText(source, captures.get("return_type")));
            assertTrue(TreeSitterQuerySupport.modifiersFromCapture(source, captures, "modifiers")
                    .containsAll(List.of("public", "static", "synchronized")));
        }
    }

    @Test
    void shouldExtractFieldModifiersViaQuery() {
        String source = """
                class Config {
                  private static final int MAX_RETRIES = 3;
                }
                """;

        try (var parsed = session.parseJava(source)) {
            var classBody = TreeSitterSupport.findBody(
                    TreeSitterSupport.childrenOfType(parsed.root(), "class_declaration").get(0));
            var fieldNode = TreeSitterQuerySupport.directChildrenOfType(classBody, "field_declaration").get(0);

            Map<String, io.roastedroot.treesitter.TreeSitterNode> captures =
                    TreeSitterQuerySupport.firstCaptureNodes(queries.fieldDeclaration(), fieldNode, source);

            assertEquals("MAX_RETRIES", TreeSitterSupport.nodeText(source, captures.get("field_name")));
            assertTrue(TreeSitterQuerySupport.modifiersFromCapture(source, captures, "modifiers")
                    .containsAll(List.of("private", "static", "final")));
        }
    }

    @Test
    void shouldExtractMethodCallsViaQuery() {
        String source = """
                class Processor {
                  public void process() {
                    validate();
                    transform();
                  }
                  private void validate() {}
                }
                """;

        try (var parsed = session.parseJava(source)) {
            var classBody = TreeSitterSupport.findBody(
                    TreeSitterSupport.childrenOfType(parsed.root(), "class_declaration").get(0));
            var processMethod = TreeSitterQuerySupport.directChildrenOfType(classBody, "method_declaration").get(0);

            List<String> calls = TreeSitterQuerySupport.captureTexts(
                    queries.methodInvocation(), processMethod, source, "call_name");

            assertEquals(List.of("validate", "transform"), calls);
        }
    }

    @Test
    void shouldFindEnumMethodsInsideEnumBodyDeclarations() {
        String source = """
                enum Priority {
                  LOW, MEDIUM, HIGH;
                  public String label() { return name(); }
                }
                """;

        try (var parsed = session.parseJava(source)) {
            var enumBody = TreeSitterSupport.findBody(
                    TreeSitterSupport.childrenOfType(parsed.root(), "enum_declaration").get(0));
            var methods = TreeSitterQuerySupport.typeBodyMembers(enumBody, "method_declaration");
            assertEquals(1, methods.size());
            assertEquals("label", TreeSitterSupport.methodNameText(source, methods.get(0)));
        }
    }

    @Test
    void shouldNotIncludeNestedClassMethodsAsDirectChildren() {
        String source = """
                class Outer {
                  public void outerWork() {}
                  static class Inner {
                    public void innerWork() {}
                  }
                }
                """;

        try (var parsed = session.parseJava(source)) {
            var outerBody = TreeSitterSupport.findBody(
                    TreeSitterSupport.childrenOfType(parsed.root(), "class_declaration").get(0));

            var methods = TreeSitterQuerySupport.directChildrenOfType(outerBody, "method_declaration");
            assertEquals(1, methods.size());
            assertEquals("outerWork", TreeSitterSupport.methodNameText(source, methods.get(0)));
        }
    }
}
