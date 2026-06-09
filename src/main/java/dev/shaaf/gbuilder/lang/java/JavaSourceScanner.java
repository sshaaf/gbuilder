package dev.shaaf.gbuilder.lang.java;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class JavaSourceScanner {

    private static final Logger LOG = Logger.getLogger(JavaSourceScanner.class);

    private JavaSourceScanner() {}

    public static List<Path> scanJavaFiles(Path rootDir) throws IOException {
        return scanJavaFiles(rootDir, path -> !path.getFileName().toString().equals("module-info.java"));
    }

    public static List<Path> scanJavaFiles(Path rootDir, Predicate<Path> extraFilter) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(rootDir)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(extraFilter)
                    .forEach(javaFiles::add);
        }
        return javaFiles;
    }

    public static String readSource(Path filePath) throws IOException {
        return Files.readString(filePath);
    }

    public static void logParseSummary(Path rootDir, int classCount, String backend) {
        LOG.infof("Parsed %d classes from %s using %s", classCount, rootDir, backend);
    }
}
