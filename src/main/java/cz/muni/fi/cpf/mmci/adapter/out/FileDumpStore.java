package cz.muni.fi.cpf.mmci.adapter.out;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cz.muni.fi.cpf.mmci.domain.model.SignedDocument;
import cz.muni.fi.cpf.mmci.port.ProvenanceStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Dry-run store: writes the PROV-JSON document and the exact CPF-Storage upload
 * body to a directory instead of POSTing it. The {@code *.request.json} file is
 * the {@code DocumentFormDTO} body for
 * {@code POST /api/v1/organizations/{orgId}/documents}.
 */
public final class FileDumpStore implements ProvenanceStore {

    private final Path outDir;
    private final ObjectMapper json = new ObjectMapper();

    public FileDumpStore(Path outDir) {
        this.outDir = outDir;
    }

    @Override
    public void store(SignedDocument document) {
        String name = document.document().bundleName().replace(':', '_');
        byte[] provJson = document.document().provJson();
        try {
            Files.createDirectories(outDir);
            Files.write(outDir.resolve(name + ".json"), provJson);

            ObjectNode body = json.createObjectNode();
            body.put("document", Base64.getEncoder().encodeToString(provJson));
            body.put("documentFormat", "JSON");
            body.put("signature", Base64.getEncoder().encodeToString(document.signature()));
            json.writerWithDefaultPrettyPrinter()
                .writeValue(outDir.resolve(name + ".request.json").toFile(), body);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed writing output for " + name, e);
        }
    }
}
