package dev.shaaf.gbuilder.lang.java;

import io.roastedroot.treesitter.TreeSitterQuery;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class JavaTreeSitterQueries {

    static final String METHOD_DECLARATION = """
            (method_declaration
              (modifiers)? @modifiers
              type: (_) @return_type
              name: (identifier) @method_name)
            """;

    static final String CONSTRUCTOR_DECLARATION = """
            (constructor_declaration
              (modifiers)? @modifiers
              name: (identifier) @ctor_name)
            """;

    static final String FIELD_DECLARATION = """
            (field_declaration
              (modifiers)? @modifiers
              declarator: (variable_declarator name: (identifier) @field_name))
            """;

    static final String METHOD_INVOCATION = """
            (method_invocation name: (identifier) @call_name)
            """;

    @Inject
    TreeSitterSession session;

    private TreeSitterQuery methodDeclaration;
    private TreeSitterQuery constructorDeclaration;
    private TreeSitterQuery fieldDeclaration;
    private TreeSitterQuery methodInvocation;

    @PostConstruct
    void init() {
        methodDeclaration = session.newQuery(METHOD_DECLARATION);
        constructorDeclaration = session.newQuery(CONSTRUCTOR_DECLARATION);
        fieldDeclaration = session.newQuery(FIELD_DECLARATION);
        methodInvocation = session.newQuery(METHOD_INVOCATION);
    }

    @PreDestroy
    void close() {
        closeQuietly(methodDeclaration);
        closeQuietly(constructorDeclaration);
        closeQuietly(fieldDeclaration);
        closeQuietly(methodInvocation);
    }

    public TreeSitterQuery methodDeclaration() {
        return methodDeclaration;
    }

    public TreeSitterQuery constructorDeclaration() {
        return constructorDeclaration;
    }

    public TreeSitterQuery fieldDeclaration() {
        return fieldDeclaration;
    }

    public TreeSitterQuery methodInvocation() {
        return methodInvocation;
    }

    private static void closeQuietly(TreeSitterQuery query) {
        if (query != null) {
            query.close();
        }
    }
}
