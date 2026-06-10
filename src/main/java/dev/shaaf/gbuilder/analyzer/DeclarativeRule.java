package dev.shaaf.gbuilder.analyzer;

import dev.shaaf.gbuilder.graph.model.ClassNode;

import java.util.Set;

/**
 * A TechnologyRule driven entirely by a declarative {@link RuleDefinition}.
 * No Java code is needed per technology — only a JSON file.
 */
public class DeclarativeRule implements TechnologyRule {

    private final RuleDefinition definition;
    private final Set<String> annotationSet;
    private final Set<String> superClassSet;
    private final Set<String> interfaceSet;

    public DeclarativeRule(RuleDefinition definition) {
        this.definition = definition;
        this.annotationSet = Set.copyOf(definition.match().annotations());
        this.superClassSet = Set.copyOf(definition.match().superClasses());
        this.interfaceSet = Set.copyOf(definition.match().interfaces());
    }

    @Override
    public String technologyId() {
        return definition.id();
    }

    @Override
    public String technologyName() {
        return definition.name();
    }

    @Override
    public String category() {
        return definition.category();
    }

    @Override
    public boolean matches(ClassNode classNode) {
        if (matchesAnnotations(classNode)) return true;
        if (matchesImports(classNode)) return true;
        if (matchesSuperClass(classNode)) return true;
        if (matchesInterfaces(classNode)) return true;
        return false;
    }

    private boolean matchesAnnotations(ClassNode classNode) {
        return AnnotationMatching.matchesAny(annotationSet, classNode);
    }

    private boolean matchesImports(ClassNode classNode) {
        var prefixes = definition.match().importPrefixes();
        if (prefixes.isEmpty()) return false;
        for (String imp : classNode.imports()) {
            for (String prefix : prefixes) {
                if (imp.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesSuperClass(ClassNode classNode) {
        if (superClassSet.isEmpty()) return false;
        return classNode.superClass() != null && superClassSet.contains(classNode.superClass());
    }

    private boolean matchesInterfaces(ClassNode classNode) {
        if (interfaceSet.isEmpty()) return false;
        for (String iface : classNode.interfaces()) {
            if (interfaceSet.contains(iface)) {
                return true;
            }
        }
        return false;
    }

    public RuleDefinition getDefinition() {
        return definition;
    }

    @Override
    public String toString() {
        return "DeclarativeRule[" + definition.id() + "]";
    }
}
