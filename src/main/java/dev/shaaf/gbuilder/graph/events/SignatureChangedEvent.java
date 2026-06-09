package dev.shaaf.gbuilder.graph.events;

import java.time.Instant;

public record SignatureChangedEvent(
    String nodeId,
    String oldSignature,
    String newSignature,
    Instant timestamp
) implements GraphMutationEvent {}
