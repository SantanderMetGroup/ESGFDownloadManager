package es.unican.meteo.esgf.search;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import es.unican.meteo.esgf.download.ChecksumType;

/**
 * <p>
 * The Parameter class represents ESGF RESTful service keywords and facets
 * </p>
 * 
 * <p>
 * Keywords parameters are query parameters that have reserved names, and are
 * interpreted by the search service to control the fundamental nature of a
 * search request: where to issue the request to, how many results to return,
 * etc.
 * </p>
 * 
 * <p>
 * Keyword parameter values must be properly URL-encoded.
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
 * 
 * 
 * @author Karem Terry
 * 
 */
public class Parameters implements Serializable, Cloneable {

    private static final String ENCODE_FORMAT = "UTF-8";

    /** Logger. */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(Parameters.class);

    /**
     * Map of parameters - value. Key Map is a {@link Parameter} and value is an
     * {@link Object}. This allows flexibility to be able to add values ​​of
     * different types.
     */
    Map<Parameter, Object> parameters;

    /**
     * Constructor.
     */
    public Parameters() {
        logger.trace("[IN]  Parameters");
        parameters = new HashMap<Parameter, Object>();
        logger.trace("[OUT] Parameters");
    }

    /**
     * Set parameter.
     * 
     * @param key
     *            name of parameter
     * @param value
     *            value of parameter
     */
    public void setParameter(Parameter key, Object value) {
        logger.trace("[IN]  setParameter");
        parameters.put(key, value);
        logger.trace("[OUT] setParameter");
    }

    /**
     * Get value of parameter. Generic Method. {
     * 
     * 
     * @param key
     *            name of parameter
     * 
     * @return value of parameter, or null if not exists
     */
    @SuppressWarnings("unchecked")
    public <E> E getParameter(Parameter key) {
        logger.trace("[IN]  getParameter");
        Object o = parameters.get(key);
        logger.trace("[OUT] getParameter");
        return (E) o;
    }

    /**
     * Check if parameter is configurated.
     * 
     * @param key
     *            name of parameter
     * @return true if exists, false otherwise
     */
    public boolean isConfigured(Parameter key) {
        logger.trace("[IN]  isConfigured");
        logger.trace("[OUT] isConfigured");
        return parameters.get(key) != null;

    }

    /**
     * Get map parameter-values
     * 
     * @return the a {@link Map} with key Map is a Parameter and value is an
     *         Object. This allows flexibility to be able to add values ​​of
     *         different types.
     */
    public Map<Parameter, Object> getMapParameterValue() {
        logger.trace("[IN]  getMapParameterValue");
        logger.trace("[OUT] getMapParameterValue");
        return parameters;
    }

    /**
     * Set all parameters.
     * 
     * @param parameters
     *            Map of parameters - value. Key Map is a Parameter and value is
     *            an Object. This allows flexibility to be able to add values
     *            ​​of different types.
     */
    public void setMapParameterValue(Map<Parameter, Object> parameters) {
        logger.trace("[IN]  setMapParameterValue");
        this.parameters = parameters;
        logger.trace("[OUT] setMapParameterValue");
    }

    /**
     * Get the access service configured for searching records that they offer
     * it.
     * 
     * @return the service offered by ESGF for access to data
     */
    public List<Service> getAccess() {
        logger.trace("[IN]  getAccess");
        logger.trace("[OUT] getAccess");
        return getParameter(Parameter.ACCESS);
    }

    /**
     * Get [west,south,east,north] of a spatial coverage query.
     * 
     * @return bbox [west,south,east,north] of a spatial coverage query
     */
    public float[] getBbox() {
        logger.trace("[IN]  getBbox");
        logger.trace("[OUT] getBbox");
        return getParameter(Parameter.BBOX);
    }

    /**
     * Get list of CF standard name configured for search.
     * 
     * @return the list of CF standard name that all searched records must have.
     */
    public List<String> getCfStandardName() {
        logger.trace("[IN]  getCfStandardName");
        logger.trace("[OUT] getCfStandardName");
        return getParameter(Parameter.CF_STANDARD_NAME);
    }

    /**
     * Get the checksum configured for search.
     * 
     * @return the checksum that all searched records must have.
     */
    public String getChecksum() {
        logger.trace("[IN]  getChecksum");
        logger.trace("[OUT] getChecksum");
        return getParameter(Parameter.CHECKSUM);
    }

    /**
     * Get the checksum type configured for search.
     * 
     * @return the checksum type that all searched records must have.
     */
    public ChecksumType getChecksumType() {
        logger.trace("[IN]  getChecksumType");
        logger.trace("[OUT] getChecksumType");
        return getParameter(Parameter.CHECKSUM_TYPE);
    }

    /**
     * Get the list of CMIP table configured for search.
     * 
     * @return the list of CMIP table that all searched records must have.
     */
    public List<String> getCmipTable() {
        logger.trace("[IN]  getCmorTable");
        logger.trace("[OUT] getCmorTable");
        return getParameter(Parameter.CMOR_TABLE);
    }

    /**
     * Get the list of data nodes configured for search.
     * 
     * @return the list of data nodes that all searched records must have.
     */
    public List<String> getDataNode() {
        logger.trace("[IN]  getDataNode");
        logger.trace("[OUT] getDataNode");
        return getParameter(Parameter.DATA_NODE);
    }

    /**
     * Get the data_id configured for search. Only files and aggregations. If is
     * configured for another records the search will returns 0 records.
     * 
     * @return the dataset id that all searched records must have.
     */
    public String getDatasetId() {
        logger.trace("[IN]  getDatasetId");
        logger.trace("[OUT] getDatasetId");
        return getParameter(Parameter.DATASET_ID);
    }

    /**
     * Get end time of temporal query.
     * 
     * @return the end time to a temporal range query
     */
    public Calendar getEnd() {
        logger.trace("[IN]  getEnd");
        logger.trace("[OUT] getEnd");
        return getParameter(Parameter.END);
    }

    /**
     * Get the list of ensembles configured for search.
     * 
     * @return the list of ensembles that all searched records must have.
     */
    public List<String> getEnsemble() {
        logger.trace("[IN]  getEnsemble");
        logger.trace("[OUT] getEnsemble");
        return getParameter(Parameter.ENSEMBLE);
    }

    /**
     * Get list of the experiments configured for search.
     * 
     * @return the list of experiments that all searched records must have.
     */
    public List<String> getExperiment() {
        logger.trace("[IN]  getExperiment");
        logger.trace("[OUT] getExperiment");
        return getParameter(Parameter.EXPERIMENT);
    }

    /**
     * Get the list of experiment familys configured for search.
     * 
     * @return the list of experiment familys that all searched records must
     *         have.
     */
    public List<String> getExperimentFamily() {
        logger.trace("[IN]  getExperimentFamily");
        logger.trace("[OUT] getExperimentFamily");
        return getParameter(Parameter.EXPERIMENT_FAMILY);
    }

    /**
     * Get Facets
     * 
     * @return Set<String> strings of name facets
     */
    public Set<String> getFacets() {
        logger.trace("[IN]  getFacets");
        logger.trace("[OUT] getFacets");
        return getParameter(Parameter.FACETS);
    }

    /**
     * Get fields to search.
     * 
     * @return the fields of metadata to show.
     */
    public Set<Metadata> getFields() {
        logger.trace("[IN]  getFields");
        logger.trace("[OUT] getFields");
        return getParameter(Parameter.FIELDS);
    }

    /**
     * Get response document output format.
     * 
     * @return the format to specify the response document output format
     */
    public Format getFormat() {
        logger.trace("[IN]  getFormat");
        logger.trace("[OUT] getFormat");
        return getParameter(Parameter.FORMAT);
    }

    /**
     * Get from of a a query based on the record last update (timestamp).
     * 
     * @return the from of a a query based on the record last update (timestamp)
     */
    public Calendar getFrom() {
        logger.trace("[IN]  getFrom");
        logger.trace("[OUT] getFrom");
        return getParameter(Parameter.FROM);
    }

    /**
     * Get the id configured for search. This id is unique for each instance of
     * Record for all versions and replicas.
     * 
     * @return the id that all searched records must have.
     */
    public List<String> getId() {
        logger.trace("[IN]  getId");
        logger.trace("[OUT] getId");
        return getParameter(Parameter.ID);
    }

    /**
     * Get the list of index nodes configured for search.
     * 
     * @return the list of index nodes that all searched records must have.
     */
    public List<String> getIndexNode() {
        logger.trace("[IN]  getIndexNode");
        logger.trace("[OUT] getIndexNode");
        return getParameter(Parameter.INDEX_NODE);
    }

    /**
     * Get the instance id configured for search. This instance id is same for
     * all replicas across federation, but specific to each version.
     * 
     * @return the instance id that all searched records must have.
     */
    public List<String> getInstanceId() {
        logger.trace("[IN]  getInstanceId");
        logger.trace("[OUT] getInstanceId");
        return getParameter(Parameter.INSTANCE_ID);
    }

    /**
     * Get the list of institute configured for search.
     * 
     * @return th list of institute that all searched records must have.
     */
    public List<String> getInstitute() {
        logger.trace("[IN]  getInstitute");
        logger.trace("[OUT] getInstitute");
        return getParameter(Parameter.INSTITUTE);
    }

    /**
     * Get the list of instruments (source_id) configured for search.
     * 
     * @return the list of instruments that all searched records must have.
     */
    public List<String> getInstrument() {
        logger.trace("[IN]  getInstrument");
        logger.trace("[OUT] getInstrument");
        return getParameter(Parameter.SOURCE_ID);
    }

    /**
     * Get the type of record versions configured for search.
     * 
     * @return latest or previous or all versions type of search
     */
    public Latest getLatest() {
        return getParameter(Parameter.LATEST);
    }

    /**
     * Get limit (pagination).
     * 
     * @return limit to paginate through the available results. Values greater
     *         than 0 are used to set the number of results; a limit of 0 is
     *         used to get just the count of results, without results
     *         themselves. Negative values are dismissed
     */
    public int getLimit() {
        logger.trace("[IN]  getLimit");
        logger.trace("[OUT] getLimit");
        if (isConfigured(Parameter.LIMIT)) {
            return getParameter(Parameter.LIMIT);
        } else {
            return -1;
        }
    }

    /**
     * Get the master id configured for search. This instance id is same for all
     * replicas and versions across the federation.
     * 
     * @return the master id that all searched records must have.
     */
    public List<String> getMasterId() {
        logger.trace("[IN]  getMasterId");
        logger.trace("[OUT] getMasterId");
        return getParameter(Parameter.MASTER_ID);
    }

    /**
     * Get the list of model configured for search.
     * 
     * @return the list of model that all searched records must have.
     */
    public List<String> getModel() {
        logger.trace("[IN]  getModel");
        logger.trace("[OUT] getModel");
        return getParameter(Parameter.MODEL);
    }

    /**
     * Get offset (pagination).
     * 
     * @return offset to paginate through the available results
     */
    public int getOffset() {
        logger.trace("[IN]  getOffset");
        logger.trace("[OUT] getOffset");
        if (isConfigured(Parameter.OFFSET)) {
            return getParameter(Parameter.OFFSET);
        } else {
            return -1;
        }
    }

    /**
     * Get the list of product configured for search.
     * 
     * @return the list of product that all searched records must have.
     */
    public List<String> getProduct() {
        logger.trace("[IN]  getProduct");
        logger.trace("[OUT] getProduct");
        return getParameter(Parameter.PRODUCT);
    }

    /**
     * Get the list of project configured for search.
     * 
     * @return the list of project that all searched records must have.
     */
    public List<String> getProject() {
        logger.trace("[IN]  getProject");
        logger.trace("[OUT] getProject");
        return getParameter(Parameter.PROJECT);
    }

    /**
     * Get query value.
     * 
     * @return the query
     */
    public String getQuery() {
        logger.trace("[IN]  getQuery");
        logger.trace("[OUT] getQuery");
        return getParameter(Parameter.QUERY);
    }

    /**
     * Get the list of realms configured for search.
     * 
     * @return the list of realms that all searched records must have.
     */
    public List<String> getRealm() {
        logger.trace("[IN]  getRealm");
        logger.trace("[OUT] getRealm");
        return getParameter(Parameter.REALM);
    }

    /**
     * Get the type of replica that are configured for search.
     * 
     * @return search master or replicas or all replicas
     */
    public Replica getReplica() {
        return getParameter(Parameter.REPLICA);
    }

    /**
     * Get list of shards to be queried (index nodes).
     * 
     * @return explicit list of shards separated with "," to be queried
     */
    public String getShards() {
        logger.trace("[IN]  getShards");
        logger.trace("[OUT] getShards");
        return getParameter(Parameter.SHARDS);
    }

    /**
     * Get start of temporal query.
     * 
     * @return the start time to a temporal range query
     */
    public Calendar getStart() {
        logger.trace("[IN]  getStart");
        logger.trace("[OUT] getStart");
        return getParameter(Parameter.START);
    }

    /**
     * Get the list of time frequency configured for search.
     * 
     * @return the list of time frequency that all searched records must have.
     */
    public List<String> getTimeFrequency() {
        logger.trace("[IN]  getTimeFrequency");
        logger.trace("[OUT] getTimeFrequency");
        return getParameter(Parameter.TIME_FREQUENCY);
    }

    /**
     * Get to of a a query based on the record last update (timestamp).
     * 
     * @return to of a a query based on the record last update (timestamp)
     */
    public Calendar getTo() {
        logger.trace("[IN]  getTo");
        logger.trace("[OUT] getTo");
        return getParameter(Parameter.TO);
    }

    /**
     * Get tracking id configured for search. Only Files. To search by the UUID
     * assigned to a File by some special publication software, if available.
     * Only files. If is configured for another records the search will returns
     * 0 records.
     * 
     * @return tracking id that all searched files must have.
     */
    public String getTrackingId() {
        logger.trace("[IN]  getTrackingId");
        logger.trace("[OUT] getTrackingId");
        return getParameter(Parameter.TRACKING_ID);
    }

    /**
     * Get the type configured for search. Possible values: Dataset, File,
     * Aggregation
     * 
     * @return the type that all searched records must have.
     */
    public RecordType getType() {
        logger.trace("[IN]  getType");
        logger.trace("[OUT] getType");
        return getParameter(Parameter.TYPE);
    }

    /**
     * Get the list of variable configured for search.
     * 
     * @return the list of variable that all searched records must have.
     */
    public List<String> getVariable() {
        logger.trace("[IN]  getVariable");
        logger.trace("[OUT] getVariable");
        return getParameter(Parameter.VARIABLE);
    }

    /**
     * Get the list of variables long name configured for search.
     * 
     * @return the list of variables that all searched records must have.
     */
    public List<String> getVariableLongName() {
        logger.trace("[IN]  getVariableLongName");
        logger.trace("[OUT] getVariableLongName");
        return getParameter(Parameter.VARIABLE_LONG_NAME);
    }

    /**
     * Get the version configured for search.
     * 
     * @return the version that all searched records must have.
     */
    public List<String> getVersion() {
        logger.trace("[IN]  getVersion");
        logger.trace("[OUT] getVersion");
        return getParameter(Parameter.VERSION);
    }

    /**
     * Get the xlink configured for search. To search by reference to external
     * record documentation, such as technical notes.
     * 
     * @return the xlink that all searched records must have.
     */
    public String getXlink() {
        logger.trace("[IN]  getXlink");
        logger.trace("[OUT] getXlink");
        return getParameter(Parameter.XLINK);
    }

    /**
     * Get type of search (local or grid). If is not configured the search is
     * global (grid)
     * 
     * @return true for grid search and false for local search
     */
    public boolean isDistrib() {
        logger.trace("[IN]  isDistrib");
        logger.trace("[OUT] isDistrib");
        if (isConfigured(Parameter.DISTRIB)) {
            return getParameter(Parameter.DISTRIB);
        } else {
            return true; // if not configured the search is global
        }
    }

    /**
     * Set the access service for searching records that offer this service.
     * 
     * @param accessList
     *            access service offered by ESGF for access to data
     */
    public void setAccess(List<Service> accessList) {
        logger.trace("[IN]  setAccess");
        setParameter(Parameter.ACCESS, accessList);
        logger.trace("[OUT] setAccess");
    }

    /**
     * Set [west,south,east,north] of a spatial coverage query.
     * 
     * @param bbox
     *            [west,south,east,north] of a spatial coverage query
     */
    public void setBbox(float[] bbox) throws IndexOutOfBoundsException {

        logger.trace("[IN]  setBbox");
        if (bbox.length > 4) {
            logger.error("Illegal Argument. Bounding box must be [west,south,east,north]");
            throw new IllegalArgumentException(
                    "Bounding box must be [west,south,east,north]");
        } else {
            setParameter(Parameter.BBOX, bbox);
        }
        logger.trace("[OUT] setBbox");
    }

    /**
     * Set the list of CF Standard name configured for the search. Searched
     * records must have Must have at least one of given cf_standard__names.
     * 
     * @param cfStandardName
     *            the list of cf_standard_name to search by CF Standard name.
     */
    public void setCfStandardName(List<String> cfStandardName) {
        logger.trace("[IN]  setCfStandardName");
        setParameter(Parameter.CF_STANDARD_NAME, cfStandardName);
        logger.trace("[OUT] setCfStandardName");
    }

    /**
     * Set the checksum configured for search. Searched records must have the
     * given checksum.
     * 
     * @param checksum
     *            the checksum to search by file checksum .
     */
    public void setChecksum(String checksum) {
        logger.trace("[IN]  setChecksum");
        setParameter(Parameter.CHECKSUM, checksum);
        logger.trace("[OUT] setChecksum");
    }

    /**
     * Set the checksum type configured for search. Searched records must have
     * the given checksum type.
     * 
     * @param checksumType
     *            the checksum to search by file checksum type.
     */
    public void setChecksumType(ChecksumType checksumType) {
        logger.trace("[IN]  setChecksumType");
        setParameter(Parameter.CHECKSUM_TYPE, checksumType);
        logger.trace("[OUT] setChecksumType");
    }

    /**
     * Set the list of CMIP tables configured for search. Searched records must
     * have Must have at least one of given cf_standard__names.
     * 
     * @param cmipTable
     *            the cmiptable to search by CMIP table.
     */
    public void setCmipTable(List<String> cmipTable) {
        logger.trace("[IN]  setCmipTable");
        setParameter(Parameter.CMOR_TABLE, cmipTable);
        logger.trace("[OUT] setCmipTable");
    }

    /**
     * Set the list of data node configured for search. Searched records must
     * have Must have at least one of given dataNodes.
     * 
     * @param dataNode
     *            the list of data node to search by data node where the data is
     *            stored
     */
    public void setDataNode(List<String> dataNode) {
        logger.trace("[IN]  setDataNode");
        setParameter(Parameter.DATA_NODE, dataNode);
        logger.trace("[OUT] setDataNode");
    }

    /**
     * Set the data node configured for search. Only files and aggregations. If
     * is configured for another records the search will returns 0 records.
     * Searched files must have the given dataset id.
     * 
     * @param datasetId
     *            the dataset id to search by dataset id
     */
    public void setDatasetId(String datasetId) {
        logger.trace("[IN]  setDatasetId");
        setParameter(Parameter.DATASET_ID, datasetId);
        logger.trace("[OUT] setDatasetId");
    }

    /**
     * Set type of search (local or grid).
     * 
     * @param distrib
     *            true for grid search and false for local search
     */
    public void setDistrib(boolean distrib) {
        logger.trace("[IN]  setDistrib");
        setParameter(Parameter.DISTRIB, distrib);
        logger.trace("[OUT] setDistrib");
    }

    /**
     * Set end time of temporal query
     * 
     * @param end
     *            end time to a temporal range query
     */
    public void setEnd(Calendar end) {
        logger.trace("[IN]  setEnd");
        setParameter(Parameter.END, end);
        logger.trace("[OUT] setEnd");
    }

    /**
     * Set the list of ensembles configured for search. Searched records must
     * have Must have at least one of given ensembles.
     * 
     * @param ensemble
     *            the list of ensembles to search by ensemble
     */
    public void setEnsemble(List<String> ensemble) {
        logger.trace("[IN]  setEnsemble");
        setParameter(Parameter.ENSEMBLE, ensemble);
        logger.trace("[OUT] setEnsemble");
    }

    /**
     * Set the list of experiment configured for search. Searched records must
     * have Must have at least one of given experiment.
     * 
     * @param experiment
     *            the list of experiment to search by experiment
     */
    public void setExperiment(List<String> experiment) {
        logger.trace("[IN]  setExperiment");
        setParameter(Parameter.EXPERIMENT, experiment);
        logger.trace("[OUT] setExperiment");
    }

    /**
     * Set the experiment family configured for search. Searched records must
     * have Must have at least one of given experiment families.
     * 
     * @param experimentFamily
     *            the list of experiment families to search by experiment
     */
    public void setExperimentFamily(List<String> experimentFamily) {
        logger.trace("[IN]  setExperimentFamily");
        setParameter(Parameter.EXPERIMENT_FAMILY, experimentFamily);
        logger.trace("[OUT] setExperimentFamily");
    }

    /**
     * Set Facets
     * 
     * @param facets
     *            Set<String> strings of name facets
     */
    public void setFacets(Set<String> facets) {
        logger.trace("[IN]  setFacets");
        setParameter(Parameter.FACETS, facets);
        logger.trace("[OUT] setFacets");
    }

    /**
     * Set fields to search.
     * 
     * @param fields
     *            the set of metadata to set.
     */
    public void setFields(Set<Metadata> fields) {
        logger.trace("[IN]  setFields");
        setParameter(Parameter.FIELDS, fields);
        logger.trace("[OUT] setFields");
    }

    /**
     * Set response document output format.
     * 
     * @param format
     *            the format to specify the response document output format
     */
    public void setFormat(Format format) {
        logger.trace("[IN]  setFormat");
        setParameter(Parameter.FORMAT, format);
        logger.trace("[OUT] setFormat");
    }

    /**
     * Set from of a a query based on the record last update (timestamp).
     * 
     * @param from
     *            the to of a a query based on the record last update
     *            (timestamp)
     */
    public void setFrom(Calendar from) {
        logger.trace("[IN]  setFrom");
        setParameter(Parameter.FROM, from);
        logger.trace("[OUT] setFrom");
    }

    /**
     * Set the id configured for search. Searched records must have the given
     * id. This id is unique for each instance of Record for all versions and
     * replicas.
     * 
     * @param idList
     *            To search a unique instance of Record.
     */
    public void setId(List<String> idList) {
        logger.trace("[IN]  setId");
        setParameter(Parameter.ID, idList);
        logger.trace("[OUT] setId");
    }

    /**
     * Set the list of index node configured for search. Searched records must
     * have Must have at least one of given index nodes.
     * 
     * @param indexNode
     *            the list of index nodes to search by index node where the data
     *            is published
     */
    public void setIndexNode(List<String> indexNode) {
        logger.trace("[IN]  setIndexNode");
        setParameter(Parameter.INDEX_NODE, indexNode);
        logger.trace("[OUT] setIndexNode");
    }

    /**
     * Set the instance id configured for search. Searched records must have the
     * given instance id. This instance id is same for all replicas across
     * federation, but specific to each version.
     * 
     * @param instanceIdList
     *            id To search all {@link Record} instances for one version.
     */
    public void setInstanceId(List<String> instanceIdList) {
        logger.trace("[IN]  setInstanceId");
        setParameter(Parameter.INSTANCE_ID, instanceIdList);
        logger.trace("[OUT] setInstanceId");
    }

    /**
     * Set the list of institutes configured for search. Searched records must
     * have Must have at least one of given institutes.
     * 
     * @param institute
     *            the list of institutes to search by institute
     * 
     */
    public void setInstitute(List<String> institute) {
        logger.trace("[IN]  setInstitute");
        setParameter(Parameter.INSTITUTE, institute);
        logger.trace("[OUT] setInstitute");
    }

    /**
     * Set the list of instruments configured for search. Searched records must
     * have Must have at least one of given instrument.
     * 
     * @param instrument
     *            the list of instruments to search by instrument
     * 
     */
    public void setInstrument(List<String> instrument) {
        logger.trace("[IN]  setInstrument");
        setParameter(Parameter.SOURCE_ID, instrument);
        logger.trace("[OUT] setInstrument");
    }

    /**
     * Set the latest configured for search. Searched records must have the type
     * of search version configured: (latest, previous or all versions)
     * 
     * @param latest
     *            to search latest or previous or all versions type of search
     * 
     */
    public void setLatest(Latest latest) {
        logger.trace("[IN]  setLatest");
        setParameter(Parameter.LATEST, latest);
        logger.trace("[OUT] setLatest");
    }

    /**
     * Set limit (pagination).
     * 
     * @param limit
     *            limit to paginate through the available results. Values
     *            greater than 0 are used to set the number of results; a limit
     *            of 0 is used to get just the count of results, without results
     *            themselves. Negative values are dismissed
     */
    public void setLimit(int limit) {
        logger.trace("[IN]  setLimit");
        // The system imposes a maximum value of limit < 10,000
        if (limit > 9999) {
            setParameter(Parameter.LIMIT, 9999);
        }
        setParameter(Parameter.LIMIT, limit);
        logger.trace("[OUT] setLimit");
    }

    /**
     * Set the instance id configured for search. Searched records must have the
     * given instance id. This instance id is same for all replicas and versions
     * across federation.
     * 
     * @param masterIdList
     *            to search all instances for all versions of a Record.
     */
    public void setMasterId(List<String> masterIdList) {
        logger.trace("[IN]  setMasterId");
        setParameter(Parameter.MASTER_ID, masterIdList);
        logger.trace("[OUT] setMasterId");
    }

    /**
     * Set the list of models configured for search.Searched records must have
     * Must have at least one of given models.
     * 
     * @param model
     *            the list of models to search by model
     * 
     */
    public void setModel(List<String> model) {
        logger.trace("[IN]  setModel");
        setParameter(Parameter.MODEL, model);
        logger.trace("[OUT] setModel");
    }

    /**
     * Set offset (pagination).
     * 
     * @param offset
     *            offset to paginate through the available results
     */
    public void setOffset(int offset) {
        logger.trace("[IN]  setOffset");
        setParameter(Parameter.OFFSET, offset);
        logger.trace("[OUT] setOffset");
    }

    /**
     * Set the list of products configured for search. Searched records must
     * have Must have at least one of given products.
     * 
     * @param product
     *            the list of products to search by product
     * 
     */
    public void setProduct(List<String> product) {
        logger.trace("[IN]  setProduct");
        setParameter(Parameter.PRODUCT, product);
        logger.trace("[OUT] setProduct");
    }

    /**
     * Set the list of projects configured for search. Searched records must
     * have Must have at least one of given projects.
     * 
     * @param project
     *            the list of projects project to search by project
     * 
     */
    public void setProject(List<String> project) {
        logger.trace("[IN]  setProject");
        setParameter(Parameter.PROJECT, project);
        logger.trace("[OUT] setProject");
    }

    /**
     * Set query value.
     * 
     * @param query
     *            query string
     */
    public void setQuery(String query) {
        logger.trace("[IN]  setQuery");
        setParameter(Parameter.QUERY, query);
        logger.trace("[OUT] setQuery");
    }

    /**
     * Set the list of realms configured for search. Searched records must have
     * Must have at least one of given realms.
     * 
     * @param realm
     *            the list of realms to search by realm
     * 
     */
    public void setRealm(List<String> realm) {
        logger.trace("[IN]  setRealm");
        setParameter(Parameter.REALM, realm);
        logger.trace("[OUT] setRealm");
    }

    /**
     * Set the type of replica that are configured for search. Searched records
     * must have the type of search replica configured: (master, replicas or all
     * replicas)
     * 
     * @param replica
     *            to search master, replicas or all replica.
     * 
     */
    public void setReplica(Replica replica) {
        logger.trace("[IN]  setReplica");
        setParameter(Parameter.REPLICA, replica);
        logger.trace("[OUT] setReplica");
    }

    /**
     * Set list of shards to be queried (index nodes). Only if search is
     * distributed (distrib=true)
     * 
     * @param shards
     *            explicit list of shards separated with "," to be queried
     * 
     * @throws IllegalStateException
     *             when distrib parameter is true. i.e distributed search is
     *             configured
     */
    public void setShards(String shards) throws IllegalStateException {
        logger.trace("[IN]  setShards");
        if (isDistrib()) {
            setParameter(Parameter.SHARDS, shards);
        } else {
            throw new IllegalStateException();
        }
        logger.trace("[OUT] setShards");
    }

    /**
     * Set start of temporal query.
     * 
     * @param start
     *            start time to a temporal range query
     */
    public void setStart(Calendar start) {
        logger.trace("[IN]  setStart");
        setParameter(Parameter.START, start);
        logger.trace("[OUT] setStart");
    }

    /**
     * Set the list of time frequencies configured for search. Searched records
     * must have Must have at least one of given time frequencies.
     * 
     * @param timeFrequency
     *            the list of time frequency value to search by time frequency
     * 
     */
    public void setTimeFrequency(List<String> timeFrequency) {
        logger.trace("[IN]  setTimeFrequency");
        setParameter(Parameter.TIME_FREQUENCY, timeFrequency);
        logger.trace("[OUT] setTimeFrequency");
    }

    /**
     * Set From of a a query based on the record last update (timestamp)
     * 
     * @param to
     *            the to of a a query based on the record last update
     *            (timestamp)
     */
    public void setTo(Calendar to) {
        logger.trace("[IN]  setTo");
        setParameter(Parameter.TO, to);
        logger.trace("[OUT] setTo");
    }

    /**
     * Set the tracking id configured for search. Searched records must have the
     * given tracking id. To search by the UUID assigned to a File by some
     * special publication software, if available. Only files. If is configured
     * for another records the search will returns 0 records.
     * 
     * @param trackingId
     *            tracking id value to search by tracking id.
     * 
     */
    public void setTrackingId(String trackingId) {
        logger.trace("[IN]  setTrackingId");
        setParameter(Parameter.TRACKING_ID, trackingId);
        logger.trace("[OUT] setTrackingId");
    }

    /**
     * Set the type configured for search. Searched records must have the given
     * type.
     * 
     * @param type
     *            type of record: dataset or file or aggregation
     * 
     */
    public void setType(RecordType type) {
        logger.trace("[IN]  setType");
        setParameter(Parameter.TYPE, type);
        logger.trace("[OUT] setType");
    }

    /**
     * Set the list of variables configured for search. Searched records must
     * have Must have at least one of given variables.
     * 
     * @param variable
     *            the list of variables to search by variable
     * 
     */
    public void setVariable(List<String> variable) {
        logger.trace("[IN]  setVariable");
        setParameter(Parameter.VARIABLE, variable);
        logger.trace("[OUT] setVariable");
    }

    /**
     * Set the list of variable long names configured for search. Searched
     * records must have Must have at least one of given variable long names.
     * 
     * @param variableLongName
     *            the list of variable to search by variable long name
     * 
     */
    public void setVariableLongName(List<String> variableLongName) {
        logger.trace("[IN]  setVariableLongName");
        setParameter(Parameter.VARIABLE_LONG_NAME, variableLongName);
        logger.trace("[OUT] setVariableLongName");
    }

    /**
     * Set the version configured for search. Searched records must have the
     * given version.
     * 
     * @param versionList
     *            version to search by version
     * 
     */
    public void setVersion(List<String> versionList) {
        logger.trace("[IN]  setVersion");
        setParameter(Parameter.VERSION, versionList);
        logger.trace("[OUT] setVersion");
    }

    /**
     * Set the xlink configured for search. Searched records must have the given
     * xlink.
     * 
     * @param xlink
     *            xlink to search by reference to external record documentation,
     *            such as technical notes.
     * 
     */
    public void setXlink(String xlink) {
        logger.trace("[IN]  setXlink");
        setParameter(Parameter.XLINK, xlink);
        logger.trace("[OUT] setXlink");
    }

    /**
     * Generate the partial query string corresponding to parameters
     * configuration.
     * 
     * @return paramters query string, URL-encoded
     */
    public String toQueryString() {
        logger.trace("[IN]  toQueryString");

        // query string values must be URL-encoded
        try {

            // Aux
            String queryString = "";
            SimpleDateFormat dateFormat = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'");

            logger.debug("Parameters \"keywords\" to encode query string");

            // keywords parameters
            // facets
            Set<String> facets = getFacets();
            if (facets != null && !facets.isEmpty()) {
                String sfacets = "";
                for (String facet : facets) {
                    sfacets = sfacets + facet.toLowerCase() + ",";
                }

                // remove last ","
                sfacets = sfacets.substring(0, sfacets.length() - 1);
                queryString = queryString + "facets="
                        + URLEncoder.encode(sfacets, ENCODE_FORMAT) + "&";
            }

            // shards
            String shards = getShards();
            if (shards != null) {
                queryString = queryString + "shards="
                        + URLEncoder.encode(shards, ENCODE_FORMAT) + "&";
            }

            // offset
            int offset = getOffset();
            if (offset > 0) {
                queryString = queryString + "offset=" + offset + "&";
            }

            // limit
            int limit = getLimit();
            if (limit >= 0) {
                queryString = queryString + "limit=" + limit + "&";
            }

            // fields
            Set<Metadata> fields = getFields();
            if (fields != null && !fields.isEmpty()) {
                String sfields = "fields=";
                for (Metadata metadata : fields) {
                    sfields = sfields
                            + URLEncoder.encode(metadata.toString()
                                    .toLowerCase(), ENCODE_FORMAT) + ",";
                }

                // remove last ","
                sfields = sfields.substring(0, sfields.length() - 1);
                queryString = queryString + sfields + "&";
            }

            // format
            Format format = getFormat();
            if (format != null) {
                queryString = queryString + "format="
                        + URLEncoder.encode(format.toString(), ENCODE_FORMAT)
                        + "&";
            }

            // core facets parameters
            logger.debug("Parameters \"core facets\" to encode query string");
            // query
            String query = getQuery();
            if (query != null) {
                queryString = queryString + "query="
                        + URLEncoder.encode(query, ENCODE_FORMAT) + "&";
            }

            // distrib
            boolean distrib = isDistrib();
            queryString = queryString + "distrib=" + distrib + "&";

            // id
            List<String> ids = getId();
            if (ids != null) {

                for (String id : ids) {
                    queryString = queryString + "id="
                            + URLEncoder.encode(id, ENCODE_FORMAT) + "&";
                }
            }

            // instance_id
            List<String> instance_ids = getInstanceId();
            if (instance_ids != null) {

                for (String instance_id : instance_ids) {
                    queryString = queryString + "instance_id="
                            + URLEncoder.encode(instance_id, ENCODE_FORMAT)
                            + "&";
                }
            }

            // master_id
            List<String> master_ids = getMasterId();
            if (master_ids != null) {

                for (String master_id : master_ids) {
                    queryString = queryString + "master_id="
                            + URLEncoder.encode(master_id, ENCODE_FORMAT) + "&";
                }
            }

            // type
            RecordType type = getType();
            if (type != null) {
                queryString = queryString + "type="
                        + URLEncoder.encode(type.toString(), ENCODE_FORMAT)
                        + "&";
            }

            // replica
            Replica replica = getReplica();
            if (replica != null) {

                if (replica == Replica.MASTER) {
                    queryString = queryString + "replica=false&";
                } else if (replica == Replica.REPLICA) {
                    queryString = queryString + "replica=true&";
                }
            }

            // latest
            Latest latest = getLatest();
            if (latest != null) {

                if (latest == Latest.LATEST) {
                    queryString = queryString + "latest=true&";
                } else if (latest == Latest.PREVIOUS) {
                    queryString = queryString + "latest=false&";
                }
            }

            // data_node
            List<String> data_node = getDataNode();
            if (data_node != null) {

                for (String dataNode : data_node) {
                    queryString = queryString + "data_node="
                            + URLEncoder.encode(dataNode, ENCODE_FORMAT) + "&";
                }
            }

            List<String> index_node = getIndexNode();
            // index_node
            if (index_node != null) {
                for (String indexNode : index_node) {
                    queryString = queryString + "index_node="
                            + URLEncoder.encode(indexNode, ENCODE_FORMAT) + "&";
                }
            }

            // version
            List<String> versions = getVersion();
            if (versions != null) {
                // For each versin in version list
                for (String version : versions) {
                    queryString = queryString + "version="
                            + URLEncoder.encode(version, ENCODE_FORMAT) + "&";
                }
            }

            // access
            List<Service> access = getAccess();
            if (access != null) {
                // For each service in access list
                for (Service service : access) {
                    queryString = queryString
                            + "access="
                            + URLEncoder.encode(service.toString(),
                                    ENCODE_FORMAT) + "&";
                }
            }

            // xlink
            String xlink = getXlink();
            if (xlink != null) {
                queryString = queryString + "xlink="
                        + URLEncoder.encode(xlink, ENCODE_FORMAT) + "&";
            }

            // checksum
            String checksum = getChecksum();
            if (checksum != null) {
                queryString = queryString + "checksum="
                        + URLEncoder.encode(checksum, ENCODE_FORMAT) + "&";
            }

            // checksum_type
            ChecksumType checksum_type = getChecksumType();
            if (checksum_type != null) {
                queryString = queryString
                        + "checksum_type="
                        + URLEncoder.encode(checksum_type.toString(),
                                ENCODE_FORMAT) + "&";
            }

            // dataset_id
            String dataset_id = getDatasetId();
            if (dataset_id != null) {
                queryString = queryString + "dataset_id="
                        + URLEncoder.encode(dataset_id, ENCODE_FORMAT) + "&";
            }

            // tracking_id
            String tracking_id = getTrackingId();
            if (tracking_id != null) {
                queryString = queryString + "tracking_id="
                        + URLEncoder.encode(tracking_id, ENCODE_FORMAT) + "&";
            }

            // start
            Calendar start = getStart();
            if (start != null) {
                queryString = queryString
                        + "start="
                        + URLEncoder.encode(
                                dateFormat.format(start.getTime().getTime()),
                                ENCODE_FORMAT) + "&";
            }

            // end
            Calendar end = getEnd();
            if (end != null) {
                queryString = queryString
                        + "end="
                        + URLEncoder.encode(dateFormat.format(end.getTime()),
                                ENCODE_FORMAT) + "&";
            }

            // bbox
            float[] bbox = getBbox();
            if (bbox != null) {
                queryString = queryString
                        + "bbox="
                        + URLEncoder.encode("[" + bbox[0] + "," + bbox[1] + ","
                                + bbox[2] + "," + bbox[3] + "]", ENCODE_FORMAT)
                        + "&";
            }

            // from
            Calendar from = getFrom();
            if (from != null) {
                queryString = queryString
                        + "from="
                        + URLEncoder.encode(dateFormat.format(from.getTime()),
                                ENCODE_FORMAT) + "&";
            }

            // to
            Calendar to = getTo();
            if (to != null) {
                queryString = queryString
                        + "to="
                        + URLEncoder.encode(dateFormat.format(to.getTime()),
                                ENCODE_FORMAT) + "&";
            }

            // Search Category facets
            logger.debug("Parameters \"search category facets\" to encode query string");

            // cf_standard_name
            List<String> cf_standard_name = getCfStandardName();
            if (cf_standard_name != null) {
                for (String cfStandardName : cf_standard_name) {
                    queryString = queryString + "cf_standard_name="
                            + URLEncoder.encode(cfStandardName, ENCODE_FORMAT)
                            + "&";
                }
            }

            // ensemble
            List<String> ensemble = getEnsemble();
            if (ensemble != null) {
                for (String strEnsemble : ensemble) {
                    queryString = queryString + "ensemble="
                            + URLEncoder.encode(strEnsemble, ENCODE_FORMAT)
                            + "&";
                }
            }

            // experiment
            List<String> experiment = getExperiment();
            if (experiment != null) {

                for (String strExperiment : experiment) {
                    queryString = queryString + "experiment="
                            + URLEncoder.encode(strExperiment, ENCODE_FORMAT)
                            + "&";

                }
            }

            // experiment_family
            List<String> experiment_family = getExperimentFamily();
            if (experiment_family != null) {
                for (String experimentFamily : experiment_family) {
                    queryString = queryString
                            + "experiment_family="
                            + URLEncoder
                                    .encode(experimentFamily, ENCODE_FORMAT)
                            + "&";
                }
            }

            // institute
            List<String> institute = getInstitute();
            if (institute != null) {
                for (String strInstitute : institute) {
                    queryString = queryString + "institute="
                            + URLEncoder.encode(strInstitute, ENCODE_FORMAT)
                            + "&";

                }
            }

            // cmor_table
            List<String> cmor_table = getCmipTable();
            if (cmor_table != null) {
                for (String cmorTable : cmor_table) {
                    queryString = queryString + "cmor_table="
                            + URLEncoder.encode(cmorTable, ENCODE_FORMAT) + "&";
                }
            }

            // model
            List<String> model = getModel();
            if (model != null) {
                for (String strModel : model) {
                    queryString = queryString + "model="
                            + URLEncoder.encode(strModel, ENCODE_FORMAT) + "&";
                }
            }

            // project
            List<String> project = getProject();
            if (project != null) {
                for (String strProject : project) {
                    queryString = queryString + "project="
                            + URLEncoder.encode(strProject, ENCODE_FORMAT)
                            + "&";
                }
            }

            // product
            List<String> product = getProduct();
            if (product != null) {
                for (String strProduct : product) {
                    queryString = queryString + "product="
                            + URLEncoder.encode(strProduct, ENCODE_FORMAT)
                            + "&";
                }
            }

            // realm
            List<String> realm = getRealm();
            if (realm != null) {
                for (String strRealm : realm) {
                    queryString = queryString + "realm="
                            + URLEncoder.encode(strRealm, ENCODE_FORMAT) + "&";
                }
            }

            // time_frequency
            List<String> time_frequency = getTimeFrequency();
            if (time_frequency != null) {
                for (String timeFrequency : time_frequency) {
                    queryString = queryString + "time_frequency="
                            + URLEncoder.encode(timeFrequency, ENCODE_FORMAT)
                            + "&";
                }
            }

            // variable
            List<String> variable = getVariable();
            if (variable != null) {
                for (String strVariable : variable) {
                    queryString = queryString + "variable="
                            + URLEncoder.encode(strVariable, ENCODE_FORMAT)
                            + "&";
                }
            }

            // variable_long_name
            List<String> variable_long_name = getVariableLongName();
            if (variable_long_name != null) {
                for (String variableLongName : variable_long_name) {
                    queryString = queryString
                            + "variable_long_name="
                            + URLEncoder
                                    .encode(variableLongName, ENCODE_FORMAT)
                            + "&";
                }
            }

            // source_id
            List<String> source_id = getInstrument();
            if (source_id != null) {
                for (String strSourceId : source_id) {
                    queryString = queryString + "source_id="
                            + URLEncoder.encode(strSourceId, ENCODE_FORMAT)
                            + "&";
                }
            }

            // remove last '&', if any
            if (queryString.length() > 0
                    && queryString.charAt(queryString.length() - 1) == '&') {
                queryString = queryString
                        .substring(0, queryString.length() - 1);
            }

            logger.trace("[OUT] toQueryString");

            return queryString;
        } catch (UnsupportedEncodingException e) {
            logger.error(
                    "Unsupported encoding exception in parameter conversion to query string: {}",
                    e.getStackTrace());
            // this should never happen
            return null;
        }
    }

    /**
     * Get a string representation of parameters and all it's values.
     * 
     * @return a string representation of parametersand all it's values
     */
    @Override
    public String toString() {
        String text = "";

        // For each parameter enum value
        for (Parameter parameter : Parameter.values()) {
            // if document parameter is confgured in map
            if (parameters.containsKey(parameter)) {

                Object value = parameters.get(parameter);

                if (value instanceof Calendar) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss'Z'");
                    Calendar calendar = (Calendar) value;
                    String stringDate = dateFormat.format(calendar.getTime());
                    text = text + parameter.name().toLowerCase() + "="
                            + stringDate + " ";
                } else {
                    // text = text + parameter + "=" + parameters.get(parameter)
                    // + " ";
                    text = text + parameter.name().toLowerCase() + "=" + value
                            + " ";
                }
            }
        }

        return text;

    }

    /**
     * Remove configured parameter. If parameter has not been configured,
     * nothing happens. *
     * 
     * @param parameter
     *            parameter to be removed
     * 
     */
    public void removeParameter(Parameter parameter) {
        parameters.remove(parameter);
    }

    /**
     * for clone
     * 
     * @param parameters
     *            the parameters to set
     */
    private void setParameters(Map<Parameter, Object> parameters) {
        this.parameters = parameters;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        Object clone = null;
        clone = super.clone();

        // ('deep clone')

        Map<Parameter, Object> parametersClone = new HashMap<Parameter, Object>(
                parameters);

        ((Parameters) clone).setParameters(parametersClone);

        return clone;
    }

    /**
     * Return a String with all parameters that are constraints of result in the
     * search and not are configurations of the result shown.
     * 
     * @return a String with all parameters that are constraints of result in
     *         search
     */
    public String getConstraintParametersString() {
        String text = "";

        // For each parameter enum value
        for (Parameter param : Parameter.values()) {
            // if document parameter is confgured in map
            if (parameters.containsKey(param)) {

                if (param != Parameter.LIMIT && param != Parameter.OFFSET
                        && param != Parameter.DISTRIB
                        && param != Parameter.FIELDS
                        && param != Parameter.FACETS && param != Parameter.TYPE
                        && param != Parameter.FORMAT) {

                    Object value = parameters.get(param);

                    if (value instanceof Calendar) {
                        SimpleDateFormat dateFormat = new SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss'Z'");
                        Calendar calendar = (Calendar) value;
                        String stringDate = dateFormat.format(calendar
                                .getTime());
                        text = text + param.name().toLowerCase() + "="
                                + stringDate + " ";
                    } else {
                        // text = text + parameter + "=" +
                        // parameters.get(parameter)
                        // + " ";
                        text = text + param.name().toLowerCase() + "=" + value
                                + " ";
                    }
                }
            }
        }

        return text;
    }

}
