package cz.muni.fi.cpf.mmci;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.muni.fi.cpf.mmci.adapter.map.DigitalPathologyMapper;
import cz.muni.fi.cpf.mmci.domain.model.MappedDocument;
import cz.muni.fi.cpf.mmci.domain.model.SourceRecord;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Self-check: the sample case maps into a bundle with a correct
 * traversal-information backbone and the domain relations from the input.
 */
class DigitalPathologyMapperTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void mapsSampleIntoBackbonePlusDomain() throws Exception {
        JsonNode sample;
        try (var in = getClass().getResourceAsStream("/samples/preanalytics-case-0.json")) {
            assertNotNull(in, "sample resource missing");
            sample = json.readTree(in);
        }

        MappedDocument doc = new DigitalPathologyMapper().map(new SourceRecord("case-0", sample));
        assertEquals("aplab:preanalyticsIn_bundle", doc.bundleName());

        JsonNode root = json.readTree(doc.provJson()); // must re-parse
        JsonNode b = root.get("bundle").fields().next().getValue();

        // node counts
        assertEquals(5, b.get("entity").size(), "entities (3 domain + 2 connectors)");
        assertEquals(3, b.get("activity").size(), "activities (2 domain + main)");
        assertEquals(2, b.get("agent").size(), "agents");

        // exactly the typed traversal-info nodes exist
        assertNotNull(byType(b.get("entity"), "cpm:backwardConnector"), "backwardConnector");
        assertNotNull(byType(b.get("entity"), "cpm:forwardConnector"), "forwardConnector");
        JsonNode main = byType(b.get("activity"), "cpm:mainActivity");
        assertNotNull(main, "mainActivity");
        assertNotNull(byType(b.get("agent"), "cpm:senderAgent"), "senderAgent");
        assertNotNull(byType(b.get("agent"), "cpm:currentAgent"), "currentAgent");

        // main activity has both domain steps as parts
        assertEquals(2, main.get("dct:hasPart").size(), "hasPart");

        // backbone edges (1 each) + domain edges (2 each)
        assertEquals(3, b.get("used").size(), "used (main->back + 2 domain)");
        assertEquals(3, b.get("wasGeneratedBy").size(), "wasGeneratedBy (fwd->main + 2 domain)");
        assertEquals(1, b.get("wasAttributedTo").size(), "wasAttributedTo (back->sender)");
        assertEquals(1, b.get("wasAssociatedWith").size(), "wasAssociatedWith (main->current)");

        // specializationOf: 2, and the connector is the SPECIFIC entity (direction check)
        JsonNode spec = b.get("specializationOf");
        assertEquals(2, spec.size(), "specializationOf");
        Set<String> connectors = Set.of("aplab:sample-connector", "aplab:slide-connector");
        for (JsonNode s : spec) {
            assertTrue(connectors.contains(s.get("prov:specificEntity").asText()),
                    "connector must be the specific entity, got " + s.get("prov:specificEntity").asText());
        }
    }

    /** Returns the first node in {@code container} whose prov:type includes {@code typeQn}. */
    private JsonNode byType(JsonNode container, String typeQn) {
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
