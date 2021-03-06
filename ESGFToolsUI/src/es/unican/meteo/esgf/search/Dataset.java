package es.unican.meteo.esgf.search;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * <p>
 * Represents a ESGF Dataset of a Output Climate Model.
 * </p>
 * 
 * <p>
 * This is a conceptual Dataset, this is because a dataset can be ESGF in
 * different instances {@link RecordReplica}. Each instance can be the master or
 * a replica of a data output and share each metadata about the information that
 * they contain.
 * </p>
 * 
 * <p>
 * This instance of dataset also have different metadata which are references
 * localization, indexing and type of service that is offered for download.
 * </p>
 * 
 * <p>
 * Datasets are abstract data containers which contain files. Each file contains
 * a single output variable (along with coordinate/grid variables, attributes
 * and other metadata) from a single model and a single simulation (i.e., from a
 * single ensemble member of a single climate experiment). This method of
 * structuring model output best serves the needs of most researchers who are
 * typically interested in only a few of the many variables in the Model
 * Intercomparison Project (MIP) databases.
 * </p>
 * 
 * <p>
 * The metadata is constrained by the CF convention (NetCDF Climate and Forecast
 * (CF) Metadata Convention) and as specified in the CMIP5 tables.
 * </p>
 * 
 * 
 * @author Karem Terry
 * 
 */
public class Dataset extends Record implements Serializable {

    /** Set of File of which it is dataset composed . */
    private Set<DatasetFile> files;

    /**
     * To specify harvesting status of Record. EMPTY, PARTIAL_HARVESTED,
     * HARVESTED and FAILED.
     */
    private DatasetHarvestStatus harvestStatus;

    /** List of file observers. */
    private LinkedList<DatasetObserver> observers;

    /**
     * Empty constructor.
     */
    public Dataset() {
        super();
        files = new HashSet<DatasetFile>();
        // Initialize observers
        observers = new LinkedList<DatasetObserver>();
    }

    /**
     * Constructor.
     */
    public Dataset(String instanceID) {
        super(instanceID);
        harvestStatus = DatasetHarvestStatus.EMPTY;
        files = new HashSet<DatasetFile>();
        // Initialize observers
        observers = new LinkedList<DatasetObserver>();
    }

    /**
     * Get dataset file with an intance_id.
     * 
     * @param instanceID
     *            instance id of file that must be returned
     * @return the dataset file if exists and null if not
     * 
     */
    public DatasetFile getFileWithInstanceId(String instanceID) {

        DatasetFile found = null;

        // to compare standard representation of instanceID (without
        // "_Number" in case of id finish with ".nc_Number")
        String sInstanceID = standardizeESGFFileInstanceID(instanceID);

        // Search in all files a file with its instace id = instanceID
        for (DatasetFile file : files) {
            String fileInstanceId = file.getMetadata(Metadata.INSTANCE_ID);
            // to compare standard representation of instanceID (without
            // "_Number" in case of id finish with ".nc_Number")
            String sfileInstanceId = standardizeESGFFileInstanceID(fileInstanceId);
            if (sfileInstanceId.equalsIgnoreCase(sInstanceID)) {
                found = file;
            }
        }

        return found;
    }

    /**
     * Verify if instance ID of ESGF file is correct and if id is corrupted then
     * it corrects the id
     * 
     * @param instanceID
     *            instance_id of file
     * @return the same instance_id if it is a valid id or a new corrected
     *         instance_id , otherwise
     */
    private String standardizeESGFFileInstanceID(String instanceID) {
        // file instane id have this form
        //
        // project.output.model[...]_2000010106-2006010100.nc
        // dataset id have this form
        //
        // project.output.model[...]_2000010106-2006010100

        // If id have ".nc_0" or others instead of .nc
        // Then warning and return correct id

        if (instanceID.matches(".*\\.nc_\\d$")) {
            String[] splitted = instanceID.split(".nc_\\d$");
            instanceID = splitted[0] + ".nc";
        }

        return instanceID;
    }

    /**
     * Get files contained in the dataset.
     * 
     * @return the files contained in the dataset
     */
    public Set<DatasetFile> getFiles() {
        return files;
    }

    /**
     * Set files contained in the dataset.
     * 
     * @param files
     *            the files to set
     */
    public void setFiles(Set<DatasetFile> files) {
        this.files = files;
    }

    /**
     * Get number of files
     * 
     * @return number of files
     */
    public int getNumberOfFiles() {
        return files.size();
    }

    /**
     * Register new object that implement observer for this dataset download
     * status.
     * 
     * @param observer
     */
    public void registerObserver(DatasetObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    /**
     * Get a set of {@link Service} that its files have
     * 
     * @return the mapOfServices or empty map if dataset hasn't files
     */
    public Set<Service> getFileServices() {
        // logger.trace("[IN]  getFileServices");
        Set<Service> fileServices = new HashSet<Service>();

        // return empty map if dataset hasn't files
        if (files.size() > 0) {
            // All files that belongs to a dataset have the same services.
            // Because all file replicas belongs to a dataset replica
            Iterator<DatasetFile> iter = files.iterator();
            fileServices = iter.next().getServicesInReplicas().keySet();

        }

        // logger.trace("[OUT] getFileServices");
        return fileServices;
    }

    /**
     * Get harvest status. Specifies harvesting status of Record.
     * 
     * @return the {@link DatasetHarvestStatus}: EMPTY, PARTIAL_HARVESTED,
     *         HARVESTED and FAILED.
     */
    public DatasetHarvestStatus getHarvestStatus() {
        return harvestStatus;
    }

    /**
     * Set harvest status. Specifies harvesting status of Record.
     * 
     * @param harvestStatus
     *            the {@link DatasetHarvestStatus}: EMPTY PARTIAL_HARVESTED,
     *            HARVESTED and FAILED.
     */
    public void setHarvestStatus(DatasetHarvestStatus harvestStatus) {
        this.harvestStatus = harvestStatus;
        notifyChangeOfHarvestStatus();
    }

    /**
     * Notify on change of harvest status
     */
    private void notifyChangeOfHarvestStatus() {
        for (DatasetObserver o : observers) {
            o.onChangeOfHarvestState(this);
        }
    }

    /**
     * Summary of information of Dataset
     * 
     * @return summary of information of Dataset
     */
    public String getSummaryString() {
        String summary = "";
        summary = summary
                + "\n---=======------------------------------------------------------";
        summary = summary
                + "\n---DATASET------------------------------------------------------";
        summary = summary + "Dataset_Instance_id:" + getInstanceID() + "\nUrl:"
                + getMetadata(Metadata.URL) + "\nServices:";

        for (Map.Entry<Service, List<RecordReplica>> entry : getServicesInReplicas()
                .entrySet()) {

            Service service = entry.getKey();
            String replicasID = "";

            for (RecordReplica replica : entry.getValue()) {
                replicasID = replicasID + " " + replica.getId();
            }

            summary = summary + "\n   service:" + service + " replicas:"
                    + replicasID;
        }

        summary = summary + "\nInstances:";

        for (RecordReplica replica : getReplicas()) {
            summary = summary + "\n   id:" + replica.getId();
            summary = summary + "\n   data_node:" + replica.getDataNode();
            summary = summary + "\n   services:";
            for (Map.Entry<Service, String> element : replica.getServices()
                    .entrySet()) {
                summary = summary + "\n   service:" + element.getKey() + " : "
                        + element.getValue();
            }

            summary = summary + "\n";
        }

        summary = summary + "\nFiles:";
        summary = summary
                + "\n---Files------------------------------------------------------";
        for (DatasetFile file : files) {
            summary = summary + "\nfile:" + file.getSummaryString() + "\n";
        }

        summary = summary
                + "\n---------------------------------------------------------";
        summary = summary
                + "\n---=======------------------------------------------------------";

        return summary;
    }

    /**
     * Dataset to JSON
     * 
     * @return dataset in JSON format
     */
    public JSONObject toJSON() {

        JSONObject jsonDataset = new JSONObject();

        // intance id-----------------------------------------
        String instance_id = getInstanceID();
        jsonDataset.put("instance_id", instance_id);

        // Metadata----------------------------------------------------

        for (Map.Entry<Metadata, Object> element : getRecordMetadata()
                .entrySet()) {

            if (element.getKey() != Metadata.ID) {
                if (element.getValue() != null) {

                    if (element.getValue() instanceof List) {
                        JSONArray array = new JSONArray(
                                (List) element.getValue());

                        // XXX temporal solution
                        // to show in same level an array of metadata
                        // project, model, etc are array only one value
                        // thats not sense
                        if (array.length() < 2) {
                            jsonDataset.put(element.getKey().toString()
                                    .toLowerCase(), element.getValue());
                        } else {
                            jsonDataset.put(element.getKey().toString()
                                    .toLowerCase(), array);
                        }
                    } else {
                        jsonDataset.put(element.getKey().toString()
                                .toLowerCase(), element.getValue());
                    }
                }
            }
        }
        // ------------------------------------------------------------

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
        jsonDataset.put("summary_of_services", jsonServices);
        // ------------------------------------------------------------

        // Dataset replicas--------------------------------------------
        JSONArray jsonDatasetReplicas = new JSONArray();
        for (RecordReplica dataReplica : getReplicas()) {

            JSONObject jsonReplica = new JSONObject();

            jsonReplica.put("id", dataReplica.getId());
            jsonReplica.put("data_node", dataReplica.getDataNode());
            jsonReplica.put("index_node", dataReplica.getIndexNode());

            JSONObject dataReplicaServices = new JSONObject();
            for (Map.Entry<Service, String> element : dataReplica.getServices()
                    .entrySet()) {

                dataReplicaServices.put(element.getKey().name(),
                        element.getValue());
            }
            jsonReplica.put("services", dataReplicaServices);

            jsonDatasetReplicas.put(jsonReplica);
        }
        jsonDataset.put("replicas", jsonDatasetReplicas);
        // ------------------------------------------------------------

        // Files-------------------------------------------------------
        JSONArray jsonFiles = new JSONArray();
        for (DatasetFile file : files) {
            jsonFiles.put(file.toJSON());
        }
        jsonDataset.put("files", jsonFiles);
        // ------------------------------------------------------------

        return jsonDataset;
    }

    /**
     * 
     * @param file
     *            the directory where dataset will be export in json file
     */
    public void exportToJSON(File file) {
        JSONObject jsonDataset = toJSON();

        FileWriter fichero = null;
        PrintWriter pw = null;
        try {

            fichero = new FileWriter(file.getAbsolutePath() + "/"
                    + jsonDataset.get("dataset_instance_id") + ".txt");
            pw = new PrintWriter(fichero);
            pw.println(jsonDataset.toString(4));

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                // close io.file
                if (null != fichero) {
                    fichero.close();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }

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

    public void shallowCopy(Dataset dataset) {
        setInstanceID(dataset.getInstanceID());
        setRecordMetadata(dataset.getRecordMetadata());
        setReplicas(dataset.getReplicas());
        this.files = dataset.getFiles();
    }

    // public static void main(String args[]) {
    // System.out.println("Probando expresion regular...");
    //
    // System.out.println("Creo Dataset...");
    // // dataset 1
    // Dataset dataset1 = new Dataset("project1.model2.atributo3.etc.v1");
    // dataset1.addMetadata(Metadata.PROJECT, "CMIP5");
    // dataset1.addMetadata(Metadata.DATA_NODE, "la rata");
    // dataset1.addMetadata(Metadata.EXPERIMENT, "lols1");
    // dataset1.addMetadata(Metadata.SIZE, new Long("1024"));
    //
    // DatasetFile file1 = new DatasetFile(
    // "file1.project1.model2.atributo3.etc.v1.nc",
    // "project1.model2.atributo3.etc.v1");
    // file1.addMetadata(Metadata.PROJECT, "CMIP5");
    // file1.addMetadata(Metadata.DATA_NODE, "la rata");
    // file1.addMetadata(Metadata.EXPERIMENT, "lols1");
    // file1.addMetadata(Metadata.SIZE, new Long("512"));
    //
    // DatasetFile file2 = new DatasetFile(
    // "file2.project1.model2.atributo3.etc.v1.nc_4",
    // "project1.model2.atributo3.etc.v1");
    // file2.addMetadata(Metadata.PROJECT, "CMIP5");
    // file2.addMetadata(Metadata.DATA_NODE, "la rata");
    // file2.addMetadata(Metadata.EXPERIMENT, "lols1");
    // file2.addMetadata(Metadata.SIZE, new Long("512"));
    //
    // Set<DatasetFile> files1 = new HashSet<DatasetFile>();
    // files1.add(file1);
    // files1.add(file2);
    //
    // dataset1.setFiles(files1);
    //
    // System.out
    // .println("Probando getFileForInstanceID of file1.project1.model2.atributo3.etc.v1.nc_3...");
    // DatasetFile fileTest1 = dataset1
    // .getFileWithInstanceId("file1.project1.model2.atributo3.etc.v1.nc_3");
    //
    // System.out
    // .println("Probando getFileForInstanceID of file2.project1.model2.atributo3.etc.v1.nc");
    // DatasetFile fileTest2 = dataset1
    // .getFileWithInstanceId("file2.project1.model2.atributo3.etc.v1.nc");
    //
    // System.out
    // .println("Probando getFileForInstanceID file2.project1.nc_0.model2.atributo3.etc.v1.nc");
    // DatasetFile fileTest3 = dataset1
    // .getFileWithInstanceId("file2.project1.nc_0.model2.atributo3.etc.v1.nc");
    // }
}
