package dev.shaaf.gbuilder.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.shaaf.gbuilder.lang.ParserBackend;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GraphBuilderIntegrationTest {

    @Inject
    SemanticGraphService graphService;

    @Inject
    GraphRepository graphRepo;

    @Inject
    GraphStoreLocation storeLocation;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void cleanGraph() {
        storeLocation.reset();
        graphRepo.clearAll();
    }

    @Test
    void shouldProduceExactMetricsForKnownFixture() throws IOException {
        Path tempDir = Files.createTempDirectory("metrics-test-");
        try {
            writeFixtureFiles(tempDir);

            var result = graphService.buildGraph(tempDir).result();

            assertEquals(5, result.typeCount(), "total type count");

            var k = result.kindBreakdown();
            assertEquals(1, k.classes());
            assertEquals(1, k.interfaces());
            assertEquals(1, k.enums());
            assertEquals(1, k.records());
            assertEquals(1, k.annotationTypes());

            assertEquals(7, result.methodCount());
            assertEquals(2, result.constructorCount());
            assertEquals(2, result.fieldCount());
            assertEquals(1, result.importCount());
            assertEquals(1, result.annotationCount());
            assertEquals(3, result.enumConstantCount());
            assertEquals(2, result.recordComponentCount());
            assertTrue(result.communityCount() >= 1, "should detect at least one community");

            var stats = graphRepo.getGraphStats();
            assertEquals(5, stats.typeCount());
            assertEquals(7, stats.methodCount());
            assertEquals(7, stats.containsEdges());
            assertTrue(stats.callsEdges() >= 1);
            assertEquals(0, stats.dependsOnEdges());
            assertEquals(0, stats.extendsEdges());
            assertEquals(1, stats.implementsEdges());
            assertEquals(0, stats.externalTypeCount());
            assertEquals(0, stats.technologyCount());
            assertEquals(0, stats.usesEdges());
            assertTrue(stats.communityCount() >= 1);

            assertTrue(graphRepo.findClassNode("com.fixture.Greeter").isPresent());
            assertTrue(graphRepo.findClassNode("com.fixture.GreeterImpl").isPresent());
            assertTrue(graphRepo.getCommunityId("com.fixture.GreeterImpl").isPresent());
        } finally {
            deleteRecursive(tempDir);
        }
    }

    @Test
    void shouldProduceExactMetricsForInheritanceFixture() throws IOException {
        Path tempDir = Files.createTempDirectory("inheritance-test-");
        try {
            Files.writeString(tempDir.resolve("Animal.java"), """
                package com.zoo;
                public class Animal {
                    private String name;
                    public Animal(String name) { this.name = name; }
                    public String speak() { return ""; }
                }
                """);
            Files.writeString(tempDir.resolve("Dog.java"), """
                package com.zoo;
                public class Dog extends Animal {
                    public Dog(String name) { super(name); }
                    public String speak() { return "Woof"; }
                }
                """);
            Files.writeString(tempDir.resolve("Cat.java"), """
                package com.zoo;
                public class Cat extends Animal implements Vocal {
                    public Cat(String name) { super(name); }
                    public String speak() { return "Meow"; }
                    public int volume() { return 5; }
                }
                """);
            Files.writeString(tempDir.resolve("Vocal.java"), """
                package com.zoo;
                public interface Vocal {
                    int volume();
                }
                """);

            var result = graphService.buildGraph(tempDir).result();

            assertEquals(4, result.typeCount());
            assertEquals(8, result.methodCount());
            assertEquals(3, result.constructorCount());

            var stats = graphRepo.getGraphStats();
            assertEquals(4, stats.typeCount());
            assertEquals(8, stats.methodCount());
            assertEquals(8, stats.containsEdges());
            assertEquals(2, stats.extendsEdges());
            assertEquals(1, stats.implementsEdges());
            assertEquals(0, stats.dependsOnEdges());

            assertTrue(graphRepo.classExists("com.zoo.Dog"));
            assertTrue(graphRepo.classExists("com.zoo.Animal"));
            assertTrue(graphRepo.classExists("com.zoo.Cat"));
            assertTrue(graphRepo.classExists("com.zoo.Vocal"));
        } finally {
            deleteRecursive(tempDir);
        }
    }

    @Test
    void shouldProduceExactDependsOnEdgesForIntraCodebaseImports() throws IOException {
        Path tempDir = Files.createTempDirectory("depends-test-");
        try {
            Files.writeString(tempDir.resolve("Config.java"), """
                package com.app;
                public class Config {
                    public String getValue() { return "v"; }
                }
                """);
            Files.writeString(tempDir.resolve("Service.java"), """
                package com.app;
                import com.app.Config;
                public class Service {
                    public void run() { }
                }
                """);

            var result = graphService.buildGraph(tempDir).result();
            assertEquals(2, result.typeCount());
            assertEquals(1, result.importCount());

            var stats = graphRepo.getGraphStats();
            assertEquals(1, stats.dependsOnEdges());
            assertTrue(graphRepo.getNeighbors("com.app.Service", 1, java.util.Set.of("DEPENDS_ON"))
                    .contains("com.app.Config"));
        } finally {
            deleteRecursive(tempDir);
        }
    }

    @Test
    void shouldProduceExactCallsEdges() throws IOException {
        Path tempDir = Files.createTempDirectory("calls-test-");
        try {
            Files.writeString(tempDir.resolve("Processor.java"), """
                package com.calls;
                public class Processor {
                    public void process() {
                        validate();
                        transform();
                    }
                    private void validate() { }
                    private void transform() { }
                }
                """);

            var result = graphService.buildGraph(tempDir).result();
            assertEquals(1, result.typeCount());
            assertEquals(3, result.methodCount());

            var stats = graphRepo.getGraphStats();
            assertEquals(2, stats.callsEdges());
        } finally {
            deleteRecursive(tempDir);
        }
    }

    @Test
    void shouldReturnClusterJsonForCommunity() throws IOException {
        Path tempDir = Files.createTempDirectory("cluster-test-");
        try {
            writeFixtureFiles(tempDir);
            graphService.buildGraph(tempDir);

            int communityId = graphRepo.getCommunityId("com.fixture.GreeterImpl").orElseThrow();
            String clusterJson = graphRepo.getClusterJson(String.valueOf(communityId));

            JsonNode nodes = mapper.readTree(clusterJson);
            assertTrue(nodes.isArray());
            assertFalse(nodes.isEmpty());
        } finally {
            deleteRecursive(tempDir);
        }
    }

    @Test
    void shouldBuildGraphWithJparserBackendAndRecordParserBackend() throws IOException {
        Path tempDir = Files.createTempDirectory("jparser-test-");
        try {
            writeFixtureFiles(tempDir);
            var result = graphService.buildGraph(tempDir, ParserBackend.JPARSER).result();

            assertEquals(5, result.typeCount());
            assertEquals(ParserBackend.JPARSER, result.parserBackend());
            assertTrue(result.communityCount() >= 1);
        } finally {
            deleteRecursive(tempDir);
        }
    }

    @Test
    void shouldBuildGraphWithTreeSitterBackend() throws IOException {
        Path tempDir = Files.createTempDirectory("treesitter-test-");
        try {
            Files.writeString(tempDir.resolve("Greeter.java"), """
                package com.fixture;
                public interface Greeter {
                    String greet(String name);
                }
                """);
            Files.writeString(tempDir.resolve("GreeterImpl.java"), """
                package com.fixture;
                public class GreeterImpl implements Greeter {
                    public String greet(String name) { return helper(name); }
                    private String helper(String name) { return name; }
                }
                """);

            var result = graphService.buildGraph(tempDir, ParserBackend.TREESITTER).result();

            assertEquals(2, result.typeCount());
            assertEquals(ParserBackend.TREESITTER, result.parserBackend());
            assertTrue(result.communityCount() >= 1);
            var stats = graphRepo.getGraphStats();
            assertEquals(2, stats.typeCount());
            assertTrue(stats.callsEdges() >= 1);
        } finally {
            deleteRecursive(tempDir);
        }
    }

    @Test
    void shouldProduceComparableMetricsWithTreeSitterBackend() throws IOException {
        Path tempDir = Files.createTempDirectory("treesitter-metrics-");
        try {
            writeFixtureFiles(tempDir);
            var result = graphService.buildGraph(tempDir, ParserBackend.TREESITTER).result();

            assertEquals(5, result.typeCount());
            assertEquals(ParserBackend.TREESITTER, result.parserBackend());
            assertEquals(1, result.kindBreakdown().interfaces());
            assertEquals(1, result.kindBreakdown().enums());
            assertEquals(1, result.kindBreakdown().records());
            assertTrue(result.methodCount() >= 5);
            assertTrue(result.communityCount() >= 1);

            var stats = graphRepo.getGraphStats();
            assertEquals(5, stats.typeCount());
            assertTrue(stats.callsEdges() >= 1);
            assertTrue(graphRepo.findClassNode("com.fixture.GreeterImpl").isPresent());
        } finally {
            deleteRecursive(tempDir);
        }
    }

    @Test
    void shouldAcquireAndReleaseSubGraphLocks() {
        graphService.acquireSliceLock("node-1");
        graphService.acquireSliceLock("node-1");
        graphService.releaseSliceLock("node-1");
        graphService.releaseSliceLock("node-1");
    }

    private void writeFixtureFiles(Path dir) throws IOException {
        Files.writeString(dir.resolve("Greeter.java"), """
            package com.fixture;
            public interface Greeter {
                String greet(String name);
            }
            """);

        Files.writeString(dir.resolve("GreeterImpl.java"), """
            package com.fixture;
            import java.util.List;
            @Audited
            public class GreeterImpl implements Greeter {
                private String prefix;
                public GreeterImpl(String prefix) { this.prefix = prefix; }
                public String greet(String name) { return helper(name); }
                private String helper(String name) { return prefix + " " + name; }
            }
            """);

        Files.writeString(dir.resolve("Priority.java"), """
            package com.fixture;
            public enum Priority {
                LOW(1), MEDIUM(5), HIGH(10);
                private final int weight;
                Priority(int weight) { this.weight = weight; }
                public int getWeight() { return weight; }
            }
            """);

        Files.writeString(dir.resolve("Point.java"), """
            package com.fixture;
            public record Point(int x, int y) {
                public double distance() { return Math.sqrt(x * x + y * y); }
            }
            """);

        Files.writeString(dir.resolve("Audited.java"), """
            package com.fixture;
            public @interface Audited {
                String value() default "";
            }
            """);
    }

    private void deleteRecursive(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }
        }
    }
}
