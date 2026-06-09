package dev.shaaf.gbuilder.cli;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class GbuilderVersionProviderTest {

    @Inject
    GbuilderVersionProvider versionProvider;

    @Test
    void versionShouldMatchPomXml() {
        String expected = System.getProperty("gbuilder.expected.version");
        assertEquals(expected, versionProvider.getVersion()[0]);
    }
}
