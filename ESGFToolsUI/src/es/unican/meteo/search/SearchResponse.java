package es.unican.meteo.search;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import net.sf.ehcache.Cache;
import es.unican.meteo.esgf.download.Download;
import es.unican.meteo.esgf.download.DownloadObserver;
import es.unican.meteo.petition.HTTPStatusCodeException;
import es.unican.meteo.petition.RequestManager;

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

    /** Logger. */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(SearchResponse.class);

    /** EH cache. */
    private Cache cache;

    /** List of dataset metadata collector. */
    private List<DatasetMetadataCollector> collectors;

    /** Executor that schedules and executes metadata collectors in threads. */
    private ExecutorService collectorsExecutor;

    /** State that indicates if this search response is completed or not. */
    private boolean completed;

    /** Number of {@link Dataset} completed. **/
    private int completedDatasets;

    /**
     * Map of dataset - array of fileInstanceID. Shows the files that must be
     * downloaded for each dataset
     */
    private Map<String, Set<String>> datasetFileInstanceIDMap;

    /**
     * Map[instanceID, boolean] where instanceID is the identifier of dataset
     * and the boolean indicates if the harvesting is completed.
     */
    private Map<String, Boolean> datasetHarvestingStatus;

    /** Number of {@link Dataset} that matches with the search. **/
    private int datasetTotalCount;

    /** Download finish date. */
    private Date downloadFinish;

    /** Download start date. */
    private Date downloadStart;

    /** Name of search response. */
    private String name;

    /** List of search response observers. */
    private List<DownloadObserver> observers;

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
                .synchronizedMap(new HashMap<String, Boolean>());
        this.collectors = new LinkedList<DatasetMetadataCollector>();
        this.completed = false;
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
     * @param cache
     *            cache ehCache
     */
    public SearchResponse(String name, RESTfulSearch search,
            ExecutorService executorService, Cache cache) {
        logger.trace("[IN]  SearchResponse");

        this.search = search;
        this.name = name;
        this.datasetTotalCount = 0;
        this.completedDatasets = 0;
        this.datasetHarvestingStatus = Collections
                .synchronizedMap(new HashMap<String, Boolean>());
        this.completed = false;
        this.downloadStart = null;
        this.downloadFinish = null;
        this.observers = new LinkedList<DownloadObserver>();
        this.collectorsExecutor = executorService;
        this.collectors = new LinkedList<DatasetMetadataCollector>();
        this.datasetFileInstanceIDMap = new HashMap<String, Set<String>>();
        this.cache = cache;

        logger.trace("[OUT] SearchResponse");
    }

    @Override
    public void download() {
        logger.trace("[IN]  download");

        logger.debug("Iniciating harvesting..");
        downloadStart = new Date();

        // Start thread that inits all metadata collectors. One by dataset
        HarvestingInitiator harvestingInitiator = new HarvestingInitiator(this,
                cache, collectorsExecutor, collectors);
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
        long diffTime = actualDate.getTime() - downloadStart.getTime();

        long millis = 0;
        // Calculate approximate time to download finish
        int datasetCurrentCount = getCompletedDatasets();
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
        int datasetCurrentCount = getCompletedDatasets();
        if (datasetTotalCount > 0) {

            percent = (datasetCurrentCount * 100) / datasetTotalCount;
        } else {
            percent = 0;
        }

        logger.trace("[OUT] getCurrentProgress");
        return percent;
    }

    /**
     * Get dataset harvesting status of search response
     * 
     * @return Map of datasets [instanceID, boolean] where instanceID is the
     *         identifier of dataset and boolean indicates if the harvesting is
     *         completed
     */
    public Map<String, Boolean> getDatasetHarvestingStatus() {
        logger.trace("[IN]  getDatasetHarvestingStatus");
        logger.trace("[OUT] getDatasetHarvestingStatus");
        return datasetHarvestingStatus;
    }

    /**
     * @return the datasetTotalCount
     */
    public synchronized int getDatasetTotalCount() {
        logger.trace("[IN]  getDatasetTotalCount");
        logger.trace("[OUT] getDatasetTotalCount");
        return datasetTotalCount;
    }

    /**
     * @return the completedDatasets
     */
    public int getCompletedDatasets() {
        return completedDatasets;
    }

    /**
     * @param completedDatasets
     *            the completedDatasets to set
     */
    public void setCompletedDatasets(int completedDatasets) {
        this.completedDatasets = completedDatasets;
    }

    /**
     * @return the downloadFinish
     */
    public Date getDownloadFinish() {
        logger.trace("[IN]  getDownloadFinish");
        logger.trace("[OUT] getDownloadFinish");
        return downloadFinish;
    }

    /**
     * @return the downloadStart
     */
    public Date getDownloadStart() {
        logger.trace("[IN]  getDownloadStart");
        logger.trace("[OUT] getDownloadStart");
        return downloadStart;
    }

    /**
     * Return instanceid of files that satisfy the constraints
     * 
     * @param datasetInstanceID
     */
    public Set<String> getFilesToDownload(String datasetInstanceID) {
        return datasetFileInstanceIDMap.get(datasetInstanceID);
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
     * Get a {@link Dataset} harvested
     * 
     * @param instanceID
     *            instance_id of dataset
     * @return dataset harvested
     * 
     * @throws IllegalArgumentException
     *             if dataset don't belongs to {@link SearchResponse} or if
     *             dataset hasn't been harvested
     * @throws IOException
     *             if some error happens when dataset has been obtained from
     *             file system
     */
    public Dataset getHarvestedDataset(String instanceID)
            throws IllegalArgumentException, IOException {
        logger.trace("[IN]  getHarvestedDataset");

        logger.debug(
                "Checking if datasets belonging to the search response and if dataset has been harvested. {}",
                instanceID);
        if (!datasetHarvestingStatus.containsKey(instanceID)) {
            logger.error("dataset {} don't belongs to search response {}",
                    instanceID, getName());
            throw new IllegalArgumentException();
        }
        if (datasetHarvestingStatus.get(instanceID) == false) {
            logger.error(
                    "dataset {} hasn't been harvested in {} search response",
                    instanceID, getName());
            throw new IllegalArgumentException();
        }

        Dataset dataset = new Dataset();

        logger.debug("Getting dataset {} from EHCache", instanceID);
        try {
            synchronized (cache) {
                if (cache.isKeyInCache(instanceID)) {
                    if (cache.get(instanceID) != null) {
                        dataset = (Dataset) cache.get(instanceID)
                                .getObjectValue();

                        logger.debug("Dataset {} found in cache", instanceID);
                    }
                }
            }
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

    /**
     * Add to search response a new dataset that will be harvested. This is used
     * by HarvestingIniciator Thread
     * 
     * @param dataInstanceID
     *            instance_id of dataset
     */
    public synchronized void addDatasetToHarvestingStatus(String dataInstanceID) {
        logger.trace("[IN]  addDatasetToHarvestingStatus");

        logger.debug("Adding to harvestig status of search{} the dataset {}",
                getName(), dataInstanceID);
        datasetHarvestingStatus.put(dataInstanceID, false);

        logger.trace("[OUT] addDatasetToHarvestingStatus");
    }

    /**
     * Check the state of harvesting of search response.
     * 
     * @return boolean that indicates if this search response is completed or
     *         not.
     */
    public boolean isCompleted() {
        logger.trace("[IN]  isCompleted");
        logger.trace("[OUT] isCompleted");
        return completed;
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
        logger.trace("[OUT] pause");
    }

    /**
     * Check if there are an active dataset harvesting
     * 
     * @return true if there are an active dataset harvesting
     */
    public boolean isHarvestingActive() {
        logger.trace("[IN]  isHarvestingActive");
        if (collectors.size() > 0) {
            return true;
        }

        logger.trace("[OUT] isHarvestingActive");
        return false;
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
    public void reset() {
        logger.trace("[IN]  reset");

        logger.debug("Reseting all threads and metadata in cache and disc");
        // terminate all threads
        for (DatasetMetadataCollector collector : collectors) {
            collector.terminate();
        }

        // remove in cache
        for (String instanceID : datasetHarvestingStatus.keySet()) {
            cache.remove(instanceID);
        }

        logger.debug("Reseting all attributes");
        // reset all attributes
        this.collectors = new LinkedList<DatasetMetadataCollector>();
        this.datasetTotalCount = 0;
        this.completedDatasets = 0;

        this.datasetHarvestingStatus = Collections
                .synchronizedMap(new HashMap<String, Boolean>());
        this.completed = false;

        this.downloadStart = null;
        this.downloadFinish = null;
        this.observers = new LinkedList<DownloadObserver>();

        logger.trace("[OUT] reset");
    }

    public void setCache(Cache cache) {
        logger.trace("[IN]  setCache");
        this.cache = cache;
        logger.trace("[OUT] setCache");
    }

    /**
     * Set list of metadata collectors that are instance of
     * {@link DatasetMetadataCollector}
     * 
     * @param collectors
     *            the collectors to set
     */
    public void setCollectors(List<DatasetMetadataCollector> collectors) {
        logger.trace("[IN]  setCollectors");
        this.collectors = collectors;
        logger.trace("[OUT] setCollectors");
    }

    /**
     * Set state of harvesting to completed.
     * 
     * @param completed
     *            state that indicates if this search response is completed or
     *            not
     * 
     */
    public void setCompleted(boolean completed) {
        logger.trace("[IN]  setCompleted");
        this.completed = completed;
        logger.trace("[OUT] setCompleted");
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
     * Set a map[instanceID, boolean] where instanceID is the identifier of
     * dataset and the boolean indicates if the harvesting is completed.
     * 
     * @param datasetHarvestingStatus
     *            a map[instanceID, boolean]
     */
    public void setDatasetHarvestingStatus(
            Map<String, Boolean> datasetHarvestingStatus) {
        logger.trace("[IN]  setDatasetHarvestingStatus");
        this.datasetHarvestingStatus = datasetHarvestingStatus;
        logger.trace("[OUT] setDatasetHarvestingStatus");
    }

    /**
     * Set download finish date.
     * 
     * @param downloadFinish
     *            the downloadFinish to set
     */
    public void setDownloadFinish(Date downloadFinish) {
        logger.trace("[IN]  setDownloadFinish");
        this.downloadFinish = downloadFinish;
        logger.trace("[OUT] setDownloadFinish");
    }

    /**
     * Set download start date.
     * 
     * @param downloadStart
     *            the downloadStart to set
     */
    public void setDownloadStart(Date downloadStart) {
        logger.trace("[IN]  setDownloadStart");
        this.downloadStart = downloadStart;
        logger.trace("[OUT] setDownloadStart");
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
    public void setObservers(List<DownloadObserver> observers) {
        logger.trace("[IN]  setObservers");
        this.observers = observers;
        logger.trace("[OUT] setObservers");
    }

    /**
     * @param search
     *            the search to set
     */
    public void setSearch(RESTfulSearch search) {
        logger.trace("[IN]  setSearch");
        this.search = search;
        logger.trace("[OUT] setSearch");
    }

    /**
     * Check datasets of search in system files
     * 
     * @throws IOException
     *             if datasets completed isn't in file system
     */
    public void checkDatasets() throws IOException {
        logger.trace("[IN]  restoreDatasets");

        if (cache != null) {
            int sum = 0;

            // For each dataset
            for (String instanceID : datasetHarvestingStatus.keySet()) {

                logger.debug("Checking if dataset {} is completed...",
                        instanceID);
                if (isDatasetHarvested(instanceID)) {
                    if (cache.isKeyInCache(instanceID)) {
                        logger.debug("Checking if harvested dataset is cache");
                        if (cache.get(instanceID) != null) {
                            datasetHarvestingStatus.put(instanceID, true);
                            logger.debug("Dataset {} find in cache", instanceID);
                        }
                    } else {
                        throw new IOException(
                                "dataset completed isn't in cache");
                    }
                }
            }
        } else {
            logger.warn("Saved datasets can't be loaded because cache is null");
        }
        logger.trace("[OUT] restoreDatasets");
    }

    /**
     * Check if a dataset has been harvested.
     * 
     * @param instanceID
     *            of dataset
     * @return true if a dataset has been harvested; otherwise false
     */
    public boolean isDatasetHarvested(String instanceID) {
        logger.trace("[IN]  isDatasetHarvested");
        logger.trace("[OUT] isDatasetHarvested");
        return datasetHarvestingStatus.get(instanceID);
    }

    /**
     * Remove to search response a harvested {@link Dataset}
     * 
     * @param instanceID
     */
    public void datasetHarvestingAborted(String instanceID) {
        logger.trace("[IN]  datasetHarvestingAborted");
        datasetHarvestingStatus.remove(instanceID);
        logger.trace("[OUT] datasetHarvestingAborted");
    }

    /**
     * Set harvesting of a dataset to completed
     * 
     * @param instanceID
     *            of dataset
     */
    public synchronized void datasetHarvestingCompleted(String instanceID) {
        logger.trace("[IN]  datasetHarvestingCompleted");

        logger.debug("Setting new completed dataset: {}", instanceID);
        this.completedDatasets = this.completedDatasets + 1;
        datasetHarvestingStatus.put(instanceID, true);

        logger.debug("Notify observers");
        notifyDownloadProgressObservers();

        if (completedDatasets == datasetTotalCount) {
            logger.debug("Metadata harvesting of {} has finished", getName());
            setCompleted(true);
            setDownloadFinish(new Date());
            notifyDownloadCompletedObservers();
            this.collectors = new LinkedList<DatasetMetadataCollector>();
        }

        logger.trace("[OUT] datasetHarvestingCompleted");
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
     * Check if search response contains a {@link Dataset}
     * 
     * @param instanceID
     *            of dataset
     * @return true if search response contains a dataset
     */
    public boolean contains(String instanceID) {
        return datasetHarvestingStatus.containsKey(instanceID);
    }

    /**
     * Private class runnable, that obtains first set of datasets. If exists an
     * error, the object responseDatasets is null
     * 
     * @author Karem Terry
     */
    public class HarvestingInitiator extends Thread {

        /** Current {@link RESTfulSearch}. **/
        private RESTfulSearch search;
        /** Set of datasets. */
        Set<Dataset> datasets;
        /** Executor that schedules and executes metadata collectors in threads. */
        ExecutorService collectorsExecutor;
        /** EHCache . */
        Cache cache;
        /** List of dataset metadata collector. */
        List<DatasetMetadataCollector> collectors;
        /** Class that represents the response of a search. */
        SearchResponse searchResponse;
        /**
         * Map of dataset - array of fileInstanceID. Shows the files that must
         * be downloaded for each dataset.
         */
        Map<String, Set<String>> datasetFileInstanceIDMap;

        /**
         * Constructor with parameters
         * 
         * @param collectorsExecutor
         *            executor that schedules and executes metadata collectors
         *            in threads
         * @param cache
         *            EHCache
         * @param collectors
         *            List of dataset metadata collector
         * @param searchResponse
         *            Class that represents the response of a search.
         */
        public HarvestingInitiator(SearchResponse searchResponse, Cache cache,
                ExecutorService collectorsExecutor,
                List<DatasetMetadataCollector> collectors) {
            logger.trace("[IN]  HarvestingInitiator");
            this.search = searchResponse.getSearch();
            this.collectorsExecutor = collectorsExecutor;
            this.cache = cache;
            this.collectors = collectors;
            this.searchResponse = searchResponse;
            this.datasetFileInstanceIDMap = searchResponse
                    .getMapDatasetFileInstanceID();

            logger.trace("[OUT] HarvestingInitiator");
        }

        @Override
        public void run() {
            logger.trace("[IN]  run");
            try {
                // Get instance_id of datasets that satisfy the constraints
                Set<String> datasetInstanceIDs = RequestManager
                        .getDatasetInstanceIDsFromSearch(search);

                for (String instanceID : datasetInstanceIDs) {

                    // Create new dataset with this instance id and
                    // predetermined
                    // indexNode with it value in search
                    Dataset dataset = new Dataset(instanceID);
                    boolean found = searchResponse.contains(instanceID);
                    boolean completed = false;

                    // if dataset exists then check if harvesting is completed
                    if (found) {
                        completed = searchResponse
                                .isDatasetHarvested(instanceID);
                    }

                    // if not completed new collector task
                    if (!completed) {

                        logger.debug(
                                "Adding new dataset {} collector in thread pool",
                                instanceID);

                        searchResponse.addDatasetToHarvestingStatus(instanceID);

                        DatasetMetadataCollector collector = new DatasetMetadataCollector(
                                instanceID, cache, searchResponse,
                                datasetFileInstanceIDMap);
                        collectors.add(collector);

                        // add runnable to collectors executor
                        collectorsExecutor.execute(collector);
                    }
                }

                searchResponse.setDatasetTotalCount(datasetInstanceIDs.size());

            } catch (IOException e) {
                logger.error(
                        "Error IOException in search response with name: {} \n {}",
                        searchResponse.getName(), e.getStackTrace());
            } catch (HTTPStatusCodeException e) {
                logger.error(
                        "Error IOException in search response with name: {} \n {}",
                        searchResponse.getName(), e.getStackTrace());
            }

            logger.trace("[OUT] run");
        }
    }

}
