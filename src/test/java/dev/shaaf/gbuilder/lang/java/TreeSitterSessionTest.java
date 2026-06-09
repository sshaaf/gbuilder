package dev.shaaf.gbuilder.lang.java;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TreeSitterSessionTest {

    private final TreeSitterSession session = new TreeSitterSession();

    @AfterEach
    void shutdown() {
        session.shutdown();
    }

    @Test
    void shouldParseJavaSourceToProgramRoot() {
        String source = """
            package com.demo;
            public class Demo {
                public void run() { helper(); }
                private void helper() {}
            }
            """;

        try (var parsed = session.parseJava(source)) {
            assertNotNull(parsed.root());
            assertEquals("program", parsed.root().type());
            assertFalse(parsed.root().namedChildCount() == 0);
        }
    }

    @Test
    void shouldCloseParseResultAndAllowReuse() {
        try (var first = session.parseJava("class A {}")) {
            assertEquals("program", first.root().type());
        }
        try (var second = session.parseJava("interface B {}")) {
            assertTrue(second.root().namedChildCount() > 0);
        }
    }
}
