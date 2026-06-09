package dev.shaaf.gbuilder.graph.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.shaaf.gbuilder.graph.GraphRepository;
import dev.shaaf.gbuilder.graph.analysis.GraphAnalysisResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class GraphExportService {

    @Inject
    GraphRepository graphRepository;

    @Inject
    ObjectMapper objectMapper;

    public void exportHtml(Path codebaseRoot, GraphAnalysisResult analysis) throws IOException {
        Path out = codebaseRoot.resolve(".gbuilder").resolve("graph.html");
        Files.createDirectories(out.getParent());

        Map<Integer, String> colors = Map.of(
                0, "#4e79a7", 1, "#f28e2b", 2, "#e15759", 3, "#76b7b2",
                4, "#59a14f", 5, "#edc948", 6, "#b07aa1", 7, "#ff9da7");

        StringBuilder nodes = new StringBuilder();
        StringBuilder edges = new StringBuilder();
        int edgeId = 0;

        for (String fqn : graphRepository.listInternalClassFqns()) {
            int community = graphRepository.getCommunityId(fqn).orElse(0);
            String color = colors.getOrDefault(community % colors.size(), "#999999");
            String label = fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
            nodes.append("{id:'").append(escape(fqn)).append("',label:'").append(escape(label))
                    .append("',color:'").append(color).append("'},");
        }

        for (var edge : graphRepository.listClassLevelEdges()) {
            String from = fqn(edge.fromId());
            String to = fqn(edge.toId());
            if (from == null || to == null) {
                continue;
            }
            edges.append("{from:'").append(escape(from)).append("',to:'").append(escape(to))
                    .append("',id:").append(edgeId++).append("},");
        }

        String html = """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8"><title>gbuilder graph</title>
                <script src="https://unpkg.com/vis-network/standalone/umd/vis-network.min.js"></script>
                <style>body{font-family:sans-serif;margin:0}#g{width:100vw;height:100vh}</style></head>
                <body><input id="search" placeholder="Search types…" style="position:fixed;top:8px;left:8px;z-index:9;padding:6px">
                <div id="g"></div><script>
                const nodes=new vis.DataSet([%s]);
                const edges=new vis.DataSet([%s]);
                const net=new vis.Network(document.getElementById('g'),{nodes,edges},
                  {physics:{stabilization:true},interaction:{hover:true}});
                document.getElementById('search').oninput=e=>{
                  const q=e.target.value.toLowerCase();
                  nodes.forEach(n=>nodes.update({id:n.id,hidden:q&&!n.id.toLowerCase().includes(q)}));
                };
                </script></body></html>
                """.formatted(trimComma(nodes), trimComma(edges));

        Files.writeString(out, html);
    }

    public void exportGraphMl(Path codebaseRoot) throws IOException {
        Path out = codebaseRoot.resolve(".gbuilder").resolve("graph.graphml");
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <graphml xmlns="http://graphml.graphdrawing.org/xmlns">
                <graph edgedefault="undirected">
                """);
        for (String fqn : graphRepository.listInternalClassFqns()) {
            xml.append("<node id=\"").append(escapeXml(fqn)).append("\"/>\n");
        }
        int i = 0;
        for (var edge : graphRepository.listClassLevelEdges()) {
            String from = fqn(edge.fromId());
            String to = fqn(edge.toId());
            if (from == null || to == null) {
                continue;
            }
            xml.append("<edge id=\"e").append(i++).append("\" source=\"")
                    .append(escapeXml(from)).append("\" target=\"")
                    .append(escapeXml(to)).append("\"/>\n");
        }
        xml.append("</graph></graphml>");
        Files.writeString(out, xml);
    }

    public void exportSvg(Path codebaseRoot) throws IOException {
        Path out = codebaseRoot.resolve(".gbuilder").resolve("graph.svg");
        List<String> fqns = graphRepository.listInternalClassFqns();
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(fqns.size())));
        StringBuilder svg = new StringBuilder("""
                <svg xmlns="http://www.w3.org/2000/svg" width="800" height="600">
                <text x="10" y="20" font-size="14">gbuilder graph (type layout)</text>
                """);
        for (int i = 0; i < fqns.size(); i++) {
            int x = 40 + (i % cols) * 120;
            int y = 40 + (i / cols) * 40;
            String label = fqns.get(i).contains(".")
                    ? fqns.get(i).substring(fqns.get(i).lastIndexOf('.') + 1) : fqns.get(i);
            svg.append("<rect x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" width=\"100\" height=\"24\" fill=\"#e8f4fc\" stroke=\"#333\"/>");
            svg.append("<text x=\"").append(x + 4).append("\" y=\"").append(y + 16)
                    .append("\" font-size=\"10\">").append(escapeXml(label)).append("</text>");
        }
        svg.append("</svg>");
        Files.writeString(out, svg);
    }

    public void exportWiki(Path codebaseRoot, GraphAnalysisResult analysis) throws IOException {
        Path wiki = codebaseRoot.resolve(".gbuilder").resolve("wiki");
        Files.createDirectories(wiki);
        StringBuilder index = new StringBuilder("# Codebase wiki\n\n");
        for (var community : analysis.communities()) {
            String slug = "community-" + community.id();
            index.append("- [").append(community.label()).append("](").append(slug).append(".md)\n");
            StringBuilder article = new StringBuilder("# ").append(community.label()).append("\n\n");
            article.append("Cohesion: ").append(String.format("%.2f", community.cohesion())).append("\n\n");
            article.append("## Types\n\n");
            for (String fqn : community.typeFqns()) {
                article.append("- `").append(fqn).append("`\n");
            }
            Files.writeString(wiki.resolve(slug + ".md"), article);
        }
        Files.writeString(wiki.resolve("index.md"), index);
    }

    public void exportNeo4jCypher(Path codebaseRoot) throws IOException {
        Path out = codebaseRoot.resolve(".gbuilder").resolve("cypher.txt");
        StringBuilder cypher = new StringBuilder();
        for (String fqn : graphRepository.listInternalClassFqns()) {
            cypher.append("MERGE (n:Type {fqn: '").append(escapeCypher(fqn)).append("'});\n");
        }
        for (var edge : graphRepository.listClassLevelEdges()) {
            String from = fqn(edge.fromId());
            String to = fqn(edge.toId());
            if (from == null || to == null) {
                continue;
            }
            cypher.append("MATCH (a:Type {fqn: '").append(escapeCypher(from)).append("'}), ")
                    .append("(b:Type {fqn: '").append(escapeCypher(to)).append("'}) ")
                    .append("MERGE (a)-[:").append(edge.type()).append("]->(b);\n");
        }
        Files.writeString(out, cypher);
    }

    public void exportGraphJson(Path codebaseRoot) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode nodes = root.putArray("nodes");
        for (String fqn : graphRepository.listInternalClassFqns()) {
            ObjectNode n = nodes.addObject();
            n.put("id", fqn);
            n.put("label", fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn);
            graphRepository.getCommunityId(fqn).ifPresent(c -> n.put("community", c));
        }
        ArrayNode edges = root.putArray("edges");
        for (var edge : graphRepository.listClassLevelEdges()) {
            String from = fqn(edge.fromId());
            String to = fqn(edge.toId());
            if (from == null || to == null) {
                continue;
            }
            ObjectNode e = edges.addObject();
            e.put("source", from);
            e.put("target", to);
            e.put("type", edge.type());
            e.put("provenance", edge.provenance().name());
            e.put("confidence", edge.confidenceScore());
        }
        Files.writeString(codebaseRoot.resolve(".gbuilder").resolve("graph.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    private String fqn(String nodeId) {
        return nodeId != null && nodeId.startsWith("class:") ? nodeId.substring(6) : null;
    }

    private String trimComma(StringBuilder sb) {
        if (sb.isEmpty()) {
            return "";
        }
        return sb.substring(0, sb.length() - 1);
    }

    private String escape(String s) {
        return s.replace("'", "\\'");
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
    }

    private String escapeCypher(String s) {
        return s.replace("'", "\\'");
    }
}
