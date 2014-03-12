package es.unican.meteo.search;

import java.io.Serializable;

/**
 * Enum that list all intrinsic type of the record. Type of metadata
 * 
 * @author Karem Terry
 * 
 */
public enum RecordType implements Serializable {
    /** Dataset. */
    DATASET("Dataset"),

    /** File. */
    FILE("File"),

    /** Aggregation. */
    AGGREGATION("Aggregation");

    /** record type string */
    private String recordType;

    // Constructor, initialize the service name
    private RecordType(String recordType) {
        this.recordType = recordType;
    }

    // Return
    @Override
    public String toString() {
        return recordType;
    }

}
