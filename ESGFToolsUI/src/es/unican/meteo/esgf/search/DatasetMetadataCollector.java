/**
 * 
 */
package es.unican.meteo.esgf.search;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import es.unican.meteo.esgf.petition.HTTPStatusCodeException;
import es.unican.meteo.esgf.petition.RequestManager;

/**
 * 
 * Dataset metadata collector. Collects all dataset metadata of a
 * {@link Dataset} and therefore, their {@link RecordReplica},
 * {@link DatasetFile}. Also, implements {@link Runnable} to download in
 * separated thread.
 * 
 * @author Karem Terry
 * 
 */
public class DatasetMetadataCollector implements Runnable {

    /** Logger. */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(DatasetMetadataCollector.class);

    /**
     * Instance_id of dataset
     */
    private String instanceID;

    /**
     * Dataset whose information will be harvested. Metadata, files, replicas,
     * etc
     */
    private Dataset dataset;

    /**
     * Search response
     */
    private SearchResponse searchResponse;

    /** EH cache. */
    private Cache cache;

    /**
     * boolean of run loop
     */
    private boolean alive;

    /** previous harvest status of dataset. */
    private DatasetHarvestStatus previousHarvestStatus;

    /** indicate if dataset is locked for this collector */
    private boolean datasetLocked;

    /**
     * Map of dataset - array of fileInstanceID. Shows the files that must be
     * downloaded for each dataset
     */
    Map<String, Set<String>> datasetFileInstanceIDMap;

    public DatasetMetadataCollector() {
    };

    /**
     * Constructor
     * 
     * @param instanceID
     *            of Dataset whose information will be harvested.
     * @param cache
     * @param searchResponse
     * @param datasetFileInstanceIDMap
     */
    public DatasetMetadataCollector(String instanceID, Cache cache,
            SearchResponse searchResponse,
            Map<String, Set<String>> datasetFileInstanceIDMap) {

        super();
        logger.trace("[IN]  DatasetMetadataCollector");
        this.instanceID = instanceID;
        this.searchResponse = searchResponse;
        this.cache = cache;
        this.alive = true;
        this.datasetFileInstanceIDMap = datasetFileInstanceIDMap;

        logger.trace("[OUT] DatasetMetadataCollector");
    }

    @Override
    public void run() {
        logger.trace("[IN]  run");

        try {
            if (isAlive()) {
                searchResponse
                        .putHarvestStatusOfDatasetToHarvesting(instanceID);
            } else {
                return; // end thread
            }

            // If a dataset has been harvested for another node do nothing
            boolean cont = true;
            while (isAlive() && cont) {

                // if collector has been paused or reset
                if (!isAlive()) {
                    return; // end thread
                }
                // if dataset already completed do nothing
                if (searchResponse.getHarvestStatus(instanceID) == HarvestStatus.COMPLETED) {
                    return;
                }

                // If dataset is locked for another search then wait
                if (SearchManager.isDatasetLocked(instanceID)) {
                    java.lang.Thread.sleep(3000);

                    if (!SearchManager.isDatasetLocked(instanceID)) {
                        cont = false;
                        // lock dataset
                        SearchManager.lockDataset(instanceID);
                        datasetLocked = true;
                    }

                } else {
                    cont = false;
                    // lock dataset
                    SearchManager.lockDataset(instanceID);
                    datasetLocked = true;
                }
            }
        } catch (InterruptedException e) {
            releaseDataset();
            // if happens something wrong in lock/release dataset
            logger.error(
                    "Happen something wrong (InterruptedException) in lock/release dataset {} in search {}",
                    instanceID, searchResponse.getName());
            searchResponse.putHarvestStatusOfDatasetToFailed(instanceID);
            return; // end thread
        } catch (IllegalArgumentException e) {

            releaseDataset();
            // if happens something wrong in lock/release dataset
            logger.error(
                    "Happen something wrong in lock/release dataset {} in search {}",
                    instanceID, searchResponse.getName());
            searchResponse.putHarvestStatusOfDatasetToFailed(instanceID);
            return; // end thread
        }

        // if collector has been paused or reset
        if (!isAlive()) {
            releaseDataset();
            return; // end thread
        }

        // XXX
        // Obtain dataset
        logger.debug("Checking current state of dataset {} in file system.. ",
                instanceID);
        boolean found = false;
        synchronized (cache) {
            if (cache.isKeyInCache(instanceID)) {
                if (cache.get(instanceID) != null) {
                    dataset = (Dataset) cache.get(instanceID).getObjectValue();

                    logger.debug("Dataset {} found in file system", instanceID);
                    found = true;
                }
            }
        }

        // If dataset are in cache
        if (found) {

            // check if harvesting is already completed
            boolean completed = false;
            // if full harvesting
            if (searchResponse.getHarvestType() == SearchHarvestType.COMPLETE) {
                if (dataset.getHarvestStatus() == DatasetHarvestStatus.HARVESTED) {
                    completed = true;
                }
            } else { // if PARTIAL (dataset partial or full harvested) is
                     // completed harvest
                if (dataset.getHarvestStatus() == DatasetHarvestStatus.HARVESTED
                        || dataset.getHarvestStatus() == DatasetHarvestStatus.PARTIAL_HARVESTED) {
                    completed = true;
                }
            }

            // if harvesting of dataset is completed then harvest
            // instance_id of files that satisfy the constraints
            if (completed) {

                // release dataset
                SearchManager.releaseDataset(instanceID);
                datasetLocked = false;

                logger.debug("Getting intance_id's of files that must be"
                        + " download.");
                try {
                    getInstanceIdOfFilesToDownload();

                } catch (IOException e) {
                    logger.error(
                            "Error harvesting file instanceIDs of dataset {} with search {}",
                            instanceID, searchResponse.getSearch()
                                    .generateServiceURL());
                    searchResponse
                            .putHarvestStatusOfDatasetToFailed(instanceID);
                    return; // end thread
                } catch (HTTPStatusCodeException e) {
                    logger.error(
                            "Error harvesting file instanceIDs of dataset {} with search {}",
                            instanceID, searchResponse.getSearch()
                                    .generateServiceURL());
                    searchResponse
                            .putHarvestStatusOfDatasetToFailed(instanceID);
                    return; // end thread
                }

                if (!isAlive()) {
                    return; // end thread
                } else {
                    if (datasetFileInstanceIDMap.get(dataset.getInstanceID()) == null) {
                        logger.error(
                                "Error harvesting file instanceIDs of dataset {} with search {}",
                                instanceID, searchResponse.getSearch()
                                        .generateServiceURL());
                        searchResponse
                                .putHarvestStatusOfDatasetToFailed(instanceID);
                        return; // end thread
                    }
                    // if dataset is completed and file instances id are
                    // complete too
                    searchResponse
                            .putHarvestStatusOfDatasetToCompleted(instanceID);
                    return; // end thread
                }
            }
        }

        // if collector has been paused or reset
        if (!isAlive()) {
            releaseDataset();
            return; // end thread
        }

        // if not found in file system do new dataset
        if (!found) {
            dataset = new Dataset(instanceID);
        }

        // XXX replicas
        // Harvesting replicas

        // if is a new dataset
        if (dataset.getHarvestStatus() == DatasetHarvestStatus.EMPTY) {
            Set<Record> records;
            try {
                records = getReplicaRecords();
            } catch (IOException e) {
                releaseDataset();
                searchResponse.putHarvestStatusOfDatasetToFailed(instanceID);
                return; // end thread

            } catch (HTTPStatusCodeException e) {

                releaseDataset();
                // do the same
                // that IOException
                searchResponse.putHarvestStatusOfDatasetToFailed(instanceID);
                return; // end thread
            }

            // if collector has been paused or reset
            if (!isAlive()) {
                releaseDataset();
                return; // end thread
            }

            // Add all dataset replicas in Dataset
            logger.debug("Creating dataset replicas");
            for (Record record : records) {

                logger.debug("Adding dataset replica in Dataset");
                // this method processes the records as replicas
                addDatasetReplica(record);

            }
        } else if (dataset.getHarvestStatus() == DatasetHarvestStatus.PARTIAL_HARVESTED) {
            if (searchResponse.getHarvestType() == SearchHarvestType.COMPLETE) {
                // if replicas of a dataset partial harvested is 0
                // this is an error in harvesting
                if (dataset.getReplicas().size() == 0) {

                    // /TODO RESET?
                    releaseDataset();
                    logger.error("Dataset partial harvest {} haven't replicas",
                            instanceID);
                    searchResponse
                            .putHarvestStatusOfDatasetToFailed(instanceID);
                    return; // end thread
                }

                Set<Record> records;
                try {
                    records = getReplicaRecords();
                } catch (IOException e) {

                    releaseDataset();
                    searchResponse
                            .putHarvestStatusOfDatasetToFailed(instanceID);
                    return; // end thread

                } catch (HTTPStatusCodeException e) {

                    releaseDataset();
                    searchResponse
                            .putHarvestStatusOfDatasetToFailed(instanceID);
                    return; // end thread
                }

                // if collector has been paused or reset
                if (!isAlive()) {
                    releaseDataset();
                    return; // end thread
                }

                // Get metadata of records
                for (Record record : records) {
                    getDatasetMetadataOfRecords(record);
                }
            } else {
                // if dataset partial-harvested reaches this point in a PARTIAL
                // search harvest then it is an error

                releaseDataset();
                logger.error(
                        "An attempt was made to partial harvest a dataset {} "
                                + "already partial harvested", instanceID);
                searchResponse.putHarvestStatusOfDatasetToFailed(instanceID);
                return; // end thread
            }

        } else {

            releaseDataset();
            // if a dataset harvested reaches this point of method
            // it is an error
            logger.error(
                    "An attempt was made to harvest a dataset {} already harvested",
                    instanceID);
            searchResponse.putHarvestStatusOfDatasetToFailed(instanceID);
            return; // end thread
        }

        // if collector has been paused or reset
        if (!isAlive()) {
            releaseDataset();
            return; // end thread
        }

        // XXX files
        // Harvesting files

        // if is a new dataset
        if (dataset.getHarvestStatus() == DatasetHarvestStatus.EMPTY) {
            Set<Record> fileRecords = new HashSet<Record>();

            // get files for each replica
            for (RecordReplica replica : dataset.getReplicas()) {
                // try to get files of replica
                try {
                    fileRecords = getFileRecords(replica.getId(),
                            replica.getIndexNode());

                    for (Record record : fileRecords) {

                        // Add new file replica into a file with same
                        // instance_id. And add file if isn't exist a
                        // file with this record instance_id
                        addFileAndFileReplica(record);

                        // if collector has been paused or reset
                        if (!isAlive()) {
                            releaseDataset();
                            return; // end thread
                        }
                    }

                } catch (IOException e) {
                    releaseDataset();
                    searchResponse
                            .putHarvestStatusOfDatasetToFailed(instanceID);

                    return; // end thread

                } catch (HTTPStatusCodeException e) {
                    releaseDataset();
                    searchResponse
                            .putHarvestStatusOfDatasetToFailed(instanceID);
                    return; // end thread
                }
            }

        } else if (dataset.getHarvestStatus() == DatasetHarvestStatus.PARTIAL_HARVESTED) {
            Set<Record> fileRecords = new HashSet<Record>();

            // get files for each replica
            for (RecordReplica replica : dataset.getReplicas()) {
                // try to get files of replica
                try {
                    fileRecords = getFileRecords(replica.getId(),
                            replica.getIndexNode());

                    for (Record record : fileRecords) {

                        // Add new file replica into a file with same
                        // instance_id. And add file if isn't exist a
                        // file with this record instance_id
                        getFileMetadataOfRecords(record);

                        // if collector has been paused or reset
                        if (!isAlive()) {
                            releaseDataset();
                            return; // end thread
                        }
                    }
                } catch (IOException e) {
                    releaseDataset();
                    searchResponse
                            .putHarvestStatusOfDatasetToFailed(instanceID);
                    return; // end thread

                } catch (HTTPStatusCodeException e) {
                    releaseDataset();
                    searchResponse
                            .putHarvestStatusOfDatasetToFailed(instanceID);
                    return; // end thread
                }
            }
        }
        // if collector has been paused or reset
        if (!isAlive()) {
            releaseDataset();
            return; // end thread
        }

        // XXX aggregations
        // Harvesting aggregations

        // XXX remove unnecessary metadata
        // Remove all metadata that belongs to replicas and
        // not to record
        if (searchResponse.getHarvestType() == SearchHarvestType.COMPLETE) {
            removeMetadataOfReplicas(dataset);
            for (DatasetFile file : dataset.getFiles()) {
                removeMetadataOfReplicas(file);
            }
        }

        // if collector has been paused or reset
        if (!isAlive()) {
            releaseDataset();
            return; // end thread
        }

        // XXX dataset finished
        // Set new harvest status
        if (searchResponse.getHarvestType() == SearchHarvestType.PARTIAL) {
            dataset.setHarvestStatus(DatasetHarvestStatus.PARTIAL_HARVESTED);
        } else {
            dataset.setHarvestStatus(DatasetHarvestStatus.HARVESTED);
        }

        // Put new dataset in cache
        synchronized (cache) {
            cache.put(new Element(instanceID, dataset));
        }
        releaseDataset();

        // if collector has been paused or reset
        if (!isAlive()) {
            releaseDataset();
            return; // end thread
        }

        // XXX Instance_ids of files
        // Harvesting Instance_ids of files

        logger.debug("Getting intance_id's of files that must be download.");
        try {
            getInstanceIdOfFilesToDownload();
        } catch (IOException e) {

            releaseDataset();
            searchResponse.putHarvestStatusOfDatasetToFailed(instanceID);
            return; // end thread
        } catch (HTTPStatusCodeException e) {

            releaseDataset();
            searchResponse.putHarvestStatusOfDatasetToFailed(instanceID);
            return; // end thread
        }

        // if collector has been paused or reset
        if (!isAlive()) {
            releaseDataset();
            return; // end thread
        }

        if (datasetFileInstanceIDMap.get(dataset.getInstanceID()) == null) {
            logger.error(
                    "Error (null value) harvesting instance_ids of files of dataset {}"
                            + "with search {}", instanceID, searchResponse
                            .getSearch().generateServiceURL());
            releaseDataset();
            searchResponse.putHarvestStatusOfDatasetToFailed(instanceID);
            return; // end thread
        }

        if (isAlive()) {
            // harvest finished
            searchResponse.putHarvestStatusOfDatasetToCompleted(instanceID);
            logger.trace("[OUT] run");
            return; // end thread
        } else {
            releaseDataset();
            logger.trace("[OUT] run");
            return; // end thread
        }

    }

    /**
     * Do a search in all dataset replicas because the metadata info isn't the
     * same in all replicas. That's why some files couldn't be returned by the
     * request. This is the same reason why must be save the instance_id of file
     * and not the id. Because all replicas of this file are valid.
     * 
     * @throws IOException
     * @throws HTTPStatusCodeException
     */
    private void getInstanceIdOfFilesToDownload() throws IOException,
            HTTPStatusCodeException {

        if (dataset.getReplicas() == null || dataset.getReplicas().size() == 0) {
            logger.error("Error trying to get file instance_id of dataset {}"
                    + "Number of replicas = 0 ", instanceID);
            throw new IOException();
        }

        for (RecordReplica replica : dataset.getReplicas()) {

            // Get all instance_id of files that satisfy the
            // constraints of search
            String id = replica.getId();
            Set<String> instanceIds;
            try {
                instanceIds = RequestManager.getInstanceIDOfFilesToDownload(
                        searchResponse.getSearch(), id);
            } catch (IOException e) {
                logger.error(
                        "Error trying to get file instance_id of replica {}.",
                        id);
                throw e;
            } catch (HTTPStatusCodeException e) {
                logger.error(
                        "Error trying to get file instance_id of replica {}.",
                        id);
                throw e;
            }

            Set<String> value = datasetFileInstanceIDMap.get(dataset
                    .getInstanceID());

            if (value != null) {
                for (String instanceID : instanceIds) {
                    value.add(standardizeESGFFileInstanceID(instanceID));
                }
            } else {
                value = new HashSet<String>();
                for (String instanceID : instanceIds) {
                    value.add(standardizeESGFFileInstanceID(instanceID));
                }
                datasetFileInstanceIDMap.put(dataset.getInstanceID(), value);
            }
        }

    }

    /**
     * Get replica records of Dataset
     * 
     * @return a set of records that are replicas of a dataset
     * @throws IOException
     * @throws HTTPStatusCodeException
     */
    private Set<Record> getReplicaRecords() throws IOException,
            HTTPStatusCodeException {
        // Create new restful search object with current index
        // node
        Set<Record> records = null;
        // List<String> nodes = RequestManager.getESGFNodes();
        // String randIndexNode = nodes.get((int) (Math.random() * nodes
        // .size()));
        RESTfulSearch search = new RESTfulSearch(
                SearchManager.getCurrentIndexNode());

        logger.debug("Configuring restful search for search dataset replicas...");
        // Set format
        search.getParameters().setFormat(Format.JSON);
        // Set type to Dataset
        search.getParameters().setType(RecordType.DATASET);
        // set distrib
        search.getParameters().setDistrib(true);

        logger.debug("Searching all dataset replicas...");
        // For find all replicas of a dataset set parameter
        // instance_id.
        List<String> instanceId = new LinkedList<String>();
        instanceId.add(dataset.getInstanceID());
        search.getParameters().setInstanceId(instanceId);

        // Configuring fields
        if (searchResponse.getHarvestType() == SearchHarvestType.PARTIAL) {
            Set<Metadata> fields = new HashSet<Metadata>();
            fields.add(Metadata.ID);
            fields.add(Metadata.INSTANCE_ID);
            fields.add(Metadata.INDEX_NODE);
            fields.add(Metadata.DATA_NODE);
            fields.add(Metadata.REPLICA);
            fields.add(Metadata.URL);
            search.getParameters().setFields(fields);
        }

        try {
            // get datasets replicas like records. Retry in all nodes if
            // return zero records
            records = RequestManager.getRecordsFromSearch(search, true);
        } catch (IOException e) {
            logger.error(
                    "Can not be access the replicas in any node of {} dataset from request {}",
                    instanceID, search);
            throw e;

        } catch (HTTPStatusCodeException e) { // do the same
            // that
            // IOException
            // XXX mentira ver RequestManager
            // getNumOfRecordsOfSearch
            logger.error(
                    "Can not be access the replicas in any node of {} dataset from request {}",
                    instanceID, search);
            throw e;
        }

        // auxrecord can't be 0
        if (records.size() == 0) {
            releaseDataset();
            logger.error(
                    "Error in getting replicas of this dataset: {} in ESGF",
                    dataset.getInstanceID());
            throw new IOException(
                    "Can not be access the replicas in any node of "
                            + instanceID + " dataset from request "
                            + search.generateServiceURL()
                            + ". Number of replicas must not be 0");
        }

        return records;
    }

    /**
     * Add new {@link DatasetFileReplica} into a {@link DatasetFile} with same
     * instance_id. And add {@link DatasetFile} if isn't exist a
     * {@link DatasetFile} with this record instance_id.
     * 
     * @param record
     *            record of type File
     */
    private void addFileAndFileReplica(Record record) {
        logger.trace("[IN]  addFileAndFileReplica");

        logger.debug("Checking if exist a file with record instance_id and checking for search new file metadata");
        DatasetFile file = getFileMetadataOfRecords(record);

        logger.debug("Adding new replica file in File");
        String id = record.getMetadata(Metadata.ID);
        String dataNode = record.getMetadata(Metadata.DATA_NODE);
        String indexNode = record.getMetadata(Metadata.INDEX_NODE);
        boolean master = false;

        // check this because getMetadata() can return null
        if (record.contains(Metadata.REPLICA)) {
            boolean replica = record.getMetadata(Metadata.REPLICA);
            master = !replica;
        }
        // XXX revisar si bien

        Map<Service, String> services = new HashMap<Service, String>();
        List<String> urls = record.getMetadata(Metadata.URL);

        // XXX Maybe change in the future (if ESGF changes)
        // Search access services for url because some dataset not have ACCESS
        // metadata
        for (String url : urls) {
            String serviceUrl = url.substring(url.lastIndexOf("|") + 1);

            if (Service.valueOf(serviceUrl.toUpperCase()) != null) {
                services.put(Service.valueOf(serviceUrl.toUpperCase()), url);
            }
        }

        // Add file replica
        RecordReplica datasetFileReplica = new RecordReplica(id, dataNode,
                indexNode, master, services);
        file.getReplicas().add(datasetFileReplica);

        logger.debug("Searching if exists new File services");
        // Search new File services
        searchNewFileServices(datasetFileReplica, file);

        logger.trace("[OUT] addFileAndFileReplica");
    }

    /**
     * Search if exist new service for a file in replica for file services
     * 
     * @param replica
     */
    private void searchNewFileServices(RecordReplica datasetFileReplica,
            DatasetFile file) {
        logger.trace("[IN]  searchNewFileServices");
        for (Map.Entry<Service, String> entry : datasetFileReplica
                .getServices().entrySet()) {

            Service service = entry.getKey();

            if (file.hasService(service)) {
                logger.debug("Adding new replica {} in service {}",
                        datasetFileReplica.getId(), service);
                file.getServicesInReplicas().get(service)
                        .add(datasetFileReplica);
            } else {
                logger.debug("Adding new record service {} find in replica {}",
                        service, datasetFileReplica);
                List<RecordReplica> newList = new LinkedList<RecordReplica>();
                newList.add(datasetFileReplica);
                file.getServicesInReplicas().put(service, newList);
            }
        }
        logger.trace("[OUT] searchNewFileServices");
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
     * Search if exist new service in replica for dataset services
     * 
     * @param replica
     */
    private void searchNewDatasetServices(RecordReplica datasetReplica) {
        logger.trace("[IN]  searchNewDatasetServices");

        for (Map.Entry<Service, String> entry : datasetReplica.getServices()
                .entrySet()) {

            Service service = entry.getKey();

            if (dataset.hasService(service)) {
                logger.debug("Adding new replica {} in service {}",
                        datasetReplica.getId(), service);
                dataset.getServicesInReplicas().get(service)
                        .add(datasetReplica);
            } else {
                logger.debug("Adding new record service {} find in replica {}",
                        service, datasetReplica);
                List<RecordReplica> newList = new LinkedList<RecordReplica>();
                newList.add(datasetReplica);
                dataset.getServicesInReplicas().put(service, newList);
            }
        }

        logger.trace("[OUT] searchNewDatasetServices");
    }

    /**
     * Add {@link RecordReplica} in {@link Dataset}. Processes the records as
     * replicas
     * 
     * @param record
     *            record of type Dataset
     */
    private void addDatasetReplica(Record record) {
        logger.trace("[IN]  addDatasetReplica");

        logger.debug("Checking replica for search new Dataset metadata");
        getDatasetMetadataOfRecords(record);

        logger.debug("Adding new replica in Dataset");
        String id = record.getMetadata(Metadata.ID);
        String dataNode = record.getMetadata(Metadata.DATA_NODE);
        String indexNode = record.getMetadata(Metadata.INDEX_NODE);
        boolean master = false;
        // check this because getMetadata() can return null
        if (record.contains(Metadata.REPLICA)) {
            boolean replica = record.getMetadata(Metadata.REPLICA);
            master = !replica;
        }

        Map<Service, String> services = new HashMap<Service, String>();
        List<String> urls = record.getMetadata(Metadata.URL);

        // if url is null then record don't offers services
        if (urls == null) {
            logger.warn("Record {} hasn't services", id);
        } else {

            // XXX Maybe change in the future (if ESGF wants)
            // Search access services for url because some dataset not have
            // ACCESS
            // metadata
            for (String url : urls) {
                String serviceUrl = url.substring(url.lastIndexOf("|") + 1);
                if (Service.valueOf(serviceUrl.toUpperCase()) != null) {
                    services.put(Service.valueOf(serviceUrl.toUpperCase()), url);
                }
            }
        }

        // Add dataset replica
        RecordReplica datasetReplica = new RecordReplica(id, dataNode,
                indexNode, master, services);
        dataset.getReplicas().add(datasetReplica);

        logger.debug("Searching if exists new Dataset services in the new replica");
        // Search new Dataset services
        searchNewDatasetServices(datasetReplica);

        logger.trace("[OUT] addDatasetReplica");
    }

    /**
     * Get file records of a dataset replica in ESGF
     * 
     * @param id
     *            of dataset replica in ESGF
     * @param indexNode
     *            where dataset replica are located
     * 
     * @return a set of records that are files of a dataset replica
     * @throws IOException
     * @throws HTTPStatusCodeException
     */
    private Set<Record> getFileRecords(String id, String indexNode)
            throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  getRecordFiles");

        RESTfulSearch search = new RESTfulSearch(indexNode);

        try {
            // Search files of each Dataset replica
            logger.debug("Configuring search for search files");

            // Set index node
            // Remove all parameters because must be download all
            // files of dataset in repository.
            search.setParameters(new Parameters());

            // format json
            search.getParameters().setFormat(Format.JSON);
            // Set type to Dataset
            search.getParameters().setType(RecordType.FILE);

            logger.debug("Configuring search for search files of {} replica",
                    id);

            // Set search to files of this dataset replica
            // and do a local search
            search.getParameters().setDistrib(false);
            search.setIndexNode(indexNode);
            search.getParameters().setDatasetId(id);

            // Configuring fields
            // if (searchResponse.getHarvestType() == SearchHarvestType.PARTIAL)
            // {
            Set<Metadata> fields = new HashSet<Metadata>();
            fields.add(Metadata.ID);
            fields.add(Metadata.INSTANCE_ID);
            fields.add(Metadata.INDEX_NODE);
            fields.add(Metadata.DATA_NODE);
            fields.add(Metadata.CHECKSUM_TYPE);
            fields.add(Metadata.CHECKSUM);
            fields.add(Metadata.REPLICA);
            fields.add(Metadata.URL);
            fields.add(Metadata.SIZE);
            search.getParameters().setFields(fields);
            // }

            // get files like records because will be
            // processed later
            Set<Record> records = RequestManager.getRecordsFromSearch(search,
                    false);

            logger.trace("[OUT] getRecordFiles");
            return records;
        } catch (IOException e) {
            logger.error(
                    "Can not be access the files in any node of {} dataset from request {}",
                    id, search);
            logger.trace("[OUT] getRecordFiles");
            throw e;

        } catch (HTTPStatusCodeException e) {
            logger.error(
                    "Can not be access the files in any node of {} dataset from request {}",
                    id, search);
            logger.trace("[OUT] getRecordFiles");
            throw e;
        }
    }

    /**
     * Check if record that is a {@link RecordReplica} have new Dataset
     * metadata. In this case add metadata in {@link Dataset}
     * 
     * @param record
     *            record of type Dataset
     */
    private void getDatasetMetadataOfRecords(Record record) {
        logger.trace("[IN]  checkIfHaveNewDatasetMetadata");

        logger.debug("Checking if exists new Dataset metadata");
        // If exists some metadata in this record that Dataset hasn't
        for (Metadata metadata : record.getRecordMetadata().keySet()) {
            if (!dataset.contains(metadata)) {
                // Add new metadata
                logger.debug("Adding new Dataset metadata");
                dataset.addMetadata(metadata, record.getMetadata(metadata));
            }
        }

        logger.debug("Searching if exists new Dataset services");

        logger.trace("[OUT] checkIfHaveNewDatasetMetadata");

    }

    /**
     * Check if record that is {@link DatasetFileReplica} have new Dataset
     * metadata. In case that already exists add metadata in {@link DatasetFile}
     * with same instance_id and otherwise add new file
     * 
     * @param record
     *            record of type Dataset
     * 
     * @return {@link DatasetFile} of this {@link DatasetFileReplica}
     */
    private DatasetFile getFileMetadataOfRecords(Record record) {
        logger.trace("[IN]  checkFileReplica");

        logger.debug("Checking if file is already exists");
        // Get file of this file replica if exists
        DatasetFile file = dataset.getFileWithInstanceId((String) record
                .getMetadata(Metadata.INSTANCE_ID));

        // If file isn't exit
        if (file == null) {
            logger.debug("Adding new file");

            // Ceate new file with metadata of record
            file = new DatasetFile(
                    (String) record.getMetadata(Metadata.INSTANCE_ID),
                    dataset.getInstanceID());
            file.setRecordMetadata(record.getRecordMetadata());

            // add new file
            dataset.getFiles().add(file);
        } else { // file exist

            logger.debug("Checking if exists new file metadata");
            // If exists some metadata in this record that file hasn't
            for (Metadata metadata : record.getRecordMetadata().keySet()) {
                if (!file.contains(metadata)) {
                    // Add new metadata
                    logger.debug("Adding new file metadata");
                    dataset.addMetadata(metadata, record.getMetadata(metadata));
                }
            }

        }

        logger.trace("[OUT] checkFileReplica");
        return file;
    }

    /**
     * Remove all metadata in metadata attribute that belongs to record replica.
     * (data node, index node, replica, id, url , access, dataset_id)
     */
    private void removeMetadataOfReplicas(Record record) {
        logger.trace("[IN]  removeMetadataOfReplicas");

        if (record.contains(Metadata.DATA_NODE)) {
            record.getRecordMetadata().put(Metadata.DATA_NODE, null);
        }

        if (record.contains(Metadata.INDEX_NODE)) {
            record.getRecordMetadata().put(Metadata.INDEX_NODE, null);
        }

        if (record.contains(Metadata.REPLICA)) {
            record.getRecordMetadata().put(Metadata.REPLICA, null);
        }

        if (record.contains(Metadata.ID)) {
            record.getRecordMetadata().put(Metadata.ID, null);
        }

        if (record.contains(Metadata.URL)) {
            record.getRecordMetadata().put(Metadata.URL, null);
        }

        if (record.contains(Metadata.ACCESS)) {
            record.getRecordMetadata().put(Metadata.ACCESS, null);
        }

        if (record.contains(Metadata.DATASET_ID)) {
            record.getRecordMetadata().put(Metadata.DATASET_ID, null);
        }

        logger.trace("[OUT] removeMetadataOfReplicas");

    }

    /**
     * @param dataset
     *            the dataset to set
     */
    public void setDataset(Dataset dataset) {
        logger.trace("[IN]  setDataset");
        this.dataset = dataset;
        logger.trace("[OUT] setDataset");
    }

    /**
     * @return the searchResponse
     */
    public SearchResponse getSearchResponse() {
        logger.trace("[IN]  getSearchResponse");
        logger.trace("[OUT] getSearchResponse");
        return searchResponse;
    }

    /**
     * @param searchResponse
     *            the searchResponse to set
     */
    public void setSearchResponse(SearchResponse searchResponse) {
        logger.trace("[IN]  setSearchResponse");
        this.searchResponse = searchResponse;
        logger.trace("[OUT] setSearchResponse");
    }

    /**
     * Check if is alive
     * 
     * @return true if is alive
     */
    public synchronized boolean isAlive() {
        logger.trace("[IN]  isAlive");
        logger.trace("[OUT] isAlive");
        return alive;
    }

    /**
     * Set of boolean alive
     * 
     * @param alive
     */
    public synchronized void setAlive(boolean alive) {
        logger.trace("[IN]  setAlive");
        this.alive = alive;
        logger.trace("[OUT] setAlive");
    }

    private void releaseDataset() {
        // release dataset
        if (datasetLocked) {
            SearchManager.releaseDataset(instanceID);
            datasetLocked = false;
        } else {
            logger.warn("Trying realese dataset {} doesn't locked", instanceID);
        }
    }

    public synchronized void terminate() {
        logger.trace("[IN]  terminate");
        setAlive(false);
        logger.trace("[OUT] terminate");
    }

    /**
     * Get instance id of dataset that the collector is harvesting
     * 
     * @return Instance_id of dataset
     */
    public String getInstanceID() {
        logger.trace("[IN]  getInstanceID");
        logger.trace("[OUT] getInstanceID");
        return instanceID;
    }
}
