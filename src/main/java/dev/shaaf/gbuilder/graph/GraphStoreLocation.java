package dev.shaaf.gbuilder.graph;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class GraphStoreLocation {

    @ConfigProperty(name = "gbuilder.graph.store-path", defaultValue = ".gbuilder/graph.db")
    String configuredStorePath;

    private Path activeStorePath;

    public Path resolveForCodebase(Path codebaseRoot) {
        Path storePath = codebaseRoot.resolve(".gbuilder").resolve("graph.db");
        this.activeStorePath = storePath;
        return storePath;
    }

    public Path resolveDefault() {
        if (activeStorePath != null) {
            return activeStorePath;
        }
        Path path = Path.of(configuredStorePath);
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir")).resolve(path);
        }
        this.activeStorePath = path;
        return path;
    }

    public Path current() {
        return activeStorePath != null ? activeStorePath : resolveDefault();
    }

    public void reset() {
        activeStorePath = null;
    }

    public void ensureParentDirectory(Path dbPath) {
        try {
            Path parent = dbPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create graph store directory: " + dbPath, e);
        }
    }
}
