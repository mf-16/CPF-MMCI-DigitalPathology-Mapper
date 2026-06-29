# AGENTS.md

Guide for agents and developers working in this repo. `CLAUDE.md` points here.

## What this is

`CPF-MMCI-DigitalPathology-Mapper` maps biobank / digital-pathology records into
Common Provenance Model (CPM, ISO 23494) documents and uploads them to
**CPF-Storage**. It is one component of the Common Provenance Framework:

```
input system → read a record → map to a CPM document
            → serialize PROV-JSON → sign → store in CPF-Storage
```

Architecture is hexagonal (ports & adapters). Full design and "how to add X"
recipes: **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**. The produced provenance
graph (backbone + domain) is drawn in **[docs/GRAPH.md](docs/GRAPH.md)**.

The mapper is **data-driven**: the input lists `agents`, `connectors`,
`mainActivity`, `entities`, `activities`, and a `relations[]` array; the mapper
types each node and auto-generates the traversal-information backbone, then
translates the domain relations one-to-one.

## Status

First end-to-end slice works: reads a LIS-style case from `src/main/resources/samples/`,
maps it into the `aplab:preanalyticsIn_bundle` CPM bundle, signs it, and writes
the PROV-JSON + the CPF-Storage upload body to an output dir (dry-run). Live
upload is wired but optional (`--post`).

## Build / run / test

- **JDK 23+** required (the CPM library is compiled for Java 23). Built/tested on
  **Temurin 25**. Maven 3.9+. Point Maven at the JDK, e.g.
  `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot"`.
- Build: `mvn package` → runnable jar `target/cpf-mmci-mapper.jar`.
- Test: `mvn test` (self-check asserts the bundle matches the reference diagram).
- Run (dry-run): `java -jar target/cpf-mmci-mapper.jar --in src/main/resources/samples --out target/out`
  → `target/out/aplab_preanalyticsIn_bundle.json` (PROV-JSON) + `.request.json`
  (the `DocumentFormDTO` body). Add `--post <storeBaseUrl> --org <id>` to also POST.

### Prerequisite: the CPM library (one-time local install)

Depends on `cz.muni.fi.cpm:cpm-core:1.0.0`, which is **not on Maven Central**.
The jar is vendored in the sibling repo `../CPF-Search-API` — install it (and its
ProvToolbox deps come from Central) into your local `~/.m2`:

```bash
RES=../CPF-Search-API/bundle-search/src/main/resources
mvn install:install-file -Dfile="$RES/cpm-core-1.0.0.jar" \
  -DgroupId=cz.muni.fi.cpm -DartifactId=cpm-core -Dversion=1.0.0 -Dpackaging=jar
```

The jar carries an internal pom referencing a non-existent parent `cz.muni.fi.cpm:cpm`,
so if `install-file` picks that up and resolution later fails, reinstall with a
flat hand-written pom via `-DpomFile`. ProvToolbox deps (`prov-model`,
`prov-interop`, `prov-nf` 2.2.1; `prov-json` 1.0.0) are declared in `pom.xml`.

### Downstream: CPF-Storage

End-to-end runs need CPF-Storage up (cloned at `../CPF-Storage`):
`STORE_URL=http://localhost:8081/api/v1/ docker compose up --build -d`.
Register an organization, then upload. See CPF-Storage's README for cert
generation and the document-upload contract.

## The boundaries (swap points)

Ports in `port/`, with their current adapters:

| Port | Current adapter | Swap to |
|---|---|---|
| `ProvenanceSource` | `adapter/in/FileProvenanceSource` (reads `*.json`) | a real LIS reader |
| `CpmMapper` | `adapter/map/DigitalPathologyMapper` (builds the bundle) | a revised/another mapping |
| `ProvenanceStore` | `adapter/out/FileDumpStore` (+ optional `CpfStorageClient`) | another store |
| `DocumentSigner` | `adapter/out/EcDocumentSigner` (SHA256withECDSA) | the org's real key |

Wiring is in `cli/MapCommand`. To add a source / mapper / store, follow the
recipes in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#how-to-add-x).

## Conventions

- **Keep `domain/` and `port/` pure** — no I/O, no frameworks, no HTTP/DB/CPM/crypto
  imports. Those belong in `adapter/`.
- **All wiring lives in `cli/`** (the composition root). The domain only ever
  sees ports.
- Non-trivial logic ships with one runnable check (a JUnit test or an
  `assert`-based self-check). No test framework sprawl.

## Line endings (important)

`.gitattributes` forces LF (`* text=auto eol=lf`). Do **not** set
`core.autocrlf=true` — CRLF silently breaks shell scripts / entrypoints when
they run in Linux containers (observed across sibling CPF repos).
