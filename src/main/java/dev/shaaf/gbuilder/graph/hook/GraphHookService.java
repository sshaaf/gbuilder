package dev.shaaf.gbuilder.graph.hook;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@ApplicationScoped
public class GraphHookService {

    private static final String MARKER = "# gbuilder graph hook";

    public void install(Path codebaseRoot) throws IOException {
        Path hooks = codebaseRoot.resolve(".git").resolve("hooks").resolve("post-commit");
        String script = """
                #!/bin/sh
                %s
                gbuilder -p "$(git rev-parse --show-toplevel)" --update 2>/dev/null || true
                """.formatted(MARKER);
        if (Files.exists(hooks)) {
            String existing = Files.readString(hooks);
            if (existing.contains(MARKER)) {
                return;
            }
            Files.writeString(hooks, existing + "\n" + script, StandardOpenOption.APPEND);
        } else {
            Files.writeString(hooks, "#!/bin/sh\n" + script);
        }
        hooks.toFile().setExecutable(true);
    }

    public void uninstall(Path codebaseRoot) throws IOException {
        Path hooks = codebaseRoot.resolve(".git").resolve("hooks").resolve("post-commit");
        if (!Files.exists(hooks)) {
            return;
        }
        String content = Files.readString(hooks);
        int idx = content.indexOf(MARKER);
        if (idx >= 0) {
            String cleaned = content.substring(0, idx).trim() + "\n";
            if (cleaned.isBlank() || cleaned.equals("\n")) {
                Files.deleteIfExists(hooks);
            } else {
                Files.writeString(hooks, cleaned);
            }
        }
    }

    public HookStatus status(Path codebaseRoot) {
        Path hooks = codebaseRoot.resolve(".git").resolve("hooks").resolve("post-commit");
        if (!Files.exists(hooks)) {
            return HookStatus.NOT_INSTALLED;
        }
        try {
            return Files.readString(hooks).contains(MARKER)
                    ? HookStatus.INSTALLED : HookStatus.NOT_INSTALLED;
        } catch (IOException e) {
            return HookStatus.NOT_INSTALLED;
        }
    }

    public enum HookStatus { INSTALLED, NOT_INSTALLED }
}
