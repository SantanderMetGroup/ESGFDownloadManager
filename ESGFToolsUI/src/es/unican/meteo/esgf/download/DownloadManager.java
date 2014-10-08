package es.unican.meteo.esgf.download;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.unican.meteo.esgf.petition.DatasetAccessClass;
import es.unican.meteo.esgf.search.Dataset;
import es.unican.meteo.esgf.search.DatasetFile;
import es.unican.meteo.esgf.search.Metadata;
import es.unican.meteo.esgf.search.RecordReplica;
import es.unican.meteo.esgf.search.SearchManager;
import es.unican.meteo.esgf.search.SearchResponse;
import es.unican.meteo.esgf.search.Service;

/**
 * Manage Download from ESGF data nodes. Extends observable for Java Observer
 * implementation. In each addition and substraction of files/datasets/searches
 *
 * @author Karem Terry
 *
 */
public class DownloadManager extends Observable implements DownloadObserver {

    /** Logger. */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(DownloadManager.class);

    /**
     * Number of files downloads simultaneous
     */
    private static final int SIMULTANEOUS_DOWNLOADS = 5;
    private static final String DATASET_DOWNLOADS_FILE_NAME = "dataset_downloads.data";
    private static final String FILEINSTANCEIDS_FILE_NAME = "fileInstanceIDs.data";

    /**
     * Map of instance id of {@link Dataset} and its
     * {@link DatasetDownloadStatus} status.
     */
    private Map<String, DatasetDownloadStatus> instanceIDDataStatusMap;

    /**
     * Search responses.
     */
    private Set<SearchResponse> searches;

    /**
     * Set of instance id of {@link DatasetFile} put to download in download
     * queue.
     */
    private Set<String> fileInstanceIDs;

    /** Executor that schedules and executes file downloads. Thread pool */
    private ExecutorService downloadExecutor;

    /** Dataset access class. */
    private DatasetAccessClass dataAccessClass;

    private String datasetDownloadsPath;

    private String fileInstanceIDsPath;

    /** Singleton instance. */
    private static DownloadManager INSTANCE = null;

    /**
     * Create a thread-safe singleton.
     */
    private static void createInstance() {
        logger.trace("[IN]  createInstance");

        logger.debug("Checking if exist an instance of DownloadManager");
        // creating a thread-safe singleton
        if (INSTANCE == null) {

            // Only the synchronized block is accessed when the instance hasn't
            // been created.
            synchronized (DownloadManager.class) {
                // Inside the block it must check again that the instance has
                // not been created.
                if (INSTANCE == null) {
                    logger.debug("Creating new instance of DownloadManager");
                    INSTANCE = new DownloadManager();
                }
            }
        }
        logger.trace("[OUT] createInstance");
    }

    /**
     * Get singleton instance of {@link DownloadManager}. This instance is the
     * only that exists.
     *
     * @return the unique instance of {@link DownloadManager}.
     */
    public static DownloadManager getInstance() {
        logger.trace("[IN]  getInstance");
        createInstance();
        logger.trace("[OUT] getInstance");
        return INSTANCE;
    }

    /**
     * Constructor
     */
    public DownloadManager() {
        logger.trace("[IN]  DownloadManager");

        // Initialize download executor
        // Thread pool with a fixed number of threads
        downloadExecutor = Executors.newFixedThreadPool(SIMULTANEOUS_DOWNLOADS);
        instanceIDDataStatusMap = new HashMap<String, DatasetDownloadStatus>();
        fileInstanceIDs = new HashSet<String>();

        this.searches = new HashSet<SearchResponse>();
        this.dataAccessClass = DatasetAccessClass.getInstance();

        this.datasetDownloadsPath = System.getProperty("user.home")
                + File.separator + ".esgData" + File.separator
                + DATASET_DOWNLOADS_FILE_NAME;
        this.fileInstanceIDsPath = System.getProperty("user.home")
                + File.separator + ".esgData" + File.separator
                + FILEINSTANCEIDS_FILE_NAME;

        logger.trace("[OUT] DownloadManager");
    }

    /**
     * Returns true if download manager contains a dataset with an instance id
     * and otherwise, false
     *
     * @param instanceID
     * @return true if download manager contains a dataset with an instance id
     *         and otherwise, false
     */
    public boolean containsDataset(String instanceID) {

        logger.trace("[IN]  containsDataset");
        logger.trace("[OUT] containsDataset");
        return instanceIDDataStatusMap.containsKey(instanceID);
    }

    /**
     * Configure file to download add to download queue
     *
     * @param fileStatus
     * @throws IOException
     *             if file info don't found
     */
    public void downloadFile(FileDownloadStatus fileStatus) throws IOException {
        logger.trace("[IN]  downloadFile");

        logger.debug("Download file {}", fileStatus.getInstanceID());

        DatasetDownloadStatus dDStatus = fileStatus.getDatasetDownloadStatus();
        dDStatus.downloadFile(fileStatus);

        logger.trace("[OUT] downloadFile");
    }

    /**
     * Add dataset to be downloaded. Put dataset in download queue.
     *
     * @param searchResponse
     *
     * @param dataset
     *            dataset
     * @param fileInstanceIDs
     *            set of file_instanceId for files to be downloaded.
     * @param path
     *            path of downloads. If path parameter is null then path =
     *            user.home/ESGF_DATA
     *
     * @throws IOException
     *
     * @throws IllegalStateException
     *             if the dataset has been added previously.
     * @throws IllegalArgumentException
     *             if the dataset hasn't files or HTTP server.
     */
    public void enqueueDataset(SearchResponse searchResponse, Dataset dataset,
            Set<String> fileInstanceIDs, String path) throws IOException {
        logger.trace("[IN]  enqueueDatasetDownload");

        if (dataset.getFiles().size() <= 0) {
            logger.error("Dataset {} hasn't files to download",
                    dataset.getInstanceID());
            throw new IllegalArgumentException(
                    "Dataset hasn't files to download");
        } else if (!dataset.getFileServices().contains(Service.HTTPSERVER)) {
            logger.error("Dataset {} hasn't HTTP service",
                    dataset.getInstanceID());
            throw new IllegalArgumentException("Dataset hasn't HTTP service");
        }

        logger.debug("Check if dataset: {} is already enqueued",
                dataset.getInstanceID());
        // If dataset already in download queue
        if (instanceIDDataStatusMap.containsKey(dataset.getInstanceID())) {
            logger.debug("Dataset {} has been added previously",
                    dataset.getInstanceID());
            unskipFiles(instanceIDDataStatusMap.get(dataset.getInstanceID()),
                    fileInstanceIDs);
        } else {

            Map<String, Long> fileSizeMap = new HashMap<String, Long>();
            Map<String, String> fileNamesMap = new HashMap<String, String>();

            logger.debug("Getting all files and its size from dataset info");
            for (DatasetFile file : dataset.getFiles()) {
                fileSizeMap.put(file.getInstanceID(),
                        (Long) file.getMetadata(Metadata.SIZE));

                if (file.contains(Metadata.TITLE)) {
                    fileNamesMap.put(file.getInstanceID(),
                            (String) file.getMetadata(Metadata.TITLE));
                } else {
                    fileNamesMap.put(
                            file.getInstanceID(),
                            generateNameOfFile(file.getInstanceID(),
                                    dataset.getInstanceID()));
                }
            }

            DatasetDownloadStatus datasetStatus = new DatasetDownloadStatus(
                    dataset.getInstanceID(), fileSizeMap, fileNamesMap, path,
                    downloadExecutor);

            instanceIDDataStatusMap.put(dataset.getInstanceID(), datasetStatus);
            logger.debug("Setting dataset {} to download",
                    dataset.getInstanceID());
            datasetStatus.setFilesToDownload(fileInstanceIDs);

            // register observer
            datasetStatus.registerObserver(this);

            // XXX
            // maybe not necessary auto download
            datasetStatus.download();
        }

        // add files to instanceID-files map
        this.fileInstanceIDs.addAll(fileInstanceIDs);

        // save datasets download
        serializeDatasetDownloads();

        // Save fileInstanceIDs
        serializeSelectedFileInstanceIDs();

        // notify observers
        setChanged();
        notifyObservers();

        logger.trace("[OUT] enqueueDatasetDownload");
    }

    private void serializeSelectedFileInstanceIDs() {
        ObjectOutputStream out;
        try {

            File file = new File(fileInstanceIDsPath);
            out = new ObjectOutputStream(new FileOutputStream(file));
            out.writeObject(getFileInstanceIDs());
            out.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Add search response to be downloaded. Put any {@link Dataset} in
     * {@link SearchResponse}
     *
     * @param searchResponse
     * @param path
     *            path of downloads. If path parameter is null then path =
     *            user.home/ESGF_DATA
     *
     */
    public void enqueueSearch(SearchResponse searchResponse, String path) {
        logger.trace("[IN]  enqueueSearch");

        searches.add(searchResponse);

        if (searchResponse.isCompleted()) {

            logger.debug("Adding only files that satisfy the constraints of search");
            for (String instanceID : searchResponse
                    .getDatasetHarvestingStatus().keySet()) {
                try {
                    Dataset dataset = searchResponse.getDataset(instanceID);
                    enqueueDataset(searchResponse, dataset,
                            searchResponse.getFilesToDownload(dataset
                                    .getInstanceID()), path);
                } catch (Exception e) {
                    logger.warn("Couldn't add dataset {} : {}", instanceID,
                            e.getMessage());
                }
            }
        }

        // notify observers
        setChanged();
        notifyObservers();

        logger.trace("[OUT] enqueueSearch");
    }

    private String generateNameOfFile(String instanceID,
            String datasetInstanceID) {
        // TODO Auto-generated method stub
        return instanceID.substring(datasetInstanceID.length() + 1);
    }

    /**
     * Get a {@link Dataset} object
     *
     * @param instanceID
     *            instance_id of dataset
     * @return dataset or null if isn't in DB
     *
     * @throws IOException
     *             if some error happens when dataset has been obtained from
     *             file system
     */
    public Dataset getDataset(String instanceID) throws IOException {
        logger.trace("[IN]  getDataset");

        logger.debug("Getting dataset {} from system", instanceID);

        Dataset dataset = dataAccessClass.getDataset(instanceID);

        if (dataset != null) {
            logger.debug("Dataset {} found in cache", instanceID);
        }

        logger.trace("[OUT] getDataset");
        return dataset;
    }

    /**
     * Get all dataset download status in download manager
     *
     * @return all {@link DatasetDownloadStatus} in {@link DownloadManager}
     */
    public Set<DatasetDownloadStatus> getDatasetDownloads() {
        logger.trace("[IN]  getDatasetDownloads");
        logger.trace("[OUT] getDatasetDownloads");
        return new HashSet<DatasetDownloadStatus>(
                instanceIDDataStatusMap.values());
    }

    /**
     * Get dataset download path.
     *
     * @param dataDownloadStatus
     *            download status whose download path will be return
     * @return download path
     */
    public String getDatasetPath(DatasetDownloadStatus dataDownloadStatus) {

        logger.trace("[IN]  getDatasetPath");

        logger.debug("Getting path of dataset: {}",
                dataDownloadStatus.getInstanceID());
        String path = "not found";

        if (instanceIDDataStatusMap.containsKey(dataDownloadStatus
                .getInstanceID())) {
            // find dataset in selected dataset and return its download path
            path = dataDownloadStatus.getPath(); // return
        } else {
            logger.error("Path of {} not found",
                    dataDownloadStatus.getInstanceID());
            throw new IllegalStateException();
        }

        logger.trace("[OUT] getDatasetPath");
        return path;
    }

    /**
     * Get a set of {@link RecordReplica} of {@link Dataset} of a specific
     * {@link Service}
     *
     * @param instanceID
     *            dataset instance_id
     * @param service
     *            service offered to access dataset
     * @return list of RecordReplica that offer a {@link Service} or null if any
     *         replica offers this service
     * @throws IOException
     *             when {@link Dataset} hasn't been obtained from file system
     */
    public List<RecordReplica> getDatasetReplicasOfService(String instanceID,
            Service service) throws IOException {
        logger.trace("[IN]  getDatasetReplicasOfService");
        Dataset dataset = getDataset(instanceID);

        logger.trace("[OUT] getDatasetReplicasOfService");
        return dataset.getReplicasOfService(service);
    }

    /**
     * Get executor that schedules and executes file downloads.
     *
     * @return executor that schedules and executes file downloads
     */
    public ExecutorService getDownloadExecutor() {
        logger.trace("[IN]  getDownloadExecutor");
        logger.trace("[OUT] getDownloadExecutor");
        return downloadExecutor;
    }

    /**
     * Get a {@link DatasetFile} object
     *
     * @param datasetInstanceID
     *            instance_id of dataset witch file belongs
     * @param fileInstanceID
     *            instance_id of file that will be get of system file
     * @return a {@link DatasetFile} object
     * @throws IOException
     *             when file hasn't been obtained from file system
     */
    public DatasetFile getFile(String datasetInstanceID, String fileInstanceID)
            throws IOException {
        logger.trace("[IN]  getFile");

        try {
            Dataset dataset = getDataset(datasetInstanceID);

            logger.trace("[OUT] getFile");
            return dataset.getFileWithInstanceId(fileInstanceID);
        } catch (Exception e) {
            logger.error(
                    "Error happens when metadata of file {} has been obtained from file system",
                    fileInstanceID);
            throw new IOException(
                    "Error happens when data has been obtained from cache "
                            + fileInstanceID + " " + e.getMessage());
        }

    }

    /**
     * Get a set of instance id of DatasetFiles put to download.
     *
     * @return a set of instance id of DatasetFiles put to download
     */
    public Set<String> getFileInstanceIDs() {
        logger.trace("[IN]  getFileInstanceIDs");
        logger.trace("[OUT] getFileInstanceIDs");
        return fileInstanceIDs;
    }

    /**
     * Get a set of {@link RecordReplica} of {@link DatasetFile} of a specific
     * {@link Service}
     *
     * @param datasetInstanceID
     *            dataset instance_id
     * @param fileInstanceID
     *            file instance_id
     * @param service
     *            service offered to access file
     * @return list of RecordReplica that offer a {@link Service} or null if any
     *         replica offers this service
     * @throws IOException
     *             when {@link DatasetFile} hasn't been obtained from file
     *             system
     */
    public List<RecordReplica> getFileReplicasOfService(
            String datasetInstanceID, String fileInstanceID, Service service)
                    throws IOException {
        logger.trace("[IN]  getFileReplicasOfService");
        DatasetFile file = getFile(datasetInstanceID, fileInstanceID);

        logger.trace("[OUT] getFileReplicasOfService");
        return file.getReplicasOfService(service);
    }

    /**
     * Get a set of {@link DatasetFile} contained in {@link Dataset}
     *
     * @param datasetInstanceID
     *            instance_id of dataset
     * @return a set of files contained in the dataset
     * @throws IOException
     *             when files hasn't been obtained from file system
     */
    public Set<DatasetFile> getFiles(String datasetInstanceID)
            throws IOException {
        logger.trace("[IN]  getFile");

        try {
            Dataset dataset = getDataset(datasetInstanceID);

            logger.trace("[OUT] getFile");
            return dataset.getFiles();
        } catch (Exception e) {
            logger.error(
                    "Error happens when files of dataset {} has been obtained from file system",
                    datasetInstanceID);
            throw new IOException(
                    "Error happens when files of datasets has been obtained from cache "
                            + datasetInstanceID + " " + e.getMessage());
        }
    }

    /**
     *
     * @param instanceID
     * @return
     * @throws IOException
     *             if dataset info can't restore from file system
     */
    private Map<String, Long> getFilesAndSizesOfDataset(String instanceID)
            throws IOException {
        logger.trace("[IN]  getFilesAndSizesOfDataset");
        Dataset dataset = getDataset(instanceID);
        Map<String, Long> fileSizeMap = new HashMap<String, Long>();

        logger.debug("Getting all files and its size from dataset info");
        for (DatasetFile file : dataset.getFiles()) {
            fileSizeMap.put(file.getInstanceID(),
                    (Long) file.getMetadata(Metadata.SIZE));
        }

        logger.trace("[OUT] getFilesAndSizesOfDataset");
        return fileSizeMap;
    }

    /**
     * Get map of instance id of Dataset and its DatasetDownloadStatus status.
     *
     * @return the instanceIDDataStatusMap
     */
    public Map<String, DatasetDownloadStatus> getInstanceIDDataStatusMap() {
        logger.trace("[IN]  getInstanceIDDataStatusMap");
        logger.trace("[OUT] getInstanceIDDataStatusMap");
        return instanceIDDataStatusMap;
    }

    /**
     * Get number of datasets.
     *
     * @return number of datasets.
     */
    public int getNumOfDatasets() {
        logger.trace("[IN]  getNumOfDatasets");
        logger.trace("[OUT] getNumOfDatasets");
        return instanceIDDataStatusMap.size();
    }

    /**
     * Get priority of a {@link DatasetDownloadStatus}
     *
     * @param dataDownloadStatus
     *
     * @return priority of dataset
     *
     * @throws IllegalStateException
     *             if {@link DatasetDownloadStatus} isn't in download list
     */
    public DownloadPriority getPriority(DatasetDownloadStatus dataDownloadStatus) {

        logger.trace("[IN]  getPriority");

        logger.debug("Getting priority of {}:",
                dataDownloadStatus.getInstanceID());

        if (!instanceIDDataStatusMap.containsKey(dataDownloadStatus
                .getInstanceID())) {
            logger.error("Dataset {} isn't be in download list",
                    dataDownloadStatus.getInstanceID());
            throw new IllegalStateException();
        } else {

            logger.trace("[OUT] getPriority");
            return dataDownloadStatus.getPriority();
        }
    }

    /**
     * Get {@link RecordStatus} of a {@link DatasetDownloadStatus}.
     *
     * @param dataDownloadStatus
     *
     * @return record status of {@link DatasetDownloadStatus}
     *
     * @throws IllegalStateException
     *             if dataset isn't in download list
     */
    public RecordStatus getRecordStatus(DatasetDownloadStatus dataDownloadStatus) {

        logger.trace("[IN]  getRecordStatus");

        logger.debug("Getting record status of {}:",
                dataDownloadStatus.getInstanceID());

        if (!instanceIDDataStatusMap.containsKey(dataDownloadStatus
                .getInstanceID())) {
            logger.error("Dataset {} isn't be in download list",
                    dataDownloadStatus.getInstanceID());
            throw new IllegalStateException();
        } else {
            logger.trace("[OUT] getRecordStatus");
            return dataDownloadStatus.getRecordStatus();
        }
    }

    public boolean isDatasetQueued(String instanceID) {
        return instanceIDDataStatusMap.containsKey(instanceID);
    }

    /**
     * Check if a {@link DatasetFile} is added to dowload
     *
     * @param instanceID
     *            of {@link DatasetFile}
     * @return true if file have been added to queue of downloads or false
     *         otherwise
     */
    public boolean isFileAddedToDownload(String instanceID) {
        logger.trace("[IN]  isFileAddedToDownload");
        logger.trace("[OUT] isFileAddedToDownload");
        return fileInstanceIDs.contains(instanceID);
    }

    @Override
    public void onDownloadCompleted(Download download) {
        serializeDatasetDownloads();
    }

    @Override
    public void onDownloadChange(Download download) {
        serializeDatasetDownloads();

    }

    private void serializeDatasetDownloads() {
        // Serialize dataset downloads objects in file
        ObjectOutputStream out = null;
        try {

            File file = new File(datasetDownloadsPath);
            // copy file from avoid error if the program is
            // abruptly closed
            File bkfile = new File(datasetDownloadsPath + "_backup");
            copyFile(file, bkfile);

            // write new info in dataset downloads file
            out = new ObjectOutputStream(new FileOutputStream(file));
            out.writeObject(getDatasetDownloads());
            out.close();

            // if is success then remove backup file
            bkfile.delete();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onError(Download download) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onUnauthorizedError(Download download) {
        // TODO Auto-generated method stub

    }

    /**
     * Pause all active downloads
     */
    public void pauseActiveDownloads() {
        logger.trace("[IN]  pauseActiveDownloads");
        for (Map.Entry<String, DatasetDownloadStatus> entry : instanceIDDataStatusMap
                .entrySet()) {

            DatasetDownloadStatus ddstatus = entry.getValue();
            if (ddstatus.getRecordStatus() != RecordStatus.FINISHED) {
                entry.getValue().pause();
            }
        }
        logger.trace("[OUT] pauseActiveDownloads");
    }

    /**
     * Pause the download of dataset.
     *
     * @param dataDownloadStatus
     */
    public void pauseDataSetDownload(DatasetDownloadStatus dataDownloadStatus) {
        logger.trace("[IN]  pauseDataSetDownload");

        if (instanceIDDataStatusMap.containsKey(dataDownloadStatus
                .getInstanceID())) {

            logger.debug("Pausing download of {}:",
                    dataDownloadStatus.getInstanceID());
            // pause dataset download and pause its files, therefore all job
            // files (tasks) will be finished and configured to pause state.
            dataDownloadStatus.pause();
        }
        logger.trace("[OUT] pauseDataSetDownload");
    }

    /**
     * Reset {@link FileDownloadStatus} download
     *
     * @param fileStatus
     *            whose current download will be reset
     */
    public void pauseFile(FileDownloadStatus fileStatus) {

        logger.trace("[IN]  pauseFile");

        logger.debug("Reset file {}", fileStatus.getInstanceID());

        DatasetDownloadStatus dDStatus = fileStatus.getDatasetDownloadStatus();
        dDStatus.pauseFile(fileStatus);

        logger.trace("[OUT] pauseFile");
    }

    /**
     * Put to download queue all files that record status are UNAUTHORIZED
     *
     * @throws IOException
     *             if info of file don't found in file system
     */
    public synchronized void putToDownloadUnauthorizedFiles()
            throws IOException {
        logger.trace("[IN]  putToDownloadUnauthorizedFiles");

        for (Map.Entry<String, DatasetDownloadStatus> entry : instanceIDDataStatusMap
                .entrySet()) {
            entry.getValue().putToDownloadUnauthorizedFiles();
        }

        logger.trace("[OUT] putToDownloadUnauthorizedFiles");
    }

    /**
     * Remove {@link Dataset} of list of downloads. If not exists nothing
     * happens
     *
     * @param dataDownloadStatus
     */
    public void removeDataset(DatasetDownloadStatus dataDownloadStatus) {

        logger.trace("[IN]  removeDataset");

        logger.debug("Removing dataset {} of downloads",
                dataDownloadStatus.getInstanceID());

        if (dataDownloadStatus.getRecordStatus() == RecordStatus.DOWNLOADING) {
            dataDownloadStatus.pause(); // pause download
        }

        String instanceID = dataDownloadStatus.getInstanceID();

        // remove dataset
        instanceIDDataStatusMap.remove(instanceID);

        // remove its files
        for (FileDownloadStatus fileStatus : dataDownloadStatus
                .getFilesDownloadStatus()) {
            if (fileStatus.getRecordStatus() == RecordStatus.DOWNLOADING) {
                fileStatus.pause();
            }
            fileInstanceIDs.remove(fileStatus.getInstanceID());
        }

        // remove dataset of DB
        if (SearchManager.getInstance().getNumberOfSearchOfDataset(instanceID) < 1) {
            try {
                dataAccessClass.removeDataset(instanceID);
            } catch (IOException e) {
                // do nothing
            }
        }

        serializeSelectedFileInstanceIDs();

        serializeDatasetDownloads();

        // notify observers
        setChanged();
        notifyObservers();

        logger.trace("[OUT] removeDataset");
    }

    /**
     * Reset all configuration an downloads configurated in manager. And remove
     * all downloads (datatasets and files).
     */
    public void reset() {
        logger.trace("[IN]  reset");

        logger.debug("Reset all configurations in download manager");

        pauseActiveDownloads();

        // Initialize download executor
        // Thread pool with a fixed number of threads
        downloadExecutor = Executors.newFixedThreadPool(SIMULTANEOUS_DOWNLOADS);

        instanceIDDataStatusMap = new HashMap<String, DatasetDownloadStatus>();
        fileInstanceIDs = new HashSet<String>();
        searches = new HashSet<SearchResponse>();

        serializeSelectedFileInstanceIDs();

        // Serialize dataset downloads objects in file
        serializeDatasetDownloads();

        logger.trace("[OUT] reset");

    }

    /**
     * Reset {@link Dataset} download.
     *
     * @param dataDownloadStatus
     *            of dataset whose current download will be reset
     */
    public void resetDataSetDownload(DatasetDownloadStatus dataDownloadStatus) {

        logger.trace("[IN]  resetDataSetDownload");

        logger.debug("Reset download of dataset {}",
                dataDownloadStatus.getInstanceID());

        dataDownloadStatus.reset();

        logger.trace("[OUT] resetDataSetDownload");
    }

    /**
     * Reset {@link FileDownloadStatus} download
     *
     * @param fileStatus
     *            whose current download will be reset
     */
    public void resetFile(FileDownloadStatus fileStatus) {

        logger.trace("[IN]  resetFile");

        logger.debug("Reset file {}", fileStatus.getInstanceID());

        DatasetDownloadStatus dDStatus = fileStatus.getDatasetDownloadStatus();
        dDStatus.resetFile(fileStatus);
        logger.trace("[OUT] resetFile");
    }

    /**
     * Uses to reaload datasets status created in another session of this
     * program.
     *
     * @param datasetDownloads
     */
    public void restoreDatasetDownloads(
            Set<DatasetDownloadStatus> datasetDownloads) {
        logger.trace("[IN]  restoreDatasetDownloads");

        logger.debug("Reload record download status of previous sessions");
        // Fill map of instance id of Dataset and its DatasetDownloadStatus
        // status.
        for (DatasetDownloadStatus dDStatus : datasetDownloads) {
            instanceIDDataStatusMap.put(dDStatus.getInstanceID(), dDStatus);

            // register observer
            dDStatus.registerObserver(this);
        }

        logger.trace("[OUT] restoreDatasetDownloads");
    }

    /**
     * Uses to reload files instance ids created in another session of this
     * program.
     *
     * @param fileInstanceIDs
     *            Set of instance id of DatasetFile put to download in download
     *            queue
     */
    public void restoreFileInstanceIDs(Set<String> fileInstanceIDs) {
        logger.trace("[IN]  restoreFileInstanceIDs");
        this.fileInstanceIDs = fileInstanceIDs;

        logger.trace("[OUT] restoreFileInstanceIDs");
    }

    /**
     * Reset all failed downloads in a dataset
     *
     * @param datasetStatus
     */
    public void resetAllFailedDownloads(DatasetDownloadStatus datasetStatus) {

        // reset all FAILED files
        for (FileDownloadStatus fDStatus : datasetStatus
                .getFilesDownloadStatus()) {
            if (fDStatus.getRecordStatus() == RecordStatus.FAILED) {
                fDStatus.reset();
            }
        }

        // save datasets download
        serializeDatasetDownloads();

        // Save fileInstanceIDs
        serializeSelectedFileInstanceIDs();
    }

    /**
     * Set dataset download path.
     *
     * @param dataDownloadStatus
     *            {@link DatasetDownloadStatus} of dataset whose download path
     *            will be return
     * @param path
     *            download path
     *
     * @throws AlreadyBeingDownloadedException
     */
    public void setDatasetPath(DatasetDownloadStatus dataDownloadStatus,
            String path) throws AlreadyBeingDownloadedException {

        logger.trace("[IN]  setDatasetPath");

        logger.debug("Setting dataset path for download of {}",
                dataDownloadStatus.getInstanceID());

        if (dataDownloadStatus.getRecordStatus() == RecordStatus.DOWNLOADING
                || dataDownloadStatus.getRecordStatus() == RecordStatus.FINISHED) {
            logger.error(
                    "Can't change path of dataset {}. Dataset is being downloaded",
                    dataDownloadStatus.getInstanceID());
            throw new AlreadyBeingDownloadedException();
        } else {
            dataDownloadStatus.setPath(path);
        }

        logger.trace("[OUT] setDatasetPath");
    }

    /**
     * Set a set of instance id of DatasetFiles put to download.
     *
     * @param fileInstanceIDs
     */
    public void setFileInstanceIDs(Set<String> fileInstanceIDs) {
        logger.trace("[IN]  setFileInstanceIDs");
        this.fileInstanceIDs = fileInstanceIDs;
        logger.trace("[OUT] setFileInstanceIDs");
    }

    /**
     * Set map of instance id of Dataset and its DatasetDownloadStatus status.
     *
     * @param instanceIDDataStatusMap
     *            the instanceIDDataStatusMap to set
     */
    public void setInstanceIDDataStatusMap(
            Map<String, DatasetDownloadStatus> instanceIDDataStatusMap) {
        logger.trace("[IN]  setInstanceIDDataStatusMap");
        this.instanceIDDataStatusMap = instanceIDDataStatusMap;
        logger.trace("[OUT] setInstanceIDDataStatusMap");
    }

    /**
     * Set priority of a {@link DatasetDownloadStatus} to download
     *
     * @param dataDownloadStatus
     *
     * @throws IllegalStateException
     *             if dataset of {@link DatasetDownloadStatus} isn't in download
     *             list
     */
    public void setPriority(DatasetDownloadStatus dataDownloadStatus,
            DownloadPriority priority) {

        logger.trace("[IN]  setPriority");

        logger.debug("Setting priority of {}:",
                dataDownloadStatus.getInstanceID());

        if (!instanceIDDataStatusMap.containsKey(dataDownloadStatus
                .getInstanceID())) {
            logger.error("Dataset {} isn't be in download list",
                    dataDownloadStatus.getInstanceID());
            throw new IllegalStateException();
        } else {
            dataDownloadStatus.setPriority(priority);
        }

        logger.trace("[OUT] setPriority");
    }

    /**
     * Remove file in download queue. If download completed (checksum checked or
     * not) the file persists, otherwise the local file is removed
     *
     * @param fileStatus
     */
    public void skipFile(FileDownloadStatus fileStatus) {
        logger.trace("[IN]  skipFile");

        logger.debug("Skip file download {}", fileStatus.getInstanceID());

        DatasetDownloadStatus dDStatus = fileStatus.getDatasetDownloadStatus();
        dDStatus.skipFile(fileStatus);

        // remove file to isntanceid-files map
        this.fileInstanceIDs.remove(fileStatus.getInstanceID());

        serializeSelectedFileInstanceIDs();

        // Serialize dataset downloads objects in file
        serializeDatasetDownloads();

        // notify observers
        setChanged();
        notifyObservers();

        logger.trace("[OUT] skipFile");

    }

    /**
     * Start all downloads
     */
    public void startAllDownloads() {
        logger.trace("[IN]  startAllDownloads");
        for (Map.Entry<String, DatasetDownloadStatus> entry : instanceIDDataStatusMap
                .entrySet()) {

            DatasetDownloadStatus ddstatus = entry.getValue();
            if (ddstatus.getRecordStatus() != RecordStatus.FINISHED) {
                try {
                    ddstatus.download();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    // for now nothing only not put to download
                }
            }
        }

        logger.trace("[OUT] startAllDownloads");

    }

    /**
     * Unskip {@link FileDownloadStatus} of {@link DatasetDownloadStatus} that
     * belongs to set of file instance IDS.
     *
     * Files that are not skipped remain unchanged.
     *
     * @param datasetDownloadStatus
     * @param fileInstanceIDs
     * @throws IOException
     *             if info of some file don't found in system file
     */
    private void unskipFiles(DatasetDownloadStatus datasetDownloadStatus,
            Set<String> fileInstanceIDs) throws IOException {
        logger.trace("[IN]  unskipFiles");

        for (FileDownloadStatus fDStatus : datasetDownloadStatus
                .getFilesDownloadStatus()) {
            if (fDStatus.getRecordStatus() == RecordStatus.SKIPPED) {
                if (fileInstanceIDs.contains(fDStatus.getInstanceID())) {
                    datasetDownloadStatus.setFileToDownload(fDStatus);
                }
            }
        }
        logger.trace("[OUT] unskipFiles");
    }

    /**
     *
     * @param sourceFile
     * @param destFile
     * @throws IOException
     */

    private static void copyFile(File sourceFile, File destFile)
            throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(sourceFile);
            os = new FileOutputStream(destFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            is.close();
            os.close();
        }
    }
}
