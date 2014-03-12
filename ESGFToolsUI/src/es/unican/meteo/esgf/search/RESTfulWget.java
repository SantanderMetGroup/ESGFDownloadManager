package es.unican.meteo.esgf.search;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * <p>
 * The RESTfulSearch class extends {@link RESTful}, to implement wget
 * functionality.
 * </p>
 * 
 * <p>
 * The same RESTful API that is used to query the ESGF search services can also
 * be used, with minor modifications, to generate a wget script to download all
 * files matching the given constraints.
 * </p>
 * 
 * <p>
 * Specifically, each ESGF Index Node exposes the following URL for generating
 * wget scripts:
 * </p>
 * 
 * <ul>
 * <li>
 * <code>http://base_search_URL/wget?[keyword parameters as (name, value)
 * pairs][facet parameters as (name,value) pairs]</code>
 * </li>
 * </ul>
 * 
 * <p>
 * Where again base_search_URL is the base URL of the search service at a given
 * Index Node. The only syntax differences with respect to the search URL are:
 * </p>
 * 
 * <ul>
 * <li>core facet <b>type</b> is not allowed, as the wget URL always assumes
 * type=File</li>
 * 
 * <li>keyword <b>format</b> is not allowed, as the wget URL always returns a
 * shell script as response document</li>
 * 
 * <li>keyword <b>limit</b> is assigned a default value of limit=1000 (and must
 * still be limit < 10,000)</li>
 * 
 * <li><b><i style="color:#80BFFF">Not implemented yet </i></b> keyword
 * <b>download_structure</b>is used for defining a relative directory structure
 * for the download by using the facets value (i.e. of Files and not Datasets)</li>
 * 
 * <li><b><i style="color:#80BFFF">Not implemented yet </i></b> keyword
 * <b>download_emptypath</b> is used to define what to do it download_structure
 * is set and the facet return no value (e.g. mixing files from CMIP5 and
 * obs4MIP and selecting instrument as a facet value will result in all CMIP5
 * files returning an empty value)</li>
 * </ul>
 * 
 * @author Karem Terry
 * 
 */
public class RESTfulWget extends RESTful {

    public RESTfulWget(String baseUrl) {
        super(baseUrl);
    }

    /**
     * Set {@link Parameters} of service.
     * 
     * @param parameters
     *            the parameters to set
     * 
     * @throws IllegalStateException
     *             if configurated parameters are not compatible with wget
     *             service
     */
    @Override
    public void setParameters(Parameters parameters) {
        // format service parameters must not be set
        // if format is configure then
        if (parameters.getFormat() != null) {
            throw new IllegalStateException();
        }
        this.parameters = parameters;
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

        if (parameters.getFormat() != null) {
            parameters.setFormat(null);
        }

        String url = indexNode.toString();
        url = url + "/esg-search/wget?" + parameters.toQueryString() + "&";

        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parse {@link RESTfulWget} service in {@link RESTfulSearch} service
     * 
     * @return RESTfulWget service to generate a wget script to download all
     *         files matching the given constraints.
     */
    public RESTfulSearch parseToRESTfulSearch() {

        // create new object RESTfulWget
        RESTfulSearch newRest = new RESTfulSearch(indexNode.toString());

        // set parameters of new object with this RESTfulSearch parameters
        newRest.setParameters(parameters);

        // add facets of new object with this RESTfulSearch facets
        return newRest;
    }
}
