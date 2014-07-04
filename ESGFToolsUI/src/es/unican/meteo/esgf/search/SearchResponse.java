package es.unican.meteo.esgf.search;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.Writer;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import es.unican.meteo.esgf.download.Download;
import es.unican.meteo.esgf.download.DownloadManager;
import es.unican.meteo.esgf.download.DownloadObserver;
import es.unican.meteo.esgf.petition.HTTPStatusCodeException;
import es.unican.meteo.esgf.petition.RequestManager;

/**
 * <p>
 * Class that represents the response of a search.
 * </p>
 * 
 * <p>
 * Contains metadata for datasets that meet the constraints of a search. Also
 * their corresponding files.
 * 
 * For each record will get the record replicas of such records, ie both master
 * records as the record aftershocks. Absolutely all replicas will be of the
 * latest version of the data.
 * </p>
 * 
 * <p>
 * This some attributes in class is saved in preferences, but can't be saved the
 * attributes: Cache and Executor because they aren't java beans. Thats why
 * their values must be assigned when the main class is created.
 * </p>
 * 
 * @author Karem Terry
 * 
 */
public class SearchResponse implements Download, Serializable {

    /**
     * Private class runnable, that obtains first set of datasets. If exists an
     * error, the object responseDatasets is null
     * 
     * @author Karem Terry
     */
    private class HarvestingInitiator extends Thread {

        public HarvestingInitiator() {
        }

        @Override
        public void run() {
            logger.trace("[IN]  run");
            try {

                // Save previous harvest status and put it to HARVESTING
                HarvestStatus previousHarvestStatus = getHarvestStatus();
                setHarvestStatus(HarvestStatus.HARVESTING);

                logger.debug("Getting instance_id of datasets that satisfy the"
                        + " constraints from", search.generateServiceURL());
                Set<String> datasetInstanceIDs = RequestManager
                        .getDatasetInstanceIDsFromSearch(search);

                logger.debug("Checking if search response isn't paused");
                if (getHarvestStatus() == HarvestStatus.PAUSED) {
                    logger.debug("Search response {} has been paused",
                            getName());
                    return; // if search is paused then finish thread
                }

                if (datasetInstanceIDs.size() == 0) {
                    throw new IOException(
                            "Instance ids of datasets of first prequest of a harvesting failed"
                                    + search.generateServiceURL());
                }

                if (previousHarvestStatus == HarvestStatus.CREATED) {
                    // Put all datasets to harvest
                    for (String instanceID : datasetInstanceIDs) {

                        addDatasetToHarvest(instanceID);

                        logger.debug(
                                "Adding new dataset {} collector in thread pool",
                                instanceID);
                        DatasetMetadataCollector collector = new DatasetMetadataCollector(
                                instanceID, SearchResponse.this,
                                datasetFileInstanceIDMap);
                        collectors.add(collector);

                        // add runnable to collectors executor
                        collectorsExecutor.execute(collector);
                    }

                } else { // if previous harvest status is failed or paused
                    // Find datasets not recolected or with fail harvesting
                    // status and putting them to harvest

                    for (String instanceID : datasetInstanceIDs) {

                        boolean needDatasetCollector = true;

                        // Avoid two insertions for same dataset
                        synchronized (datasetHarvestingStatus) {

                            boolean found = contains(instanceID);
                            // if search response contains dataset
                            if (found) {
                                // check if harvest that are related to specific
                                // dataset and the search is complete
                                HarvestStatus harvestStatus = getHarvestStatus(instanceID);
                                if (harvestStatus == HarvestStatus.COMPLETED
                                        || harvestStatus == HarvestStatus.FAILED) {
                                    needDatasetCollector = false;
                                }
                            } else { // if not found
                                addDatasetToHarvest(instanceID);
                            }

                            // if new dataset, failed dataset, or paused
                            // dataset then need a dataset collector
                            if (needDatasetCollector) {

                                logger.debug(
                                        "Adding new dataset {} collector in thread pool",
                                        instanceID);
                                DatasetMetadataCollector collector = new DatasetMetadataCollector(
                                        instanceID, SearchResponse.this,
                                        datasetFileInstanceIDMap);
                                collectors.add(collector);

                                // add runnable to collectors executor
                                collectorsExecutor.execute(collector);
                            }
                        }

                    }
                }

                setDatasetTotalCount(datasetInstanceIDs.size());
            } catch (IOException e) {
                logger.error(
                        "Error IOException in search response with name: {} \n {}",
                        getName(), e.getStackTrace());
                setHarvestStatus(HarvestStatus.FAILED);
            } catch (HTTPStatusCodeException e) {
                logger.error(
                        "Error IOException in search response with name: {} \n {}",
                        getName(), e.getStackTrace());
                setHarvestStatus(HarvestStatus.FAILED);
            }

            logger.trace("[OUT] run");
        }
    }

    /** Logger. */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(SearchResponse.class);

    /** List of dataset metadata collector. */
    private transient List<DatasetMetadataCollector> collectors;

    /** Executor that schedules and executes metadata collectors in threads. */
    private transient ExecutorService collectorsExecutor;

    /**
     * State that indicates the state of search response. CREATING, HARVESTING,
     * PAUSED, COMPLETED and FAILED
     */
    private HarvestStatus harvestStatus;

    /**
     * Type of harvesting of search response. PARTIAL (for basic harvesting to
     * allow download process) and COMPLETE (for full harvesting)
     */
    private SearchHarvestType harvestType;

    /** Number of {@link Dataset} completed. **/
    private int processedDatasets;

    /**
     * Map of dataset - array of fileInstanceID. Shows the files that must be
     * downloaded for each dataset
     */
    private Map<String, Set<String>> datasetFileInstanceIDMap;

    /**
     * Map[instanceID, {@link HarvestStatus}] where instanceID is the identifier
     * of dataset and the {@link HarvestStatus} is the harvesting status that
     * are related to specific dataset and the search
     */
    private Map<String, HarvestStatus> datasetHarvestingStatus;

    /** Number of {@link Dataset} that matches with the search. **/
    private int datasetTotalCount;

    /** Harvesting finish date. */
    private Date harvestingFinish;

    /** Harvesting start date. */
    private Date harvestingStart;

    /** Name of search response. */
    private String name;

    /** List of search response observers. */
    private transient List<DownloadObserver> observers;

    /** Dataset access class. */
    private transient DatasetAccessClass dataAccessClass;

    /**
     * Boolean that indicates if exists some error in the search at dataset
     * level.
     */
    private boolean existsErrors;

    /**
     * Current {@link RESTfulSearch}, that is the ESGF search petition that
     * generates the response.
     **/
    private RESTfulSearch search;

    /**
     * Empty constructor. Allows use preferences and ehcache
     */
    public SearchResponse() {
        this.observers = new LinkedList<DownloadObserver>();
        this.datasetHarvestingStatus = Collections
                .synchronizedMap(new HashMap<String, HarvestStatus>());
        this.collectors = new LinkedList<DatasetMetadataCollector>();
        this.dataAccessClass = DatasetAccessClass.getInstance();
    }

    /**
     * Constructor
     * 
     * @param name
     *            name name of search
     * @param search
     *            the ESGF search petition that generates the response
     * @param executorService
     *            Executor that schedules and executes metadata collectors in
     *            threads
     */
    public SearchResponse(String name, RESTfulSearch search,
            ExecutorService executorService) {
        logger.trace("[IN]  SearchResponse");

        this.search = search;
        this.name = name;
        this.datasetTotalCount = 0;
        this.processedDatasets = 0;
        this.datasetHarvestingStatus = Collections
                .synchronizedMap(new HashMap<String, HarvestStatus>());
        this.harvestingStart = null;
        this.harvestingFinish = null;
        this.observers = new LinkedList<DownloadObserver>();
        this.collectorsExecutor = executorService;
        this.collectors = new LinkedList<DatasetMetadataCollector>();
        this.datasetFileInstanceIDMap = new HashMap<String, Set<String>>();
        this.existsErrors = false;
        this.harvestStatus = HarvestStatus.CREATED;
        this.dataAccessClass = DatasetAccessClass.getInstance();

        logger.trace("[OUT] SearchResponse");
    }

    /**
     * Add to search response a new dataset with created state that will be
     * harvested. This is used by HarvestingIniciator Thread.
     * 
     * @param dataInstanceID
     *            instance_id of dataset
     */
    public synchronized void addDatasetToHarvest(String dataInstanceID) {
        logger.trace("[IN]  addDatasetToHarvestingStatus");

        logger.debug("Adding to harvestig status of search{} the dataset {}",
                getName(), dataInstanceID);
        datasetHarvestingStatus.put(dataInstanceID, HarvestStatus.CREATED);

        logger.trace("[OUT] addDatasetToHarvestingStatus");
    }

    /**
     * Check datasets of search in system files. If Datasets can't be restored
     * of local system its harvesting info will be reset
     * 
     */
    public synchronized void checkDatasets() throws IOException {
        logger.trace("[IN]  checkDatasets");

        // For each dataset
        for (String instanceID : datasetHarvestingStatus.keySet()) {

            logger.debug("Checking if dataset {} is in file system...",
                    instanceID);

            if (datasetHarvestingStatus.get(instanceID) == HarvestStatus.COMPLETED) {
                // if dataset harvested isn't in cache

                if (dataAccessClass.getDataset(instanceID) == null) {
                    logger.warn(
                            "Dataset harvested {} can't reload from file system",
                            instanceID);
                    logger.info(
                            "Dataset harvested {} can't reload from file system."
                                    + "Reseting harvesting info of dataset",
                            instanceID);
                    datasetHarvestingStatus.put(instanceID,
                            HarvestStatus.CREATED);
                }
            }
        }

        logger.trace("[OUT] checkDatasets");
    }

    /**
     * Check if search response contains a {@link Dataset}
     * 
     * @param instanceID
     *            of dataset
     * @return true if search response contains a dataset
     */
    public boolean contains(String instanceID) {
        return datasetHarvestingStatus.containsKey(instanceID);
    }

    public synchronized void decrementProcessedDataset() {
        this.processedDatasets = this.processedDatasets - 1;
    }

    /**
     * Start the harvesting. It's called for startCompleteHarvesting() or
     * startpartialHarvesting(), not recommended for use from outside the class.
     * 
     */
    @Override
    public void download() {
        logger.trace("[IN]  download");

        logger.debug("Iniciating a {} harvesting", harvestType);
        harvestingStart = new Date();

        // Start thread that inits all metadata collectors. One by dataset
        HarvestingInitiator harvestingInitiator = new HarvestingInitiator();
        harvestingInitiator.start();

        logger.trace("[OUT] download");

    }

    /**
     * Get current approximate time to finish the download in milliseconds.
     * 
     * @return current approximate time to finish the download or 0 if download
     *         is completed
     */
    @Override
    public long getApproximateTimeToFinish() {
        logger.trace("[IN]  getApproximateTimeToFinish");

        // if download is finished. return 0
        if (isCompleted()) {
            logger.trace("[OUT] getApproximateTimeToFinish");
            return 0;
        }

        // Get actual date
        Date actualDate = new Date();

        // Calculate difference in milliseconds
        // getdate() returns the number of milliseconds since January 1, 1970
        long diffTime = actualDate.getTime() - harvestingStart.getTime();

        long millis = 0;
        // Calculate approximate time to download finish
        int datasetCurrentCount = getProcessedDatasets();
        if (datasetCurrentCount > 0) {
            millis = (datasetTotalCount * diffTime) / datasetCurrentCount;
        }

        logger.trace("[OUT] getApproximateTimeToFinish");
        return millis;
    }

    /**
     * Get current progress of the download.
     */
    @Override
    public int getCurrentProgress() {
        logger.trace("[IN]  getCurrentProgress");
        int percent;
        int datasetCurrentCount = getProcessedDatasets();
        if (datasetTotalCount > 0) {

            percent = (datasetCurrentCount * 100) / datasetTotalCount;
        } else {
            percent = 0;
        }

        logger.trace("[OUT] getCurrentProgress");
        return percent;
    }

    /**
     * Get a {@link Dataset}
     * 
     * @param instanceID
     *            instance_id of dataset
     * @return dataset
     * 
     * @throws IllegalArgumentException
     *             if dataset don't belongs to {@link SearchResponse}
     * @throws IOException
     *             if some error happens when dataset has been obtained from
     *             file system
     */
    public Dataset getDataset(String instanceID)
            throws IllegalArgumentException, IOException {
        logger.trace("[IN]  getHarvestedDataset");

        logger.debug("Checking if dataset {} belongs to the search response",
                instanceID);
        if (!datasetHarvestingStatus.containsKey(instanceID)) {
            logger.error("dataset {} don't belongs to search response {}",
                    instanceID, getName());
            throw new IllegalArgumentException();
        }

        Dataset dataset = null;

        logger.debug("Getting dataset {} from file system", instanceID);
        try {
            dataset = dataAccessClass.getDataset(instanceID);
        } catch (Exception e) {
            logger.error(
                    "Error happens when dataset {} has been obtained from file system in search {}",
                    instanceID, getName());
            throw new IOException(
                    "Error happens when data has been obtained from file system"
                            + e.getMessage());
        }

        logger.trace("[OUT] getHarvestedDataset");

        return dataset;
    }

    /**
     * Get dataset harvesting status of search response
     * 
     * @return Map[instanceID, {@link HarvestStatus}] where instanceID is the
     *         identifier of dataset and the {@link HarvestStatus} is the
     *         harvesting status that are related to specific dataset and the
     *         search
     */
    public Map<String, HarvestStatus> getDatasetHarvestingStatus() {
        logger.trace("[IN]  getDatasetHarvestingStatus");
        logger.trace("[OUT] getDatasetHarvestingStatus");
        return datasetHarvestingStatus;
    }

    /**
     * Get number of datasets of search
     * 
     * @return the datasetTotalCount
     */
    public synchronized int getDatasetTotalCount() {
        logger.trace("[IN]  getDatasetTotalCount");
        logger.trace("[OUT] getDatasetTotalCount");
        return datasetTotalCount;
    }

    /**
     * Return instance_id of files that satisfy the constraints
     * 
     * @param datasetInstanceID
     */
    public Set<String> getFilesToDownload(String datasetInstanceID) {
        return datasetFileInstanceIDMap.get(datasetInstanceID);
    }

    /**
     * Get the finish date of harvesting
     * 
     * @return the downloadFinish
     */
    public Date getHarvestingFinish() {
        logger.trace("[IN]  getHarvestingFinish");
        logger.trace("[OUT] getHarvestingFinish");
        return harvestingFinish;
    }

    /**
     * Get the start date of harvesting
     * 
     * @return the downloadStart
     */
    public Date getHarvestingStart() {
        logger.trace("[IN]  getHarvestingStart()");
        logger.trace("[OUT] getHarvestingStart()");
        return harvestingStart;
    }

    /**
     * Get status {@link HarvestStatus} that indicates the state of search
     * response.
     * 
     * @return the harvesting status of a search
     *         <ul>
     *         <li>CREATING</li>
     *         <li>HARVESTING</li>
     *         <li>PAUSED</li>
     *         <li>COMPLETED</li>
     *         <li>FAILED</li>
     *         </ul>
     */
    public synchronized HarvestStatus getHarvestStatus() {
        return harvestStatus;
    }

    /**
     * Get the {@link HarvestStatus} that are related to specific dataset and
     * the search
     * 
     * @param instanceID
     *            of dataset
     * @return Get the harvest status of dataset
     */
    public HarvestStatus getHarvestStatus(String instanceID) {
        logger.trace("[IN]  getDatasetHarvestStatus");
        logger.trace("[OUT] getDatasetHarvestStatus");
        return datasetHarvestingStatus.get(instanceID);
    }

    /**
     * Get type of harvesting configurated in search response.
     * 
     * @return the type of harvesting of search response
     *         <ul>
     *         <li>PARTIAL (for basic harvesting to allow download process)</li>
     *         <li>COMPLETE (for full harvesting)</li>
     *         </ul>
     */
    public synchronized SearchHarvestType getHarvestType() {
        return harvestType;
    }

    /**
     * Get a map of dataset - array of fileInstanceID. Shows the files that must
     * be downloaded for each dataset.
     * 
     * KeyMap is a {@link Dataset} object and Value is an array of
     * {@link String} with the instance_id of file that must be downloaded.
     * 
     * @return the datasetFileInstanceIDMap
     */
    public Map<String, Set<String>> getMapDatasetFileInstanceID() {
        return datasetFileInstanceIDMap;
    }

    /**
     * Return name of actual search response
     * 
     * @return name of actual search response
     */
    public String getName() {
        logger.trace("[IN]  getName");
        logger.trace("[OUT] getName");
        return name;
    }

    /**
     * Get number of Dataset completed.
     * 
     * @return the processedDatasets
     */
    public int getProcessedDatasets() {
        return processedDatasets;
    }

    /**
     * Get {@link RESTfulSearch}, that is the ESGF search petition that
     * generates the response.
     * 
     * @return the search
     */
    public RESTfulSearch getSearch() {
        logger.trace("[IN]  getSearch");
        logger.trace("[OUT] getSearch");
        return search;
    }

    public synchronized void incrementProcessedDataset() {
        this.processedDatasets = this.processedDatasets + 1;
    }

    /**
     * Check the state of harvesting of search response.
     * 
     * @return boolean that indicates if this search response is completed or
     *         not.
     */
    public boolean isCompleted() {
        logger.trace("[IN]  isCompleted");
        boolean completed = false;

        if (getHarvestStatus() == HarvestStatus.COMPLETED) {
            completed = true;
        }

        logger.trace("[OUT] isCompleted");
        return completed;
    }

    /**
     * Check if there are an active dataset harvesting
     * 
     * @return true if there are an active dataset harvesting
     */
    public boolean isHarvestingActive() {
        logger.trace("[IN]  isHarvestingActive");
        boolean active = false;
        if (getHarvestStatus() == HarvestStatus.HARVESTING) {
            active = true;
        }

        logger.trace("[OUT] isHarvestingActive");
        return active;
    }

    /**
     * Call method onDownloadCompleted() in all observers
     */
    private void notifyDownloadCompletedObservers() {
        logger.trace("[IN]  notifyDownloadCompletedObservers");
        for (DownloadObserver o : observers) {
            o.onDownloadCompleted(this);
            logger.debug("Download completed notified to {} observer",
                    o.getClass());
        }
        logger.trace("[OUT] notifyDownloadCompletedObservers");
    }

    /**
     * Call method onError() in all observers
     */
    private void notifyDownloadErrorObservers() {
        logger.trace("[IN]  notifyDownloadErrorObservers");
        for (DownloadObserver o : observers) {
            o.onError(this);
            logger.debug("Download error notified to {} observer", o.getClass());
        }
        logger.trace("[OUT] notifyDownloadErrorObservers");
    }

    /**
     * Call method onDownloadProgress() in all observers
     */
    private void notifyDownloadProgressObservers() {
        logger.trace("[IN]  notifyDownloadProgressObservers");
        for (DownloadObserver o : observers) {
            o.onDownloadProgress(this);
            logger.debug("Download progress notified to {} observer",
                    o.getClass());
        }
        logger.trace("[OUT] notifyDownloadProgressObservers");
    }

    @Override
    public void pause() {
        logger.trace("[IN]  pause");

        if (getHarvestStatus() != HarvestStatus.COMPLETED) {
            setHarvestStatus(HarvestStatus.PAUSED);

            // terminate all active threads and remove all collectors
            logger.debug("Pausing all collectors in {}", getName());
            synchronized (collectors) {
                Iterator collectorIter = collectors.iterator();

                while (collectorIter.hasNext()) {
                    DatasetMetadataCollector collector = (DatasetMetadataCollector) collectorIter
                            .next();
                    collector.terminate();
                    collectorIter.remove();
                }
            }

            for (Map.Entry<String, HarvestStatus> entry : datasetHarvestingStatus
                    .entrySet()) {
                synchronized (datasetHarvestingStatus) {
                    String instanceID = entry.getKey();
                    if (getHarvestStatus(instanceID) == HarvestStatus.HARVESTING) {
                        datasetHarvestingStatus.put(instanceID,
                                HarvestStatus.CREATED);
                    }
                }
            }
        }

        logger.trace("[OUT] pause");
    }

    /**
     * Put the harvesting status that are related to specific dataset and the
     * search to COMPLETED
     * 
     * @param instanceID
     */
    public synchronized void putHarvestStatusOfDatasetToCompleted(
            String instanceID) {
        logger.trace("[IN]  putHarvestStatusOfDatasetToCompleted");

        if (getHarvestStatus(instanceID) != HarvestStatus.COMPLETED) {
            logger.debug("Setting new completed dataset: {}", instanceID);
            datasetHarvestingStatus.put(instanceID, HarvestStatus.COMPLETED);
            incrementProcessedDataset();

            logger.debug("Notify observers");
            notifyDownloadProgressObservers();

            if (processedDatasets == datasetTotalCount) {
                logger.debug("Metadata harvesting of {} has finished",
                        getName());
                setHarvestStatus(HarvestStatus.COMPLETED);
                setHarvestingFinish(new Date());
                checkIfExistsAFailedDataset();
                notifyDownloadCompletedObservers();
                this.collectors = new LinkedList<DatasetMetadataCollector>();
            }
        }

        logger.trace("[OUT] putHarvestStatusOfDatasetToCompleted");
    }

    /**
     * Check if exists some error in the search at dataset level. And save it in
     * a boolean attribute. Always called at end of search
     */
    private void checkIfExistsAFailedDataset() {
        int i = 0;
        boolean cont = true;

        Iterator it = datasetHarvestingStatus.entrySet().iterator();
        while (it.hasNext() && cont) {
            Map.Entry e = (Map.Entry) it.next();
            if (e.getValue() == HarvestStatus.FAILED) {
                cont = false;
            }
        }

        // if some dataset is failed
        if (cont == false) {
            this.existsErrors = true;
        } else {
            this.existsErrors = false;
        }

    }

    /**
     * Put the harvesting status that are related to specific dataset and the
     * search to FAILED
     * 
     * @param instanceID
     */
    public synchronized void putHarvestStatusOfDatasetToFailed(String instanceID) {
        logger.trace("[IN]  putHarvestStatusOfDatasetToFailed");

        if (datasetHarvestingStatus.containsKey(instanceID)) {
            datasetHarvestingStatus.put(instanceID, HarvestStatus.FAILED);
            incrementProcessedDataset();
        } else {
            logger.warn("Collector has tried to put to failed a dataset {} "
                    + "that doesn't belong to search {}", instanceID, getName());
        }

        logger.debug("Notify observers");
        notifyDownloadProgressObservers();

        if (processedDatasets == datasetTotalCount) {
            logger.debug("Metadata harvesting of {} has finished", getName());
            setHarvestStatus(HarvestStatus.COMPLETED);
            setHarvestingFinish(new Date());
            checkIfExistsAFailedDataset();
            notifyDownloadCompletedObservers();
            this.collectors = new LinkedList<DatasetMetadataCollector>();
        }

        logger.trace("[OUT] putHarvestStatusOfDatasetToFailed");
    }

    /**
     * Put the harvesting status that are related to specific dataset and the
     * search to HARVESTING
     * 
     * @param instanceID
     */
    public synchronized void putHarvestStatusOfDatasetToHarvesting(
            String instanceID) {
        logger.trace("[IN]  putHarvestStatusOfDatasetToHarveting");

        if (datasetHarvestingStatus.containsKey(instanceID)) {
            datasetHarvestingStatus.put(instanceID, HarvestStatus.HARVESTING);
        } else {
            logger.warn(
                    "Collector has tried to put to harvesting a dataset {} "
                            + "that doesn't belong to search {}", instanceID,
                    getName());
        }

        logger.trace("[OUT] putHarvestStatusOfDatasetToHarveting");
    }

    /**
     * Register new object that implement observer for this dataset download
     * status.
     * 
     * @param observer
     */
    public void registerObserver(DownloadObserver observer) {
        logger.trace("[IN]  registerObserver");

        logger.debug("Register new observer: {}", observer.getClass());
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
        logger.trace("[OUT] registerObserver");
    }

    @Override
    public synchronized void reset() {
        logger.trace("[IN]  reset");

        setHarvestStatus(HarvestStatus.CREATED);

        // remove from file system
        for (String instanceID : datasetHarvestingStatus.keySet()) {
            try {
                // only remove if dataset isn't in another search response
                // or dataset is being download
                if (!datasetIsUsedByAnother(instanceID)) {
                    dataAccessClass.removeDataset(instanceID);
                }
            } catch (IOException e) {
                logger.error("Dataset can't be removed from file system");
            }
        }

        logger.debug("Reseting all threads and metadata in cache and disc");
        // terminate all threads
        for (DatasetMetadataCollector collector : collectors) {
            collector.terminate();
        }

        logger.debug("Reseting all attributes");
        // reset all attributes
        this.collectors = new LinkedList<DatasetMetadataCollector>();
        this.datasetTotalCount = 0;
        this.processedDatasets = 0;

        this.datasetHarvestingStatus = Collections
                .synchronizedMap(new HashMap<String, HarvestStatus>());
        this.harvestStatus = HarvestStatus.CREATED;
        this.datasetFileInstanceIDMap = new HashMap<String, Set<String>>();
        this.datasetHarvestingStatus = new HashMap<String, HarvestStatus>();

        this.harvestingStart = null;
        this.harvestingFinish = null;
        this.observers = new LinkedList<DownloadObserver>();
        this.existsErrors = false;

        logger.trace("[OUT] reset");
    }

    /**
     * Private method that return true if dataset is in another search response
     * or is being download
     * 
     * @param instanceID
     * @return
     */
    private boolean datasetIsUsedByAnother(String instanceID) {

        int number = SearchManager.getInstance().getNumberOfSearchOfDataset(
                instanceID);
        if (number > 1) {
            return true;
        } else {

            boolean queued = DownloadManager.getInstance().isDatasetQueued(
                    instanceID);
            if (queued) {
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Reset harvest of a dataset
     * 
     * @param instanceID
     *            instance_id of dataset
     * 
     * @throws IllegalArgumentException
     *             if a dataset doesn't belongg to search
     */
    public synchronized void resetDataset(String instanceID)
            throws IllegalArgumentException {
        logger.trace("[IN]  resetDataset");

        if (!datasetHarvestingStatus.containsKey(instanceID)) {
            logger.error("dataset {} doesn't belongg to search", instanceID);
            throw new IllegalArgumentException();
        }

        if (getHarvestStatus(instanceID) == HarvestStatus.FAILED) {

            if (getHarvestStatus() != HarvestStatus.HARVESTING) {
                setHarvestStatus(HarvestStatus.HARVESTING);
            }

            this.existsErrors = false;

            decrementProcessedDataset();
            datasetHarvestingStatus.put(instanceID, HarvestStatus.CREATED);

            try {
                dataAccessClass.removeDataset(instanceID);
            } catch (IOException e) {
                logger.error("Dataset {} can't be removed from system",
                        instanceID);
            }

            for (DatasetMetadataCollector collector : collectors) {
                if (collector.getInstanceID().equals(instanceID)) {
                    collector.terminate();
                }
            }

            DatasetMetadataCollector collector = new DatasetMetadataCollector(
                    instanceID, SearchResponse.this, datasetFileInstanceIDMap);
            collectors.add(collector);

            collectorsExecutor.execute(collector);

            notifyDownloadProgressObservers();
        }

        logger.trace("[OUT] resetDataset");

    }

    /**
     * Set list of metadata collectors that are instance of
     * {@link DatasetMetadataCollector}
     * 
     * @param collectors
     *            the collectors to set
     */
    public synchronized void setCollectors(
            List<DatasetMetadataCollector> collectors) {
        logger.trace("[IN]  setCollectors");
        this.collectors = collectors;
        logger.trace("[OUT] setCollectors");
    }

    /**
     * Set a Map[instanceID, {@link HarvestStatus}] where instanceID is the
     * identifier of dataset and the {@link HarvestStatus} is the harvesting
     * status that are related to specific dataset and the search
     * 
     * @param datasetHarvestingStatus
     *            a map[instanceID, HarvestStatus]
     */
    public synchronized void setDatasetHarvestingStatus(
            Map<String, HarvestStatus> HarvestingStatus) {
        logger.trace("[IN]  setDatasetHarvestingStatus");
        this.datasetHarvestingStatus = HarvestingStatus;
        logger.trace("[OUT] setDatasetHarvestingStatus");
    }

    /**
     * Set number of Dataset that matches with the search.
     * 
     * @param datasetTotalCount
     *            number of Dataset that matches with the search.
     */
    public synchronized void setDatasetTotalCount(int datasetTotalCount) {
        logger.trace("[IN]  setDatasetTotalCount");
        this.datasetTotalCount = datasetTotalCount;
        logger.trace("[OUT] setDatasetTotalCount");
    }

    /**
     * Set executor that schedules and executes metadata collectors in threads.
     * 
     * @param executor
     */
    public void setExecutor(ExecutorService executor) {
        logger.trace("[IN]  setExecutor");
        this.collectorsExecutor = executor;
        logger.trace("[OUT] setExecutor");
    }

    /**
     * Set harvesting finish date.
     * 
     * @param harvestingFinish
     *            the harvesting finish date to set
     */
    public synchronized void setHarvestingFinish(Date harvestingFinish) {
        logger.trace("[IN]  setHarvestingFinish(Date)");
        this.harvestingFinish = harvestingFinish;
        logger.trace("[OUT] setHarvestingFinish(Date)");
    }

    /**
     * Set harvesting start date.
     * 
     * @param harvestingStart
     *            the harvesting start date to set
     */
    public synchronized void setHarvestingStart(Date harvestingStart) {
        logger.trace("[IN]  setHarvestingStart");
        this.harvestingStart = harvestingStart;
        logger.trace("[OUT] setHarvestingStart");
    }

    /**
     * Set status {@link HarvestStatus} that indicates the state of search
     * response:
     * <ul>
     * <li>CREATING</li>
     * <li>HARVESTING</li>
     * <li>PAUSED</li>
     * <li>COMPLETED</li>
     * <li>FAILED</li>
     * </ul>
     * 
     * @param harvestStatus
     *            the {@link HarvestStatus} to set
     */
    public synchronized void setHarvestStatus(HarvestStatus harvestStatus) {
        this.harvestStatus = harvestStatus;
        if (harvestStatus == HarvestStatus.FAILED) {
            notifyDownloadErrorObservers();
        }
    }

    /**
     * Set status {@link HarvestStatus} of a dataset
     * <ul>
     * <li>CREATING</li>
     * <li>HARVESTING</li>
     * <li>PAUSED</li>
     * <li>COMPLETED</li>
     * <li>FAILED</li>
     * </ul>
     * 
     * @param instanceID
     *            instanceID of dataset
     * @param harvestStatus
     *            the {@link HarvestStatus} to set
     */
    public synchronized void setHarvestStatus(String instanceID,
            HarvestStatus harvestStatus) {
        datasetHarvestingStatus.put(instanceID, harvestStatus);
    }

    /**
     * Set type of harvesting configurated in search response.
     * <ul>
     * <li>PARTIAL (for basic harvesting to allow download process)</li>
     * <li>COMPLETE (for full harvesting)</li>
     * </ul>
     * 
     * @param harvestType
     *            the harvestType to set
     */
    public synchronized void setHarvestType(SearchHarvestType harvestType) {
        this.harvestType = harvestType;
    }

    /**
     * Set a map of dataset - array of fileInstanceID. Shows the files that must
     * be downloaded for each dataset.
     * 
     * KeyMap is a {@link Dataset} object and Value is an array of
     * {@link String} with the instance_id of file that must be downloaded.
     * 
     * 
     * @param datasetFileInstanceIDMap
     *            the datasetFileInstanceIDMap to set
     */
    public void setMapDatasetFileInstanceID(
            Map<String, Set<String>> datasetFileInstanceIDMap) {
        this.datasetFileInstanceIDMap = datasetFileInstanceIDMap;
    }

    /**
     * Set name of actual search response
     * 
     */
    public void setName(String name) {
        logger.trace("[IN]  setName");
        this.name = name;
        logger.trace("[OUT] setName");
    }

    /**
     * Only set because don't want save observers in XML Encoder
     * 
     * @param observers
     *            the observers to set
     */
    public synchronized void setObservers(List<DownloadObserver> observers) {
        logger.trace("[IN]  setObservers");
        this.observers = observers;
        logger.trace("[OUT] setObservers");
    }

    /**
     * Set number of Dataset completed.
     * 
     * @param processedDatasets
     *            the processedDatasets to set
     */
    public void setProcessedDatasets(int processedDatasets) {
        this.processedDatasets = processedDatasets;
    }

    /**
     * Set {@link RESTfulSearch} of search response
     * 
     * @param search
     *            the search to set
     */
    public void setSearch(RESTfulSearch search) {
        logger.trace("[IN]  setSearch");
        this.search = search;
        logger.trace("[OUT] setSearch");
    }

    /**
     * Starts a complete harvesting of search. Harvest all metadata in ESGF
     * including files and [aggregations (under development)]
     * 
     * @throws IllegalStateException
     *             if search already has been harvested in a full harvest or
     *             exists a current process of harvest
     */
    public void startCompleteHarvesting() throws IllegalStateException {
        logger.trace("[IN]  startCompleteHarvesting");

        logger.debug("Check if search {} already has been harvested (full)",
                getName());
        if (harvestType == SearchHarvestType.COMPLETE
                && harvestStatus == HarvestStatus.COMPLETED) {
            logger.error(
                    "Search {} already has been harvested in a full harvest",
                    getName());
            throw new IllegalStateException("Search " + getName()
                    + " already has been harvested in a full harvest");
        }

        logger.debug("Check if exists a harvesting in process of {}", getName());
        if (harvestStatus == HarvestStatus.HARVESTING) {
            logger.error("Exists a harvesting in process of {}", getName());
            throw new IllegalStateException(
                    "Exists a harvesting in process of " + getName());
        }

        setHarvestType(SearchHarvestType.COMPLETE);
        download();

        logger.trace("[OUT] startCompleteHarvesting");
    }

    /**
     * Starts a partial harvesting of search. Only harvest basic metadata to
     * allow download process. Propertys (id, instance_id, index_node,
     * data_node, checksum, checksumType, size), replicas, file y services.
     * 
     * @throws IllegalStateException
     *             if search already has been harvested in a partial harvest or
     *             full harvest or exists a current process of harvest
     */
    public void startPartialHarvesting() throws IllegalStateException {
        logger.trace("[IN]  startPartialHarvesting");

        logger.debug(
                "Check if search {} already has been harvested (full or partial)",
                getName());
        if (harvestStatus == HarvestStatus.COMPLETED) {
            logger.error("Search {} already has been harvested in a harvest",
                    getName());
            throw new IllegalStateException(
                    "Search already has been harvested in a full harvest or in a partial harvest");
        }

        logger.debug("Check if exists a harvesting in process of {}", getName());
        if (harvestStatus == HarvestStatus.HARVESTING) {
            logger.error("Exists a harvesting in process of {}", getName());
            throw new IllegalStateException(
                    "Exists a harvesting in process of " + getName());
        }

        setHarvestType(SearchHarvestType.PARTIAL);
        download();

        logger.trace("[OUT] startPartialHarvesting");
    }

    /**
     * Check if exists some error in the search at dataset level. Only have
     * sense in search responses with a completed harvesting.
     * 
     * @return the existsErrors
     */
    public boolean hasErrorsInHarvesting() {
        return existsErrors;
    }

    /**
     * @return the existsErrors
     */
    public boolean isExistsErrors() {
        return existsErrors;
    }

    /**
     * @param existsErrors
     *            the existsErrors to set
     */
    public void setExistsErrors(boolean existsErrors) {
        this.existsErrors = existsErrors;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        logger.trace("[IN]  toString");
        logger.trace("[OUT] toString");
        return getName();
    }

    /**
     * To export in Metalink
     * 
     * @param fileName
     */
    public void exportToMetalink(String fileName) {
        if (isCompleted() == false) {
            throw new IllegalStateException("Search harvesting of" + getName()
                    + "isn't complete");
        }

        XMLOutputFactory factory = XMLOutputFactory.newInstance();

        try {

            Date date = new Date();

            // the bug in previous code:
            // XMLStreamWriter xtw = xof.createXMLStreamWriter(new
            // FileWriter(fileName));
            // http://stackoverflow.com/questions/2943605/stax-setting-the-version-and-encoding-using-xmlstreamwriter
            // the default platform encoding (which must be CP-1252, windows?).
            // Always explicitly specify encoding you are using.
            // passing OutputStream and encoding to let XMLStreamWriter do the
            // right thing)

            Writer ioWriter = new OutputStreamWriter(new FileOutputStream(
                    fileName), "UTF-8");
            XMLStreamWriter writer = factory.createXMLStreamWriter(ioWriter);
            writer.writeStartDocument("UTF-8", "1.0");

            writer.writeStartElement("metalink");
            writer.writeAttribute("version", "3.0");
            writer.writeAttribute("xmlns", "http://www.metalinker.org/");
            writer.writeAttribute("type", "static");
            writer.writeAttribute("pubdate", date.toGMTString());
            writer.writeAttribute("generator",
                    "ESGFToolsUI  -  https://meteo.unican.es/trac/wiki/ESGFToolsUI");

            writer.writeStartElement("description");
            writer.writeCharacters(search.getParameters()
                    .getConstraintParametersString());
            writer.writeEndElement();

            writer.writeStartElement("files");

            // For each dataset of search response
            for (String datasetInstanceId : datasetHarvestingStatus.keySet()) {

                // get the dataset of system
                Dataset dataset = getDataset(datasetInstanceId);

                // put to XML only the files that satisfy the constraints
                Set<String> fileInstanceIDs = datasetFileInstanceIDMap
                        .get(datasetInstanceId);

                for (DatasetFile file : dataset.getFiles()) {
                    // only add files that are in set of instance_id of files
                    if (fileInstanceIDs
                            .contains(standardizeESGFFileInstanceID(file
                                    .getInstanceID()))) {

                        if (file.hasService(Service.HTTPSERVER)) {
                            writer.writeStartElement("file");

                            // name attribute
                            if (file.contains(Metadata.TITLE)) {
                                writer.writeAttribute("name", (String) file
                                        .getMetadata(Metadata.TITLE));
                            } else {
                                String datasetId = file.getDatasetInstanceID();
                                String fileId = file.getInstanceID();
                                // text = fileId-datasetId + size
                                // sum 1 to erase the dot
                                writer.writeAttribute("name", fileId
                                        .substring(datasetId.length() + 1));

                            }

                            // Add identity
                            if (file.contains(Metadata.MASTER_ID)) {
                                writer.writeStartElement("identity");
                                writer.writeCharacters((String) file
                                        .getMetadata(Metadata.MASTER_ID));
                                writer.writeEndElement();// </identity>
                            }

                            // Add version
                            if (file.contains(Metadata.VERSION)) {
                                writer.writeStartElement("version");
                                writer.writeCharacters((String) file
                                        .getMetadata(Metadata.VERSION));
                                writer.writeEndElement();// </version>
                            }

                            // Add file size
                            if (file.contains(Metadata.SIZE)) {
                                writer.writeStartElement("size");
                                String size = ((Long) file
                                        .getMetadata(Metadata.SIZE)).toString();
                                writer.writeCharacters(size);
                                writer.writeEndElement();// </size>
                            }

                            // Add mimetype
                            writer.writeStartElement("mimetype");
                            writer.writeCharacters("application/netcdf");
                            writer.writeEndElement();// </mimetype>

                            // Add tags
                            writer.writeStartElement("tags");
                            String tags = generateTagsOf(dataset);
                            writer.writeCharacters(tags);
                            writer.writeEndElement();// </tags>

                            // Add verification
                            if (file.contains(Metadata.CHECKSUM)
                                    && file.contains(Metadata.CHECKSUM_TYPE)) {

                                String checksumType = ((String) file
                                        .getMetadata(Metadata.CHECKSUM_TYPE))
                                        .toLowerCase();
                                String checksum = file
                                        .getMetadata(Metadata.CHECKSUM);

                                writer.writeStartElement("verification");
                                writer.writeStartElement("hash");
                                writer.writeAttribute("type", checksumType);
                                writer.writeCharacters(checksum);
                                writer.writeEndElement();// </hash>
                                writer.writeEndElement();// </verification>
                            }

                            // Add resources of file
                            writer.writeStartElement("resources");

                            for (RecordReplica fileReplica : file
                                    .getReplicasOfService(Service.HTTPSERVER)) {
                                writer.writeStartElement("url");
                                writer.writeAttribute("type", "http");
                                writer.writeCharacters(fileReplica
                                        .getUrlEndPointOfService(Service.HTTPSERVER));
                                writer.writeEndElement();// </url>
                            }

                            writer.writeEndElement();// </resources>
                            writer.writeEndElement();// </file>
                        } else {
                            logger.warn(
                                    "ESG Error: File {}, hasn't HTTP service",
                                    file.getInstanceID());
                        }
                    }
                }

            }

            writer.writeEndElement();// </files>
            writer.writeEndElement();// </metalink>
            writer.writeEndDocument();

            writer.flush();
            writer.close();

        } catch (XMLStreamException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generate metalink tags for a dataset
     * 
     * @param dataset
     * @return
     */
    private String generateTagsOf(Dataset dataset) {

        String tags = "";
        boolean removeVersion = false;

        String verboseID = dataset.getMetadata(Metadata.MASTER_ID);
        if (verboseID == null) {
            verboseID = dataset.getInstanceID();
            removeVersion = true;
        }

        String metadataSplited[] = verboseID.split(".");

        // Get all verbose metadata in the id and separate them by ","
        for (int i = 0; i < metadataSplited.length; i++) {
            if (i != metadataSplited.length - 1) {
                tags = tags + metadataSplited[i] + ", ";
            } else {
                if (removeVersion) {
                    tags = tags.substring(0, metadataSplited.length - 1);
                } else {
                    tags = tags + metadataSplited;
                }
            }
        }

        return tags;
    }

    /**
     * Retry harvesting in all datasets in search response with failed status.
     */
    public void retryFailedDatasets() {
        logger.trace("[IN]  retryHarvestFailedDatasets");

        for (Map.Entry<String, HarvestStatus> entry : datasetHarvestingStatus
                .entrySet()) {
            if (entry.getValue() == HarvestStatus.FAILED) {
                logger.debug("Reseting dataset failed and restart harvesting");
                resetDataset(entry.getKey());
            }
        }

        logger.trace("[OUT] retryHarvestFailedDatasets");
    }

    /**
     * Overwrites read object of object serialization
     * 
     * @param is
     */
    private void readObject(ObjectInputStream is) throws IOException,
            ClassNotFoundException {

        // default read object
        is.defaultReadObject();

        // quit null values in some transient attributes
        this.collectors = new LinkedList<DatasetMetadataCollector>();
        this.observers = new LinkedList<DownloadObserver>();
        this.dataAccessClass = DatasetAccessClass.getInstance();
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

}
