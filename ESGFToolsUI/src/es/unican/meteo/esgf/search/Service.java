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
    HTTPSERVER("HTTPServer", "HTTPSERVER", "HTTPServer", "httpserver", "HTTP",
            "http", "httpServer", "HttpServer"),
    /** OPeNDAP. */
    OPENDAP("OPENDAP", "OPENDAP", "OPeNDAP", "opendap", "openDAP", "Opendap",
            "OpenDAP"),
    /** GridFTP. */
    GRIDFTP("GridFTP", "GRIDFTP", "GridFTP", "gridftp", "gridFTP"),
    /** LAS */
    LAS("LAS", "LAS", "las", "Las"),
    /** THREDDS. */
    CATALOG("Catalog THREDDS", "CATALOG", "Catalog", "Catalog THREDDS",
            "CATALOG THREDDS", "THREDDS", "thredds", "catalog thredds",
            "catalog THREDDS"),
    /** SRM: */
    SRM("SMR", "SMR");

    /** Service name. */
    private String serviceName;

    /** Tags of the service. */
    private String[] serviceTags;

    /**
     * Constructor, initialize the service name and the tags that identify the
     * service
     * 
     * @param serviceName
     * @param tags
     */
    private Service(String serviceName, String... tags) {
        this.serviceName = serviceName;
        this.serviceTags = new String[tags.length];

        for (int i = 0; i < tags.length; i++) {
            this.serviceTags[i] = tags[i];
        }
    }

    /**
     * Return the tags that identify the service in ESGF
     * 
     * @return
     */
    public String[] getTags() {
        return serviceTags;
    }

    /**
     * Returns the enum constant of the specified value
     * 
     * @param value
     * @return
     * 
     * @throws IllegalArgumentException
     *             if the String value is unknow
     */
    public static Service getEnum(String value) throws IllegalArgumentException {

        for (Service service : values()) {
            for (String tag : service.getTags()) {
                if (value.equals(tag)) {
                    return service;
                }
            }
        }

        throw new IllegalArgumentException("Unknown String Value: " + value);
    }

    @Override
    public String toString() {
        return serviceName;
    }
}
