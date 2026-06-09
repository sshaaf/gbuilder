package dev.shaaf.gbuilder.graph.diff;

import java.util.List;

public record GraphDiffResult(
        long typesBefore,
        long typesAfter,
        long edgesBefore,
        long edgesAfter,
        List<String> addedTypes,
        List<String> removedTypes,
        long addedEdges,
        long removedEdges,
        String summary
) {
    public long typeDelta() {
        return typesAfter - typesBefore;
    }

    public long edgeDelta() {
        return edgesAfter - edgesBefore;
    }
}
