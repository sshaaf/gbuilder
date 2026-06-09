package dev.shaaf.gbuilder.lang.java;

import io.roastedroot.treesitter.TreeSitterNode;
import io.roastedroot.treesitter.TreeSitterQuery;
import io.roastedroot.treesitter.TreeSitterQueryResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TreeSitterQuerySupport {

    private TreeSitterQuerySupport() {}

    public static Map<String, TreeSitterNode> firstCaptureNodes(
            TreeSitterQuery query, TreeSitterNode scope, String source) {
        Map<String, TreeSitterNode> captures = new LinkedHashMap<>();
        for (TreeSitterQueryResult result : query.exec(scope, source)) {
            captures.putIfAbsent(result.name(), result.node());
        }
        return captures;
    }

    public static List<String> captureTexts(
            TreeSitterQuery query, TreeSitterNode scope, String source, String captureName) {
        List<String> texts = new ArrayList<>();
        for (TreeSitterQueryResult result : query.exec(scope, source)) {
            if (captureName.equals(result.name())) {
                texts.add(TreeSitterSupport.nodeText(source, result.node()).trim());
            }
        }
        return texts;
    }

    public static Optional<TreeSitterNode> firstCapture(
            Map<String, TreeSitterNode> captures, String name) {
        return Optional.ofNullable(captures.get(name));
    }

    public static List<String> modifiersFromCapture(
            String source, Map<String, TreeSitterNode> captures, String captureName) {
        return firstCapture(captures, captureName)
                .map(node -> TreeSitterSupport.parseModifierNode(source, node))
                .orElseGet(List::of);
    }

    public static List<TreeSitterNode> typeBodyMembers(TreeSitterNode body, String type) {
        List<TreeSitterNode> matches = new ArrayList<>(directChildrenOfType(body, type));
        TreeSitterNode enumDeclarations = TreeSitterSupport.findFirstNamedChildOfTypes(
                body, "enum_body_declarations");
        if (enumDeclarations != null) {
            matches.addAll(directChildrenOfType(enumDeclarations, type));
        }
        return matches;
    }

    public static List<TreeSitterNode> directChildrenOfType(TreeSitterNode parent, String type) {
        List<TreeSitterNode> matches = new ArrayList<>();
        for (int i = 0; i < parent.namedChildCount(); i++) {
            TreeSitterNode child = parent.namedChild(i);
            if (child != null && type.equals(child.type())) {
                matches.add(child);
            }
        }
        return matches;
    }
}
