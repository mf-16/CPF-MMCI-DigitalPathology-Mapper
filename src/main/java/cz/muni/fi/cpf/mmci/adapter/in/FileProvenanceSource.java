package cz.muni.fi.cpf.mmci.adapter.in;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.muni.fi.cpf.mmci.domain.model.SourceRecord;
import cz.muni.fi.cpf.mmci.port.ProvenanceSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads every {@code *.json} file in a directory as a {@link SourceRecord}. This
 * is the stand-in for a real LIS source: replace it with another
 * {@link ProvenanceSource} that reads the database, nothing else changes.
 */
public final class FileProvenanceSource implements ProvenanceSource {

    private final Path dir;
    private final ObjectMapper mapper = new ObjectMapper();

    public FileProvenanceSource(Path dir) {
        this.dir = dir;
    }

    @Override
    public List<SourceRecord> read() {
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Not a directory: " + dir);
        }
        List<SourceRecord> records = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonNode data = mapper.readTree(file.toFile());
                String id = file.getFileName().toString().replaceFirst("\\.json$", "");
                records.add(new SourceRecord(id, data));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed reading source dir: " + dir, e);
        }
        return records;
    }
}
