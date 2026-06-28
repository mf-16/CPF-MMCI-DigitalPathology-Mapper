package cz.muni.fi.cpf.mmci.cli;

import cz.muni.fi.cpf.mmci.adapter.in.FileProvenanceSource;
import cz.muni.fi.cpf.mmci.adapter.map.DigitalPathologyMapper;
import cz.muni.fi.cpf.mmci.adapter.out.CpfStorageClient;
import cz.muni.fi.cpf.mmci.adapter.out.EcDocumentSigner;
import cz.muni.fi.cpf.mmci.adapter.out.FileDumpStore;
import cz.muni.fi.cpf.mmci.domain.MappingPipeline;
import cz.muni.fi.cpf.mmci.port.ProvenanceStore;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Composition root: wires the file source, the digital-pathology mapper, an EC
 * signer, and the output store(s) into the pipeline, then runs it.
 */
@Command(name = "map", mixinStandardHelpOptions = true,
        description = "Map LIS records into CPM bundles and write/upload them.")
public final class MapCommand implements Callable<Integer> {

    @Option(names = {"-i", "--in"}, required = true, description = "Input directory of LIS *.json files.")
    Path in;

    @Option(names = {"-o", "--out"}, defaultValue = "out",
            description = "Output directory for PROV-JSON + request bodies (default: ${DEFAULT-VALUE}).")
    Path out;

    @Option(names = "--post", description = "Also POST to this CPF-Storage base URL, e.g. http://localhost:8081/api/v1")
    String storeUrl;

    @Option(names = "--org", description = "Organization id for --post.")
    String orgId;

    @Override
    public Integer call() {
        ProvenanceStore fileStore = new FileDumpStore(out);
        ProvenanceStore store = fileStore;
        if (storeUrl != null) {
            if (orgId == null) {
                System.err.println("--post requires --org");
                return 2;
            }
            ProvenanceStore client = new CpfStorageClient(storeUrl, orgId);
            store = doc -> { fileStore.store(doc); client.store(doc); };
        }

        MappingPipeline pipeline = new MappingPipeline(
                new FileProvenanceSource(in),
                new DigitalPathologyMapper(),
                new EcDocumentSigner(),
                store);

        int count = pipeline.run();
        System.out.printf("Mapped %d record(s) -> %s%n", count, out.toAbsolutePath());
        return 0;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new MapCommand()).execute(args));
    }
}
