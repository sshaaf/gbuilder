package dev.shaaf.gbuilder.graph.detection;

import dev.shaaf.gbuilder.lang.Language;
import dev.shaaf.gbuilder.lang.NamespaceDetector;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class JavaNamespaceDetector implements NamespaceDetector {

    private static final List<String> DEPRECATED = List.of("javax.");

    private static final List<String> EJB_ANNOTATIONS = List.of(
            "@Stateless", "@Stateful", "@Singleton", "@MessageDriven"
    );

    public record DeprecatedNamespaceHit(
        String filePath,
        String importValue,
        String classFqn
    ) {}

    @Override
    public Language language() {
        return Language.JAVA;
    }

    @Override
    public List<String> deprecatedNamespaces() {
        return DEPRECATED;
    }

    @Override
    public boolean hasDeprecatedImports(ClassNode node) {
        return node.imports().stream()
                .anyMatch(imp -> imp.startsWith("javax."));
    }

    public List<DeprecatedNamespaceHit> detectDeprecatedNamespaces(List<ClassNode> nodes) {
        List<DeprecatedNamespaceHit> hits = new ArrayList<>();
        for (ClassNode node : nodes) {
            for (String imp : node.imports()) {
                if (imp.startsWith("javax.")) {
                    hits.add(new DeprecatedNamespaceHit(
                            node.filePath(), imp, node.fullyQualifiedName()
                    ));
                }
            }
        }
        return hits;
    }

    public List<ClassNode> detectEjbBoundaries(List<ClassNode> nodes) {
        return nodes.stream()
                .filter(n -> n.annotations().stream()
                        .anyMatch(a -> EJB_ANNOTATIONS.stream()
                                .anyMatch(a::contains)))
                .toList();
    }
}
