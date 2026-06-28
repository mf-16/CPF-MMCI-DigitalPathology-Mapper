package cz.muni.fi.cpf.mmci.domain;

import cz.muni.fi.cpf.mmci.domain.model.MappedDocument;
import cz.muni.fi.cpf.mmci.domain.model.SignedDocument;
import cz.muni.fi.cpf.mmci.domain.model.SourceRecord;
import cz.muni.fi.cpf.mmci.port.CpmMapper;
import cz.muni.fi.cpf.mmci.port.DocumentSigner;
import cz.muni.fi.cpf.mmci.port.ProvenanceSource;
import cz.muni.fi.cpf.mmci.port.ProvenanceStore;

import java.util.List;

/**
 * Orchestrates read -> map -> sign -> store, depending only on the ports. Knows
 * nothing about files, HTTP, the CPM library, or crypto.
 */
public final class MappingPipeline {

    private final ProvenanceSource source;
    private final CpmMapper mapper;
    private final DocumentSigner signer;
    private final ProvenanceStore store;

    public MappingPipeline(ProvenanceSource source, CpmMapper mapper,
                           DocumentSigner signer, ProvenanceStore store) {
        this.source = source;
        this.mapper = mapper;
        this.signer = signer;
        this.store = store;
    }

    /** Runs the pipeline over every record from the source. Returns the count processed. */
    public int run() {
        List<SourceRecord> records = source.read();
        for (SourceRecord record : records) {
            MappedDocument doc = mapper.map(record);
            byte[] signature = signer.sign(doc.provJson());
            store.store(new SignedDocument(doc, signature));
        }
        return records.size();
    }
}
