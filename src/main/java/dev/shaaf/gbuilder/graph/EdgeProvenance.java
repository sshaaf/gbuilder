package dev.shaaf.gbuilder.graph;

public enum EdgeProvenance {
    EXTRACTED,
    INFERRED,
    AMBIGUOUS;

    public static EdgeProvenance fromString(String value) {
        if (value == null || value.isBlank()) {
            return EXTRACTED;
        }
        return EdgeProvenance.valueOf(value.toUpperCase());
    }
}
