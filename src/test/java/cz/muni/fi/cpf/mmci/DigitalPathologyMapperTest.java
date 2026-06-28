package cz.muni.fi.cpf.mmci;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.muni.fi.cpf.mmci.adapter.map.DigitalPathologyMapper;
import cz.muni.fi.cpf.mmci.domain.model.MappedDocument;
import cz.muni.fi.cpf.mmci.domain.model.SourceRecord;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Self-check: the bundled sample case maps into the preanalytics_in bundle shown
 * in the reference diagram. Guards the mapping's core invariants.
 */
class DigitalPathologyMapperTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void mapsSampleIntoExpectedBundle() throws Exception {
        JsonNode sample;
        try (var in = getClass().getResourceAsStream("/samples/preanalytics-case-0.json")) {
            assertNotNull(in, "sample resource missing");
            sample = json.readTree(in);
        }

        MappedDocument doc = new DigitalPathologyMapper().map(new SourceRecord("case-0", sample));
        assertEquals("aplab:preanalyticsIn_bundle", doc.bundleName());

        JsonNode root = json.readTree(doc.provJson()); // must re-parse
        JsonNode bundles = root.get("bundle");
        assertNotNull(bundles, "no bundle in output");
        Map.Entry<String, JsonNode> bundle = bundles.fields().next();
        assertEquals("aplab:preanalyticsIn_bundle", bundle.getKey());
        JsonNode b = bundle.getValue();

        // 9 domain activities + 1 main activity
        assertEquals(10, b.get("activity").size(), "activity count");

        // the main activity carries cpm:mainActivity and a 9-part dct:hasPart
        JsonNode main = findByType(b.get("activity"), "cpm:mainActivity");
        assertNotNull(main, "no cpm:mainActivity");
        assertEquals(9, main.get("dct:hasPart").size(), "hasPart count");

        // both connectors present
        assertNotNull(findByType(b.get("entity"), "cpm:backwardConnector"), "no backward connector");
        assertNotNull(findByType(b.get("entity"), "cpm:forwardConnector"), "no forward connector");

        // backbone relations and id links exist
        assertTrue(b.has("used") && b.get("used").size() >= 9, "used edges");
        assertTrue(b.has("wasGeneratedBy") && b.get("wasGeneratedBy").size() >= 9, "generation edges");
        assertTrue(b.has("alternateOf") && b.get("alternateOf").size() == 9, "alternateOf edges");
        assertTrue(b.has("specializationOf") && b.get("specializationOf").size() == 2, "specializationOf edges");
        assertTrue(b.has("wasAttributedTo"), "wasAttributedTo");
        assertTrue(b.has("wasAssociatedWith"), "wasAssociatedWith");
    }

    /** Returns the first node in {@code container} whose prov:type includes {@code typeQn}. */
    private JsonNode findByType(JsonNode container, String typeQn) {
        if (container == null) return null;
        for (Iterator<JsonNode> it = container.elements(); it.hasNext(); ) {
            JsonNode node = it.next();
            JsonNode types = node.get("prov:type");
            if (types == null) continue;
            for (JsonNode t : types) {
                String value = t.isObject() ? t.path("$").asText() : t.asText();
                if (typeQn.equals(value)) return node;
            }
        }
        return null;
    }
}
