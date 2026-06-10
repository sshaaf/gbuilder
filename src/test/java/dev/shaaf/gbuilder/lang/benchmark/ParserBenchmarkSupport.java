package dev.shaaf.gbuilder.lang.benchmark;

import dev.shaaf.gbuilder.analyzer.model.AnalysisResult;
import dev.shaaf.gbuilder.analyzer.model.TechBOM;
import dev.shaaf.gbuilder.analyzer.model.TechEntry;
import dev.shaaf.gbuilder.graph.GraphBuildOptions;
import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.GraphStoreLocation;
import dev.shaaf.gbuilder.graph.SemanticGraphService;
import dev.shaaf.gbuilder.lang.ParserBackend;
import dev.shaaf.gbuilder.lang.java.JavaSourceScanner;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class ParserBenchmarkSupport {

    private ParserBenchmarkSupport() {}

    public static Path materializeFixture(String classpathResourceDir) throws IOException {
        URL resource = Objects.requireNonNull(
                ParserBenchmarkSupport.class.getResource(classpathResourceDir),
                "Missing benchmark fixture: " + classpathResourceDir);
        try {
            Path source = Path.of(resource.toURI());
            Path target = Files.createTempDirectory("gbuilder-parser-benchmark-");
            copyRecursive(source, target);
            return target;
        } catch (URISyntaxException e) {
            throw new IOException("Could not resolve fixture path: " + classpathResourceDir, e);
        }
    }

    public static int countJavaFiles(Path sourceRoot) throws IOException {
        return JavaSourceScanner.scanJavaFiles(sourceRoot).size();
    }

    public static ParserBenchmarkRun runBackend(
            SemanticGraphService graphService,
            GraphRepository graphRepo,
            GraphStoreLocation storeLocation,
            Path sourceRoot,
            ParserBackend backend) throws IOException {
        storeLocation.reset();
        graphRepo.clearAll();
        graphRepo.setStoreRoot(sourceRoot);

        var outcome = graphService.buildGraph(
                sourceRoot,
                backend,
                GraphBuildOptions.defaults().withSkipVisualization(true));
        var result = outcome.result();
        var stats = graphRepo.getGraphStats();
        List<String> fqns = graphRepo.listInternalClassFqns().stream().sorted().toList();

        return new ParserBenchmarkRun(
                backend,
                outcome.durationMs(),
                result.typeCount(),
                result.methodCount(),
                result.fieldCount(),
                result.importCount(),
                result.annotationCount(),
                result.communityCount(),
                stats,
                result.analysisResult(),
                fqns,
                technologyCounts(result.analysisResult()));
    }

    public static void writeReport(Path reportPath, String markdown) throws IOException {
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, markdown);
    }

    public static void deleteRecursive(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to delete " + path, e);
                }
            });
        }
    }

    private static Map<String, Long> technologyCounts(AnalysisResult analysis) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (analysis == null || analysis.techBom() == null) {
            return counts;
        }
        TechBOM bom = analysis.techBom();
        for (TechEntry entry : bom.entries()) {
            counts.put(entry.technologyName(), entry.usageCount());
        }
        return counts;
    }

    private static void copyRecursive(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Path destination = target.resolve(relative);
                Files.createDirectories(destination);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
