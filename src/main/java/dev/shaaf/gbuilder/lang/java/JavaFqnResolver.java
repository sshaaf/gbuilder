package dev.shaaf.gbuilder.lang.java;

import dev.shaaf.gbuilder.graph.model.ImportNode;

import java.util.ArrayList;
import java.util.List;

public final class JavaFqnResolver {

    private JavaFqnResolver() {}

    public static String buildFqn(String packageName, List<String> enclosingTypeNames, String simpleName) {
        List<String> parts = new ArrayList<>(enclosingTypeNames);
        parts.add(simpleName);
        String typePath = String.join(".", parts);
        return packageName == null || packageName.isEmpty() ? typePath : packageName + "." + typePath;
    }

    public static String resolveToFqn(String simpleName, List<ImportNode> importNodes, String packageName) {
        for (ImportNode imp : importNodes) {
            if (!imp.isWildcard() && !imp.isStatic() && imp.name().endsWith("." + simpleName)) {
                return imp.name();
            }
        }

        if (isJavaLangType(simpleName)) {
            return "java.lang." + simpleName;
        }

        for (ImportNode imp : importNodes) {
            if (imp.isWildcard() && !imp.isStatic()) {
                return imp.name() + "." + simpleName;
            }
        }

        if (packageName != null && !packageName.isEmpty()) {
            return packageName + "." + simpleName;
        }

        return simpleName;
    }

    public static boolean isJavaLangType(String simpleName) {
        try {
            Class.forName("java.lang." + simpleName, false, ClassLoader.getSystemClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
