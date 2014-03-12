package es.unican.meteo.search;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * <p>
 * Represents a ESGF File of Output Climate Model Dataset.
 * </p>
 * 
 * *
 * <p>
 * This is a conceptual file of a Dataset, this is because a dataset can be ESGF
 * in different instances {@link RecordReplica}. Each instance can be the master
 * or a replica of a data output and share each metadata about the information
 * that they contain.
 * </p>
 * 
 * <p>
 * File contains a single output variable (along with coordinate/grid variables,
 * attributes and other metadata) from a single model and a single simulation
 * (i.e., from a single ensemble member of a single climate experiment).
 * </p>
 * 
 * <p>
 * There is flexibility in specifying how many time slices (samples) are stored
 * in a single file. A single file can contain all the time-samples for a given
 * variable and climate experiment, or the samples can be distributed in a
 * sequence of files.
 * </p>
 * 
 * <p>
 * The metadata is constrained by the CF convention (NetCDF Climate and Forecast
 * (CF) Metadata Convention) and as specified in the CMIP5 tables.
 * </p>
 * 
 * <p>
 * The output files are written through the NetCDF API following the
 * NETCDF_CLASSIC model and without compression of any kind.
 * </p>
 * 
 * @author Karem Terry
 * 
 */
public class DatasetFile extends Record implements Serializable {

    /** Instance id of the dataset that it belongs. */
    private String datasetInstanceID;

    /**
     * Empty constructor.
     */
    public DatasetFile() {
        super();
    }

    /**
     * Constructor.
     */
    public DatasetFile(String instanceID, String datasetInstanceID) {
        super(instanceID);
        this.datasetInstanceID = datasetInstanceID;
    }

    /**
     * Get the dataset instance id of the dataset that it belongs.
     * 
     * @return the datasetInstanceID
     */
    public String getDatasetInstanceID() {
        return datasetInstanceID;
    }

    /**
     * Set the dataset instance id of the dataset that it belongs.
     * 
     * @param datasetInstanceID
     *            the datasetInstanceID to set
     */
    public void setDatasetInstanceID(String datasetInstanceID) {
        this.datasetInstanceID = datasetInstanceID;
    }

    /**
     * Summary of information of DatasetFile
     * 
     * @return summary of information of DatasetFile
     */
    public String getSummaryString() {
        String summary = "";

        summary = summary + "File_Instance_id:"
                + getMetadata(Metadata.INSTANCE_ID) + "\nSize:"
                + getMetadata(Metadata.SIZE) + "\nServices:";

        summary = summary + "\nInstances:";

        for (RecordReplica replica : getReplicas()) {
            summary = summary + "\n   id:" + replica.getId();
            summary = summary + "\n   data_node:" + replica.getDataNode();
            // summary = summary + "\n   url:" + fileInstance.getServices();
            summary = summary + "\n   Services:";

            for (Map.Entry<Service, String> element : replica.getServices()
                    .entrySet()) {
                summary = summary + "\n   service:" + element.getKey() + " : "
                        + element.getValue();
            }
            summary = summary + "\n";
        }

        return summary;
    }

    /**
     * DatasetFile to JSON format
     * 
     * @return DatasetFile in JSON format
     */
    public JSONObject toJSON() {
        JSONObject jsonFile = new JSONObject();

        // file instance id
        jsonFile.put("instance_id", getInstanceID());
        jsonFile.put("dataset_instance_id", getDatasetInstanceID());

        // Metadata----------------------------------------------------
        for (Map.Entry<Metadata, Object> element : getRecordMetadata()
                .entrySet()) {

            if (element.getValue() instanceof List) {
                JSONArray array = new JSONArray((List) element.getValue());

                // XXX temporal solution
                // to show in same level an array of metadata
                // project, model, etc are array only one value
                // thats not sense
                if (array.length() < 2) {
                    jsonFile.put(element.getKey().toString().toLowerCase(),
                            element.getValue());
                } else {
                    jsonFile.put(element.getKey().toString().toLowerCase(),
                            array);
                }
            } else {
                jsonFile.put(element.getKey().toString().toLowerCase(),
                        element.getValue());
            }

        }
        // -------------------------------------------------------------

        // Services----------------------------------------------------
        JSONObject jsonServices = new JSONObject();
        for (Map.Entry<Service, List<RecordReplica>> entry : getServicesInReplicas()
                .entrySet()) {
            JSONArray jsonIDOfReplicas = new JSONArray();

            for (RecordReplica replica : entry.getValue()) {
                jsonIDOfReplicas.put(replica.getId());
            }

            jsonServices.put(entry.getKey().name(), jsonIDOfReplicas);
        }
        jsonFile.put("summary_of_services", jsonServices);
        // ------------------------------------------------------------

        // file replicas------------------------------
        JSONArray jsonFileReplicas = new JSONArray();
        for (RecordReplica replica : getReplicas()) {

            JSONObject jsonReplica = new JSONObject();

            jsonReplica.put("id", replica.getId());
            jsonReplica.put("data_node", replica.getDataNode());
            jsonReplica.put("index_node", replica.getIndexNode());

            JSONObject replicaServices = new JSONObject();
            for (Map.Entry<Service, String> element : replica.getServices()
                    .entrySet()) {
                replicaServices
                        .put(element.getKey().name(), element.getValue());
            }
            jsonReplica.put("services", replicaServices);

            jsonFileReplicas.put(jsonReplica);
        }
        jsonFile.put("replicas", jsonFileReplicas);
        // ------------------------------------------------------------

        return jsonFile;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return getInstanceID().hashCode();
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

        Dataset other = (Dataset) obj;
        if (getInstanceID() == null) {
            if (other.getInstanceID() != null) {
                return false;
            }
        } else if (!getInstanceID().equals(other.getInstanceID())) {
            return false;
        }
        return true;
    }

}
