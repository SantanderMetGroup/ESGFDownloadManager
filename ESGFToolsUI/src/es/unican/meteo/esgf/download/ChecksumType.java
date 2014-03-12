package es.unican.meteo.esgf.download;

import java.io.Serializable;

/**
 * Checksum Algorithms
 * 
 * @author terryk
 * 
 */
public enum ChecksumType implements Serializable {

    /** MD2. */
    MD2("MD2"),
    /** MD5. */
    MD5("MD5"),
    /** SHA-1. */
    SHA1("SHA-1"),
    /** SHA-256. */
    SHA256("SHA-256"),
    /** SHA-384. */
    SHA384("SHA-384"),
    /** SHA-512. */
    SHA512("SHA-512");

    /** Checksum name. */
    private String checksumName;

    // Constructor, initialize the available algorithm checksum names
    private ChecksumType(String checksumName) {
        this.checksumName = checksumName;
    }

    // Return
    @Override
    public String toString() {
        return checksumName;
    }
}
