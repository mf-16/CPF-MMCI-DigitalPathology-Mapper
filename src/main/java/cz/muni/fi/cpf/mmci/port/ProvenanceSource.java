package cz.muni.fi.cpf.mmci.port;

import cz.muni.fi.cpf.mmci.domain.model.SourceRecord;

import java.util.List;

/** Input boundary: yields neutral records from some input system. */
public interface ProvenanceSource {
    List<SourceRecord> read();
}
