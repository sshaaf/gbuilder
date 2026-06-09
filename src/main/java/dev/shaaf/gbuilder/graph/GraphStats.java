package dev.shaaf.gbuilder.graph;

public record GraphStats(
        long typeCount,
        long externalTypeCount,
        long methodCount,
        long containsEdges,
        long callsEdges,
        long dependsOnEdges,
        long extendsEdges,
        long implementsEdges,
        long technologyCount,
        long usesEdges,
        int communityCount
) {}
