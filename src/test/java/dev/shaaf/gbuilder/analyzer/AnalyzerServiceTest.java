package dev.shaaf.gbuilder.analyzer;

import dev.shaaf.gbuilder.analyzer.model.AnalysisResult;
import dev.shaaf.gbuilder.analyzer.model.TechEntry;
import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.GraphStoreLocation;
import dev.shaaf.gbuilder.graph.GraphTestFixtures;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AnalyzerServiceTest {

    @Inject
    AnalyzerService analyzerService;

    @Inject
    GraphRepository graphRepo;

    @Inject
    GraphStoreLocation storeLocation;

    @BeforeEach
    void cleanGraph() {
        storeLocation.reset();
        graphRepo.clearAll();
    }

    @Test
    void shouldDetectMultipleTechnologies() {
        ClassNode ejbBean = GraphTestFixtures.classNode("com.app.MyBean", "MyBean",
                List.of("Stateless"), List.of("javax.ejb.Stateless", "javax.inject.Inject"));
        ClassNode jpaEntity = GraphTestFixtures.classNode("com.app.MyEntity", "MyEntity",
                List.of("Entity", "Table"), List.of("javax.persistence.Entity", "javax.persistence.Table"));
        ClassNode plainClass = GraphTestFixtures.classNode("com.app.Utils", "Utils",
                List.of(), List.of("java.util.List"));

        graphRepo.persistClassNode(ejbBean);
        graphRepo.persistClassNode(jpaEntity);
        graphRepo.persistClassNode(plainClass);

        AnalysisResult result = analyzerService.analyze(List.of(ejbBean, jpaEntity, plainClass));

        assertTrue(result.nodesTagged() >= 2);
        var techIds = result.techBom().entries().stream().map(TechEntry::technologyId).toList();
        assertTrue(techIds.contains("ejb"));
        assertTrue(techIds.contains("jpa"));
        assertTrue(techIds.contains("cdi"));
    }

    @Test
    void shouldPersistTechnologyNodesInGraphStore() {
        ClassNode jaxrsEndpoint = GraphTestFixtures.classNode("com.app.MyResource", "MyResource",
                List.of("Path"), List.of("javax.ws.rs.Path", "javax.ws.rs.GET"));

        graphRepo.persistClassNode(jaxrsEndpoint);
        analyzerService.analyze(List.of(jaxrsEndpoint));

        assertEquals(1, graphRepo.countTechnologyById("jaxrs"));
        assertEquals(1, graphRepo.countUsesEdgesForTechnology("jaxrs"));
    }

    @Test
    void shouldHandleNoMatchesGracefully() {
        ClassNode plainClass = GraphTestFixtures.classNode("com.app.Utils", "Utils",
                List.of(), List.of("java.util.List"));

        graphRepo.persistClassNode(plainClass);
        AnalysisResult result = analyzerService.analyze(List.of(plainClass));

        assertTrue(result.techBom().entries().isEmpty());
        assertEquals(0, result.nodesTagged());
        assertTrue(result.rulesApplied() > 0);
    }

    @Test
    void shouldNotDuplicateTechnologyNodes() {
        ClassNode bean1 = GraphTestFixtures.classNode("com.app.Bean1", "Bean1",
                List.of("Stateless"), List.of("javax.ejb.Stateless"));
        ClassNode bean2 = GraphTestFixtures.classNode("com.app.Bean2", "Bean2",
                List.of("Stateful"), List.of("javax.ejb.Stateful"));

        graphRepo.persistClassNode(bean1);
        graphRepo.persistClassNode(bean2);
        analyzerService.analyze(List.of(bean1, bean2));

        assertEquals(1, graphRepo.countTechnologyById("ejb"));
        assertEquals(2, graphRepo.countUsesEdgesForTechnology("ejb"));
    }

    @Test
    void shouldProduceCorrectTechBOMStructure() {
        ClassNode ejbBean = GraphTestFixtures.classNode("com.app.MyBean", "MyBean",
                List.of("Stateless"), List.of("javax.ejb.Stateless"));
        ClassNode jpaEntity = GraphTestFixtures.classNode("com.app.MyEntity", "MyEntity",
                List.of("Entity"), List.of("javax.persistence.Entity"));

        graphRepo.persistClassNode(ejbBean);
        graphRepo.persistClassNode(jpaEntity);
        AnalysisResult result = analyzerService.analyze(List.of(ejbBean, jpaEntity));

        var bom = result.techBom();
        assertNotNull(bom.generatedAt());
        assertFalse(bom.categorySummary().isEmpty());
        for (TechEntry entry : bom.entries()) {
            assertTrue(entry.usageCount() > 0);
            assertFalse(entry.exampleFqns().isEmpty());
            assertTrue(entry.exampleFqns().size() <= 5);
        }
    }
}
