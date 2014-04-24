package es.unican.meteo.esgf.search;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * <p>
 * The RESTful class is a abstract class that represents the structure of ESGF
 * RESTful API.
 * </p>
 * 
 * <p>
 * Allows access to data in ESGF by URL in ESGF index node and return results
 * matching the given constraints. Because of the distributed capabilities of
 * the ESGF search, the URL at any Index Node can be used to query that Node
 * only, or all Nodes in the ESGF system.
 * </p>
 * 
 * <p>
 * RESTful API that is used to query the ESGF search services can also be used,
 * with minor modifications, to generate a wget script to download all files
 * matching the given constraints. This functionality is extended and
 * implemented in {@link RESTfulSearch} and {@link RESTfulWget}.
 * </p>
 * 
 * <p>
 * The general syntax of the ESGF RESTful service URL is:
 * </p>
 * 
 * <ul>
 * <li>
 * <b>Search service: </b>
 * <code> http://baseURL/search?[keyword parameters as (name,
 * value) pairs][facet parameters as (name,value) pairs]</code>
 * </li>
 * <li><b>Wget service: </b>
 * <code>http://baseURL/wget?[keyword parameters as (name,
 * value) pairs][facet parameters as (name,value) pairs]</code>
 * </li>
 * </ul>
 * 
 * <p>
 * or more explicity:
 * </p>
 * <li>http://base search
 * url/?[query=...][offset=...][limit=...][type=...][format=
 * ...][facets=...][fields
 * =...][lat,lon,radius,polygon,location=...][start,end=...
 * ][from,to=...][facet1=value1][facet2=value2][...]</li>
 * 
 * <p>
 * Use generateServiceURL() method for generate url of RESTful service.
 * </p>
 * 
 * @author Karem Terry
 * 
 */
public abstract class RESTful implements Serializable, Cloneable {

    /**
     * Base service url of RESTful, is an url of an ESGF index node. The RESTful
     * service request will be processed in that node.
     */
    protected String indexNode = null;

    /**
     * Parameters are query parameters that have reserved names, and are
     * interpreted by the search service to control the fundamental nature of a
     * search request.
     */
    protected Parameters parameters;

    /**
     * Constructor, creates a RESTful service
     * 
     * @param indexNodeURL
     *            url of ESGF node where the RESTful service request will be
     *            processed
     */
    public RESTful(String indexNodeURL) {
        // if isn't complete url
        if (!indexNodeURL.substring(0, 7).equals("http://")) {
            // Add "http://"
            indexNodeURL = "http://" + indexNodeURL;
        }

        // Initialize base service url
        this.indexNode = indexNodeURL;

        // Initialize service parameters
        this.parameters = new Parameters();
    }

    public RESTful() {
    }

    /**
     * Get base URL.
     * 
     * @return indexNode is an url of an ESGF node. The RESTful service request
     *         will be processed in that node.
     */
    public String getIndexNode() {
        return indexNode;
    }

    /**
     * Set base URL.
     * 
     * @param indexNode
     *            url of ESGF node where the RESTful service request will be
     *            processed
     * 
     * @throws MalformedURLException
     */
    public void setIndexNode(String indexNode) {
        // if isn't complete url
        if (!indexNode.substring(0, 7).equals("http://")) {
            // Add "http://"
            indexNode = "http://" + indexNode;
        }
        this.indexNode = indexNode;
    }

    /**
     * Get {@link Parameters} of service.
     * 
     * @return parameters configured in this RESTful service
     */
    public Parameters getParameters() {
        return parameters;
    }

    /**
     * Set {@link Parameters} parameters of service.
     * 
     * @param parameters
     *            the Parameters to set
     */
    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    /**
     * Constructs url of service RESTful. The general syntax of the ESGF service
     * URL is:
     * 
     * http://<base_URL>/[search? or wget?][keyword parameters as (name, value)
     * pairs][facet parameters as (name,value) pairs]
     * 
     * @return URL
     */
    public abstract URL generateServiceURL();

    @Override
    public Object clone() throws CloneNotSupportedException {
        Object clone = null;
        clone = super.clone();
        // ('deep clone')

        ((RESTful) clone).setParameters((Parameters) parameters.clone());

        return clone;
    }
}
