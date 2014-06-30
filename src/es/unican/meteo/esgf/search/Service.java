package es.unican.meteo.esgf.search;

import java.io.Serializable;

/***
 * Lists the services offered by ESGF for access to data.
 * 
 * @author Karem Terry
 * 
 */
public enum Service implements Serializable {
    /** Http Server. */
    HTTPSERVER("HTTPServer"),
    /** OPeNDAP. */
    OPENDAP("OPENDAP"),
    /** GridFTP. */
    GRIDFTP("GridFTP"),
    /** LAS */
    LAS("LAS"),
    /** SHA-384. */
    CATALOG("Catalog THREDDS"),
    /** SRM: */
    SRM("SMR");

    /** Checksum name. */
    private String serviceName;

    // Constructor, initialize the service name
    private Service(String serviceName) {
        this.serviceName = serviceName;
    }

    // Return
    @Override
    public String toString() {
        return serviceName;
    }
}
