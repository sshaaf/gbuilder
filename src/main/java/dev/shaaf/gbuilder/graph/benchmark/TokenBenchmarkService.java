package dev.shaaf.gbuilder.graph.benchmark;

import dev.shaaf.gbuilder.graph.ContextCompiler;
import dev.shaaf.gbuilder.graph.model.ClassNode;
import dev.shaaf.gbuilder.lang.java.JavaSourceScanner;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class TokenBenchmarkService {

    @Inject
    ContextCompiler contextCompiler;

    public BenchmarkResult benchmark(Path codebaseRoot, List<ClassNode> classNodes) throws IOException {
        long rawChars = 0;
        for (Path file : JavaSourceScanner.scanJavaFiles(codebaseRoot)) {
            rawChars += JavaSourceScanner.readSource(file).length();
        }
        long rawTokens = Math.max(1, rawChars / 4);

        long graphTokens = 0;
        for (ClassNode node : classNodes) {
            for (var method : node.methods()) {
                if (!method.constructor()) {
                    var ctx = contextCompiler.compile(
                            new ContextCompiler.ContextRequest(
                                    node.fullyQualifiedName() + "#" + method.name(), 500));
                    graphTokens += Math.max(1, ctx.json().length() / 4);
                }
            }
        }
        graphTokens = Math.max(1, graphTokens);

        double ratio = (double) rawTokens / graphTokens;
        String summary = String.format(
                "Raw corpus ~%,d tokens vs graph context ~%,d tokens (%.1fx reduction for sampled methods)",
                rawTokens, graphTokens, ratio);
        return new BenchmarkResult(rawTokens, graphTokens, ratio, summary);
    }

    public record BenchmarkResult(long rawTokens, long graphTokens, double reductionRatio, String summary) {}
}
