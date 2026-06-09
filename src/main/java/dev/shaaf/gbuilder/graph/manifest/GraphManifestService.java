package dev.shaaf.gbuilder.graph.manifest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.shaaf.gbuilder.lang.java.JavaSourceScanner;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class GraphManifestService {

    private static final String MANIFEST_FILE = "manifest.json";

    @Inject
    ObjectMapper objectMapper;

    public Path manifestPath(Path codebaseRoot) {
        return codebaseRoot.resolve(".gbuilder").resolve(MANIFEST_FILE);
    }

    public Map<String, String> loadManifest(Path codebaseRoot) throws IOException {
        Path path = manifestPath(codebaseRoot);
        if (!Files.exists(path)) {
            return Map.of();
        }
        return objectMapper.readValue(path.toFile(), new TypeReference<LinkedHashMap<String, String>>() {});
    }

    public void saveManifest(Path codebaseRoot, Map<String, String> manifest) throws IOException {
        Path path = manifestPath(codebaseRoot);
        Files.createDirectories(path.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), manifest);
    }

    public Map<String, String> scanCurrentHashes(Path codebaseRoot) throws IOException {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (Path file : JavaSourceScanner.scanJavaFiles(codebaseRoot)) {
            String relative = codebaseRoot.relativize(file).toString().replace('\\', '/');
            hashes.put(relative, sha256(file));
        }
        return hashes;
    }

    public ManifestDiff diff(Path codebaseRoot) throws IOException {
        Map<String, String> previous = loadManifest(codebaseRoot);
        Map<String, String> current = scanCurrentHashes(codebaseRoot);

        Set<String> previousKeys = previous.keySet();
        Set<String> currentKeys = current.keySet();

        List<String> added = currentKeys.stream().filter(k -> !previousKeys.contains(k)).sorted().toList();
        List<String> deleted = previousKeys.stream().filter(k -> !currentKeys.contains(k)).sorted().toList();
        List<String> changed = currentKeys.stream()
                .filter(previousKeys::contains)
                .filter(k -> !current.get(k).equals(previous.get(k)))
                .sorted()
                .toList();
        List<String> unchanged = currentKeys.stream()
                .filter(k -> previousKeys.contains(k) && current.get(k).equals(previous.get(k)))
                .sorted()
                .toList();

        return new ManifestDiff(added, changed, deleted, unchanged, current);
    }

    public List<Path> filesToParse(Path codebaseRoot, ManifestDiff diff) {
        return diff.addedAndChanged().stream()
                .map(rel -> codebaseRoot.resolve(rel))
                .collect(Collectors.toList());
    }

    public String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IOException("Failed to hash " + file, e);
        }
    }

    public record ManifestDiff(
            List<String> added,
            List<String> changed,
            List<String> deleted,
            List<String> unchanged,
            Map<String, String> currentHashes
    ) {
        public List<String> addedAndChanged() {
            return java.util.stream.Stream.concat(added.stream(), changed.stream()).sorted().toList();
        }

        public boolean hasChanges() {
            return !added.isEmpty() || !changed.isEmpty() || !deleted.isEmpty();
        }
    }
}
