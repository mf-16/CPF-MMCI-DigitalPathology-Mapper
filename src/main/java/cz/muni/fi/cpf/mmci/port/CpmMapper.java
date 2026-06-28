package cz.muni.fi.cpf.mmci.port;

import cz.muni.fi.cpf.mmci.domain.model.MappedDocument;
import cz.muni.fi.cpf.mmci.domain.model.SourceRecord;

/** Mapping boundary: turns a neutral record into a finalized CPM document. */
public interface CpmMapper {
    MappedDocument map(SourceRecord record);
}
