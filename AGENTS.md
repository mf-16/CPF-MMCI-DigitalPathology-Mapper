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
recipes: **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

## Status

Scaffold only. The `src/` tree is created but empty (`.gitkeep` placeholders).
No production code yet — implementation follows the recipes in ARCHITECTURE.md.

## Build / run / test

- **JDK 25** (latest LTS) and Maven 3.9+.
- Build: `mvn package` → runnable jar `target/cpf-mmci-mapper.jar`.
- Test: `mvn test`.
- Run: a CLI entry point in `cli` (once it exists) → `java -jar target/cpf-mmci-mapper.jar ...`.

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

## The boundaries (swap points)

Nothing is implemented yet. The intended edges, each to sit behind a port
interface in `port/`:

- **input** — read records from a source (`adapter/in`)
- **mapping** — turn a record into a CPM document (`adapter/map`)
- **output** — persist to CPF-Storage and sign for storage (`adapter/out`)

To add a source / mapper / store, follow the recipes in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#how-to-add-x).

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
