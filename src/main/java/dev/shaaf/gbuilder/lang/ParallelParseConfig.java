package dev.shaaf.gbuilder.lang;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ParallelParseConfig {

    @ConfigProperty(name = "gbuilder.parse.parallel-threshold", defaultValue = "16")
    int parallelThreshold;

    @ConfigProperty(name = "gbuilder.parse.max-parallelism", defaultValue = "0")
    int maxParallelism;

    public boolean useParallel(int fileCount) {
        return fileCount >= Math.max(1, parallelThreshold);
    }

    public int maxParallelism() {
        if (maxParallelism > 0) {
            return maxParallelism;
        }
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }
}
