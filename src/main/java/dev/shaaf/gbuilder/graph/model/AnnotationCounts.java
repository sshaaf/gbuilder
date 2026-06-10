package dev.shaaf.gbuilder.graph.model;

public final class AnnotationCounts {

    private AnnotationCounts() {}

    public static int totalForClass(ClassNode node) {
        int count = node.annotations().size();
        for (MethodNode method : node.methods()) {
            count += method.annotations().size();
            for (ParameterNode parameter : method.parameters()) {
                count += parameter.annotations().size();
            }
        }
        for (FieldNode field : node.fields()) {
            count += field.annotations().size();
        }
        for (EnumConstantNode constant : node.enumConstants()) {
            count += constant.annotations().size();
        }
        for (RecordComponentNode component : node.recordComponents()) {
            count += component.annotations().size();
        }
        return count;
    }
}
