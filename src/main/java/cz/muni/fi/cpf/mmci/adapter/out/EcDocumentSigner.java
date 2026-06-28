package cz.muni.fi.cpf.mmci.adapter.out;

import cz.muni.fi.cpf.mmci.port.DocumentSigner;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

/**
 * Signs document bytes with {@code SHA256withECDSA} — the scheme CPF-Storage
 * verifies. By default it generates an ephemeral P-256 key pair so the produced
 * request body is complete; for a real upload, construct it with the
 * organization's private key instead.
 */
public final class EcDocumentSigner implements DocumentSigner {

    private final PrivateKey privateKey;

    /** Uses a freshly generated P-256 key pair (demo / dry-run). */
    public EcDocumentSigner() {
        this.privateKey = generateKey();
    }

    /** Uses a supplied EC private key (e.g. the registered organization's key). */
    public EcDocumentSigner(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    @Override
    public byte[] sign(byte[] content) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(content);
            return signature.sign();
        } catch (Exception e) {
            throw new IllegalStateException("Signing failed", e);
        }
    }

    private static PrivateKey generateKey() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = gen.generateKeyPair();
            return pair.getPrivate();
        } catch (Exception e) {
            throw new IllegalStateException("EC key generation failed", e);
        }
    }
}
