package dev.shaaf.gbuilder.graph;

public final class GraphNodeIds {

    private GraphNodeIds() {}

    public static String classId(String fqn) {
        return "class:" + fqn;
    }

    public static String methodId(String classFqn, String signature) {
        return "method:" + classFqn + "#" + signature;
    }

    public static String technologyId(String techId) {
        return "tech:" + techId;
    }

    public static String fqnFromClassId(String nodeId) {
        return nodeId.substring("class:".length());
    }

    public static String classFqnFromMethodId(String nodeId) {
        String body = nodeId.substring("method:".length());
        int hash = body.indexOf('#');
        return hash > 0 ? body.substring(0, hash) : body;
    }

    public static String signatureFromMethodId(String nodeId) {
        String body = nodeId.substring("method:".length());
        int hash = body.indexOf('#');
        return hash > 0 ? body.substring(hash + 1) : "";
    }
}
