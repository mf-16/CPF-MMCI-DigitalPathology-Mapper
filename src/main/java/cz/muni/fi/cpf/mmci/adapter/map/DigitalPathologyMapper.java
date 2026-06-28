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
import org.openprovenance.prov.model.Entity;
import org.openprovenance.prov.model.Name;
import org.openprovenance.prov.model.Namespace;
import org.openprovenance.prov.model.QualifiedName;
import org.openprovenance.prov.model.Statement;
import org.openprovenance.prov.model.interop.Formats;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a LIS-style digital-pathology case into the {@code preanalyticsIn_bundle}
 * CPM bundle: a provenance backbone (sender agent, backward/forward connectors,
 * main activity, current agent) over the domain chain
 * (Tissue -> Accessioning -> ... -> Final slide).
 *
 * <p>All knowledge of the LIS JSON shape lives here. To change the mapping,
 * change this class; nothing else moves.
 */
public final class DigitalPathologyMapper implements CpmMapper {

    private static final String DCT = "http://purl.org/dc/terms/";

    private final org.openprovenance.prov.vanilla.ProvFactory pF = new org.openprovenance.prov.vanilla.ProvFactory();
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
        int[] relCounter = {0};

        // --- domain entities: specimen + each step output (+ alsoProduces) ---
        Map<String, QualifiedName> entityIds = new LinkedHashMap<>();
        Map<String, QualifiedName> identifierIds = new LinkedHashMap<>(); // externalId value -> identifier entity

        JsonNode specimen = root.get("specimen");
        addEntity(statements, entityIds, identifierIds, ns, prefix, specimen, relCounter, relNs);

        // --- domain activities, in order, with their used/generated edges ---
        List<QualifiedName> partActivities = new ArrayList<>();
        for (JsonNode step : root.withArray("steps")) {
            String actKey = step.get("activity").asText();
            QualifiedName actQn = qn(ns, prefix, actKey);
            partActivities.add(actQn);
            Activity activity = pF.newActivity(actQn, text(step, "name", actKey));
            setTimes(activity, step);
            statements.add(activity);

            if (step.hasNonNull("input")) {
                QualifiedName in = entityIds.get(step.get("input").asText());
                if (in != null) {
                    statements.add(pF.newUsed(relId(relNs, relCounter), actQn, in));
                }
            }
            if (step.has("output")) {
                QualifiedName out = addEntity(statements, entityIds, identifierIds, ns, prefix,
                        step.get("output"), relCounter, relNs);
                statements.add(pF.newWasGeneratedBy(relId(relNs, relCounter), out, actQn));
            }
            if (step.has("alsoProduces")) {
                QualifiedName also = addEntity(statements, entityIds, identifierIds, ns, prefix,
                        step.get("alsoProduces"), relCounter, relNs);
                statements.add(pF.newWasGeneratedBy(relId(relNs, relCounter), also, actQn));
            }
        }

        // --- backbone: sender agent + backward connector ---
        JsonNode sender = root.get("senderDepartment");
        QualifiedName senderQn = qn(ns, prefix, sender.get("id").asText());
        List<Attribute> senderAttrs = new ArrayList<>();
        if (sender.hasNonNull("contactIdPid")) {
            senderAttrs.add(cpmPF.newCpmAttribute(CpmAttribute.CONTACT_ID_PID,
                    sender.get("contactIdPid").asText(), name.XSD_STRING));
        }
        Agent senderAgent = cpmPF.newCpmAgent(senderQn, CpmType.SENDER_AGENT, senderAttrs);
        label(senderAgent, sender.get("name").asText());
        statements.add(senderAgent);

        JsonNode up = root.get("upstream");
        QualifiedName backConnQn = qn(ns, prefix, up.get("connectorId").asText());
        List<Attribute> backAttrs = new ArrayList<>();
        backAttrs.add(cpmPF.newCpmAttribute(CpmAttribute.EXTERNAL_ID, up.get("externalId").asText(), name.XSD_STRING));
        backAttrs.add(cpmPF.newCpmAttribute(CpmAttribute.REFERENCED_BUNDLE_ID, qn(ns, prefix, up.get("referencedBundleId").asText())));
        backAttrs.add(cpmPF.newCpmAttribute(CpmAttribute.REFERENCED_META_BUNDLE_ID, qn(ns, prefix, up.get("referencedMetaBundleId").asText())));
        backAttrs.add(cpmPF.newCpmAttribute(CpmAttribute.REFERENCED_BUNDLE_HASH_VALUE, up.get("referencedBundleHashValue").asText(), name.XSD_STRING));
        backAttrs.add(cpmPF.newCpmAttribute(CpmAttribute.HASH_ALG, up.get("hashAlg").asText(), name.XSD_STRING));
        Entity backConnector = cpmPF.newCpmEntity(backConnQn, CpmType.BACKWARD_CONNECTOR, backAttrs);
        label(backConnector, text(up, "connectorName", "Sample connector"));
        statements.add(backConnector);
        // ponytail: dropped the diagram's standalone surDep:preanalyticsOut_bundle line —
        // it is already captured by REFERENCED_BUNDLE_ID. Add back if a profile demands it.

        // --- backbone: main activity over the domain steps ---
        QualifiedName mainQn = qn(ns, prefix, "physical-slide-preparation");
        List<Attribute> mainAttrs = new ArrayList<>();
        mainAttrs.add(cpmPF.newCpmAttribute(CpmAttribute.REFERENCED_META_BUNDLE_ID,
                qn(ns, prefix, text(bundle, "referencedMetaBundleId", "preanalyticsIn_bundle_meta"))));
        QualifiedName hasPart = pF.newQualifiedName(DCT, "hasPart", "dct");
        for (QualifiedName part : partActivities) {
            mainAttrs.add(pF.newOther(hasPart, part, name.PROV_QUALIFIED_NAME));
        }
        Activity mainActivity = cpmPF.newCpmActivity(mainQn, null, null, CpmType.MAIN_ACTIVITY, mainAttrs);
        label(mainActivity, "Physical slide preparation");
        statements.add(mainActivity);

        // --- backbone: current agent (the lab) ---
        JsonNode lab = root.get("laboratory");
        QualifiedName labQn = qn(ns, prefix, lab.get("id").asText());
        Agent currentAgent = pF.newAgent(labQn, lab.get("name").asText());
        // CpmType has no CURRENT_AGENT in this version; set the cpm:currentAgent type directly.
        currentAgent.getType().add(pF.newType(cpmPF.newCpmQualifiedName("currentAgent"), name.PROV_QUALIFIED_NAME));
        statements.add(currentAgent);

        // --- backbone relations ---
        statements.add(pF.newWasAttributedTo(relId(relNs, relCounter), backConnQn, senderQn));
        statements.add(pF.newUsed(relId(relNs, relCounter), mainQn, backConnQn));
        statements.add(pF.newWasAssociatedWith(relId(relNs, relCounter), mainQn, labQn));
        // Tissue (specimen) specializes the incoming Sample connector.
        statements.add(pF.newSpecializationOf(entityIds.get(specimen.get("id").asText()), backConnQn));

        // --- backbone: forward connectors ---
        for (JsonNode fc : root.withArray("forwardConnectors")) {
            QualifiedName fcQn = qn(ns, prefix, fc.get("id").asText());
            List<Attribute> fcAttrs = new ArrayList<>();
            if (fc.hasNonNull("externalId")) {
                fcAttrs.add(cpmPF.newCpmAttribute(CpmAttribute.EXTERNAL_ID, fc.get("externalId").asText(), name.XSD_STRING));
            }
            Entity fwd = cpmPF.newCpmEntity(fcQn, CpmType.FORWARD_CONNECTOR, fcAttrs);
            label(fwd, text(fc, "name", fc.get("id").asText()));
            statements.add(fwd);
            statements.add(pF.newWasGeneratedBy(relId(relNs, relCounter), fcQn, mainQn));
            statements.add(pF.newWasDerivedFrom(fcQn, backConnQn));
            if (fc.hasNonNull("specializationOf")) {
                QualifiedName spec = entityIds.get(fc.get("specializationOf").asText());
                if (spec != null) {
                    statements.add(pF.newSpecializationOf(spec, fcQn));
                }
            }
        }

        // --- identifier entities (one per external id) ---
        for (Map.Entry<String, QualifiedName> e : identifierIds.entrySet()) {
            List<Attribute> idAttrs = new ArrayList<>();
            idAttrs.add(cpmPF.newCpmAttribute(CpmAttribute.EXTERNAL_ID, e.getKey(), name.XSD_STRING));
            Entity idEntity = cpmPF.newCpmEntity(e.getValue(), CpmType.IDENTIFIER, idAttrs);
            statements.add(idEntity);
        }

        // --- assemble bundle + document ---
        String bundleLocal = text(bundle, "localName", "preanalyticsIn_bundle");
        QualifiedName bundleQn = qn(ns, prefix, bundleLocal);
        Namespace namespace = cpmPF.newCpmNamespace();
        namespace.register(prefix, ns);
        namespace.register("dct", DCT);
        namespace.register("rel", relNs);

        Document document = pF.newDocument();
        document.getStatementOrBundle().add(pF.newNamedBundle(bundleQn, namespace, statements));
        document.setNamespace(namespace);

        return new MappedDocument(prefix + ":" + bundleLocal, serialize(document));
    }

    /** Creates an entity for a {id,name,externalId} node, wires externalId attr + alternateOf. */
    private QualifiedName addEntity(List<Statement> out, Map<String, QualifiedName> entityIds,
                                    Map<String, QualifiedName> identifierIds, String ns, String prefix,
                                    JsonNode node, int[] relCounter, String relNs) {
        String id = node.get("id").asText();
        QualifiedName qn = qn(ns, prefix, id);
        if (entityIds.containsKey(id)) {
            return qn; // already created
        }
        Entity entity = pF.newEntity(qn, text(node, "name", id));
        if (node.hasNonNull("externalId")) {
            String ext = node.get("externalId").asText();
            entity.getOther().add(cpmPF.newCpmAttribute(CpmAttribute.EXTERNAL_ID, ext, name.XSD_STRING));
            QualifiedName idEntity = identifierIds.computeIfAbsent(ext,
                    k -> qn(ns, prefix, "id-" + k));
            out.add(pF.newAlternateOf(qn, idEntity));
        }
        out.add(entity);
        entityIds.put(id, qn);
        return qn;
    }

    private void setTimes(Activity activity, JsonNode step) {
        if (step.hasNonNull("start")) {
            activity.setStartTime(cal(step.get("start").asText()));
        }
        if (step.hasNonNull("end")) {
            activity.setEndTime(cal(step.get("end").asText()));
        }
    }

    private XMLGregorianCalendar cal(String iso) {
        return xml.newXMLGregorianCalendar(iso);
    }

    private void label(org.openprovenance.prov.model.Element element, String text) {
        element.getLabel().add(pF.newInternationalizedString(text));
    }

    private QualifiedName qn(String ns, String prefix, String local) {
        return pF.newQualifiedName(ns, local, prefix);
    }

    private QualifiedName relId(String relNs, int[] counter) {
        return pF.newQualifiedName(relNs, "r" + (counter[0]++), "rel");
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
