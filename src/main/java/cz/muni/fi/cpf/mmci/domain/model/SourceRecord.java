package cz.muni.fi.cpf.mmci.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One neutral input record, backed by a JSON tree. Decouples the domain from any
 * specific source schema: knowledge of a given source's structure lives in its
 * input adapter and in the mapper, never here.
 *
 * @param id   stable identifier of the record (e.g. a LIS case id)
 * @param data the raw record as a JSON tree
 */
public record SourceRecord(String id, JsonNode data) {}
