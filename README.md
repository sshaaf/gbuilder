# gbuilder

CLI that parses a Java codebase into a semantic knowledge graph stored in SQLite. Use it to explore structure, dependencies, and communities — locally, without a server.

## Prerequisites

- **JDK 21+** and **Maven**
- **tree-sitter4j** (only for `--parser-backend treesitter`):

```bash
git clone https://github.com/andreaTP/tree-sitter4j.git
cd tree-sitter4j && mvn install -DskipTests
```

## Build

```bash
mvn package -DskipTests -Dquarkus.package.jar.type=uber-jar
java -jar target/gbuilder-*-runner.jar --help
```

Pre-built **uber-jar** and **native** binaries (Linux, macOS, Windows) are attached to [GitHub Actions](.github/workflows/ci.yml) workflow runs.

## Index a codebase

```bash
gbuilder -p /path/to/java-project
```

Incremental rebuild after changes:

```bash
gbuilder -p /path/to/java-project --update
```

Useful flags: `--parser-backend jparser|treesitter`, `--mode deep` (inferred semantic edges), `--wiki`, `--graphml`, `--svg`, `--neo4j`, `--no-viz`, `--cluster-only`, `--benchmark`.

## Explore the graph

Requires an existing graph under `<project>/.gbuilder/`.

```bash
gbuilder query "Service" -p /path/to/project
gbuilder query "billing flow" --dfs --budget 3000 -p /path/to/project
gbuilder path com.app.A com.app.B -p /path/to/project
gbuilder explain OrderService -p /path/to/project
```

Export: `gbuilder export html|graphml|svg|wiki|neo4j -p /path/to/project`

Automation: `gbuilder watch`, `gbuilder hook install|status|uninstall`, `gbuilder merge <primary> <secondary>`, `gbuilder cluster-only`.

## Output

Each indexed project gets a `.gbuilder/` directory:

| File | Purpose |
|------|---------|
| `graph.db` | SQLite graph store |
| `GRAPH_REPORT.md` | Build stats, communities, analytics |
| `graph.json` | Machine-readable graph |
| `graph.html` | Interactive visualization |
| `manifest.json` | File hashes for `--update` |

## Embeddings (optional)

Off by default. Enables vector storage, embedding-based semantic edges (`--mode deep`), and natural-language query seeds when a type name does not match.

```bash
export GBUILDER_EMBEDDING_ENABLED=true
export OPENAI_API_KEY=sk-...
```

## Develop

```bash
mvn test -Dmaven.compiler.release=21
```
