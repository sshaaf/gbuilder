package dev.shaaf.gbuilder.lang;

import dev.shaaf.gbuilder.graph.model.ClassNode;
import dev.shaaf.gbuilder.lang.java.JavaParserExtractor;
import dev.shaaf.gbuilder.lang.java.JavaTreeSitterExtractor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ParserFacade {

    @Inject
    JavaParserExtractor javaParserExtractor;

    @Inject
    JavaTreeSitterExtractor treeSitterExtractor;

    @ConfigProperty(name = "gbuilder.parser.backend", defaultValue = "jparser")
    String defaultBackend;

    public List<ClassNode> extract(Path sourceRoot) {
        return extract(sourceRoot, ParserBackend.fromCliValue(defaultBackend));
    }

    public List<ClassNode> extract(Path sourceRoot, ParserBackend backend) {
        return select(backend).extract(sourceRoot);
    }

    public List<ClassNode> extractFiles(Path sourceRoot, List<Path> files, ParserBackend backend) throws IOException {
        ClassNodeExtractor extractor = select(backend);
        List<ClassNode> nodes = new ArrayList<>();
        for (Path file : files) {
            nodes.addAll(extractor.extractFile(file));
        }
        return nodes;
    }

    public ClassNodeExtractor select(ParserBackend backend) {
        return switch (backend) {
            case JPARSER -> javaParserExtractor;
            case TREESITTER -> treeSitterExtractor;
        };
    }
}
