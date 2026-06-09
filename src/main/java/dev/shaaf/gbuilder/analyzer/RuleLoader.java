package dev.shaaf.gbuilder.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads technology detection rules from three tiers:
 * <ol>
 *   <li>Shipped defaults — JSON files in classpath {@code rules/} directory</li>
 *   <li>User-supplied — JSON files in a configurable file system directory</li>
 *   <li>CDI beans — any {@link TechnologyRule} registered as a CDI bean (escape hatch)</li>
 * </ol>
 * User-supplied rules with the same {@code id} override shipped defaults.
 * CDI beans always participate (they cannot be overridden by JSON).
 */
@ApplicationScoped
public class RuleLoader {

    private static final Logger LOG = Logger.getLogger(RuleLoader.class);
    private static final String CLASSPATH_RULES_DIR = "rules";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ConfigProperty(name = "gbuilder.analyzer.rules-dir")
    Optional<String> userRulesDir;

    @Inject
    @Any
    Instance<TechnologyRule> cdiRules;

    private List<TechnologyRule> allRules;

    @PostConstruct
    void init() {
        Map<String, TechnologyRule> ruleMap = new LinkedHashMap<>();

        List<RuleDefinition> shipped = loadClasspathRules();
        for (RuleDefinition def : shipped) {
            ruleMap.put(def.id(), new DeclarativeRule(def));
        }
        LOG.infof("Loaded %d shipped rule(s) from classpath", shipped.size());

        if (userRulesDir.isPresent() && !userRulesDir.get().isBlank()) {
            Path dir = Path.of(userRulesDir.get());
            if (Files.isDirectory(dir)) {
                List<RuleDefinition> userDefs = loadDirectoryRules(dir);
                for (RuleDefinition def : userDefs) {
                    ruleMap.put(def.id(), new DeclarativeRule(def));
                }
                LOG.infof("Loaded %d user rule(s) from %s (overrides: %s)", userDefs.size(), dir,
                        userDefs.stream().map(RuleDefinition::id)
                                .filter(id -> shipped.stream().anyMatch(s -> s.id().equals(id)))
                                .toList());
            } else {
                LOG.warnf("User rules directory does not exist: %s", dir);
            }
        }

        List<TechnologyRule> cdiBeans = new ArrayList<>();
        for (TechnologyRule bean : cdiRules) {
            if (!(bean instanceof DeclarativeRule)) {
                cdiBeans.add(bean);
                LOG.infof("Registered CDI rule: %s (%s)", bean.technologyId(), bean.getClass().getSimpleName());
            }
        }

        allRules = new ArrayList<>(ruleMap.values());
        allRules.addAll(cdiBeans);

        LOG.infof("Total rules available: %d (declarative: %d, CDI: %d)",
                allRules.size(), ruleMap.size(), cdiBeans.size());
    }

    public List<TechnologyRule> getAllRules() {
        return Collections.unmodifiableList(allRules);
    }

    /**
     * Register a rule at runtime (e.g., LLM-generated).
     * If a rule with the same id exists, it is replaced.
     */
    public void registerRule(RuleDefinition definition) {
        allRules.removeIf(r -> r.technologyId().equals(definition.id()));
        allRules.add(new DeclarativeRule(definition));
        LOG.infof("Registered dynamic rule: %s", definition.id());
    }

    List<RuleDefinition> loadClasspathRules() {
        List<RuleDefinition> definitions = new ArrayList<>();
        try {
            Enumeration<URL> resources = Thread.currentThread()
                    .getContextClassLoader()
                    .getResources(CLASSPATH_RULES_DIR);

            while (resources.hasMoreElements()) {
                URL dirUrl = resources.nextElement();
                definitions.addAll(loadRulesFromUrl(dirUrl));
            }
        } catch (IOException | URISyntaxException e) {
            LOG.warnf("Failed to load classpath rules: %s", e.getMessage());
        }
        return definitions;
    }

    private List<RuleDefinition> loadRulesFromUrl(URL dirUrl) throws IOException, URISyntaxException {
        List<RuleDefinition> defs = new ArrayList<>();
        URI uri = dirUrl.toURI();

        if ("file".equals(uri.getScheme())) {
            Path dirPath = Path.of(uri);
            defs.addAll(loadDirectoryRules(dirPath));
        } else if ("jar".equals(uri.getScheme())) {
            try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
                Path dirPath = fs.getPath("/" + CLASSPATH_RULES_DIR);
                defs.addAll(loadDirectoryRules(dirPath));
            }
        } else {
            // Fallback: try loading known rule files individually
            defs.addAll(loadKnownRulesFromClasspath());
        }
        return defs;
    }

    private List<RuleDefinition> loadKnownRulesFromClasspath() {
        List<RuleDefinition> defs = new ArrayList<>();
        String[] knownFiles = {"ejb", "jpa", "jaxrs", "servlet", "cdi", "jms", "jaxws", "jsf"};
        for (String name : knownFiles) {
            String resource = CLASSPATH_RULES_DIR + "/" + name + ".json";
            try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
                if (is != null) {
                    defs.add(MAPPER.readValue(is, RuleDefinition.class));
                }
            } catch (IOException e) {
                LOG.warnf("Failed to parse classpath rule %s: %s", resource, e.getMessage());
            }
        }
        return defs;
    }

    private List<RuleDefinition> loadDirectoryRules(Path dir) {
        List<RuleDefinition> defs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                try (InputStream is = Files.newInputStream(file)) {
                    defs.add(MAPPER.readValue(is, RuleDefinition.class));
                } catch (IOException e) {
                    LOG.warnf("Failed to parse rule file %s: %s", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOG.warnf("Failed to read rules directory %s: %s", dir, e.getMessage());
        }
        return defs;
    }
}
