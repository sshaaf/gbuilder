package dev.shaaf.gbuilder.graph.events;

import java.time.Instant;
import java.util.List;

public record NodeSplitEvent(
    String nodeId,
    List<String> newNodeIds,
    Instant timestamp
) implements GraphMutationEvent {}
