package dev.shaaf.gbuilder.graph.events;

import java.time.Instant;

public sealed interface GraphMutationEvent permits
        NodeSplitEvent, SignatureChangedEvent, NodeMigratedEvent {
    String nodeId();
    Instant timestamp();
}
