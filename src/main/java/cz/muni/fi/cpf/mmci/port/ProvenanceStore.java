package cz.muni.fi.cpf.mmci.port;

import cz.muni.fi.cpf.mmci.domain.model.SignedDocument;

/** Output boundary: persists a signed provenance document. */
public interface ProvenanceStore {
    void store(SignedDocument document);
}
