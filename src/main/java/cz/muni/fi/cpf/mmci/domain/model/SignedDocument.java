package cz.muni.fi.cpf.mmci.domain.model;

/**
 * A {@link MappedDocument} together with a detached signature over its PROV-JSON
 * bytes. This is what a {@code ProvenanceStore} persists.
 *
 * @param document  the mapped document
 * @param signature signature over {@code document.provJson()}
 */
public record SignedDocument(MappedDocument document, byte[] signature) {}
