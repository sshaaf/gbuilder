package com.ambitious.migration.agent.analyzer;

import dev.shaaf.gbuilder.analyzer.DeclarativeRule;
import dev.shaaf.gbuilder.analyzer.RuleDefinition;
import dev.shaaf.gbuilder.graph.model.*;
import dev.shaaf.gbuilder.lang.Language;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the declarative rule engine by loading the shipped JSON rule files
 * and verifying match behavior against synthetic ClassNode fixtures.
 */
class TechnologyRuleTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, DeclarativeRule> RULES = new HashMap<>();

    @BeforeAll
    static void loadRules() throws IOException {
        String[] ruleFiles = {"ejb", "jpa", "jaxrs", "servlet", "cdi", "jms", "jaxws", "jsf"};
        for (String name : ruleFiles) {
            try (InputStream is = TechnologyRuleTest.class.getClassLoader()
                    .getResourceAsStream("rules/" + name + ".json")) {
                assertNotNull(is, "Rule file rules/" + name + ".json must exist on classpath");
                RuleDefinition def = MAPPER.readValue(is, RuleDefinition.class);
                RULES.put(name, new DeclarativeRule(def));
            }
        }
        assertEquals(8, RULES.size(), "All 8 shipped rules should load");
    }

    // ------- EJB -------

    @Test
    void ejbRuleShouldMatchStatelessAnnotation() {
        assertTrue(RULES.get("ejb").matches(classWithAnnotations("Stateless")));
    }

    @Test
    void ejbRuleShouldMatchEjbImport() {
        assertTrue(RULES.get("ejb").matches(classWithImports("javax.ejb.TransactionAttribute")));
    }

    @Test
    void ejbRuleShouldNotMatchPlainClass() {
        assertFalse(RULES.get("ejb").matches(classWithAnnotations()));
    }

    // ------- JPA -------

    @Test
    void jpaRuleShouldMatchEntityAnnotation() {
        assertTrue(RULES.get("jpa").matches(classWithAnnotations("Entity")));
    }

    @Test
    void jpaRuleShouldMatchPersistenceImport() {
        assertTrue(RULES.get("jpa").matches(classWithImports("javax.persistence.Column")));
    }

    @Test
    void jpaRuleShouldNotMatchPlainClass() {
        assertFalse(RULES.get("jpa").matches(classWithAnnotations()));
    }

    // ------- JAX-RS -------

    @Test
    void jaxRsRuleShouldMatchPathAnnotation() {
        assertTrue(RULES.get("jaxrs").matches(classWithAnnotations("Path")));
    }

    @Test
    void jaxRsRuleShouldMatchWsRsImport() {
        assertTrue(RULES.get("jaxrs").matches(classWithImports("javax.ws.rs.core.Response")));
    }

    @Test
    void jaxRsRuleShouldNotMatchPlainClass() {
        assertFalse(RULES.get("jaxrs").matches(classWithAnnotations()));
    }

    // ------- Servlet -------

    @Test
    void servletRuleShouldMatchWebServletAnnotation() {
        assertTrue(RULES.get("servlet").matches(classWithAnnotations("WebServlet")));
    }

    @Test
    void servletRuleShouldMatchHttpServletSuperclass() {
        assertTrue(RULES.get("servlet").matches(classWithSuperClass("javax.servlet.http.HttpServlet")));
    }

    @Test
    void servletRuleShouldNotMatchPlainClass() {
        assertFalse(RULES.get("servlet").matches(classWithAnnotations()));
    }

    // ------- CDI -------

    @Test
    void cdiRuleShouldMatchInjectAnnotation() {
        assertTrue(RULES.get("cdi").matches(classWithAnnotations("Inject")));
    }

    @Test
    void cdiRuleShouldMatchCdiImport() {
        assertTrue(RULES.get("cdi").matches(classWithImports("javax.enterprise.context.RequestScoped")));
    }

    @Test
    void cdiRuleShouldNotMatchPlainClass() {
        assertFalse(RULES.get("cdi").matches(classWithAnnotations()));
    }

    // ------- JMS -------

    @Test
    void jmsRuleShouldMatchJmsImport() {
        assertTrue(RULES.get("jms").matches(classWithImports("javax.jms.ConnectionFactory")));
    }

    @Test
    void jmsRuleShouldNotMatchPlainClass() {
        assertFalse(RULES.get("jms").matches(classWithAnnotations()));
    }

    // ------- JAX-WS -------

    @Test
    void jaxWsRuleShouldMatchWebServiceAnnotation() {
        assertTrue(RULES.get("jaxws").matches(classWithAnnotations("WebService")));
    }

    @Test
    void jaxWsRuleShouldNotMatchPlainClass() {
        assertFalse(RULES.get("jaxws").matches(classWithAnnotations()));
    }

    // ------- JSF -------

    @Test
    void jsfRuleShouldMatchManagedBeanAnnotation() {
        assertTrue(RULES.get("jsf").matches(classWithAnnotations("ManagedBean")));
    }

    @Test
    void jsfRuleShouldMatchFacesImport() {
        assertTrue(RULES.get("jsf").matches(classWithImports("javax.faces.context.FacesContext")));
    }

    @Test
    void jsfRuleShouldNotMatchPlainClass() {
        assertFalse(RULES.get("jsf").matches(classWithAnnotations()));
    }

    // ------- DeclarativeRule structure -------

    @Test
    void allRulesShouldHaveValidMetadata() {
        for (var entry : RULES.entrySet()) {
            DeclarativeRule rule = entry.getValue();
            assertNotNull(rule.technologyId(), entry.getKey() + " should have id");
            assertNotNull(rule.technologyName(), entry.getKey() + " should have name");
            assertNotNull(rule.category(), entry.getKey() + " should have category");
            assertFalse(rule.technologyName().isBlank(), entry.getKey() + " name should not be blank");
        }
    }

    // ------- Dynamic rule from RuleDefinition -------

    @Test
    void shouldCreateRuleFromInlineDefinition() {
        var def = new RuleDefinition("spring-boot", "Spring Boot", "spring",
                new RuleDefinition.MatchCriteria(
                        List.of("SpringBootApplication"),
                        List.of("org.springframework.boot."),
                        List.of(),
                        List.of()
                ));
        var rule = new DeclarativeRule(def);

        assertEquals("spring-boot", rule.technologyId());
        assertTrue(rule.matches(classWithAnnotations("SpringBootApplication")));
        assertTrue(rule.matches(classWithImports("org.springframework.boot.autoconfigure.EnableAutoConfiguration")));
        assertFalse(rule.matches(classWithAnnotations()));
    }

    // ------- Helpers -------

    private static ClassNode classWithAnnotations(String... annotations) {
        var annNodes = java.util.Arrays.stream(annotations)
                .map(a -> new AnnotationNode(a, List.of()))
                .toList();
        var annStrings = List.of(annotations);
        return new ClassNode(
                "com.test.MyClass", "com.test", "MyClass", "MyClass.java",
                Language.JAVA, ClassKind.CLASS,
                List.of("public"), annNodes, annStrings,
                List.of(), List.of(),
                List.of(), null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), Map.of(), null, null, MigrationStatus.PENDING
        );
    }

    private static ClassNode classWithImports(String... imports) {
        var importNodes = java.util.Arrays.stream(imports)
                .map(i -> new ImportNode(i, false, false))
                .toList();
        return new ClassNode(
                "com.test.MyClass", "com.test", "MyClass", "MyClass.java",
                Language.JAVA, ClassKind.CLASS,
                List.of("public"), List.of(), List.of(),
                importNodes, List.of(imports),
                List.of(), null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), Map.of(), null, null, MigrationStatus.PENDING
        );
    }

    private static ClassNode classWithSuperClass(String superClass) {
        return new ClassNode(
                "com.test.MyClass", "com.test", "MyClass", "MyClass.java",
                Language.JAVA, ClassKind.CLASS,
                List.of("public"), List.of(), List.of(),
                List.of(), List.of(),
                List.of(), superClass, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(), Map.of(), null, null, MigrationStatus.PENDING
        );
    }
}
