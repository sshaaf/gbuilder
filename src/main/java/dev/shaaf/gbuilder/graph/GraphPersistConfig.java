package dev.shaaf.gbuilder.graph;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class GraphPersistConfig {

    @ConfigProperty(name = "gbuilder.graph.persist-batch-size", defaultValue = "50")
    int persistBatchSize;

    @ConfigProperty(name = "gbuilder.graph.edge-batch-size", defaultValue = "500")
    int edgeBatchSize;

    public int persistBatchSize() {
        return Math.max(1, persistBatchSize);
    }

    public int edgeBatchSize() {
        return Math.max(1, edgeBatchSize);
    }
}
