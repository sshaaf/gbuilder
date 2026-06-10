package dev.shaaf.gbuilder.analyzer;

import dev.shaaf.gbuilder.graph.model.AnnotationNode;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import dev.shaaf.gbuilder.graph.model.FieldNode;
import dev.shaaf.gbuilder.graph.model.MethodNode;
import dev.shaaf.gbuilder.graph.model.ParameterNode;

import java.util.List;
import java.util.Set;

public final class AnnotationMatching {

    private AnnotationMatching() {}

    public static boolean matchesAny(Set<String> ruleAnnotations, ClassNode classNode) {
        if (ruleAnnotations.isEmpty()) {
            return false;
        }
        if (matchesNodes(ruleAnnotations, classNode.annotationNodes())) {
            return true;
        }
        if (matchesStrings(ruleAnnotations, classNode.annotations())) {
            return true;
        }
        for (MethodNode method : classNode.methods()) {
            if (matchesNodes(ruleAnnotations, method.annotationNodes())) {
                return true;
            }
            if (matchesStrings(ruleAnnotations, method.annotations())) {
                return true;
            }
            for (ParameterNode parameter : method.parameters()) {
                if (matchesStrings(ruleAnnotations, parameter.annotations())) {
                    return true;
                }
            }
        }
        for (FieldNode field : classNode.fields()) {
            if (matchesStrings(ruleAnnotations, field.annotations())) {
                return true;
            }
        }
        for (var constant : classNode.enumConstants()) {
            if (matchesStrings(ruleAnnotations, constant.annotations())) {
                return true;
            }
        }
        for (var component : classNode.recordComponents()) {
            if (matchesStrings(ruleAnnotations, component.annotations())) {
                return true;
            }
        }
        return false;
    }

    public static String simpleName(String annotationText) {
        if (annotationText == null || annotationText.isBlank()) {
            return "";
        }
        String name = annotationText.trim();
        if (name.startsWith("@")) {
            name = name.substring(1);
        }
        int paren = name.indexOf('(');
        if (paren >= 0) {
            name = name.substring(0, paren).trim();
        }
        if (name.contains(".")) {
            name = name.substring(name.lastIndexOf('.') + 1);
        }
        return name;
    }

    private static boolean matchesNodes(Set<String> ruleAnnotations, List<AnnotationNode> annotationNodes) {
        for (AnnotationNode annotation : annotationNodes) {
            if (matchesRule(ruleAnnotations, annotation.name())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesStrings(Set<String> ruleAnnotations, List<String> annotations) {
        for (String annotation : annotations) {
            if (matchesRule(ruleAnnotations, simpleName(annotation)) || ruleAnnotations.contains(annotation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesRule(Set<String> ruleAnnotations, String simpleName) {
        if (ruleAnnotations.contains(simpleName)) {
            return true;
        }
        for (String rule : ruleAnnotations) {
            if (rule.endsWith("." + simpleName)) {
                return true;
            }
        }
        return false;
    }
}
