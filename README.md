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

Scaffold. The `src/` tree exists but is empty; implementation follows the
recipes in ARCHITECTURE.md.

## Build

Requires **JDK 25** and Maven 3.9+.

```bash
mvn package        # -> target/cpf-mmci-mapper.jar
mvn test
```

**Prerequisite:** the CPM library must be resolvable. If it is not on a
configured Maven repository, build it locally first:

```bash
git clone https://github.com/Common-Provenance-Framework/CPF-Toolbox.git
mvn -f CPF-Toolbox install      # this upstream build may require JDK 23
```

For developer/agent details (ports, conventions, running against CPF-Storage),
see [AGENTS.md](AGENTS.md).

## License

MIT — see [LICENSE](LICENSE).
