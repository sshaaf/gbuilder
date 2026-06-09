package dev.shaaf.gbuilder.graph.events;

import java.time.Instant;

public record NodeMigratedEvent(
    String nodeId,
    String newFilePath,
    Instant timestamp
) implements GraphMutationEvent {}
