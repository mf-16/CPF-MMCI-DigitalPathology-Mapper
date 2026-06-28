package cz.muni.fi.cpf.mmci.port;

/** Output boundary: signs document bytes for storage. */
public interface DocumentSigner {
    byte[] sign(byte[] content);
}
