package dev.shaaf.gbuilder.lang;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ParserBackendTest {

    @Test
    void shouldExposeCliValues() {
        assertEquals("jparser", ParserBackend.JPARSER.cliValue());
        assertEquals("treesitter", ParserBackend.TREESITTER.cliValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"jparser", "JPARSER", " Jparser "})
    void shouldResolveJparserCaseInsensitively(String input) {
        assertEquals(ParserBackend.JPARSER, ParserBackend.fromCliValue(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"treesitter", "TREESITTER", " TreeSitter "})
    void shouldResolveTreesitterCaseInsensitively(String input) {
        assertEquals(ParserBackend.TREESITTER, ParserBackend.fromCliValue(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t"})
    void shouldDefaultBlankToJparser(String input) {
        assertEquals(ParserBackend.JPARSER, ParserBackend.fromCliValue(input));
    }

    @Test
    void shouldDefaultNullToJparser() {
        assertEquals(ParserBackend.JPARSER, ParserBackend.fromCliValue(null));
    }

    @Test
    void shouldRejectUnknownBackend() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ParserBackend.fromCliValue("javac"));
        assertTrue(ex.getMessage().contains("jparser"));
        assertTrue(ex.getMessage().contains("treesitter"));
    }
}
