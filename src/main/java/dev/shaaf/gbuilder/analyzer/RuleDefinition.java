package dev.shaaf.gbuilder.analyzer;

import java.util.List;

/**
 * Declarative technology rule definition, loaded from JSON files.
 * A class matches if ANY condition in the match block is satisfied.
 */
public record RuleDefinition(
    String id,
    String name,
    String category,
    MatchCriteria match
) {
    public record MatchCriteria(
        List<String> annotations,
        List<String> importPrefixes,
        List<String> superClasses,
        List<String> interfaces
    ) {
        public MatchCriteria {
            annotations = annotations != null ? annotations : List.of();
            importPrefixes = importPrefixes != null ? importPrefixes : List.of();
            superClasses = superClasses != null ? superClasses : List.of();
            interfaces = interfaces != null ? interfaces : List.of();
        }
    }
}
