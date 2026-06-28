package cz.muni.fi.cpf.mmci.adapter.out;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cz.muni.fi.cpf.mmci.domain.model.SignedDocument;
import cz.muni.fi.cpf.mmci.port.ProvenanceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

/**
 * Optional live upload to CPF-Storage:
 * {@code POST {baseUrl}/organizations/{orgId}/documents}.
 *
 * <p>Best-effort: a non-2xx response (expected without a running Trusted Party,
 * which verifies the signature) is logged, not thrown — so a dry run is never
 * broken by an unreachable store.
 */
public final class CpfStorageClient implements ProvenanceStore {

    private static final Logger log = LoggerFactory.getLogger(CpfStorageClient.class);

    private final String baseUrl;
    private final String orgId;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    /** @param baseUrl e.g. {@code http://localhost:8081/api/v1} (no trailing slash needed) */
    public CpfStorageClient(String baseUrl, String orgId) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.orgId = orgId;
    }

    @Override
    public void store(SignedDocument document) {
        byte[] provJson = document.document().provJson();
        ObjectNode body = json.createObjectNode();
        body.put("document", Base64.getEncoder().encodeToString(provJson));
        body.put("documentFormat", "JSON");
        body.put("signature", Base64.getEncoder().encodeToString(document.signature()));

        String url = baseUrl + "/organizations/" + orgId + "/documents";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                log.info("Uploaded {} -> {}", document.document().bundleName(), response.statusCode());
            } else {
                log.warn("Upload of {} rejected ({}): {}",
                        document.document().bundleName(), response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Upload of {} failed: {}", document.document().bundleName(), e.toString());
        }
    }
}
