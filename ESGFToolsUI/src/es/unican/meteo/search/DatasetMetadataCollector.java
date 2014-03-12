/**
 * 
 */
package es.unican.meteo.search;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import es.unican.meteo.esgf.petition.HTTPStatusCodeException;
import es.unican.meteo.esgf.petition.RequestManager;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;

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
        this.dataset = new Dataset(instanceID);
        this.searchResponse = searchResponse;
        this.cache = cache;
        this.alive = true;
        this.datasetFileInstanceIDMap = datasetFileInstanceIDMap;

        logger.trace("[OUT] DatasetMetadataCollector");
    }

    @Override
    public void run() {
        logger.trace("[IN]  run");
        boolean valid = false;

        // while the harvesting of metadata isn't valid and thread is alive
        while (!valid && isAlive()) {
            try {

                // Instance id allows identify all dataset replicas
                String instanceID = dataset.getInstanceID();
                if (instanceID == null) {
                    logger.error("Dataset {} not have instance id",
                            dataset.getInstanceID());

                    searchResponse.datasetHarvestingAborted(instanceID);
                    return;
                }

                logger.debug(
                        "Starting harvesting dataset metadata of dataset {}",
                        instanceID);

                /* read some data, check cache first, otherwise read from ESGF */
                logger.debug("Searching dataset {} in cache", instanceID);

                boolean found = false;
                synchronized (cache) {
                    if (cache.isKeyInCache(dataset.getInstanceID())) {
                        if (cache.get(dataset.getInstanceID()) != null) {
                            dataset = (Dataset) cache.get(
                                    dataset.getInstanceID()).getObjectValue();

                            logger.debug("Dataset {} found in cache",
                                    instanceID);

                            found = true;
                        }
                    }
                }

                if (found) {
                    logger.debug("Getting intance_id's of files that must be download.");

                    getInstanceIdOfFilesToDownload();

                    if (datasetFileInstanceIDMap.get(dataset.getInstanceID()) == null
                            & !isAlive()) {
                        return;
                    }

                    if (isAlive()) {
                        valid = true;
                        searchResponse.datasetHarvestingCompleted(dataset
                                .getInstanceID());
                    } else {
                        return;
                    }
                }

                if (!valid & isAlive()) {

                    logger.debug("Dataset {} isn't in cache", instanceID);
                    // Aux record
                    Set<Record> auxRecord = null;

                    // Create new restful search object with a random index node
                    List<String> nodes = RequestManager.getESGFNodes();
                    String randIndexNode = nodes
                            .get((int) (Math.random() * nodes.size()));
                    RESTfulSearch search = new RESTfulSearch(randIndexNode);

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

                    try {
                        // get datasets replicas like records because will be
                        // processed later

                        if (!isAlive()) {
                            searchResponse.datasetHarvestingAborted(dataset
                                    .getInstanceID());
                            return;
                        }

                        auxRecord = RequestManager.getRecordsFromSearch(search);

                        // auxrecord can't be 0. This is an error in index node
                        if (auxRecord.size() == 0) {
                            logger.warn(
                                    "Error in index node {} getting replicas of this dataset: {}",
                                    search.getIndexNode(),
                                    dataset.getInstanceID());
                            throw new IOException();
                        }
                    } catch (IOException e) {
                        logger.warn(
                                "Exeption in index node and search {} g : {}",
                                search.generateServiceURL(), e.getStackTrace());

                        // If the predefined index node fails, try do a search
                        // in all nodes
                        auxRecord = searchRecordsInAllESGFIndexNode(search);

                        // if searchRecordsInAllESGFIndexNode return null
                        // because isn't alive thread
                        if (!isAlive()) {
                            searchResponse.datasetHarvestingAborted(dataset
                                    .getInstanceID());
                            return;
                        }

                        // If can't access dataset replicas in any ESGF index
                        // nodes
                        if (auxRecord == null) {
                            logger.error(
                                    "Can not be access the replicas in any node of {} dataset",
                                    instanceID);
                            searchResponse.datasetHarvestingAborted(dataset
                                    .getInstanceID());
                            return;// Finish thread
                        }
                    } catch (HTTPStatusCodeException e) { // do the same that
                                                          // IOException
                        logger.warn(
                                "Exeption in index node and search {} g : {}",
                                search.generateServiceURL(), e.getStackTrace());

                        // If the predefined index node fails, try do a search
                        // in all nodes
                        auxRecord = searchRecordsInAllESGFIndexNode(search);

                        // if searchRecordsInAllESGFIndexNode return null
                        // because isn't alive thread
                        if (!isAlive()) {
                            searchResponse.datasetHarvestingAborted(dataset
                                    .getInstanceID());
                            return;
                        }

                        // If can't access dataset repicas in any ESGF index
                        // nodes
                        if (auxRecord == null) {
                            logger.error(
                                    "Can not be access the replicas in any node of {} dataset",
                                    instanceID);
                            searchResponse.datasetHarvestingAborted(dataset
                                    .getInstanceID());
                            return;// Finish thread
                        }
                    }

                    // Add all dataset replicas in Dataset
                    logger.debug("Creating dataset replicas");
                    for (Record record : auxRecord) {

                        logger.debug("Adding dataset replica in Dataset");
                        addDatasetReplica(record); // this method process the
                                                   // record

                    }

                    if (dataset.getReplicas().size() > 0) {
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
                        // Set search to local search
                        search.getParameters().setDistrib(false);

                        logger.debug("Searching files of dataset replicas");
                        int i = 0;
                        List<RecordReplica> datasetReplicas = dataset
                                .getReplicas();
                        RecordReplica dataReplica = new RecordReplica();

                        while (i < datasetReplicas.size() && isAlive()) {
                            try {

                                dataReplica = datasetReplicas.get(i);

                                logger.debug(
                                        "Configuring search for search files of {} replica",
                                        dataReplica.getId());
                                // Set search to files of this dataset replica
                                // and do a local search
                                search.getParameters().setDistrib(false);
                                search.setIndexNode(dataReplica.getIndexNode());
                                search.getParameters().setDatasetId(
                                        dataReplica.getId());

                                if (!isAlive()) {
                                    searchResponse
                                            .datasetHarvestingAborted(dataset
                                                    .getInstanceID());
                                    return;
                                }
                                // get files like records because will be
                                // processed later
                                auxRecord = RequestManager
                                        .getRecordsFromSearch(search);
                            } catch (MalformedURLException e) {
                                logger.warn(
                                        "Error MalformedURLException in {} :{}",
                                        dataReplica.getIndexNode(),
                                        e.getStackTrace());

                            } catch (IOException e) {
                                logger.warn("Exeption in index node{} :{}",
                                        dataReplica.getIndexNode(),
                                        e.getStackTrace());

                                logger.warn(
                                        "Exeption in index node and search {} g : {}",
                                        search.generateServiceURL(),
                                        e.getStackTrace());

                                if (!isAlive()) {
                                    searchResponse
                                            .datasetHarvestingAborted(dataset
                                                    .getInstanceID());
                                    return;
                                }
                                // If the predefined index node fails, try do a
                                // global
                                // search
                                auxRecord = searchRecordsInAllESGFIndexNode(search);

                                // if searchRecordsInAllESGFIndexNode return
                                // null because isn't alive thread
                                if (!isAlive()) {
                                    searchResponse
                                            .datasetHarvestingAborted(dataset
                                                    .getInstanceID());
                                    return;
                                }

                                // If can't access files
                                if (auxRecord == null) {
                                    auxRecord = new HashSet<Record>();
                                }

                            } catch (HTTPStatusCodeException e) {
                                logger.warn("Exeption in index node{} :{}",
                                        dataReplica.getIndexNode(),
                                        e.getStackTrace());

                                logger.warn(
                                        "Exeption in index node and search {} g : {}",
                                        search.generateServiceURL(),
                                        e.getStackTrace());

                                // If the predefined index node fails, try do a
                                // global
                                // search
                                auxRecord = searchRecordsInAllESGFIndexNode(search);

                                // if searchRecordsInAllESGFIndexNode return
                                // null
                                // because isn't alive thread
                                if (!isAlive()) {
                                    searchResponse
                                            .datasetHarvestingAborted(dataset
                                                    .getInstanceID());
                                    return;
                                }

                                // If can't access files
                                if (auxRecord == null) {
                                    auxRecord = new HashSet<Record>();
                                }

                            }

                            // Add all files and replica files
                            logger.debug(
                                    "Creating files and file replicas of {}",
                                    dataReplica.getId());
                            for (Record record : auxRecord) {

                                // Add new file replica into a file with same
                                // instance_id. And add file if isn't exist a
                                // file
                                // with
                                // this record instance_id
                                addFileAndFileReplica(record);

                            }

                            i++;
                        }

                        // If no errors, then is valid
                        if (i == datasetReplicas.size()) {

                            logger.debug("Getting intance_id's of files that must be download.");
                            getInstanceIdOfFilesToDownload();

                            if (datasetFileInstanceIDMap.get(dataset
                                    .getInstanceID()) == null & !isAlive()) {
                                searchResponse.datasetHarvestingAborted(dataset
                                        .getInstanceID());
                                return;
                            }

                            // Remove all metadata that belongs to replicas and
                            // not to record
                            removeMetadataOfReplicas(dataset);
                            for (DatasetFile file : dataset.getFiles()) {
                                removeMetadataOfReplicas(file);
                            }

                            if (datasetFileInstanceIDMap.get(dataset
                                    .getInstanceID()) == null & !isAlive()) {
                                searchResponse.datasetHarvestingAborted(dataset
                                        .getInstanceID());
                                return;
                            }

                            if (isAlive()) {
                                // put new dataset in cache
                                synchronized (cache) {
                                    cache.put(new Element(dataset
                                            .getInstanceID(), dataset));
                                }

                                valid = true;

                                searchResponse
                                        .datasetHarvestingCompleted(dataset
                                                .getInstanceID());
                            } else {
                                return;
                            }

                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Exeption in harvesting of {} : \n {}",
                        dataset.getInstanceID(), e.getStackTrace());
            }
        }

        // If !valid in the end of task
        if (!valid) {
            searchResponse.datasetHarvestingAborted(dataset.getInstanceID());
        }

        logger.trace("[OUT] run");
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

        for (RecordReplica replica : dataset.getReplicas()) {

            // Get all instance_id of files that satisfy the
            // constraints of search
            String id = replica.getId();
            Set<String> instanceIds;
            try {
                if (!isAlive()) {
                    return;
                }
                instanceIds = RequestManager.getInstanceIDOfFilesToDownload(
                        searchResponse.getSearch(), id);
                if (!isAlive()) {
                    return;
                }
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

            // Add this set of instance_id of files in map
            if (value == null) { // create a new set
                value = instanceIds;
                datasetFileInstanceIDMap.put(dataset.getInstanceID(), value);
            } else {// add in previous set
                value.addAll(instanceIds);
            }
        }

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
        DatasetFile file = checkFileReplica(record);

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
     * Add {@link RecordReplica} in {@link Dataset}
     * 
     * @param record
     *            record of type Dataset
     */
    private void addDatasetReplica(Record record) {
        logger.trace("[IN]  addDatasetReplica");

        logger.debug("Checking replica for search new Dataset metadata");
        checkDatasetReplica(record);

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
     * Check if record that is a {@link RecordReplica} have new Dataset
     * metadata. In this case add metadata in {@link Dataset}
     * 
     * @param record
     *            record of type Dataset
     */
    private void checkDatasetReplica(Record record) {
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
    private DatasetFile checkFileReplica(Record record) {
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

    // TODO This method already must be not necessary because ESGFRequestManaegr
    // implement it but must be test it before remove it
    /**
     * Do a search in each ESGF index nodes. Return first success request in
     * some indexNode.
     * 
     * @param search
     *            the search in {@link RESTfulSearch}
     * @return records returned by index node or null in case that are a fail in
     *         all ESGF nodes
     */
    private Set<Record> searchRecordsInAllESGFIndexNode(RESTfulSearch search) {
        logger.trace("[IN]  searchRecordsInAllESGFIndexNode");

        Set<Record> records = null;

        // ESGF index nodes
        List<String> nodes;
        try {
            if (!isAlive()) {
                return null;
            }
            nodes = RequestManager.getESGFNodes();
            if (!isAlive()) {
                return null;
            }
        } catch (Exception e2) {
            logger.error(
                    "Error in configuration file (nodes) for the search: {}",
                    search.generateServiceURL());
            return null;
        }

        RESTfulSearch newSearch;
        try {
            newSearch = (RESTfulSearch) search.clone();
        } catch (CloneNotSupportedException e1) {
            logger.error("Error in search clone. {}",
                    search.generateServiceURL());
            return null;
        }
        newSearch.getParameters().setDistrib(true);

        int indexNode = 0;
        boolean cont = true;

        logger.debug("Searching for all replicas for this dataset");
        // Search all dataset replicas
        // If an exception is thrown try to in other ESGF node
        while (cont && indexNode < nodes.size()) {
            try {
                newSearch.setIndexNode(nodes.get(indexNode));

                if (!isAlive()) {
                    return null;
                }

                records = RequestManager.getRecordsFromSearch(newSearch);

                if (!isAlive()) {
                    return null;
                }

                cont = false;

            } catch (IOException e) {
                logger.warn("The index node: {} throws this exception: {}",
                        nodes.get(indexNode), e.getStackTrace());

                // if there is an error try another index node
                indexNode++;

                // if all ESGF nodes fail
                if (indexNode == nodes.size()) {
                    // XXX cambiarlo?
                    logger.error("Petition : {} thrown an Exception in all ESGF index nodes");

                    return null;
                }
            } catch (HTTPStatusCodeException e) {
                logger.warn("The index node: {} throws this exception: {}",
                        nodes.get(indexNode), e.getStackTrace());

                // if there is an error try another index node
                indexNode++;

                // if all ESGF nodes fail
                if (indexNode == nodes.size()) {
                    // XXX cambiarlo?
                    logger.error("Petition : {} thrown an Exception in all ESGF index nodes");
                    return null;
                }
            }
        }

        logger.trace("[OUT] searchRecordsInAllESGFIndexNode");

        return records;
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
     * @return the dataset
     */
    public Dataset getDataset() {
        logger.trace("[IN]  getDataset");
        logger.trace("[OUT] getDataset");
        return dataset;
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

    public synchronized void terminate() {
        logger.trace("[IN]  terminate");
        setAlive(false);
        logger.trace("[OUT] terminate");
    }
}
