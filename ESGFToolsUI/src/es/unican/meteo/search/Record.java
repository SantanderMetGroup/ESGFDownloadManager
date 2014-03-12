package es.unican.meteo.search;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * Represents a record returned by RESTful search. Dataset, File or Aggregation
 * </p>
 * 
 * <p>
 * The result of a search request is a response document that is encoded in the
 * format specified by the request parameter format.
 * </p>
 * 
 * <p>
 * Each result record is associated with a set of metadata fields. Each field
 * has a name, and may be single-valued or multiple-valued.
 * </p>
 * 
 * @author Karem Terry
 * 
 */
public class Record implements Serializable {

    /**
     * Map of metadata. Key Map is a {@link Metadata} and value is an
     * {@link Object}. This allows flexibility to be able to add values ​​of
     * different types.
     */
    private Map<Metadata, Object> recordMetadata;

    /**
     * ID of a record. In ESGF is specific for all record replicas across and
     * each version. When parsing THREDDS catalogs, it is extracted from ID
     * attribute of tag in THREDDS.
     */
    private String instanceID;

    /** Replicas. */
    private List<RecordReplica> replicas;

    /**
     * Map of < {@link Service} - {@link RecordReplica} > offered to access to
     * ESGF record.
     */
    private Map<Service, List<RecordReplica>> servicesInReplicas;

    /**
     * Empty constructor.
     */
    public Record() {
        recordMetadata = new HashMap<Metadata, Object>();
        replicas = new LinkedList<RecordReplica>();
        servicesInReplicas = new HashMap<Service, List<RecordReplica>>();
    }

    /**
     * Constructor.
     */
    public Record(String instanceID) {
        this.instanceID = instanceID;
        recordMetadata = new HashMap<Metadata, Object>();
        replicas = new LinkedList<RecordReplica>();
        servicesInReplicas = new HashMap<Service, List<RecordReplica>>();
    }

    /**
     * Add metadata.
     * 
     * @param key
     *            name of metadata
     * @param value
     *            value of metadata
     */
    public void addMetadata(Metadata key, Object value) {
        recordMetadata.put(key, value);
    }

    /**
     * Get value of metadata. Generic Method, avoid double casting.
     * 
     * 
     * @param key
     *            name of metadata
     * @return value of metadata, or null if not exists
     */
    @SuppressWarnings("unchecked")
    public <E> E getMetadata(Metadata key) {
        Object o = recordMetadata.get(key);
        return (E) o;
    }

    /**
     * Check if exists metadata value.
     * 
     * @param key
     *            name of metadata
     * @return true if exists, false otherwise
     */
    public boolean contains(Metadata key) {
        return recordMetadata.get(key) != null;
    }

    /**
     * Get instance id of dataset file.
     * 
     * @return the instanceID
     */
    public String getInstanceID() {
        return instanceID;
    }

    /**
     * Set instance id of dataset file.
     * 
     * @param instanceID
     *            the instanceID to set
     */
    public void setInstanceID(String instanceID) {
        this.instanceID = instanceID;
    }

    /**
     * Get a string representation of this component and all it's values.
     * 
     * @return a string representation of this component and all it's values
     */
    @Override
    public String toString() {
        String text = "";

        // For each Metadata enum value
        for (Metadata metadata : Metadata.values()) {
            // if document metadata contains this metadata
            if (recordMetadata.containsKey(metadata)) {
                text = text + metadata + "=" + recordMetadata.get(metadata)
                        + " ";
            }
        }

        return text;

    }

    /**
     * Get number of metadata fields
     * 
     * @return number of metadata fields
     */
    public int getNumOfMetadata() {
        return recordMetadata.size();
    }

    /**
     * Get all metadata
     * 
     * @return the all Metadata
     */
    public Map<Metadata, Object> getRecordMetadata() {
        return recordMetadata;
    }

    /**
     * Set all metadata.
     * 
     * @param recordMetadata
     *            Map of metadata. Key Map is a {@link Metadata} and value is an
     *            {@link Object}. This allows flexibility to be able to add
     *            values ​​of different types.
     */
    public void setRecordMetadata(Map<Metadata, Object> recordMetadata) {
        this.recordMetadata = recordMetadata;
    }

    /**
     * Get all record replicas
     * 
     * @return record replicas
     */
    public List<RecordReplica> getReplicas() {
        return replicas;
    }

    /**
     * Set all record replicas
     * 
     * @param recordReplicas
     *            of record replicas
     */
    public void setReplicas(List<RecordReplica> recordReplicas) {
        this.replicas = recordReplicas;
    }

    /**
     * Get map of service Map < {@link Service} , List of {@link RecordReplica}
     * > contains for each service a replicas list that provides it
     * 
     * @return the mapOfServices
     */
    public Map<Service, List<RecordReplica>> getServicesInReplicas() {
        return servicesInReplicas;
    }

    /**
     * Set map of service Map < {@link Service} , List of {@link RecordReplica}
     * > contains for each service a replicas list that provides it
     * 
     * @param mapOfServices
     *            the mapOfServices to set
     */
    public void setServicesInReplicas(
            Map<Service, List<RecordReplica>> mapOfServices) {
        this.servicesInReplicas = mapOfServices;
    }

    /**
     * Check if record has this service
     * 
     * @param service
     *            service offered to access record
     * @return true if record has this service and false otherwise
     */
    public boolean hasService(Service service) {

        return servicesInReplicas.containsKey(service);

    }

    /**
     * Get a list of {@link RecordReplica} that offer a {@link Service}.
     * 
     * @param service
     *            service offered to access record
     * 
     * @return a list of {@link RecordReplica} that offer the service or null if
     *         any replica offers this service
     */
    public List<RecordReplica> getReplicasOfService(Service service) {
        return servicesInReplicas.get(service);
    }

}
