package es.unican.meteo.esgf.search;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import es.unican.meteo.esgf.petition.HTTPStatusCodeException;
import es.unican.meteo.esgf.petition.RequestManager;

/**
 * <p>
 * Manages the search of Datasets in ESGF by setting parameters of
 * {@link RESTfulSearch}
 * </p>
 * 
 * <p>
 * Always search all versions and all replicas because some index node have more
 * metadata indexed tan others. Allows get request documents through the RESTful
 * search service in an index node ESGF and paginates the response.
 * 
 * <li>Autoupdate option:
 * <ul>
 * <li>To set if the configuration data is automatically updated or not when
 * whenever there is a change in the attributes of the instance by sending a
 * petition to ESGF search service</li>
 * 
 * <li>By default this value is true and therefore any change in the instance
 * leads to a request to the server. It can be changed by setAutoUpdate ().</li>
 * <li>To see if autoupdate is enabled or not, used isAutoUpdate () method.</li>
 * </ul>
 * </li> </ul>
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
 * </ul>
 * 
 * <p>
 * Facet are "categories" that can be used to apply constraints to REStful
 * service. Facets are formed by a name-value pair. Any parameter which is not a
 * keyword parameter is interpreted by the system as a facet parameter.
 * </p>
 * 
 * <p>
 * Facet values must be properly URL-encoded.
 * </p>
 * <p>
 * As for facet parameter values,Keyword parameter values must be properly
 * URL-encoded.
 * </p>
 * 
 * <p>
 * The following keywords are currently used by the system
 * </p>
 * 
 * <table border=1>
 * <tr>
 * <th>Keyword</th>
 * <th>Functionally</th>
 * </tr>
 * <tr>
 * <td>query (default: query=*)</td>
 * <td>used to pass a free text constraint to the search engine, to match one or
 * more fields </td
 * </tr>
 * 
 * <tr>
 * <td>distrib (default: distrib=true)</td>
 * <td>true to execute a distributed query, distrib=false to execute a local
 * query</td>
 * </tr>
 * 
 * <tr>
 * <td>facets(default: site specific)</td>
 * <td>comma separated list of facets to be returned in the response. For each
 * requested facet, the engine should return all the possible values and counts
 * (if available) for that facet across all the records matching the query (not
 * just the records returned in the current response document).</td>
 * </tr>
 * 
 * <tr>
 * <td>offset(default:0)</td>
 * <td>to paginate through the available results. The starting index for the
 * returned results</td>
 * </tr>
 * 
 * <tr>
 * <td>limit (default: site specific)</td>
 * <td>to paginate through the available results. The maximum number of returned
 * results. The search engine is also free to override this value with a maximum
 * number of records it is willing to serve for each request.</td>
 * </tr>
 * 
 * <tr>
 * <td>fields(default: site specific)</td>
 * <td>used to specify which metadata fields should be included for each
 * returned result, if available.</td>
 * </tr>
 * 
 * <tr>
 * <td>format</td>
 * <td>the format of the returned response document, encoded as the document
 * mime type</td>
 * </tr>
 * 
 * <tr>
 * <td>start, end (default: none)</td>
 * <td>
 * <p>
 * to execute a temporal range query.
 * </p>
 * <ul>
 * <li>The date and time values must be encoded in the format
 * "YYYY-MM-DDTHH:mm:ssZ".</li>
 * <li>The start, end parameters refer to the data temporal coverage, not the
 * metadata last update time stamp.
 * <li>
 * </ul>
 * </td>
 * </tr>
 * 
 * <tr>
 * <td>from, to (default: none)</td>
 * <td>used as lower and upper limit of the last update time stamp for each
 * record. The values must be encoded in the format "YYYY-MM-DDTHH:mm:ssZ"</td>
 * </tr>
 * <tr>
 * <td>
 * <ul>
 * <li>bbox=[west,south,east,north] (default: none)</li>
 * <li>radius,polygon, lat,lon <b><i style="color:#80BFFF">Not implemented yet
 * </i></b></li>
 * </ul>
 * </td>
 * <td>these parameters are used to perform a geo-spatial search</td>
 * </tr>
 * 
 * <tr>
 * <td>shards <i>Deprecated?</i></td>
 * <td>to specify an explicit list of shards to be queried</td>
 * </tr>
 * </table>
 * 
 * @author Karem Terry
 */
public class SearchManager implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1619426841271071460L;
    private static final int SIMULTANEOUS_DOWNLOADS = 7;

    /** Logger. */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(SearchManager.class);

    /**
     * Autoupdate option, to set if the configuration data is automatically
     * updated or not .
     */
    private boolean autoupdate;

    /** Number of {@link Record} that matches current RESTful search. **/
    private int numberOfRecords;

    /** Current {@link RESTfulSearch}. **/
    private static RESTfulSearch search;

    /** List of search and their responses. */
    private List<SearchResponse> searchResponses;

    /** Facet map from current search. */
    private Map<SearchCategoryFacet, List<SearchCategoryValue>> facetMap;

    /** Executor that schedules and executes metadata collectors in threads. */
    private ExecutorService collectorsExecutor;

    /**
     * Locked datasets that are being harvested in a search response
     */
    private static Map<String, Boolean> lockedDatasets = new HashMap<String, Boolean>();;

    /** Cache manager. */
    CacheManager cacheManager;

    /** Cache of {@link Dataset}. */
    private Cache cache;

    public SearchManager() {
    }

    /**
     * Constructor.
     * 
     * @param url
     *            url of ESGF index node where the {@link RESTfulSearch} service
     *            request will be processed
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public SearchManager(String url) throws IOException,
            HTTPStatusCodeException {

        logger.trace("[IN]  SearchManager");

        logger.debug("Initiating the values ​​of the attributes");

        // Initialize facetTree
        this.facetMap = new HashMap<SearchCategoryFacet, List<SearchCategoryValue>>();

        // Create all possible key-value facet for improve performance
        for (SearchCategoryFacet facet : SearchCategoryFacet.values()) {
            this.facetMap.put(facet, new ArrayList<SearchCategoryValue>());
        }

        // New RESTful search
        this.search = new RESTfulSearch(url);

        // Always search all versions and all replicas
        // Because some index node have more metadata indexed tan others
        // Predetermined format
        this.search.getParameters().setFormat(Format.JSON);

        // Set search distributed in all ESGF
        this.search.getParameters().setDistrib(true);

        // Predeterminet type (Dataset)
        this.search.getParameters().setType(RecordType.DATASET);

        // Autoupdate state false
        this.autoupdate = true;

        this.searchResponses = new LinkedList<SearchResponse>();

        this.collectorsExecutor = Executors
                .newFixedThreadPool(SIMULTANEOUS_DOWNLOADS);

        logger.debug("Configuring cache");

        try {

            this.cacheManager = CacheManager.create("ehcache_Dataset.xml");
            this.cache = cacheManager.getCache("restartableCache");

            logger.debug("Cache {} configuration is: \n {}",
                    cacheManager.getName(),
                    cacheManager.getActiveConfigurationText());
        } catch (Exception e) {
            e.printStackTrace();

        }

        logger.debug("Update first configuration");
        updateConfiguration();

        logger.trace("[OUT] SearchManager");
    }

    public void setNumberOfRecords(int numberOfRecords) {
        this.numberOfRecords = numberOfRecords;
    }

    /**
     * Return current configurated parameters
     * 
     * @return the currentParameters
     */
    public Map<Parameter, Object> getCurrentParameters() {
        return search.getParameters().getMapParameterValue();
    }

    /**
     * Return a {@link Map} where the key is a {@link SearchCategoryFacet} and
     * the value is a {@link List} of {@link SearchCategoryValue} that match
     * with search request
     * 
     * @return facet tree from current match
     */
    public Map<SearchCategoryFacet, List<SearchCategoryValue>> getFacetMap() {
        logger.trace("[IN]  getFacetMap");
        logger.trace("[OUT] getFacetMap");
        return facetMap;
    }

    /**
     * Get the url of index node of search. This is the ESGF node where the
     * search is executed
     * 
     * @return url of index node
     */
    public String getIndexNode() {
        logger.trace("[IN]  getSearchNode");
        logger.trace("[OUT] getSearchNode");
        return search.getIndexNode();
    }

    /**
     * Get number of {@link Record} found in current search.
     * 
     * @return number of records found for current configuration of search
     */
    public int getNumberOfRecords() {
        logger.trace("[IN]  getCurrentDataSetCount");
        logger.trace("[OUT] getCurrentDataSetCount");
        return numberOfRecords;
    }

    /**
     * Get list of saved search and their state
     * 
     * @return list of {@link SearchResponse}
     */
    public List<SearchResponse> getSearchResponses() {
        logger.trace("[IN]  getSearchResponses");
        logger.trace("[OUT] getSearchResponses");
        return searchResponses;
    }

    /**
     * Return true if data is automatically updated, false otherwise.
     * 
     * @return the autoUpdate option true if data is automatically updated,
     *         false otherwise.
     */
    public boolean isAutoUpdate() {
        logger.trace("[IN]  isAutoUpdate");
        logger.trace("[OUT] isAutoUpdate");
        return autoupdate;
    }

    /**
     * Remove attribute "bbox" of {@link Parameters} configured in search.
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void removeBbox() throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  removeBbox");

        logger.debug("Deletting bbox parameter with new value");
        search.getParameters().setBbox(null);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] removeBbox");
    }

    /**
     * Remove attribute "end" of {@link Parameters} configured in search.
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void removeEnd() throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  removeEnd");

        logger.debug("Deleting end parameter with new value");
        search.getParameters().setEnd(null);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] removeEnd");
    }

    /**
     * Remove a list of parameter-value pairs of configured parameters
     * 
     * @param parameterValueList
     *            list of {@link ParameterValue} that will be removed of search
     *            configuration
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void removeParameterValues(List<ParameterValue> parameterValueList)
            throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  removeParameterValues");

        logger.debug("Removing configured parameters {}", parameterValueList);
        // for each parameter-value pair
        for (ParameterValue parameterValue : parameterValueList) {

            Parameter parameter = parameterValue.getParameter();
            Object value = parameterValue.getValue();

            search.getParameters().removeParameter(parameter);

            // Remove
            /*
             * if (search.getParameters().isConfigured(parameter)) {
             * logger.debug("Getting its value"); List<String> values =
             * search.getParameters().getParameter( parameter);
             * 
             * logger.debug("Remove value{} y estos son los valores {}.", value,
             * values); boolean esta = values.remove(value);
             * logger.debug("Remove value está {}", esta); }// If not exists a
             * key parameter, do nothing
             */
        }

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }
        logger.trace("[OUT] removeParameterValues");
    }

    /**
     * Remove attribute "format" of {@link Parameters} configured in search.
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void removeFormat() throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  removeFormat");

        logger.debug("Deleting \"format\" parameter with new value");
        search.getParameters().setFormat(null);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] removeFormat");
    }

    /**
     * Remove attribute "from" of {@link Parameters} configured in search.
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void removeFrom() throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  removeFrom");

        logger.debug("Deleting \"from\" parameter with new value");
        search.getParameters().setFrom(null);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] removeFrom");
    }

    /**
     * Remove attribute "latest" of {@link Parameters} configured in search.
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void removeLatest() throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  removeLatest");

        logger.debug("Deleting \"latest\" parameter with new value");
        search.getParameters().setLatest(null);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] removeLatest");
    }

    /**
     * Remove attribute "limit" of {@link Parameters} configured in search.
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void removeLimit() throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  removeLimit");

        logger.debug("Deleting \"limit\" parameter with new value");
        search.getParameters().setLimit(-1);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] removeLimit");
    }

    /**
     * Remove attribute "offset" of {@link Parameters} configured in search.
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void removeOffset() throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  removeOffset");

        logger.debug("Deleting \"offset\" parameter with new value");
        search.getParameters().setOffset(-1);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] removeOffset");
    }

    /**
     * Remove attribute "query" of {@link Parameters} configured in search.
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void removeQuery() throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  removeQuery");

        logger.debug("Deleting query parameter");
        search.getParameters().setQuery(null);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }
        logger.trace("[OUT] removeQuery");
    }

    /**
     * Remove attribute "replica" of {@link Parameters} configured in search.
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void removeReplica() throws IOException, HTTPStatusCodeException {

        logger.trace("[IN]  removeReplica");

        logger.debug("Deleting \"replica\" parameter with new value");
        search.getParameters().setReplica(null);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            updateConfiguration();
        }

        logger.trace("[OUT] removeReplica");
    }

    /**
     * Remove attribute "shards" of {@link Parameters} configured in search.
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void removeShards() throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  removeShards");

        logger.debug("Deleting \"shards\" parameter with new value");
        search.getParameters().setShards(null);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            updateConfiguration();
        }

        logger.trace("[OUT] removeShards");
    }

    /**
     * Remove attribute "start" of {@link Parameters} configured in search.
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void removeStart() throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  removeStart");

        logger.debug("Deleting start parameter with new value");
        search.getParameters().setStart(null);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] removeStart");
    }

    /**
     * Remove attribute "to" of {@link Parameters} configured in search.
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void removeTo() throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  removeTo");

        logger.debug("Deleting \"to\" parameter with new value");
        search.getParameters().setTo(null);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] removeTo");
    }

    /**
     * Remove attribute "type" of {@link Parameters} configured in search.
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void removeType() throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  removeType");

        logger.debug("Deleting \"type\" parameter with new value");
        search.getParameters().setType(null);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] removeType");
    }

    /**
     * Reset all search configuation, but conserves search responses
     * 
     * @throws IOException
     * @throws HTTPStatusCodeException
     */
    public void resetConfiguration() throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  resetConfiguration");

        // Cache and executor must not be reset
        logger.debug("Initiating the values ​​of the attributes");

        // Initialize facetTree
        this.facetMap = new HashMap<SearchCategoryFacet, List<SearchCategoryValue>>();

        // Create all possible key-value facet for improve performance
        for (SearchCategoryFacet facet : SearchCategoryFacet.values()) {
            this.facetMap.put(facet, new ArrayList<SearchCategoryValue>());
        }

        // New RESTful search
        this.search = new RESTfulSearch(search.getIndexNode().toString());

        // XXX change?
        // Predetermined format
        this.search.getParameters().setFormat(Format.JSON);

        // Set search distributed in all ESGF
        this.search.getParameters().setDistrib(true);

        // Predeterminet type (Dataset)
        search.getParameters().setType(RecordType.DATASET);

        // Autoupdate state false
        this.autoupdate = true;

        logger.debug("Update configuration");
        updateConfiguration();

        logger.trace("[OUT] resetConfiguration");
    }

    /**
     * Save a search
     * 
     * @param name
     *            of new saved search
     * @return SearchResponse saved
     * @throws IllegalArgumentException
     *             if already exists a search with the same name
     * 
     * @throws CloneNotSupportedException
     */
    public SearchResponse saveSearch(String name)
            throws CloneNotSupportedException, IllegalArgumentException {
        logger.trace("[IN]  saveSearch");

        boolean found = false;
        for (SearchResponse searchResponse : searchResponses) {
            if (searchResponse.getName().equals(name)) {
                found = true;
            }
        }

        if (found) {
            throw new IllegalArgumentException(
                    "Already exists a search with name: " + name);
        }

        SearchResponse searchResponse = new SearchResponse(name,
                (RESTfulSearch) search.clone(), collectorsExecutor, cache);
        searchResponses.add(searchResponse);
        logger.trace("[OUT] saveSearch");

        return searchResponse;
    }

    /**
     * Set if the configuration is automatically updated or not.
     * 
     * @param autoUpdate
     *            boolean true if data is automatically updated, false
     *            otherwise.
     */
    public void setAutoUpdate(boolean autoUpdate) {
        logger.trace("[IN]  setAutoUpdate");
        logger.trace("[OUT] setAutoUpdate");
        this.autoupdate = autoUpdate;
    }

    /**
     * Set attribute "bbox" of {@link Parameters} configured in search.
     * 
     * @param bbox
     *            [west,south,east,north] of a spatial coverage query
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void setBbox(float[] bbox) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setBbox");

        logger.debug("Setting bbox parameter with new value");
        search.getParameters().setBbox(bbox);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] setBbox");
    }

    /**
     * Set attribute "type" (local or grid) of {@link Parameters} configured in
     * search.
     * 
     * @param distrib
     *            true for grid search and false for local search
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void setDistrib(boolean distrib) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setDistrib");

        logger.debug("Setting type parameter with new value");
        search.getParameters().setDistrib(distrib);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }
        logger.trace("[OUT] setDistrib");
    }

    /**
     * Set attribute "end" of {@link Parameters} configured in search.
     * 
     * @param end
     *            end time to a temporal range query
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void setEnd(Calendar end) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setEnd");

        logger.debug("Setting end parameter with new value");
        search.getParameters().setEnd(end);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] setEnd");
    }

    /**
     * Set setAccess
     * 
     * @param accessList
     *            list of {@link Service}
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void setAccess(List<Service> accessList) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setAccess");

        logger.debug("Setting access with new value");
        search.getParameters().setAccess(accessList);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] setAccess");
    }

    /**
     * Set attribute "format" of {@link Parameters} configured in search.
     * 
     * @param format
     *            the format to specify the response document output format
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void setFormat(Format format) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setFormat");

        logger.debug("Setting \"format\" parameter with new value");
        search.getParameters().setFormat(format);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] setFormat");
    }

    /**
     * Set attribute "from" of {@link Parameters} configured in search.
     * 
     * @param from
     *            the "from" of a a query based on the record last update
     *            (timestamp)
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void setFrom(Calendar from) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setFrom");

        logger.debug("Setting \"from\" parameter with new value");
        search.getParameters().setFrom(from);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] setFrom");
    }

    /**
     * Set URL of search index node.
     * 
     * @param indexNode
     *            URL of index node
     * 
     * @throws IllegalArgumentException
     *             if the parameter indexNode is invalid URL
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void setIndexNode(String indexNode) throws IllegalArgumentException,
            IOException, HTTPStatusCodeException {
        logger.trace("[IN]  setSearchNode");

        logger.debug("Checking if it is a valid url.");

        if (indexNode.length() > 7) {
            // if isn't complete url
            if (!indexNode.substring(0, 7).equals("http://")) {
                indexNode = "http://" + indexNode;
            }
        }

        logger.debug("Setting new base url.");
        search.setIndexNode(indexNode);

        // If autoupdate option is enabled
        if (autoupdate) {
            logger.debug("Update Search Manager configuration.");
            updateConfiguration();
        }

        logger.trace("[OUT] setSearchNode");
    }

    /**
     * Set attribute "latest" of {@link Parameters} configured in search.
     * 
     * @param latest
     *            indicates wether the record is the latest available version,
     *            previous versions or all versions
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void setLatest(Latest latest) throws IOException,
            HTTPStatusCodeException {

        logger.trace("[IN]  setLatest");
        logger.debug("Setting \"latest\" parameter with new value");
        search.getParameters().setLatest(latest);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            updateConfiguration();
        }

        logger.trace("[OUT] setLatest");
    }

    /**
     * Set attribute "limit" of {@link Parameters} configured in search.
     * 
     * @param limit
     *            limit to paginate through the available results. Values
     *            greater than 0 are used to set the number of results; a limit
     *            of 0 is used to get just the count of results, without results
     *            themselves. Negative values are dismissed
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void setLimit(int limit) throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  setLimit");

        logger.debug("Setting \"limit\" parameter with new value");
        search.getParameters().setLimit(limit);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            updateConfiguration();
        }

        logger.trace("[OUT] setLimit");
    }

    /**
     * Set attribute "offset" of {@link Parameters} configured in search.
     * 
     * @param offset
     *            offset to paginate through the available results
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void setOffset(int offset) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setOffset");

        logger.debug("Setting \"offset\" parameter with new value");
        search.getParameters().setOffset(offset);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] setOffset");
    }

    /**
     * Set attribute "query" of {@link Parameters} configured in search.
     * 
     * @param query
     *            the query to set
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void setQuery(String query) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setQuery");

        logger.debug("Setting query parameter with new value");
        search.getParameters().setQuery(query);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }
        logger.trace("[OUT] setQuery");
    }

    /**
     * Set attribute "replica" of {@link Parameters} configured in search.
     * 
     * @param replica
     *            indicates wether the record is the "master" copy, or a replica
     *            or all replicas. indicates wether the record is the latest
     *            available version
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void setReplica(Replica replica) throws IOException,
            HTTPStatusCodeException {

        logger.trace("[IN]  setReplica");
        logger.debug("Setting \"replica\" parameter with new value");
        search.getParameters().setReplica(replica);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            updateConfiguration();
        }

        logger.trace("[OUT] setReplica");
    }

    /**
     * Set attribute "shards" of {@link Parameters} configured in search.
     * 
     * @param shards
     *            explicit list of shards separated with "," to be queried
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void setShards(String shards) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setShards");

        logger.debug("Setting \"shards\" parameter with new value");
        search.getParameters().setShards(shards);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] setShards");
    }

    /**
     * Set attribute "start" of {@link Parameters} configured in search.
     * 
     * @param start
     *            start time to a temporal range query
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void setStart(Calendar start) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setStart");

        logger.debug("Setting start parameters with new value");
        // Set new "start" parameter configuration
        search.getParameters().setStart(start);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }
        logger.trace("[OUT] setStart");
    }

    /**
     * Set attribute "to" of {@link Parameters} configured in search.
     * 
     * @param to
     *            the "to" of a a query based on the record last update
     *            (timestamp)
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void setTo(Calendar to) throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  setTo");

        logger.debug("Setting \"to\" parameter with new value");
        search.getParameters().setTo(to);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] setTo");
    }

    /**
     * Set attribute "type" of {@link Parameters} configured in search.
     * 
     * @param type
     *            denotes the intrinsic type of the record. Currently supported
     *            values: Dataset, File, Aggregation
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void setType(RecordType type) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setType");

        logger.debug("Setting \"type\" parameter with new value");
        search.getParameters().setType(type);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] setType");
    }

    /**
     * Send RESTful petition to server and set new request configuration
     * 
     * @throws IOException
     *             if happens an error in ESGF search service
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200
     */
    public void updateConfiguration() throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  updateConfiguration");

        logger.debug("Getting number of records from current search");
        numberOfRecords = RequestManager.getNumOfRecordsFromSearch(search,
                true, false);

        logger.debug("Getting facet values and its counts that exist in ESGF from current search");
        facetMap = RequestManager.getFacetsAndValuesFromSearch(search);

        logger.debug("Updating facet values to set the values selected by user");
        updateSelectedValuesInFacetMap(facetMap);

        logger.trace("[OUT] updateConfiguration");
    }

    // TODO
    // Maybe can change maps with parameters List<ParameterValue>
    /**
     * Update parameter values, adding and removing facet-values in search
     * configuration
     * 
     * @param mapFacetValueToAdd
     *            map of {@link SearchCategoryFacet} and their values. This
     *            values must be added in search configuration
     * @param mapFacetValueToRemove
     *            map of {@link SearchCategoryFacet} and their values. This
     *            values must be removed in search configuration
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     *             activated
     */
    public void updateSearchCategoryFacetValues(
            Map<SearchCategoryFacet, List<String>> mapFacetValueToAdd,
            Map<SearchCategoryFacet, List<String>> mapFacetValueToRemove)
            throws IOException, HTTPStatusCodeException {

        logger.trace("[IN]  updateSearchCategoryFacetValues");

        // Remove
        if (mapFacetValueToRemove != null) {
            logger.debug("Removing all values of facet parameter chosen to be removed in configuration.");
            for (Map.Entry<SearchCategoryFacet, List<String>> element : mapFacetValueToRemove
                    .entrySet()) {

                // Get enum element for this SearchCategoryFacet
                Parameter parameter = Parameter
                        .valueOf(element.getKey().name());
                logger.debug("Checking if exist {} in configured parameters",
                        parameter);
                // Remove
                if (search.getParameters().isConfigured(parameter)) {
                    logger.debug("Getting its value");
                    List<String> values = search.getParameters().getParameter(
                            parameter);

                    logger.debug("Remove all chosen values.");
                    // for each value to remove in current parameter of loop
                    for (String valueToRemove : element.getValue()) {
                        values.remove(valueToRemove);// remove value of
                                                     // parameters
                    }
                }// If not exists a key parameter, do nothing
            }
        }

        // Add
        if (mapFacetValueToAdd != null) {
            logger.debug("Adding all values of facet parameter chosen to be added in configuration.");

            for (Map.Entry<SearchCategoryFacet, List<String>> element : mapFacetValueToAdd
                    .entrySet()) {

                // Get enum element for this SearchCategoryFacet
                Parameter parameter = Parameter
                        .valueOf(element.getKey().name());
                logger.debug("Checking if exist {} in configured parameters",
                        parameter);
                // Add
                if (search.getParameters().isConfigured(parameter)) {
                    logger.debug("Getting its value");
                    List<String> values = search.getParameters().getParameter(
                            parameter);

                    logger.debug("Add all chosen values.");
                    // for each value to add in current parameter of loop
                    for (String valueToAdd : element.getValue()) {
                        if (!values.contains(valueToAdd)) {// if not exist
                                                           // previously
                            values.add(valueToAdd);// add value of parameters
                        }
                    }
                } else {// If not exists a key parameter
                    logger.debug("Adding new configured parameter.");
                    search.getParameters().setParameter(parameter,
                            element.getValue());

                }
            }
        }

        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }

        logger.trace("[OUT] updateSearchCategoryFacetValues");
    }

    /**
     * Update values of {@link SearchCategoryValue} in facet map for set the
     * values selected by user
     * 
     * @param facetMap
     *            Facet map from a search
     */
    private void updateSelectedValuesInFacetMap(
            Map<SearchCategoryFacet, List<SearchCategoryValue>> facetMap) {

        logger.trace("[IN]  updateSelectedValuesInFacetMap");
        for (SearchCategoryFacet facet : SearchCategoryFacet.values()) {

            logger.debug("Getting the name of parameter for {} facet", facet);
            String facetParameter = facet.name();

            logger.debug("Getting all configurated values for {}", facet);
            // Search Category Facets are parameters type List<String>
            List<String> listOfSelectedValues = search.getParameters()
                    .getParameter(Parameter.valueOf(facetParameter));

            // If exist values configurated for this facets
            if (listOfSelectedValues != null) {
                List<SearchCategoryValue> facetValues = null;
                logger.debug("Updating facet selected values in {}", facet);
                if (facetMap.containsKey(facet)) {
                    facetValues = facetMap.get(facet);

                    for (String value : listOfSelectedValues) {
                        for (SearchCategoryValue facetValue : facetValues) {
                            if (facetValue.getValue().equals(value)) {
                                facetValue.setSelected(true);
                                logger.debug(
                                        "Value {} has been updated to selected",
                                        facetValue.getValue());
                            }
                        }
                    }
                }
            }
        }
        logger.trace("[OUT] updateSelectedValuesInFacetMap");

    }

    /**
     * Get all pairs parameter-value configurated in search.
     * 
     * @return a List of pairs {@link ParameterValue} that are configured in
     *         search
     */
    public List<ParameterValue> getListOfParameterValues() {
        logger.trace("[IN]  getListOfParameterValues");

        List<ParameterValue> list = new LinkedList<ParameterValue>();

        logger.debug("Creating list of pairs [parameter-value] that are configured in search ");
        // For each entry element (pair parameter- value) in map
        for (Map.Entry<Parameter, Object> element : search.getParameters()
                .getMapParameterValue().entrySet()) {
            // Add new ParameterValue(key,value) element
            list.add(new ParameterValue(element.getKey(), element.getValue()));
        }

        logger.trace("[OUT] getListOfParameterValues");
        return list;
    }

    public void setSearchResponses(List<SearchResponse> searchResponses) {
        this.searchResponses = searchResponses;

    }

    public Cache getCache() {
        return cache;
    }

    public ExecutorService getExecutor() {
        return collectorsExecutor;
    }

    /**
     * Set attribute "data_node" of {@link Parameters} configured in search.
     * 
     * @param dataNodesList
     *            list of datanodes
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     * 
     */
    public void setDataNodes(List<String> dataNodesList) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setDataNode");
        logger.debug("Setting data node with new value", dataNodesList);
        search.getParameters().setDataNode(dataNodesList);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }
        logger.trace("[OUT] setDataNode");
    }

    /**
     * Set attribute "index_node" of {@link Parameters} configured in search.
     * 
     * @param indexNodeList
     *            list of index nodes
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     * 
     */
    public void setIndexNodes(List<String> indexNodeList) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setIndexNodes");
        logger.debug("Setting index node with new value", indexNodeList);
        search.getParameters().setIndexNode(indexNodeList);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }
        logger.trace("[OUT] setIndexNodes");
    }

    /**
     * Set attribute "id" of {@link Parameters} configured in search.
     * 
     * @param idList
     *            list of ids
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     * 
     */
    public void setIds(List<String> idList) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setIds");
        logger.debug("Setting id node with new value", idList);
        search.getParameters().setId(idList);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }
        logger.trace("[OUT] setIds");
    }

    /**
     * Set attribute "instance_id" of {@link Parameters} configured in search.
     * 
     * @param instanceIdList
     *            list of instance id
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     * 
     */
    public void setInstanceIds(List<String> instanceIdList) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setInstanceIds");
        logger.debug("Setting instance_id with new value", instanceIdList);
        search.getParameters().setInstanceId(instanceIdList);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }
        logger.trace("[OUT] setInstanceIds");
    }

    /**
     * Set attribute "master_id" of {@link Parameters} configured in search.
     * 
     * @param masterIdList
     *            list of master id
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     * 
     */
    public void setMasterIds(List<String> masterIdList) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setMasterIds");
        logger.debug("Setting master_id with new value", masterIdList);
        search.getParameters().setMasterId(masterIdList);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }
        logger.trace("[OUT] setMasterIds");
    }

    /**
     * Set attribute "version" of {@link Parameters} configured in search.
     * 
     * @param versionList
     *            list of version
     * 
     * @throws IOException
     *             if happens an error in ESGF search service when autoupdate
     *             option is activated
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200 when autoupdate option is
     * 
     */
    public void setVersions(List<String> versionList) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  setVersions");
        logger.debug("Setting version with new value", versionList);
        search.getParameters().setInstanceId(versionList);

        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }
        logger.trace("[OUT] setVersions");
    }

    /**
     * Get the restful search that implements search functionality.
     * 
     * @return the search
     */
    public RESTfulSearch getSearch() {
        return search;
    }

    /**
     * Set the restful search that implements search functionality, but
     * conserves current index node. If some error happens restful search not
     * change.
     * 
     * @throws HTTPStatusCodeException
     * @throws IOException
     */
    public void setSearch(RESTfulSearch search) throws IOException,
            HTTPStatusCodeException {

        // Clone petition
        RESTfulSearch newSearch = this.search;
        try {
            newSearch = (RESTfulSearch) search.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        // Conserves current indexNode
        newSearch.setIndexNode(getIndexNode());

        // Set new search in search manager
        this.search = newSearch;
        // If autoupdate option is enabled update new configuration
        if (autoupdate) {
            logger.debug("Update search manager configuration");
            updateConfiguration();
        }
    }

    /**
     * Lock a dataset. Indicates that dataset are being harvested
     * 
     * @param instanceID
     *            instance_id of dataset
     * @throws IllegalArgumentException
     *             if dataset already is locked
     */
    public synchronized static void lockDataset(String instanceID)
            throws IllegalArgumentException {
        if (!isDatasetLocked(instanceID)) {
            lockedDatasets.put(instanceID, true);
        } else {
            throw new IllegalArgumentException("Dataset " + instanceID
                    + " already is locked");
        }
    }

    /**
     * Lock a dataset. Indicates that data is no longer being harvested.
     * 
     * @param instanceID
     *            instance_id of dataset
     * @throws IllegalArgumentException
     *             if dataset already isn't locked
     */
    public synchronized static void releaseDataset(String instanceID)
            throws IllegalArgumentException {
        if (isDatasetLocked(instanceID)) {
            lockedDatasets.put(instanceID, false);
        } else {
            throw new IllegalArgumentException("Dataset " + instanceID
                    + " isn't locked");
        }
    }

    public static String getCurrentIndexNode() {
        return search.getIndexNode();
    }

    /**
     * Check if some dataset is locked or not.
     * 
     * @return true if dataset is locked and false otherwise
     */
    public synchronized static boolean isDatasetLocked(String intanceID) {
        boolean locked = false;
        if (lockedDatasets.containsKey(intanceID)) {
            if (lockedDatasets.get(intanceID) == true) {
                locked = true;
            }
        }
        return locked;
    }
}
