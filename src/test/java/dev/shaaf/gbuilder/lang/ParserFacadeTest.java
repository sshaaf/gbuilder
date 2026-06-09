package dev.shaaf.gbuilder.lang;

import dev.shaaf.gbuilder.graph.model.ClassNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ParserFacadeTest {

    @Inject
    ParserFacade parserFacade;

    @TempDir
    Path tempDir;

    @Test
    void shouldParseWithJavaParserBackend() throws Exception {
        writeSimpleService(tempDir);
        List<ClassNode> nodes = parserFacade.extract(tempDir, ParserBackend.JPARSER);
        assertSingleHelloService(nodes, ParserBackend.JPARSER);
    }

    @Test
    void shouldParseWithTreeSitterBackend() throws Exception {
        writeSimpleService(tempDir);
        List<ClassNode> nodes = parserFacade.extract(tempDir, ParserBackend.TREESITTER);
        assertSingleHelloService(nodes, ParserBackend.TREESITTER);
    }

    @Test
    void shouldSelectCorrectExtractor() {
        assertEquals(ParserBackend.JPARSER, parserFacade.select(ParserBackend.JPARSER).backend());
        assertEquals(ParserBackend.TREESITTER, parserFacade.select(ParserBackend.TREESITTER).backend());
    }

    @Test
    void shouldUseDefaultJparserBackendWhenExtractingWithoutArgument() throws Exception {
        writeSimpleService(tempDir);
        List<ClassNode> nodes = parserFacade.extract(tempDir);
        assertSingleHelloService(nodes, ParserBackend.JPARSER);
    }

    @Test
    void bothBackendsShouldParseInheritanceFixture() throws Exception {
        Files.writeString(tempDir.resolve("Animal.java"), """
            package com.zoo;
            public class Animal { }
            """);
        Files.writeString(tempDir.resolve("Dog.java"), """
            package com.zoo;
            public class Dog extends Animal implements Vocal {
                public void bark() { }
            }
            interface Vocal { void speak(); }
            """);

        ClassNode jparserDog = parserFacade.extract(tempDir, ParserBackend.JPARSER).stream()
                .filter(n -> n.simpleName().equals("Dog")).findFirst().orElseThrow();
        ClassNode treesitterDog = parserFacade.extract(tempDir, ParserBackend.TREESITTER).stream()
                .filter(n -> n.simpleName().equals("Dog")).findFirst().orElseThrow();

        assertEquals("com.zoo.Animal", jparserDog.superClass());
        assertEquals("com.zoo.Animal", treesitterDog.superClass());
        assertTrue(jparserDog.interfaces().contains("com.zoo.Vocal"));
        assertTrue(treesitterDog.interfaces().contains("com.zoo.Vocal"));
    }

    @Test
    void bothBackendsShouldExtractInternalMethodCalls() throws Exception {
        Files.writeString(tempDir.resolve("Processor.java"), """
            package com.calls;
            public class Processor {
                public void process() {
                    validate();
                }
                private void validate() { }
            }
            """);

        ClassNode jparserClass = parserFacade.extract(tempDir, ParserBackend.JPARSER).get(0);
        ClassNode treesitterClass = parserFacade.extract(tempDir, ParserBackend.TREESITTER).get(0);

        assertEquals("process", jparserClass.methods().stream()
                .filter(m -> m.name().equals("process")).findFirst().orElseThrow().name());
        assertTrue(jparserClass.methods().stream()
                .filter(m -> m.name().equals("process")).findFirst().orElseThrow()
                .internalMethodCalls().contains("validate"));

        assertTrue(treesitterClass.methods().stream()
                .anyMatch(m -> m.name().equals("process")));
        assertTrue(treesitterClass.methods().stream()
                .filter(m -> m.name().equals("process")).findFirst().orElseThrow()
                .internalMethodCalls().contains("validate"));
    }

    private void writeSimpleService(Path dir) throws Exception {
        Files.writeString(dir.resolve("HelloService.java"), """
            package com.example;
            public class HelloService {
                public String greet(String name) {
                    return "Hello " + name;
                }
            }
            """);
    }

    private void assertSingleHelloService(List<ClassNode> nodes, ParserBackend backend) {
        assertEquals(1, nodes.size());
        ClassNode node = nodes.get(0);
        assertEquals("com.example.HelloService", node.fullyQualifiedName());
        assertEquals("HelloService", node.simpleName());
        assertEquals(1, node.methods().size());
        assertEquals("greet", node.methods().get(0).name());
        assertEquals(backend.cliValue(), node.languageMetadata().get("parserBackend"));
    }
}
