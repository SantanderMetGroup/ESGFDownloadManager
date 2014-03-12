package es.unican.meteo.esgf.search;

import java.io.Serializable;

/**
 * To specify the response document output format .
 * 
 * @author Karem Terry
 * 
 */
public enum Format implements Serializable {
    JSON, XML;

    /** Return format of response documen formated in format query string value. */
    @Override
    public String toString() {
        switch (this) {
            case JSON:
                return "application/solr+json";
            case XML:
                return "application/solr+xml";
            default:
                return null;
        }
    }
}
