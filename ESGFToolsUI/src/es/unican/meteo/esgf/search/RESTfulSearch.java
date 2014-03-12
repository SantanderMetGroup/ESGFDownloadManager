package es.unican.meteo.esgf.search;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * <p>
 * The RESTfulSearch class extends {@link RESTful}, to implement search
 * functionality.
 * </p>
 * 
 * <p>
 * Allows access to data in ESGF by URL in ESGF index node and return results
 * matching the given constraints. Because of the distributed capabilities of
 * the ESGF search, the URL at any Index Node (baseUrl) can be used to query
 * that Node only, or all Nodes in the ESGF system.
 * </p>
 * 
 * <p>
 * The general syntax of the ESGF RESTful service URL is:
 * </p>
 * 
 * <ul>
 * <li>
 * <b>Search service: </b>
 * <code>http://baseURL/search?[keyword parameters as (name,
 * value) pairs][facet parameters as (name,value) pairs]</code>
 * </li>
 * </ul>
 * 
 * <p>
 * Use generateServiceURL() method for generate url of search RESTful service.
 * </p>
 * 
 * <p>
 * Default values if there aren't configured keywords:
 * <ul>
 * <li><b>limit:</b> 10</li>
 * <li><b>offset:</b> 0</li>
 * <li><b>distrib:</b> true</li>
 * <li><b>fields:</b> *</li>
 * </ul>
 * 
 * </p>
 * 
 * @author Karem Terry
 * 
 */
public class RESTfulSearch extends RESTful {

    public RESTfulSearch() {
        super();
    }

    /**
     * Constructor, creates a RESTful search service
     * 
     * @param indexNodeURL
     *            url of ESGF node where the RESTful service request will be
     *            processed
     */
    public RESTfulSearch(String indexNodeURL) {
        super(indexNodeURL);
    }

    /**
     * <p>
     * Constructs url of search RESTful service. The general syntax of the ESGF
     * service URL is:
     * </p>
     * 
     * http://<base_URL>/search?[keyword parameters as (name, value)
     * pairs][facet parameters as (name,value) pairs]
     * 
     * @return URL
     */
    @Override
    public URL generateServiceURL() {

        String url = indexNode.toString();
        url = url + "/esg-search/search?" + parameters.toQueryString();

        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parse {@link RESTfulSearch} service in {@link RESTfulWget} service
     * 
     * @return RESTfulWget service to generate a wget script to download all
     *         files matching the given constraints.
     */
    public RESTfulWget parseToRESTfulWget() {

        // create new object RESTfulWget
        RESTfulWget newRest = new RESTfulWget(indexNode.toString());

        // set parameters of new object with this RESTfulSearch parameters
        newRest.setParameters(parameters);
        // set parameter format to null because not exist in RESTfulWget
        newRest.getParameters().setFormat(null);
        // set parameter limit to default in RESTful wget service
        newRest.getParameters().setLimit(1000);

        return newRest;
    }

}
