package dev.shaaf.gbuilder.lang.java;

import dev.shaaf.gbuilder.graph.model.ImportNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaFqnResolverTest {

    @Test
    void shouldBuildFqnWithPackageAndEnclosingTypes() {
        assertEquals("com.app.Outer.Inner",
                JavaFqnResolver.buildFqn("com.app", List.of("Outer"), "Inner"));
        assertEquals("TopLevel",
                JavaFqnResolver.buildFqn("", List.of(), "TopLevel"));
    }

    @Test
    void shouldResolveExplicitImport() {
        List<ImportNode> imports = List.of(
                new ImportNode("java.util.List", false, false),
                new ImportNode("com.app.Config", false, false)
        );
        assertEquals("com.app.Config",
                JavaFqnResolver.resolveToFqn("Config", imports, "com.example"));
    }

    @Test
    void shouldResolveJavaLangType() {
        assertEquals("java.lang.String",
                JavaFqnResolver.resolveToFqn("String", List.of(), "com.example"));
    }

    @Test
    void shouldResolveWildcardImport() {
        List<ImportNode> imports = List.of(new ImportNode("com.app", false, true));
        assertEquals("com.app.Service",
                JavaFqnResolver.resolveToFqn("Service", imports, "com.other"));
    }

    @Test
    void shouldFallBackToSamePackage() {
        assertEquals("com.example.Local",
                JavaFqnResolver.resolveToFqn("Local", List.of(), "com.example"));
    }

    @Test
    void shouldReturnSimpleNameWhenUnresolved() {
        assertEquals("Unknown",
                JavaFqnResolver.resolveToFqn("Unknown", List.of(), ""));
    }
}
