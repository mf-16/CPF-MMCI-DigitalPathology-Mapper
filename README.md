# CPF-MMCI-DigitalPathology-Mapper

Transforms MMCI (Masaryk Memorial Cancer Institute) biobank / digital-pathology
data into **Common Provenance Model (CPM, ISO 23494)** documents and stores them
in **CPF-Storage**. A component of the Common Provenance Framework (CPF).

```
biobank record → CPM document → signed PROV-JSON → CPF-Storage (+ Neo4j)
```

It sits between two existing components:

- **CPM library** — [`cz.muni.fi.cpm:cpm-core` + `cpm-template`](https://github.com/Common-Provenance-Framework/CPF-Toolbox)
  (Java, Maven, on top of ProvToolbox) — models and serializes provenance.
- **CPF-Storage** — Spring Boot + Neo4j service that persists signed provenance
  documents over REST.

## Design

Built as **hexagonal / ports & adapters**: the input source, the mapping, and the
storage backend each sit behind an interface at the edge, so the core logic
depends on none of them and any one can be replaced without touching the others.
See **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

## Status

First mapping works end to end: a sample LIS case
(`src/main/resources/samples/`) is mapped into the `aplab:preanalyticsIn_bundle`
CPM bundle, signed, and written as PROV-JSON + a CPF-Storage upload body
(dry-run). Live upload is optional (`--post`).

## Build & run

Requires **JDK 23+** (the CPM library is compiled for Java 23; built on
Temurin 25) and Maven 3.9+.

```bash
mvn package        # -> target/cpf-mmci-mapper.jar
mvn test
java -jar target/cpf-mmci-mapper.jar --in src/main/resources/samples --out target/out
# -> target/out/aplab_preanalyticsIn_bundle.json  (+ .request.json upload body)
```

**Prerequisite:** the CPM library (`cz.muni.fi.cpm:cpm-core:1.0.0`) is not on
Maven Central; install the vendored jar from the sibling `CPF-Search-API` repo
into your local `~/.m2` once. See [AGENTS.md](AGENTS.md) for the exact command
and the rest of the developer setup (ports, conventions, running against
CPF-Storage).

## License

MIT — see [LICENSE](LICENSE).
