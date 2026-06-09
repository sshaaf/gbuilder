package dev.shaaf.gbuilder.lang;

public enum ParserBackend {

    JPARSER("jparser"),
    TREESITTER("treesitter");

    private final String cliValue;

    ParserBackend(String cliValue) {
        this.cliValue = cliValue;
    }

    public String cliValue() {
        return cliValue;
    }

    public static ParserBackend fromCliValue(String value) {
        if (value == null || value.isBlank()) {
            return JPARSER;
        }
        String normalized = value.trim().toLowerCase();
        for (ParserBackend backend : values()) {
            if (backend.cliValue.equals(normalized)) {
                return backend;
            }
        }
        throw new IllegalArgumentException(
                "Unknown parser backend: " + value + ". Supported values: jparser, treesitter");
    }
}
