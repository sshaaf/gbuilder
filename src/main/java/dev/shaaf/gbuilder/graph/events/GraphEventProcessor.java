package dev.shaaf.gbuilder.graph.events;

import dev.shaaf.gbuilder.graph.EmbeddingService;
import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.model.MigrationStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * CDI observer that processes graph mutation events fired by the Coder agent.
 * Keeps the embedded knowledge graph in sync as code is migrated.
 */
@ApplicationScoped
public class GraphEventProcessor {

    private static final Logger LOG = Logger.getLogger(GraphEventProcessor.class);

    @Inject
    GraphRepository graphRepo;

    @Inject
    EmbeddingService embeddingService;

    public void onNodeSplit(@Observes NodeSplitEvent event) {
        LOG.infof("Processing NodeSplitEvent: %s → %s", event.nodeId(), event.newNodeIds());
        graphRepo.deleteNodeAndEdges(event.nodeId());
    }

    public void onSignatureChanged(@Observes SignatureChangedEvent event) {
        LOG.infof("Processing SignatureChangedEvent: %s → %s",
                event.oldSignature(), event.newSignature());

        String className = extractClassName(event.nodeId());
        if (className != null) {
            graphRepo.updateMethodSignature(className, event.oldSignature(), event.newSignature());
        }
    }

    public void onNodeMigrated(@Observes NodeMigratedEvent event) {
        LOG.infof("Processing NodeMigratedEvent: %s → %s", event.nodeId(), event.newFilePath());
        graphRepo.updateNodeStatus(event.nodeId(), MigrationStatus.MIGRATED);
    }

    private String extractClassName(String nodeId) {
        if (nodeId == null) return null;
        int hashIndex = nodeId.indexOf('#');
        return hashIndex > 0 ? nodeId.substring(0, hashIndex) : nodeId;
    }
}
