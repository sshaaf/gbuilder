package dev.shaaf.gbuilder.graph;

public record GraphBuildOptions(
        boolean incremental,
        boolean deepMode,
        boolean skipVisualization,
        boolean generateWiki,
        boolean exportSvg,
        boolean exportGraphml,
        boolean exportNeo4j,
        boolean clusterOnly,
        boolean benchmark,
        String clusteringAlgorithm
) {
    public static GraphBuildOptions defaults() {
        return new GraphBuildOptions(false, false, false, false, false, false, false, false, false, "label-propagation");
    }

    public GraphBuildOptions withIncremental(boolean incremental) {
        return new GraphBuildOptions(incremental, deepMode, skipVisualization, generateWiki,
                exportSvg, exportGraphml, exportNeo4j, clusterOnly, benchmark, clusteringAlgorithm);
    }

    public GraphBuildOptions withDeepMode(boolean deepMode) {
        return new GraphBuildOptions(incremental, deepMode, skipVisualization, generateWiki,
                exportSvg, exportGraphml, exportNeo4j, clusterOnly, benchmark, clusteringAlgorithm);
    }

    public GraphBuildOptions withSkipVisualization(boolean skipVisualization) {
        return new GraphBuildOptions(incremental, deepMode, skipVisualization, generateWiki,
                exportSvg, exportGraphml, exportNeo4j, clusterOnly, benchmark, clusteringAlgorithm);
    }

    public GraphBuildOptions withGenerateWiki(boolean generateWiki) {
        return new GraphBuildOptions(incremental, deepMode, skipVisualization, generateWiki,
                exportSvg, exportGraphml, exportNeo4j, clusterOnly, benchmark, clusteringAlgorithm);
    }

    public GraphBuildOptions withExportSvg(boolean exportSvg) {
        return new GraphBuildOptions(incremental, deepMode, skipVisualization, generateWiki,
                exportSvg, exportGraphml, exportNeo4j, clusterOnly, benchmark, clusteringAlgorithm);
    }

    public GraphBuildOptions withExportGraphml(boolean exportGraphml) {
        return new GraphBuildOptions(incremental, deepMode, skipVisualization, generateWiki,
                exportSvg, exportGraphml, exportNeo4j, clusterOnly, benchmark, clusteringAlgorithm);
    }

    public GraphBuildOptions withExportNeo4j(boolean exportNeo4j) {
        return new GraphBuildOptions(incremental, deepMode, skipVisualization, generateWiki,
                exportSvg, exportGraphml, exportNeo4j, clusterOnly, benchmark, clusteringAlgorithm);
    }

    public GraphBuildOptions withClusterOnly(boolean clusterOnly) {
        return new GraphBuildOptions(incremental, deepMode, skipVisualization, generateWiki,
                exportSvg, exportGraphml, exportNeo4j, clusterOnly, benchmark, clusteringAlgorithm);
    }

    public GraphBuildOptions withBenchmark(boolean benchmark) {
        return new GraphBuildOptions(incremental, deepMode, skipVisualization, generateWiki,
                exportSvg, exportGraphml, exportNeo4j, clusterOnly, benchmark, clusteringAlgorithm);
    }

    public GraphBuildOptions withClusteringAlgorithm(String clusteringAlgorithm) {
        return new GraphBuildOptions(incremental, deepMode, skipVisualization, generateWiki,
                exportSvg, exportGraphml, exportNeo4j, clusterOnly, benchmark, clusteringAlgorithm);
    }
}
