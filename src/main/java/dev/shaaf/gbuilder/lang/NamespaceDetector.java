package dev.shaaf.gbuilder.lang;

import dev.shaaf.gbuilder.graph.model.ClassNode;

import java.util.List;

/**
 * Detects deprecated or migration-target namespaces/imports in parsed code.
 * Java checks for javax.* → jakarta.*, Python might check six.* → native, etc.
 */
public interface NamespaceDetector {

    Language language();

    List<String> deprecatedNamespaces();

    boolean hasDeprecatedImports(ClassNode node);
}
