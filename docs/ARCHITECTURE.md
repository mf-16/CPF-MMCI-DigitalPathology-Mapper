# Architecture

For whoever works on the system next — developer or AI. It states what the
product does, the architectural pattern it follows, and where code goes.

The first mapping is implemented (LIS files → `aplab:preanalyticsIn_bundle` →
dry-run store). The package roles below are concrete; the "How to add X" recipes
are how you extend it.

## What this is

This service maps biobank and digital-pathology records into **Common Provenance
Model (CPM, ISO 23494)** documents and persists them. It is one component of the
Common Provenance Framework (CPF), and it integrates with two external systems:

- **Upstream:** the CPM library (`cz.muni.fi.cpm:cpm-core` + `cpm-template`,
  built on ProvToolbox) — used to build and serialize CPM documents.
- **Downstream:** **CPF-Storage** — a REST service (Spring Boot + Neo4j) that
  persists a signed, base64-encoded PROV-JSON document.

The work is a pipeline, conceptually:

```
input system → read a record → map it to a CPM document
            → serialize to PROV-JSON → sign → store in CPF-Storage
```

## Why hexagonal (ports & adapters)

The system is defined by its edges: an **input data source**, a **storage
service**, and the **CPM library / crypto** it builds on. Those are exactly the
parts that change independently of the system itself:

- **Input sources differ and change** — how a record is *read* should not dictate
  how it is *mapped* or *stored*.
- **Mapping rules evolve** — the translation from a record to CPM is the part
  most likely to be revised, and it should be revisable without touching I/O.
- **Storage and crypto are external concerns** — an HTTP service and a signing
  algorithm are integration details, not business logic.
- **Testability** — the core logic must run in a unit test with no live database,
  no storage service, and no network.

Hexagonal architecture puts each volatile concern behind an interface at the
edge, so the stable core depends on none of them. Replacing a source, revising a
mapping, or changing the storage backend stays a local change at one edge.

### The one rule

**Dependencies point inward. The core depends on nothing.**

Three rings:

```
   ADAPTERS (outside)  — talk to the world (HTTP, files, DB, CPM lib, crypto)
     PORTS (interfaces) — the contracts the core declares
       DOMAIN (center)  — pure logic + orchestration, no I/O, no frameworks
```

The domain imports only ports (interfaces). Adapters implement those ports. The
two are connected in exactly one place — the composition root in `cli`.

### "What goes where" test

- Mentions a technology (HTTP / file / DB / CPM library / crypto)? → **adapter**
- A pure rule or orchestration step? → **domain**
- A boundary the domain needs but cannot implement itself? → **port** (interface)

If the domain still compiles after every adapter is deleted, the boundary is correct.

## Package layout

Base package `cz.muni.fi.cpf.mmci`.

| Directory | Ring | Role — and what's there now |
|---|---|---|
| `domain/model` | domain | value types: `SourceRecord`, `MappedDocument`, `SignedDocument` |
| `domain` | domain | `MappingPipeline` — drives read → map → sign → store |
| `port` | port | `ProvenanceSource`, `CpmMapper`, `ProvenanceStore`, `DocumentSigner` |
| `adapter/in` | adapter | `FileProvenanceSource` (reads `*.json`) |
| `adapter/map` | adapter | `DigitalPathologyMapper` (builds the bundle via the CPM library) |
| `adapter/out` | adapter | `FileDumpStore`, `CpfStorageClient`, `EcDocumentSigner` |
| `cli` | adapter | `MapCommand` — composition root wiring the adapters into the pipeline |

`SourceRecord` is a neutral JSON-tree value, decoupling the domain from any
specific source schema.

## How to add X

Each recipe names the ring you touch and what you must **not** touch.

### Add an input source

1. Add a class in `adapter/in` that reads the source and produces the domain's
   neutral input type. Keep all source-specific schema knowledge inside it.
2. Make it satisfy the input port the domain declares in `port`.
3. Wire it in the `cli` composition root.
- **Do not touch:** domain, mapping, output adapters.

### Add or replace a mapping

1. Add a class in `adapter/map` that turns the domain's input type into a CPM
   document, using the CPM library factories (`CpmMergedFactory` /
   `CpmOrderedFactory` / `CpmUnorderedFactory`) and/or `cpm-template`.
2. Make it satisfy the mapping port in `port`.
3. Wire it in `cli`.
- **Do not touch:** `domain`, `port`, other adapters. Mapping changes are
  expected and stay contained behind the port.

### Swap the store or the signer

1. Add a class in `adapter/out` satisfying the output port (store or signer).
2. Wire it in `cli`.
- **Do not touch:** domain or other adapters.

## Conventions

- `domain` and `port` must not import any framework, HTTP, file, DB, CPM-library,
  or crypto type. If you reach for one of those, you are in an adapter.
- All wiring (constructing concrete adapters) lives only in `cli`.
- Each non-trivial unit ships with one runnable check (a JUnit test or an
  `assert`-based self-check). No test-framework sprawl. See `AGENTS.md`.
