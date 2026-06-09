package dev.shaaf.gbuilder.lang.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaSourceScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldFindJavaFilesRecursively() throws Exception {
        Path sub = tempDir.resolve("nested");
        Files.createDirectories(sub);
        Files.writeString(tempDir.resolve("Root.java"), "package root; class Root {}");
        Files.writeString(sub.resolve("Nested.java"), "package root; class Nested {}");
        Files.writeString(tempDir.resolve("Notes.txt"), "ignore me");

        List<Path> files = JavaSourceScanner.scanJavaFiles(tempDir);
        assertEquals(2, files.size());
        assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().equals("Root.java")));
        assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().equals("Nested.java")));
    }

    @Test
    void shouldSkipModuleInfoByDefault() throws Exception {
        Files.writeString(tempDir.resolve("module-info.java"), "module app {}");
        Files.writeString(tempDir.resolve("App.java"), "package app; class App {}");

        List<Path> files = JavaSourceScanner.scanJavaFiles(tempDir);
        assertEquals(1, files.size());
        assertEquals("App.java", files.get(0).getFileName().toString());
    }

    @Test
    void shouldReadSourceContent() throws Exception {
        Path file = tempDir.resolve("Sample.java");
        Files.writeString(file, "package demo; class Sample {}");
        assertEquals("package demo; class Sample {}", JavaSourceScanner.readSource(file));
    }
}
