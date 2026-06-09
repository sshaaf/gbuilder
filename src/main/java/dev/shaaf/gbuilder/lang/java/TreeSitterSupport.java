package dev.shaaf.gbuilder.lang.java;

import io.roastedroot.treesitter.TreeSitterNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class TreeSitterSupport {

    private TreeSitterSupport() {}

    public static String nodeText(String source, TreeSitterNode node) {
        if (node == null || node.startByte() < 0 || node.endByte() > source.length()) {
            return "";
        }
        return source.substring(node.startByte(), node.endByte());
    }

    public static String typeDeclarationName(String source, TreeSitterNode node) {
        TreeSitterNode nameNode = findFirstNamedChildOfTypes(node, "identifier", "type_identifier");
        return nameNode == null ? "" : nodeText(source, nameNode).trim();
    }

    public static String methodNameText(String source, TreeSitterNode methodNode) {
        for (int i = 0; i < methodNode.namedChildCount(); i++) {
            TreeSitterNode child = methodNode.namedChild(i);
            if (child != null && "identifier".equals(child.type())) {
                return nodeText(source, child).trim();
            }
        }
        return "";
    }

    public static String identifierText(String source, TreeSitterNode node) {
        return typeDeclarationName(source, node);
    }

    public static String parameterNameText(String source, TreeSitterNode paramNode) {
        for (int i = paramNode.namedChildCount() - 1; i >= 0; i--) {
            TreeSitterNode child = paramNode.namedChild(i);
            if (child != null && "identifier".equals(child.type())) {
                return nodeText(source, child).trim();
            }
        }
        return "";
    }

    public static String fieldText(String source, TreeSitterNode node, String fieldName) {
        return switch (fieldName) {
            case "body" -> {
                TreeSitterNode body = findBody(node);
                yield body == null ? "" : nodeText(source, body).trim();
            }
            case "name" -> identifierText(source, node);
            case "parameters" -> {
                TreeSitterNode params = findFirstNamedChildOfTypes(node, "formal_parameters");
                yield params == null ? "" : nodeText(source, params).trim();
            }
            case "type" -> {
                TreeSitterNode type = findReturnType(node);
                yield type == null ? "" : nodeText(source, type).trim();
            }
            default -> "";
        };
    }

    public static TreeSitterNode findBody(TreeSitterNode typeNode) {
        return findFirstNamedChildOfTypes(typeNode,
                "class_body", "interface_body", "enum_body", "annotation_body", "block");
    }

    public static TreeSitterNode findReturnType(TreeSitterNode methodNode) {
        for (int i = 0; i < methodNode.namedChildCount(); i++) {
            TreeSitterNode child = methodNode.namedChild(i);
            if (child == null) {
                continue;
            }
            String type = child.type();
            if ("void_type".equals(type) || "type_identifier".equals(type)
                    || "generic_type".equals(type) || "scoped_type_identifier".equals(type)
                    || "integral_type".equals(type) || "floating_point_type".equals(type)
                    || "boolean_type".equals(type)) {
                return child;
            }
        }
        return null;
    }

    public static List<TreeSitterNode> childrenOfType(TreeSitterNode parent, String type) {
        List<TreeSitterNode> matches = new ArrayList<>();
        collectNodes(parent, node -> type.equals(node.type()), matches);
        return matches;
    }

    public static TreeSitterNode findFirstChild(TreeSitterNode parent, String type) {
        List<TreeSitterNode> matches = childrenOfType(parent, type);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    public static TreeSitterNode findFirstNamedChildOfTypes(TreeSitterNode parent, String... types) {
        for (int i = 0; i < parent.namedChildCount(); i++) {
            TreeSitterNode child = parent.namedChild(i);
            if (child == null) {
                continue;
            }
            for (String type : types) {
                if (type.equals(child.type())) {
                    return child;
                }
            }
        }
        return null;
    }

    public static void collectNodes(TreeSitterNode node, Predicate<TreeSitterNode> predicate, List<TreeSitterNode> out) {
        if (predicate.test(node)) {
            out.add(node);
        }
        for (int i = 0; i < node.namedChildCount(); i++) {
            TreeSitterNode child = node.namedChild(i);
            if (child != null) {
                collectNodes(child, predicate, out);
            }
        }
    }

    public static List<String> extractModifiers(String source, TreeSitterNode node) {
        TreeSitterNode modifiersNode = findFirstNamedChildOfTypes(node, "modifiers");
        return modifiersNode == null ? List.of() : parseModifierNode(source, modifiersNode);
    }

    public static List<String> parseModifierNode(String source, TreeSitterNode modifiersNode) {
        List<String> modifiers = new ArrayList<>();
        for (int i = 0; i < modifiersNode.namedChildCount(); i++) {
            TreeSitterNode child = modifiersNode.namedChild(i);
            if (child != null) {
                modifiers.add(nodeText(source, child).trim());
            }
        }
        if (!modifiers.isEmpty()) {
            return modifiers;
        }
        String text = nodeText(source, modifiersNode).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        return List.of(text.split("\\s+"));
    }

    public static List<String> extractAnnotations(String source, TreeSitterNode node) {
        List<String> annotations = new ArrayList<>();
        List<TreeSitterNode> annotationNodes = new ArrayList<>();
        collectNodes(node, n -> "annotation".equals(n.type())
                || "marker_annotation".equals(n.type()), annotationNodes);
        for (TreeSitterNode annotationNode : annotationNodes) {
            annotations.add(nodeText(source, annotationNode).trim());
        }
        return annotations;
    }

    public static List<String> extractMethodInvocations(String source, TreeSitterNode methodNode) {
        List<String> calls = new ArrayList<>();
        TreeSitterNode body = findBody(methodNode);
        if (body == null) {
            return calls;
        }
        List<TreeSitterNode> invocations = new ArrayList<>();
        collectNodes(body, n -> "method_invocation".equals(n.type()), invocations);
        for (TreeSitterNode invocation : invocations) {
            TreeSitterNode nameNode = findInvocationName(invocation);
            if (nameNode != null) {
                calls.add(nodeText(source, nameNode).trim());
            }
        }
        return calls;
    }

    private static TreeSitterNode findInvocationName(TreeSitterNode invocation) {
        for (int i = invocation.namedChildCount() - 1; i >= 0; i--) {
            TreeSitterNode child = invocation.namedChild(i);
            if (child != null && "identifier".equals(child.type())) {
                return child;
            }
        }
        return null;
    }
}
