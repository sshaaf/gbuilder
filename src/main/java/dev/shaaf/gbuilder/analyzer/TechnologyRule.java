package dev.shaaf.gbuilder.analyzer;

import dev.shaaf.gbuilder.graph.model.ClassNode;

/**
 * SPI for technology detection rules. Implementations are discovered via CDI.
 * Each rule identifies a specific technology by inspecting raw ClassNode data
 * (annotations, imports, superclass, interfaces).
 */
public interface TechnologyRule {

    String technologyId();

    String technologyName();

    String category();

    boolean matches(ClassNode classNode);
}
