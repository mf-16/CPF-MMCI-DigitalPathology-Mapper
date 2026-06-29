package cz.muni.fi.cpf.mmci.adapter.map;

import com.fasterxml.jackson.databind.JsonNode;
import cz.muni.fi.cpf.mmci.domain.model.MappedDocument;
import cz.muni.fi.cpf.mmci.domain.model.SourceRecord;
import cz.muni.fi.cpf.mmci.port.CpmMapper;
import cz.muni.fi.cpm.constants.CpmAttribute;
import cz.muni.fi.cpm.constants.CpmType;
import cz.muni.fi.cpm.model.ICpmProvFactory;
import cz.muni.fi.cpm.vanilla.CpmProvFactory;
import org.openprovenance.prov.interop.InteropFramework;
import org.openprovenance.prov.model.Activity;
import org.openprovenance.prov.model.Agent;
import org.openprovenance.prov.model.Attribute;
import org.openprovenance.prov.model.Document;
import org.openprovenance.prov.model.Element;
import org.openprovenance.prov.model.Entity;
import org.openprovenance.prov.model.Name;
import org.openprovenance.prov.model.Namespace;
import org.openprovenance.prov.model.QualifiedName;
import org.openprovenance.prov.model.Statement;
import org.openprovenance.prov.model.interop.Formats;
import org.openprovenance.prov.vanilla.ProvFactory;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a digital-pathology case into a CPM bundle. The input lists the nodes
 * (agents, connectors, main activity, domain entities/activities) and the domain
 * relations explicitly; this mapper:
 *
 * <ul>
 *   <li>types each node with its CPM type,</li>
 *   <li><b>generates the traversal-information backbone</b> from the typed nodes
 *       (so it is always correct): backwardConnector wasAttributedTo senderAgent,
 *       mainActivity used backwardConnector, mainActivity wasAssociatedWith
 *       currentAgent, forwardConnector wasGeneratedBy mainActivity, and each
 *       connector specializationOf its domain entity,</li>
 *   <li>and translates the domain {@code relations[]} one-to-one.</li>
 * </ul>
 *
 * To change the mapping, change this class; nothing else moves.
 */
public final class DigitalPathologyMapper implements CpmMapper {

    private static final String DCT = "http://purl.org/dc/terms/";

    private final ProvFactory pF = new ProvFactory();
    private final ICpmProvFactory cpmPF = new CpmProvFactory(pF);
    private final Name name = pF.getName();
    private final DatatypeFactory xml;

    public DigitalPathologyMapper() {
        try {
            this.xml = DatatypeFactory.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("DatatypeFactory init failed", e);
        }
    }

    @Override
    public MappedDocument map(SourceRecord record) {
        JsonNode root = record.data();
        JsonNode bundle = root.get("bundle");
        String prefix = text(bundle, "prefix", "aplab");
        String ns = text(bundle, "namespace", "http://mmci.cz/" + prefix + "/");
        String relNs = ns + "rel/";

        List<Statement> statements = new ArrayList<>();
        Map<String, QualifiedName> nodes = new LinkedHashMap<>(); // every node id -> QN
        int[] rel = {0};

        // --- agents ---
        QualifiedName senderQn = null;
        QualifiedName currentQn = null;
        for (JsonNode a : root.withArray("agents")) {
            String id = a.get("id").asText();
            QualifiedName qn = qn(ns, prefix, id);
            String cpmType = text(a, "cpmType", "");
            List<Attribute> attrs = new ArrayList<>();
            if (a.hasNonNull("contactIdPid")) {
                attrs.add(cpmPF.newCpmAttribute(CpmAttribute.CONTACT_ID_PID, a.get("contactIdPid").asText(), name.XSD_STRING));
            }
            Agent agent;
            if ("senderAgent".equals(cpmType)) {
                agent = cpmPF.newCpmAgent(qn, CpmType.SENDER_AGENT, attrs);
                senderQn = qn;
            } else if ("receiverAgent".equals(cpmType)) {
                agent = cpmPF.newCpmAgent(qn, CpmType.RECEIVER_AGENT, attrs);
            } else {
                // CpmType has no CURRENT_AGENT in this version; set cpm:currentAgent directly.
                agent = pF.newAgent(qn);
                attrs.forEach(att -> agent.getOther().add((org.openprovenance.prov.model.Other) att));
                agent.getType().add(pF.newType(cpmPF.newCpmQualifiedName("currentAgent"), name.PROV_QUALIFIED_NAME));
                currentQn = qn;
            }
            label(agent, text(a, "name", id));
            statements.add(agent);
            nodes.put(id, qn);
        }

        // --- main activity (over the domain activities) ---
        JsonNode mainNode = root.get("mainActivity");
        String mainId = mainNode.get("id").asText();
        QualifiedName mainQn = qn(ns, prefix, mainId);
        List<Attribute> mainAttrs = new ArrayList<>();
        if (mainNode.hasNonNull("referencedMetaBundleId")) {
            mainAttrs.add(cpmPF.newCpmAttribute(CpmAttribute.REFERENCED_META_BUNDLE_ID,
                    qn(ns, prefix, mainNode.get("referencedMetaBundleId").asText())));
        }
        QualifiedName hasPart = pF.newQualifiedName(DCT, "hasPart", "dct");
        for (JsonNode act : root.withArray("activities")) {
            mainAttrs.add(pF.newOther(hasPart, qn(ns, prefix, act.get("id").asText()), name.PROV_QUALIFIED_NAME));
        }
        Activity mainActivity = cpmPF.newCpmActivity(mainQn, null, null, CpmType.MAIN_ACTIVITY, mainAttrs);
        label(mainActivity, text(mainNode, "name", mainId));
        statements.add(mainActivity);
        nodes.put(mainId, mainQn);

        // --- connectors ---
        List<QualifiedName> backwardConnectors = new ArrayList<>();
        List<QualifiedName> forwardConnectors = new ArrayList<>();
        List<String[]> connectorSpecializations = new ArrayList<>(); // {connectorId, targetEntityId}
        for (JsonNode c : root.withArray("connectors")) {
            String id = c.get("id").asText();
            QualifiedName qn = qn(ns, prefix, id);
            boolean backward = "backwardConnector".equals(text(c, "cpmType", ""));
            List<Attribute> attrs = new ArrayList<>();
            if (c.hasNonNull("externalId")) {
                attrs.add(cpmPF.newCpmAttribute(CpmAttribute.EXTERNAL_ID, c.get("externalId").asText(), name.XSD_STRING));
            }
            if (c.hasNonNull("referencedBundleId")) {
                attrs.add(cpmPF.newCpmAttribute(CpmAttribute.REFERENCED_BUNDLE_ID, qn(ns, prefix, c.get("referencedBundleId").asText())));
            }
            if (c.hasNonNull("referencedMetaBundleId")) {
                attrs.add(cpmPF.newCpmAttribute(CpmAttribute.REFERENCED_META_BUNDLE_ID, qn(ns, prefix, c.get("referencedMetaBundleId").asText())));
            }
            if (c.hasNonNull("referencedBundleHashValue")) {
                attrs.add(cpmPF.newCpmAttribute(CpmAttribute.REFERENCED_BUNDLE_HASH_VALUE, c.get("referencedBundleHashValue").asText(), name.XSD_STRING));
            }
            if (c.hasNonNull("hashAlg")) {
                attrs.add(cpmPF.newCpmAttribute(CpmAttribute.HASH_ALG, c.get("hashAlg").asText(), name.XSD_STRING));
            }
            Entity connector = cpmPF.newCpmEntity(qn, backward ? CpmType.BACKWARD_CONNECTOR : CpmType.FORWARD_CONNECTOR, attrs);
            label(connector, text(c, "name", id));
            statements.add(connector);
            nodes.put(id, qn);
            (backward ? backwardConnectors : forwardConnectors).add(qn);
            if (c.hasNonNull("specializationOf")) {
                connectorSpecializations.add(new String[]{id, c.get("specializationOf").asText()});
            }
        }

        // --- domain entities ---
        for (JsonNode e : root.withArray("entities")) {
            String id = e.get("id").asText();
            QualifiedName qn = qn(ns, prefix, id);
            Entity entity = pF.newEntity(qn);
            label(entity, text(e, "name", id));
            if (e.hasNonNull("externalId")) {
                entity.getOther().add(cpmPF.newCpmAttribute(CpmAttribute.EXTERNAL_ID, e.get("externalId").asText(), name.XSD_STRING));
            }
            statements.add(entity);
            nodes.put(id, qn);
        }

        // --- domain activities ---
        for (JsonNode act : root.withArray("activities")) {
            String id = act.get("id").asText();
            QualifiedName qn = qn(ns, prefix, id);
            Activity activity = pF.newActivity(qn);
            label(activity, text(act, "name", id));
            if (act.hasNonNull("start")) activity.setStartTime(cal(act.get("start").asText()));
            if (act.hasNonNull("end")) activity.setEndTime(cal(act.get("end").asText()));
            statements.add(activity);
            nodes.put(id, qn);
        }

        // --- traversal-information backbone (generated, always correct) ---
        for (QualifiedName b : backwardConnectors) {
            if (senderQn != null) statements.add(pF.newWasAttributedTo(relId(relNs, rel), b, senderQn));
            statements.add(pF.newUsed(relId(relNs, rel), mainQn, b));
        }
        for (QualifiedName f : forwardConnectors) {
            statements.add(pF.newWasGeneratedBy(relId(relNs, rel), f, mainQn));
        }
        if (currentQn != null) {
            statements.add(pF.newWasAssociatedWith(relId(relNs, rel), mainQn, currentQn));
        }
        for (String[] cs : connectorSpecializations) {
            // connector is the specific entity, the domain entity is the general one
            statements.add(pF.newSpecializationOf(nodes.get(cs[0]), require(nodes, cs[1])));
        }

        // --- domain relations (one-to-one from the input) ---
        for (JsonNode r : root.withArray("relations")) {
            statements.add(relation(r, nodes, relNs, rel));
        }

        // --- assemble bundle + document ---
        String bundleLocal = text(bundle, "localName", "preanalyticsIn_bundle");
        Namespace namespace = cpmPF.newCpmNamespace();
        namespace.register(prefix, ns);
        namespace.register("dct", DCT);
        namespace.register("rel", relNs);

        Document document = pF.newDocument();
        document.getStatementOrBundle().add(pF.newNamedBundle(qn(ns, prefix, bundleLocal), namespace, statements));
        document.setNamespace(namespace);

        return new MappedDocument(prefix + ":" + bundleLocal, serialize(document));
    }

    private Statement relation(JsonNode r, Map<String, QualifiedName> nodes, String relNs, int[] rel) {
        String type = r.get("type").asText();
        QualifiedName from = require(nodes, r.get("from").asText());
        QualifiedName to = require(nodes, r.get("to").asText());
        return switch (type) {
            case "used" -> pF.newUsed(relId(relNs, rel), from, to);                 // from=activity, to=entity
            case "wasGeneratedBy" -> pF.newWasGeneratedBy(relId(relNs, rel), from, to); // from=entity, to=activity
            case "wasDerivedFrom" -> pF.newWasDerivedFrom(from, to);
            case "wasAttributedTo" -> pF.newWasAttributedTo(relId(relNs, rel), from, to); // from=entity, to=agent
            case "wasAssociatedWith" -> pF.newWasAssociatedWith(relId(relNs, rel), from, to); // from=activity, to=agent
            case "specializationOf" -> pF.newSpecializationOf(from, to);            // from=specific, to=general
            case "alternateOf" -> pF.newAlternateOf(from, to);
            default -> throw new IllegalArgumentException("Unknown relation type: " + type);
        };
    }

    private void label(Element element, String text) {
        element.getLabel().add(pF.newInternationalizedString(text));
    }

    private QualifiedName qn(String ns, String prefix, String local) {
        return pF.newQualifiedName(ns, local, prefix);
    }

    private QualifiedName relId(String relNs, int[] counter) {
        return pF.newQualifiedName(relNs, "r" + (counter[0]++), "rel");
    }

    private static QualifiedName require(Map<String, QualifiedName> nodes, String id) {
        QualifiedName qn = nodes.get(id);
        if (qn == null) throw new IllegalArgumentException("Relation references unknown node: " + id);
        return qn;
    }

    private XMLGregorianCalendar cal(String iso) {
        return xml.newXMLGregorianCalendar(iso);
    }

    private static String text(JsonNode node, String field, String fallback) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : fallback;
    }

    private byte[] serialize(Document document) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        new InteropFramework().writeDocument(bos, document, Formats.ProvFormat.JSON);
        return bos.toByteArray();
    }
}
