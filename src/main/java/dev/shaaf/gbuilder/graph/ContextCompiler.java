package dev.shaaf.gbuilder.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import dev.shaaf.gbuilder.graph.model.FieldNode;
import dev.shaaf.gbuilder.graph.model.MethodNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class ContextCompiler {

    private static final Logger LOG = Logger.getLogger(ContextCompiler.class);

    private static final Set<String> NEIGHBOR_EDGE_TYPES = Set.of(
            "DEPENDS_ON", "CALLS", "IMPLEMENTS", "EXTENDS"
    );

    @Inject
    GraphRepository graphRepository;

    @Inject
    ObjectMapper objectMapper;

    public CompiledContext compile(ContextRequest request) {
        if (!request.methodId().contains("#")) {
            return CompiledContext.empty();
        }

        String[] parts = request.methodId().split("#", 2);
        String className = parts[0];
        String methodIdentifier = parts[1];

        if ("*".equals(methodIdentifier)) {
            return compileClassWide(className, request.tokenBudget());
        }

        Map<String, Object> ctx = graphRepository.findMethodContext(className, methodIdentifier);
        if (ctx.isEmpty()) {
            var callSigs = graphRepository.findInternalCallSignatures(className, List.of(methodIdentifier));
            if (!callSigs.isEmpty()) {
                ctx = graphRepository.findMethodContext(className, callSigs.get(0).get("signature"));
            }
        }
        if (ctx.isEmpty()) {
            LOG.warnf("Method not found for context compilation: %s", request.methodId());
            return CompiledContext.empty();
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("t", "surgical");

            int communityId = graphRepository.getCommunityId(className).orElse(-1);
            root.put("c", communityId);

            Optional<String> communitySummary = communityId >= 0
                    ? graphRepository.getCommunitySummary(communityId)
                    : Optional.empty();
            communitySummary.ifPresent(summary -> root.put("cs", summary));

            ObjectNode instructions = root.putObject("_instructions");
            instructions.put("task", "Rewrite the target method using the supplied graph context");
            instructions.putArray("read_order").add("cs").add("tgt").add("nbr");
            instructions.putArray("rules")
                    .add("Only rewrite tgt.body")
                    .add("Use nbr for signatures and types only")
                    .add("Respect community summary cs for architectural intent");
            instructions.putArray("do_not_request")
                    .add("full neighbor bodies")
                    .add("entire community source");

            ObjectNode target = root.putObject("tgt");
            target.put("cls", (String) ctx.getOrDefault("simpleName", ""));
            target.put("fqn", className);
            target.put("m", (String) ctx.get("name"));
            target.put("sig", (String) ctx.get("signature"));
            target.put("body", (String) ctx.getOrDefault("rawBody", ""));

            ArrayNode callArray = target.putArray("calls");
            @SuppressWarnings("unchecked")
            List<String> callNames = (List<String>) ctx.get("internalCalls");
            if (callNames != null && !callNames.isEmpty()) {
                for (var call : graphRepository.findInternalCallSignatures(className, callNames)) {
                    ObjectNode callNode = callArray.addObject();
                    callNode.put("n", call.get("method_name"));
                    callNode.put("sig", call.get("signature"));
                    callNode.put("rt", call.get("return_type"));
                }
            }

            int usedTokens = estimateTokens(root);
            int remaining = request.tokenBudget() - usedTokens;

            ArrayNode neighbors = root.putArray("nbr");
            Set<String> included = new LinkedHashSet<>();
            included.add(className);
            if (remaining > 0) {
                for (String neighborFqn : graphRepository.getNeighbors(className, 1, NEIGHBOR_EDGE_TYPES)) {
                    Optional<ClassNode> neighbor = graphRepository.findClassNode(neighborFqn);
                    if (neighbor.isEmpty()) {
                        continue;
                    }
                    ObjectNode skeleton = buildClassSkeleton(neighbor.get());
                    int skeletonTokens = estimateTokens(skeleton);
                    if (skeletonTokens > remaining) {
                        break;
                    }
                    neighbors.add(skeleton);
                    included.add(neighborFqn);
                    remaining -= skeletonTokens;
                    usedTokens += skeletonTokens;
                }
            }

            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            return new CompiledContext(json, usedTokens, List.copyOf(included), instructions.toString());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to compile context for %s", request.methodId());
            return CompiledContext.empty();
        }
    }

    private CompiledContext compileClassWide(String className, int tokenBudget) {
        Optional<ClassNode> classNode = graphRepository.findClassNode(className);
        if (classNode.isEmpty()) {
            return CompiledContext.empty();
        }
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("t", "class_wide");
            int communityId = graphRepository.getCommunityId(className).orElse(-1);
            root.put("c", communityId);

            ObjectNode metadata = root.putObject("metadata");
            metadata.put("file_path", classNode.get().filePath());
            metadata.put("package_name", classNode.get().packageName());
            metadata.put("class_name", classNode.get().simpleName());

            ArrayNode methods = root.putArray("methods");
            for (MethodNode method : classNode.get().methods()) {
                ObjectNode methodNode = methods.addObject();
                methodNode.put("name", method.name());
                methodNode.put("signature", method.signature());
                methodNode.put("return_type", method.returnType());
                methodNode.put("raw_body", method.rawBody());
            }

            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            int tokens = Math.min(estimateTokens(root), tokenBudget);
            return new CompiledContext(json, tokens, List.of(className), "{}");
        } catch (Exception e) {
            LOG.errorf(e, "Failed to compile class-wide context for %s", className);
            return CompiledContext.empty();
        }
    }

    private ObjectNode buildClassSkeleton(ClassNode classNode) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("fqn", classNode.fullyQualifiedName());
        node.put("cls", classNode.simpleName());
        ArrayNode methods = node.putArray("methods");
        for (MethodNode method : classNode.methods()) {
            methods.add(method.signature());
        }
        ArrayNode fields = node.putArray("fields");
        for (FieldNode field : classNode.fields()) {
            fields.add(field.type() + " " + field.name());
        }
        return node;
    }

    private int estimateTokens(ObjectNode node) {
        return Math.max(1, node.toString().length() / 4);
    }

    public record ContextRequest(String methodId, int tokenBudget) {}

    public record CompiledContext(
            String json,
            int estimatedTokens,
            List<String> includedNodes,
            String usageInstructions
    ) {
        public static CompiledContext empty() {
            return new CompiledContext("{}", 0, List.of(), "{}");
        }
    }
}
