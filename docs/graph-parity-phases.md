# gbuilder graph parity — implementation phases

Phased plan to close graph-feature gaps vs [graphify](https://github.com/safishamsi/graphify).

Reference: full user stories are in the backlog / prior planning notes. This file is the **phase schedule only** — edit freely.

---

## Phase P0 — Foundation

**Goal:** Faster rebuilds, trustworthy edge metadata, and a human-readable report — no LLM required.

| ID | User story | Notes |
|----|------------|-------|
| US-1.1 | Incremental graph update (`--update`) | Manifest + SHA256; merge changed files; prune deleted-file ghosts |
| US-1.2 | Graph diff report | Summary after incremental update (+/- types, edges) |
| US-2.1 | Edge provenance metadata | `EXTRACTED` / `INFERRED` / `AMBIGUOUS` + optional `confidence_score` on edges |
| US-6.2 | `GRAPH_REPORT.md` | Stats, communities, god nodes (stub OK), written to `.gbuilder/` |

**Exit criteria**
- [ ] Second build on unchanged tree skips parse work
- [ ] Report generated on every full/incremental build
- [ ] All parser-derived edges tagged `EXTRACTED`

---

## Phase P1 — Explore

**Goal:** graphify-style analytics so users can orient in the graph without SQL or custom scripts.

| ID | User story | Notes |
|----|------------|-------|
| US-4.1 | God nodes report | Rank types (optional: methods) by degree |
| US-4.2 | Surprising connections | Cross-community / rare edges ranked |
| US-4.3 | Suggested questions | Template-generated from graph structure |
| US-5.3 | Shortest path (`gbuilder path`) | Between two FQNs on class-level graph |
| US-5.4 | Explain node (`gbuilder explain`) | Type summary + neighbors + community + tech |
| US-3.2 | Community cohesion scores | Per-community 0–1 score in DB + report |
| US-3.3 | Auto-labeled communities | Short names (heuristic or optional LLM) |

**Exit criteria**
- [ ] `GRAPH_REPORT.md` includes god nodes, surprises, questions, cohesion, labels
- [ ] `explain` and `path` CLI commands work on fixture codebase

---

## Phase P2 — Query

**Goal:** Agent-facing traversal over the graph via CLI.

| ID | User story | Notes |
|----|------------|-------|
| US-5.1 | BFS graph query | `gbuilder query "..."` with depth + edge-type filter |
| US-5.2 | DFS path tracing | `gbuilder query "..." --dfs` for call/dependency chains |

**Exit criteria**
- [ ] Query output respects `--budget <tokens>`

---

## Phase P3 — Visualization

**Goal:** See and share the graph outside the IDE/CLI.

| ID | User story | Notes |
|----|------------|-------|
| US-6.1 | Interactive HTML graph | `.gbuilder/graph.html` (vis.js or similar); `--no-viz` to skip |
| US-6.3 | GraphML / SVG export | `gbuilder export graphml\|svg` |
| US-6.5 | Agent wiki export | `--wiki` → `index.md` + per-community articles |

**Exit criteria**
- [ ] HTML opens in browser with community colors + search
- [ ] Wiki navigable without DB access

---

## Phase P4 — Automation

**Goal:** Keep the graph fresh with minimal manual rebuilds; measure value.

| ID | User story | Notes |
|----|------------|-------|
| US-1.3 | Watch mode | `gbuilder watch <path>`; debounced; Java-only incremental |
| US-1.4 | Git post-commit hook | `gbuilder hook install\|uninstall\|status` |
| US-3.4 | Re-cluster only | `gbuilder cluster-only` on existing SQLite DB |
| US-4.4 | Token benchmark | Optional; compare graph context vs raw source tokens |

**Exit criteria**
- [ ] Watch updates DB on save without full rebuild
- [ ] Hook runs on `git commit` for changed `.java` files only

---

## Phase P5 — Semantic & advanced

**Goal:** Deeper graphify parity — inferred structure, exports, persistence extras. Higher cost / complexity.

| ID | User story | Notes |
|----|------------|-------|
| US-2.2 | Semantic similarity edges | Opt-in `--mode deep`; uses embeddings or LLM; `INFERRED` only |
| US-2.3 | Hyperedges | Groups of 3+ nodes (flows, cross-cutting concerns) |
| US-3.1 | Leiden clustering | Optional alternative to label propagation |
| US-6.4 | Neo4j export | Cypher file + optional `--push` |
| US-7.2 | Save query results into graph | Q&A nodes for future builds |
| US-8.1 | Merge graphs | Multi-root / monorepo merge with `repo` attribute |

**Exit criteria**
- [ ] Deep mode documented; off by default
- [ ] Neo4j export round-trips on sample graph

---

## Out of scope (for this parity track)

Not planned unless product direction changes:

- PDF / paper citation mining as graph nodes
- Image / video multimodal extraction
- URL ingest (`--add <url>`)
- Obsidian vault export
- Cross-corpus LLM extraction without Java AST
- MCP server (`gbuilder serve --mcp`)

---

## Dependency sketch

```
P0 (incremental + provenance + report)
 └── P1 (analytics + explain/path)
      └── P2 (query)
           ├── P3 (viz + wiki)
           └── P4 (watch + hooks + benchmark)
                └── P5 (semantic + Neo4j + merge)
```

---

## Changelog (edit log)

| Date | Author | Change |
|------|--------|--------|
| 2026-06-09 | — | Initial phase breakdown |
| 2026-06-09 | — | Removed MCP (US-7.1) from P2; moved to out of scope |
