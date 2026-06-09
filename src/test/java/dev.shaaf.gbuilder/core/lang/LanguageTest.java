package com.ambitious.migration.agent.core.lang;

import dev.shaaf.gbuilder.lang.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LanguageTest {

    @Test
    void shouldResolveJavaFromExtension() {
        assertEquals(Language.JAVA, Language.fromFileExtension(".java"));
    }

    @Test
    void shouldResolvePythonFromExtension() {
        assertEquals(Language.PYTHON, Language.fromFileExtension(".py"));
    }

    @Test
    void shouldResolveGoFromExtension() {
        assertEquals(Language.GO, Language.fromFileExtension(".go"));
    }

    @Test
    void shouldThrowForUnsupportedExtension() {
        assertThrows(IllegalArgumentException.class,
                () -> Language.fromFileExtension(".lua"));
    }

    @Test
    void shouldExposeMetadata() {
        assertEquals("java", Language.JAVA.id());
        assertEquals("Java", Language.JAVA.displayName());
        assertEquals(".java", Language.JAVA.fileExtension());
    }
}
