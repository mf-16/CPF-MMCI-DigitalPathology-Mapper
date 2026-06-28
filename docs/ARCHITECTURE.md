# Architecture

This document describes how the project is built and **exactly how to add new
things** later. The `src/` tree is scaffolded but empty — read this before
writing the first class.

## 1. Purpose & place

This repo is the **missing middle** of the Common Provenance Framework (CPF): it
takes biobank / digital-pathology records and turns them into Common Provenance
Model (CPM, ISO 23494) documents, then stores them.

```
 (this repo)
 ┌──────────────────────────────────────────────────────────────────┐
 │  ProvenanceSource ──▶ SourceRecord ──▶ CpmMapper ──▶ CpmDocument   │
 │        ▲                              (+ProvenanceProfile)  │       │
 │        │                                                    ▼       │
 │   mock / LIS                                         ProfileValidator│
 │                                                            │        │
 │                                                       PROV-JSON      │
 │                                                            │        │
 │                                                      DocumentSigner  │
 │                                                            │        │
 │                                                      ProvenanceStore │
 └────────────────────────────────────────────────────────────│──────┘
   uses ─▶ CPM library (cz.muni.fi.cpm:cpm-core)          POST ▼
                                                       CPF-Storage REST
                                                       (+ Neo4j)
```

- **Upstream dependency:** the CPM library (`cz.muni.fi.cpm:cpm-core` +
  `cpm-template`) builds and serializes `CpmDocument`s.
- **Downstream dependency:** `CPF-Storage` (Spring Boot + Neo4j) persists a
  signed, base64 PROV-JSON document via `POST /api/v1/documents` after the
  organization is registered via `POST /api/v1/organizations`.

## 2. Why hexagonal (ports & adapters)

Two things are unknown / unstable today:

- **No access to the real LIS database** → the input must be swappable (mock
  now, real LIS later).
- **The digital-pathology field mapping is not finalized** → the mapping must be
  swappable without disturbing anything else.

Hexagonal architecture isolates both behind interfaces, so the unstable parts
live at the edges and the stable core never changes when they do.

### The one rule

**Dependencies point inward. The domain depends on nothing.**

Three rings:

```
   ADAPTERS (outside)  — talk to the world (HTTP, files, DB, CPM lib, crypto)
     PORTS (interfaces) — the contracts the domain declares
       DOMAIN (center)  — pure logic + orchestration, no I/O, no frameworks
```

The domain imports only ports (interfaces). Adapters implement those ports and
are wired into the domain in exactly one place: the CLI (composition root).

### "What goes where" test

- Mentions a technology (HTTP / file / DB / CPM library / crypto)? → **adapter**
- A pure rule or orchestration step? → **domain**
- A method the domain needs but can't implement itself? → **port**

If the domain still compiles after you delete every adapter, the boundary is correct.

## 3. Package layout

Base package `cz.muni.fi.cpf.mmci`.

| Package | Ring | Holds |
|---|---|---|
| `domain.model` | domain | `SourceRecord` (neutral input), `FinalizedDocument` (output payload) |
| `domain.profile` | domain | `ProvenanceProfile`, `ProfileValidator` — the configurable minimal-scope model |
| `domain` | domain | `MappingPipeline` — orchestrates source → map → validate → store |
| `port` | port | `ProvenanceSource`, `CpmMapper`, `ProvenanceStore`, `DocumentSigner` |
| `adapter.in` | adapter | `SampleFileSource` (now), `LisSource` (later) — implement `ProvenanceSource` |
| `adapter.map` | adapter | `DigitalPathologyMapper` — implements `CpmMapper`, uses the CPM library |
| `adapter.out` | adapter | `CpfStorageClient` (`ProvenanceStore`), `EcDocumentSigner` (`DocumentSigner`) |
| `cli` | adapter | `MapCommand` — composition root: builds concrete adapters, hands them to the domain |

Resources: `resources/profiles/*.yaml` (provenance profiles),
`resources/samples/*.json` (stand-in biobank records until LIS access exists).

### Key types (intended)

- **`SourceRecord`** — a *neutral* representation of one input record, backed by
  a Jackson tree (`JsonNode`), **not** bound to any LIS schema. The unknown /
  changing LIS structure stays entirely inside the source adapter and the mapper;
  the domain and store never see it. This is what lets development start before
  the LIS schema is final.
- **`CpmDocument`** — from the CPM library; produced by the mapper, serialized to
  PROV-JSON for storage.

## 4. The configurable minimal-scope model (`ProvenanceProfile`)

This is the thesis's core contribution. A **provenance profile** is a
declarative YAML file that defines *which* CPM elements (agents, activities,
entities, relations) are **required** for a given process type, conditioned on
which inputs are available. It answers: "for this kind of biobank process, with
these inputs present, what is the minimum provenance we must capture?"

`ProfileValidator` checks a mapped `CpmDocument` (or a `SourceRecord` before
mapping) against the active profile and **reports missing required elements**
rather than silently emitting an incomplete document.

Intended YAML shape (illustrative — finalize when implementing):

```yaml
# resources/profiles/digital-pathology.yaml
profile: digital-pathology
appliesTo: specimen-digitization        # process type
required:
  agents:
    - role: pathologist                  # must be present
    - role: scanner-device
  activities:
    - type: slide-scanning
  entities:
    - type: physical-slide
    - type: whole-slide-image
  relations:
    - type: wasGeneratedBy               # WSI wasGeneratedBy slide-scanning
      from: whole-slide-image
      to: slide-scanning
conditional:
  # required only when the input record carries staining metadata
  - when: input.has("staining")
    requireActivities: [staining]
```

A new process type = a new YAML file. No code change to add or tighten a profile.

## 5. How to add X

Each recipe names the ring you touch and what you must **not** touch.

### Add or replace a mapping

1. Create a class in `adapter.map` implementing `CpmMapper`.
2. Build the `CpmDocument` using CPM-library factories (`CpmMergedFactory` /
   `CpmOrderedFactory` / `CpmUnorderedFactory`) and/or `cpm-template`.
3. Wire it in `cli.MapCommand` (one line: pass your mapper into the pipeline).
- **Do not touch:** `domain`, `port`, other adapters. The mapping changing is
  expected churn — it is behind `CpmMapper` precisely so it stays contained.

### Add a new input source (e.g. the real LIS)

1. Create a class in `adapter.in` implementing `ProvenanceSource`.
2. Convert each input row into a `SourceRecord` (Jackson tree). Keep all
   LIS-specific schema knowledge inside this adapter.
3. Wire it in `cli.MapCommand` (swap `SampleFileSource` for your source).
- **Do not touch:** domain, mapper, store. Only the wiring line changes.

### Author or change a provenance profile

1. Add / edit a YAML under `resources/profiles/`.
2. Select it via a CLI option (e.g. `--profile digital-pathology`).
- **No code.** Profiles are data; `ProfileValidator` reads them.

### Swap the store or the signer

1. Implement `ProvenanceStore` (or `DocumentSigner`) in `adapter.out`.
2. Wire it in `cli.MapCommand`.
- **Do not touch:** domain or other adapters.

### Add a Claude skill

Author it under `.claude/skills/<name>/`. Skills are deferred until the
workflows they would automate are known.

## 6. Conventions

- `domain` and `port` packages must not import any framework, HTTP, file, DB,
  CPM-library, or crypto type. If you reach for one of those, you're in an adapter.
- All wiring (constructing concrete adapters) lives only in `cli`.
- Each non-trivial piece of logic ships with one runnable check (a JUnit test or
  an `assert`-based self-check) — see the project conventions in `AGENTS.md`.
