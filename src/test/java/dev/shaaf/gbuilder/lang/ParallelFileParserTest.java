package dev.shaaf.gbuilder.lang;

import dev.shaaf.gbuilder.graph.model.ClassNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class ParallelFileParserTest {

    @Inject
    ParserFacade parserFacade;

    @Inject
    ParallelFileParser parallelFileParser;

    @TempDir
    Path tempDir;

    @Test
    void parallelParseShouldMatchSequentialResults() throws Exception {
        for (int i = 0; i < 20; i++) {
            Files.writeString(tempDir.resolve("Service" + i + ".java"), """
                package com.parallel;
                public class Service%d {
                    public void run() { helper(); }
                    private void helper() { }
                }
                """.formatted(i));
        }

        List<Path> files = dev.shaaf.gbuilder.lang.java.JavaSourceScanner.scanJavaFiles(tempDir);
        ClassNodeExtractor extractor = parserFacade.select(ParserBackend.JPARSER);

        List<ClassNode> parallel = parallelFileParser.parseFiles(extractor, files);
        List<ClassNode> sequential = parserFacade.extractFilesSequential(tempDir, files, ParserBackend.JPARSER);

        List<String> parallelFqns = parallel.stream()
                .map(ClassNode::fullyQualifiedName)
                .sorted(Comparator.naturalOrder())
                .toList();
        List<String> sequentialFqns = sequential.stream()
                .map(ClassNode::fullyQualifiedName)
                .sorted(Comparator.naturalOrder())
                .toList();

        assertEquals(sequentialFqns, parallelFqns);
        assertEquals(20, parallelFqns.size());
    }
}
