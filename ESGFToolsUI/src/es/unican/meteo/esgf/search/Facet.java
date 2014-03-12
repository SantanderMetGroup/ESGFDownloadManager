package es.unican.meteo.esgf.search;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * <p>
 * The Facet class represents ESGF RESTful service facets.
 * </p>
 * 
 * <p>
 * Facet are "categories" that can be used to apply constraints to REStful
 * service. Facets are formed by a name-value pair. Any parameter which is not a
 * keyword parameter is interpreted by the system as a facet parameter.
 * </p>
 * 
 * <p>
 * As for keyword parameter values, facet values must be properly URL-encoded.
 * </p>
 * 
 * @author Karem Terry
 * 
 */
public class Facet implements Cloneable {

    /**
     * Encode format of facet values
     */
    private static final String ENCODE_FORMAT = "UTF-8";

    /** Name of facet */
    private String name;

    /** Value of facet */
    private String value;

    /**
     * Empty constructor.
     */
    public Facet() {
    }

    /**
     * Constructor.
     * 
     * @param name
     *            of facet
     * @param value
     *            of facet
     */
    public Facet(String name, String value) {
        this.setName(name);
        this.setValue(value);
    }

    /**
     * Get name of facet.
     * 
     * @return name of facet
     */
    public String getName() {
        return name;
    }

    /**
     * Set facet name.
     * 
     * @param name
     *            of facet
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get value of facet
     * 
     * @return value
     */
    public String getValue() {
        return value;
    }

    /**
     * Set value of facet.
     * 
     * @param value
     *            of facet
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Facet converted into String
     * 
     * @return String representation of facet
     */
    @Override
    public String toString() {
        return "Facet [name=" + name + ", value=" + value + "]";
    }

    /**
     * Return encoded facet string like a parameter key-value of a url query
     * string
     * 
     * @return encoded string
     * @throws UnsupportedEncodingException
     */
    public String toQueryString() {
        try {
            return URLEncoder.encode(name, ENCODE_FORMAT) + "="
                    + URLEncoder.encode(value, ENCODE_FORMAT);
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Facet other = (Facet) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

    @Override
    public Object clone() {
        Object clone = null;
        try {
            clone = super.clone();
        } catch (CloneNotSupportedException e) {
            //
        }

        return clone;
    }

}
