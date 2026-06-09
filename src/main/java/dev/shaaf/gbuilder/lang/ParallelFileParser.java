package dev.shaaf.gbuilder.lang;

import dev.shaaf.gbuilder.graph.model.ClassNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

@ApplicationScoped
public class ParallelFileParser {

    private static final Logger LOG = Logger.getLogger(ParallelFileParser.class);

    @Inject
    ParallelParseConfig config;

    public List<ClassNode> parseFiles(ClassNodeExtractor extractor, List<Path> files) {
        if (files.isEmpty()) {
            return List.of();
        }
        if (!config.useParallel(files.size())) {
            return parseSequential(extractor, files);
        }

        int parallelism = config.maxParallelism();
        Semaphore limit = new Semaphore(parallelism);
        List<ClassNode> nodes = Collections.synchronizedList(new ArrayList<>());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> tasks = new ArrayList<>(files.size());
            for (Path file : files) {
                tasks.add(() -> {
                    limit.acquire();
                    try {
                        nodes.addAll(extractor.extractFile(file));
                    } catch (RuntimeException e) {
                        LOG.warnf("Skipping unparseable file: %s — %s", file, e.getMessage());
                    } finally {
                        limit.release();
                    }
                    return null;
                });
            }
            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Parallel parse interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Parallel parse failed", e.getCause());
        }

        return List.copyOf(nodes);
    }

    private List<ClassNode> parseSequential(ClassNodeExtractor extractor, List<Path> files) {
        List<ClassNode> nodes = new ArrayList<>();
        for (Path file : files) {
            try {
                nodes.addAll(extractor.extractFile(file));
            } catch (RuntimeException e) {
                LOG.warnf("Skipping unparseable file: %s — %s", file, e.getMessage());
            }
        }
        return nodes;
    }
}
