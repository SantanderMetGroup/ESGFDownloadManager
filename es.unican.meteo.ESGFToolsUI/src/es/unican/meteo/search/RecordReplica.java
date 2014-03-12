package es.unican.meteo.search;

import java.io.Serializable;
import java.util.Map;

/**
 * <p>
 * Represents a replica of a ESGF {@link Record} of a Output Climate Model.
 * </p>
 * 
 * <p>
 * Each record replica can be the master or a replica of a data output and share
 * metadata about the information that they contain (data node, index node and
 * services).
 * </p>
 * 
 * @author Karem Terry
 * 
 */
public class RecordReplica implements Serializable {

    /** Logger. */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(RecordReplica.class);

    /** Id. Unique id. */
    private String id;

    /** Node where the replica of record is stored. */
    private String dataNode;

    /** Node where the replica of record is indexed. */
    private String indexNode;

    /** Indicates if this record replica is master or replica. */
    private boolean master;

    /** List of service offered to access to the record instance. */
    private Map<Service, String> services;

    /**
     * Constructor.
     */
    public RecordReplica() {
        logger.trace("[IN]  RecordReplica");
        logger.trace("[OUT] RecordReplica");
    }

    /**
     * Constructor with parameters.
     * 
     * @param id
     *            unique id
     * @param dataNode
     *            node where the data is stored
     * @param indexNode
     *            node where the data is indexed
     * @param master
     *            if is master replica or not
     * @param services
     *            node where the data is indexed
     */
    public RecordReplica(String id, String dataNode, String indexNode,
            boolean master, Map<Service, String> services) {

        this.id = id;
        this.dataNode = dataNode;
        this.indexNode = indexNode;
        this.master = master;
        this.services = services;
    }

    /**
     * Get ESGF data node where it is stored.
     * 
     * @return data node where the it is stored.
     */
    public String getDataNode() {
        logger.trace("[IN]  getDataNode");
        logger.trace("[OUT] getDataNode");
        return dataNode;
    }

    /**
     * Set ESGF data node where it is stored.
     * 
     * @param dataNode
     *            data node where the it is stored.
     */
    public void setDataNode(String dataNode) {
        logger.trace("[IN]  setDataNode");
        this.dataNode = dataNode;
        logger.trace("[OUT] setDataNode");
    }

    /**
     * Get id unique for this replica of {@link Record}
     * 
     * @return the id
     */
    public String getId() {
        logger.trace("[IN]  getId");
        logger.trace("[OUT] getId");
        return id;
    }

    /**
     * Set id unique for this replica of {@link Record}
     * 
     * @param id
     *            the id to set
     */
    public void setId(String id) {
        logger.trace("[IN]  setId");
        this.id = id;
        logger.trace("[OUT] setId");
    }

    /**
     * Get ESGF data node where it is indexed.
     * 
     * @return data node where it is indexed.
     */
    public String getIndexNode() {
        logger.trace("[IN]  getIndexNode");
        logger.trace("[OUT] getIndexNode");
        return indexNode;
    }

    /**
     * Set ESGF index node where it is indexed.
     * 
     * @param indexNode
     *            data node where it is indexed.
     */
    public void setIndexNode(String indexNode) {
        logger.trace("[IN]  setIndexNode");
        this.indexNode = indexNode;
        logger.trace("[OUT] setIndexNode");
    }

    /**
     * Indicates if this record replica is master or replica. Return true if
     * this replica of file is master
     * 
     * @return true if this replica of record is master
     */
    public boolean isMaster() {
        logger.trace("[IN]  isMaster");
        logger.trace("[OUT] isMaster");
        return master;
    }

    /**
     * Set master attribute for this record replica. h g
     * 
     * @param master
     *            true if this replica of record master
     */
    public void setMaster(boolean master) {
        logger.trace("[IN]  setMaster");
        this.master = master;
        logger.trace("[OUT] setMaster");
    }

    /**
     * Get a map < {@link Service} , urlEndPoint > with all services that are
     * offered to access to this record replica.
     * 
     * @return services that are offered to access the data.
     */
    public Map<Service, String> getServices() {
        logger.trace("[IN]  getServices");
        logger.trace("[OUT] getServices");
        return services;
    }

    /**
     * Get urlEndPoint of {@link Service} that are offered to access to this
     * service.
     * 
     * @param service
     *            offered by ESGF for access to data
     * @return urlEndPoint or null if not exist service for this replica
     */
    public String getUrlEndPointOfService(Service service) {
        logger.trace("[IN]  getServices");
        logger.trace("[OUT] getServices");
        String url = services.get(service);
        if (url != null) {
            url = url.substring(0, url.indexOf("|"));
        }
        return url;
    }

    /**
     * Set all services that are offered to access to this record replica.
     * 
     * @param services
     *            List of service offered to access to the record instance
     */
    public void setServices(Map<Service, String> services) {
        logger.trace("[IN]  setServices");
        this.services = services;
        logger.trace("[OUT] setServices");
    }

}
