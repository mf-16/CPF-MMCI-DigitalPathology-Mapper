package cz.muni.fi.cpf.mmci.domain.model;

/**
 * A finalized provenance document produced by a mapper: the bundle name plus its
 * serialized PROV-JSON bytes.
 *
 * @param bundleName qualified bundle name, e.g. {@code aplab:preanalyticsIn_bundle}
 * @param provJson   the document serialized as PROV-JSON
 */
public record MappedDocument(String bundleName, byte[] provJson) {}
