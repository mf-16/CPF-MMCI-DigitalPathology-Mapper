# AGENTS.md

Guide for agents and developers working in this repo. `CLAUDE.md` points here.

## What this is

`CPF-MMCI-DigitalPathology-Mapper` maps MMCI biobank / digital-pathology records
into Common Provenance Model (CPM, ISO 23494) documents and uploads them to
**CPF-Storage**. It is the middle stage of the Common Provenance Framework:

```
ProvenanceSource → SourceRecord → CpmMapper (+ProvenanceProfile)
  → CpmDocument → ProfileValidator → PROV-JSON → DocumentSigner → ProvenanceStore
```

Full design and "how to add X" recipes: **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

## Status

Scaffold only. The `src/` tree is created but empty (`.gitkeep` placeholders).
No production code yet — implementation follows the recipes in ARCHITECTURE.md.

## Build / run / test

- **JDK 25** (latest LTS) and Maven 3.9+.
- Build: `mvn package` → runnable jar `target/cpf-mmci-mapper.jar`.
- Test: `mvn test`.
- Run (once `cli.MapCommand` exists): `java -jar target/cpf-mmci-mapper.jar map ...`.

### Prerequisite: the CPM library

This depends on `cz.muni.fi.cpm:cpm-core` + `cpm-template` (v1.0.0). If Maven
cannot resolve them, build them locally first:

```bash
git clone https://github.com/Common-Provenance-Framework/CPF-Toolbox.git
mvn -f CPF-Toolbox install        # may need JDK 23 for CPF-Toolbox's own build
```

This project still targets JDK 25; JDK 23 (if needed) is only for that one
`install` of the upstream library.

### Downstream: CPF-Storage

End-to-end runs need CPF-Storage up (cloned at `../CPF-Storage`):
`STORE_URL=http://localhost:8081/api/v1/ docker compose up --build -d`.
Register an organization, then upload. See CPF-Storage's README for cert
generation and the document-upload contract.

## Ports (the swap points)

| Port | Direction | Purpose |
|---|---|---|
| `ProvenanceSource` | in | yields `SourceRecord`s (mock now, LIS later) |
| `CpmMapper` | in | turns a `SourceRecord` + profile into a `CpmDocument` |
| `ProvenanceStore` | out | persists a finalized document (CPF-Storage REST) |
| `DocumentSigner` | out | signs the document for storage (SHA256withECDSA) |

To add a mapper / source / profile / store, follow the recipes in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#5-how-to-add-x).

## Conventions

- **Keep `domain/` and `port/` pure** — no I/O, no frameworks, no HTTP/DB/CPM/crypto
  imports. Those belong in `adapter/`.
- **All wiring lives in `cli/`** (the composition root). The domain only ever
  sees ports.
- **The configurable minimal-scope model lives in `resources/profiles/*.yaml`** —
  adding/tightening a profile is data, not code.
- Non-trivial logic ships with one runnable check (a JUnit test or an
  `assert`-based self-check). No test framework sprawl.

## Line endings (important)

`.gitattributes` forces LF (`* text=auto eol=lf`). Do **not** set
`core.autocrlf=true` — CRLF silently breaks shell scripts / entrypoints when
they run in Linux containers (observed across sibling CPF repos).
