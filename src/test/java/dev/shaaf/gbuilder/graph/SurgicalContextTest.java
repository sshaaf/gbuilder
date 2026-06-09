package dev.shaaf.gbuilder.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SurgicalContextTest {

    @Inject
    GraphRepository graphRepo;

    @Inject
    GraphStoreLocation storeLocation;

    @Inject
    SemanticGraphService graphService;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void cleanGraph() {
        storeLocation.reset();
        graphRepo.clearAll();
    }

    @Test
    void shouldBuildSurgicalContextForSimpleMethod() throws Exception {
        Path tempDir = buildBillingFixture();
        try {
            graphService.buildGraph(tempDir);

            String json = graphService.buildSurgicalContext("com.legacy.billing.BillingProcessor#processPayment");
            assertNotEquals("{}", json);

            JsonNode root = mapper.readTree(json);
            assertTrue(root.has("metadata"));
            assertEquals("com.legacy.billing", root.path("metadata").path("package_name").asText());
            assertEquals("BillingProcessor", root.path("metadata").path("class_name").asText());
            assertTrue(root.has("class_context"));
            assertTrue(root.has("target_method"));
            assertEquals("processPayment", root.path("target_method").path("name").asText());
            assertFalse(root.path("target_method").path("raw_body").asText().isEmpty());
        } finally {
            deleteRecursive(tempDir);
        }
    }

    @Test
    void shouldIncludeInternalCallSignaturesInContext() throws Exception {
        Path tempDir = buildBillingFixture();
        try {
            graphService.buildGraph(tempDir);

            String json = graphService.buildSurgicalContext("com.legacy.billing.BillingProcessor#processPayment");
            JsonNode calls = mapper.readTree(json).path("target_method").path("internal_method_calls");
            assertTrue(calls.isArray());
            assertTrue(calls.size() > 0);

            boolean hasCalculateTax = false;
            for (JsonNode call : calls) {
                if ("calculateTax".equals(call.path("method_name").asText())) {
                    hasCalculateTax = true;
                    assertFalse(call.path("signature").asText().isEmpty());
                }
            }
            assertTrue(hasCalculateTax);
        } finally {
            deleteRecursive(tempDir);
        }
    }

    @Test
    void shouldReturnEmptyJsonForUnknownMethod() throws Exception {
        Path tempDir = buildBillingFixture();
        try {
            graphService.buildGraph(tempDir);
            assertEquals("{}", graphService.buildSurgicalContext("com.nonexistent.Foo#bar"));
            assertEquals("{}", graphService.buildSurgicalContext("invalid-format"));
        } finally {
            deleteRecursive(tempDir);
        }
    }

    @Test
    void shouldCompileTokenBoundedContext() throws Exception {
        Path tempDir = buildBillingFixture();
        try {
            graphService.buildGraph(tempDir);

            var compiled = graphService.compileContext(
                    "com.legacy.billing.BillingProcessor#processPayment", 4000);

            assertNotEquals("{}", compiled.json());
            assertTrue(compiled.estimatedTokens() > 0);
            assertFalse(compiled.includedNodes().isEmpty());

            JsonNode root = mapper.readTree(compiled.json());
            assertEquals("surgical", root.path("t").asText());
            assertTrue(root.has("tgt"));
            assertTrue(root.has("_instructions"));
            assertTrue(root.path("c").asInt() >= 0);
        } finally {
            deleteRecursive(tempDir);
        }
    }

    private Path buildBillingFixture() throws IOException {
        Path tempDir = Files.createTempDirectory("surgical-ctx-");
        Files.writeString(tempDir.resolve("BillingProcessor.java"), """
            package com.legacy.billing;
            import javax.inject.Inject;
            import javax.ejb.Stateless;
            @Stateless
            public class BillingProcessor {
                @Inject
                private TaxService taxService;
                private String currency = "USD";
                public void processPayment(Order order) {
                    double tax = calculateTax(order);
                    taxService.apply(order, tax);
                }
                private double calculateTax(Order order) {
                    return order.amount() * 0.1;
                }
            }
            """);
        Files.writeString(tempDir.resolve("TaxService.java"), """
            package com.legacy.billing;
            public class TaxService {
                public void apply(Order order, double tax) { }
            }
            """);
        Files.writeString(tempDir.resolve("Order.java"), """
            package com.legacy.billing;
            public record Order(double amount) { }
            """);
        return tempDir;
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
