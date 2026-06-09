package dev.shaaf.gbuilder.lang;

public enum Language {

    JAVA("java", "Java", ".java"),
    KOTLIN("kotlin", "Kotlin", ".kt"),
    PYTHON("python", "Python", ".py"),
    GO("go", "Go", ".go"),
    CSHARP("csharp", "C#", ".cs"),
    RUST("rust", "Rust", ".rs");

    private final String id;
    private final String displayName;
    private final String fileExtension;

    Language(String id, String displayName, String fileExtension) {
        this.id = id;
        this.displayName = displayName;
        this.fileExtension = fileExtension;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String fileExtension() { return fileExtension; }

    public static Language fromFileExtension(String ext) {
        for (Language lang : values()) {
            if (lang.fileExtension.equals(ext)) return lang;
        }
        throw new IllegalArgumentException("Unsupported file extension: " + ext);
    }
}
