package dev.shaaf.gbuilder.lang;

import dev.shaaf.gbuilder.graph.model.ClassNode;

import java.nio.file.Path;
import java.util.List;

public interface ClassNodeExtractor {

    ParserBackend backend();

    Language language();

    List<ClassNode> extract(Path sourceRoot);

    List<ClassNode> extractFile(Path filePath);
}
