package dev.shaaf.gbuilder.lang.java;

import io.roastedroot.treesitter.Language;
import io.roastedroot.treesitter.TreeSitter;
import io.roastedroot.treesitter.TreeSitterNode;
import io.roastedroot.treesitter.TreeSitterParser;
import io.roastedroot.treesitter.TreeSitterQuery;
import io.roastedroot.treesitter.TreeSitterTree;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TreeSitterSession {

    private final TreeSitter treeSitter = TreeSitter.create();

    public ParseResult parseJava(String source) {
        TreeSitterParser parser = treeSitter.newParser();
        parser.setLanguage(Language.JAVA);
        TreeSitterTree tree = parser.parseString(source);
        return new ParseResult(parser, tree, tree.rootNode(), source);
    }

    public TreeSitterQuery newQuery(String pattern) {
        return treeSitter.newQuery(Language.JAVA, pattern);
    }

    @PreDestroy
    void shutdown() {
        treeSitter.close();
    }

    public record ParseResult(
            TreeSitterParser parser,
            TreeSitterTree tree,
            TreeSitterNode root,
            String source
    ) implements AutoCloseable {
        @Override
        public void close() {
            tree.close();
            parser.close();
        }
    }
}
