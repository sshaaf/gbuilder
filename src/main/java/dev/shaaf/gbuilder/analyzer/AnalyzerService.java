package dev.shaaf.gbuilder.analyzer;

import dev.shaaf.gbuilder.analyzer.model.AnalysisResult;
import dev.shaaf.gbuilder.analyzer.model.TechBOM;
import dev.shaaf.gbuilder.analyzer.model.TechEntry;
import dev.shaaf.gbuilder.analyzer.model.TechnologyNode;
import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@ApplicationScoped
public class AnalyzerService {

    private static final Logger LOG = Logger.getLogger(AnalyzerService.class);
    private static final int MAX_EXAMPLE_FQNS = 5;

    @Inject
    RuleLoader ruleLoader;

    @Inject
    GraphRepository graphRepo;

    public AnalysisResult analyze(List<ClassNode> classNodes) {
        Instant start = Instant.now();

        List<TechnologyRule> ruleList = ruleLoader.getAllRules();
        LOG.infof("Analyzer: running %d rules against %d class nodes", ruleList.size(), classNodes.size());

        Map<String, TechnologyNode> detectedTechnologies = new LinkedHashMap<>();
        Map<String, List<String>> techToFqns = new HashMap<>();
        Set<String> taggedFqns = new HashSet<>();

        for (ClassNode classNode : classNodes) {
            for (TechnologyRule rule : ruleList) {
                if (rule.matches(classNode)) {
                    String techId = rule.technologyId();
                    detectedTechnologies.computeIfAbsent(techId,
                            id -> new TechnologyNode(id, rule.technologyName(), rule.category()));
                    techToFqns.computeIfAbsent(techId, id -> new ArrayList<>())
                            .add(classNode.fullyQualifiedName());
                    taggedFqns.add(classNode.fullyQualifiedName());
                }
            }
        }

        for (TechnologyNode tech : detectedTechnologies.values()) {
            graphRepo.persistTechnologyNode(tech);
        }
        for (var entry : techToFqns.entrySet()) {
            for (String fqn : entry.getValue()) {
                graphRepo.createUsesEdge(fqn, entry.getKey());
            }
        }

        TechBOM techBom = buildTechBOM(detectedTechnologies, techToFqns);
        Duration elapsed = Duration.between(start, Instant.now());

        LOG.infof("Analyzer complete: %d technologies detected, %d classes tagged in %dms",
                detectedTechnologies.size(), taggedFqns.size(), elapsed.toMillis());

        return new AnalysisResult(techBom, ruleList.size(), taggedFqns.size(), elapsed);
    }

    private TechBOM buildTechBOM(Map<String, TechnologyNode> technologies,
                                  Map<String, List<String>> techToFqns) {
        List<TechEntry> entries = new ArrayList<>();
        Map<String, Long> categorySummary = new TreeMap<>();

        for (var tech : technologies.entrySet()) {
            List<String> fqns = techToFqns.getOrDefault(tech.getKey(), List.of());
            List<String> examples = fqns.size() <= MAX_EXAMPLE_FQNS
                    ? fqns
                    : fqns.subList(0, MAX_EXAMPLE_FQNS);

            entries.add(new TechEntry(
                    tech.getKey(),
                    tech.getValue().name(),
                    tech.getValue().category(),
                    fqns.size(),
                    examples
            ));

            categorySummary.merge(tech.getValue().category(), (long) fqns.size(), Long::sum);
        }

        return new TechBOM(entries, categorySummary, Instant.now());
    }
}
