package es.unican.meteo.esgf.download;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import es.unican.meteo.esgf.search.Dataset;
import es.unican.meteo.esgf.search.DatasetFile;
import es.unican.meteo.esgf.search.Metadata;
import es.unican.meteo.esgf.search.RecordReplica;


/**
 * Representation of status of dataset download.
 * 
 * @author Karem Terry
 * 
 */
public class DatasetDownloadStatus implements Download, Serializable {

    /** Logger. */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(DatasetDownloadStatus.class);

    /** Predetermined directory of downloads. */
    private static final String DATA_DIRECTORY_NAME = "ESGF_DATA";

    /** Current size of the file being downloaded. */
    private long currentSize;

    /** Executor that schedules and executes file downloads. Thread pool. */
    private ExecutorService downloadExecutor;

    /** Download finish date. */
    private Date downloadFinish;

    /** Download start date. */
    private Date downloadStart;

    /** Instance id of dataset. */
    String instanceID;

    /** Map of instance_id-dataset files to download. */
    private Map<String, FileDownloadStatus> mapInstanceIDFileDownload;

    /** List of dataset observers. */
    private LinkedList<DownloadObserver> observers;

    /** File system path. */
    private String path;

    /** Download priority. */
    private DownloadPriority priority;

    /**
     * Record status.
     */
    private RecordStatus status;

    /** Total file size. */
    private long totalSize;

    /**
     * Empty constructor.
     */
    public DatasetDownloadStatus() {
        this.observers = new LinkedList<DownloadObserver>();
    }

    /**
     * Constructor.
     * 
     * @param datasetInstanceID
     *            instance_id of dataset to download
     * @param files
     *            map key-value where key is the instance_id of file and value
     *            its size in bytes
     * @param downloadExecutor
     */
    public DatasetDownloadStatus(String datasetInstanceID,
            Map<String, Long> files, ExecutorService downloadExecutor) {
        this.status = RecordStatus.CREATED;
        this.currentSize = 0;
        this.totalSize = 0;
        this.priority = DownloadPriority.MEDIUM;
        this.observers = new LinkedList<DownloadObserver>();
        this.downloadExecutor = downloadExecutor;
        this.instanceID = datasetInstanceID;

        // drs or id

        try {
            this.path = System.getProperty("user.home") + File.separator
                    + DATA_DIRECTORY_NAME + File.separator
                    + doAESGFSyntaxPath();
        } catch (Exception e) { // if some error occurs
            // path will do with instanceID of dataset
            logger.warn("Error doing the path of dataset: {}", instanceID);
            this.path = System.getProperty("user.home") + File.separator
                    + DATA_DIRECTORY_NAME + File.separator + instanceID;
        }

        // Initialize files map
        mapInstanceIDFileDownload = new HashMap<String, FileDownloadStatus>();

        // Fill set of files
        for (Map.Entry<String, Long> entry : files.entrySet()) {

            String fileInstanceID = entry.getKey();
            long size = entry.getValue();
            mapInstanceIDFileDownload.put(fileInstanceID,
                    new FileDownloadStatus(fileInstanceID, size, this, path));
        }

    }

    public synchronized void decrementCurrentSize(long len) {
        this.currentSize = this.currentSize - len;
    }

    public synchronized void decrementTotalSize(long len) {
        this.totalSize = this.totalSize - len;
    }

    /**
     * Create a String with ESGF Syntax for datasets indicates in CMIP5 Data
     * Reference Syntax (DRS) if it is posible (have required metadata), or
     * otherwise returns string with instance id
     * 
     * <p>
     * DRS syntax:
     * </p>
     * <i>
     * 
     * <pre>
     * activity(project) / product / intitute / model / experimente / frequency
     *         / realm / MIPtable / ensemble / version
     * </pre>
     * 
     * </b>
     * 
     * @return string with drs format if dataset have the metadata necessary, or
     *         otherwise returns string with instance id
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    private String doAESGFSyntaxPath() throws Exception {

        Dataset dataset;
        try {
            dataset = DownloadManager.getDataset(instanceID);
        } catch (Exception e) {
            logger.error("Error reading of cache dataset: {}", instanceID);
            throw e;
        }

        String path = dataset.getInstanceID(); // init value
        // If contains metadata necessary for DRS format
        if (dataset.contains(Metadata.PROJECT)
                & dataset.contains(Metadata.PRODUCT)
                & dataset.contains(Metadata.INSTITUTE)
                & dataset.contains(Metadata.MODEL)
                & dataset.contains(Metadata.EXPERIMENT)
                & dataset.contains(Metadata.TIME_FREQUENCY)
                & dataset.contains(Metadata.REALM)
                & dataset.contains(Metadata.CMOR_TABLE)
                & dataset.contains(Metadata.ENSEMBLE)
                & dataset.contains(Metadata.VERSION)) {

            // TODO Its rare but for now like in ESGF response, project,
            // product, model, etc are arrays, but always have only 1 data.
            // Version are string

            path = ""
                    + ((LinkedList<String>) dataset
                            .getMetadata(Metadata.PROJECT)).get(0)
                    + File.separator
                    + ((LinkedList<String>) dataset
                            .getMetadata(Metadata.PRODUCT)).get(0)
                    + File.separator
                    + ((LinkedList<String>) dataset
                            .getMetadata(Metadata.INSTITUTE)).get(0)
                    + File.separator
                    + ((LinkedList<String>) dataset.getMetadata(Metadata.MODEL))
                            .get(0)
                    + File.separator
                    + ((LinkedList<String>) dataset
                            .getMetadata(Metadata.EXPERIMENT)).get(0)
                    + File.separator
                    + ((LinkedList<String>) dataset
                            .getMetadata(Metadata.TIME_FREQUENCY)).get(0)
                    + File.separator
                    + ((LinkedList<String>) dataset.getMetadata(Metadata.REALM))
                            .get(0)
                    + File.separator
                    + ((LinkedList<String>) dataset
                            .getMetadata(Metadata.CMOR_TABLE)).get(0)
                    + File.separator
                    + ((LinkedList<String>) dataset
                            .getMetadata(Metadata.ENSEMBLE)).get(0)
                    + File.separator + dataset.getMetadata(Metadata.VERSION);
        }
        return path;
    }

    /**
     * Start or restart dataset download. Begin or restart all files CREATED,
     * PAUSED OR UNAUTHORIZED of dataset
     * 
     * @throws IOException
     *             if some error happens when dataset has been obtained from
     *             file system or don't exists
     */
    @Override
    public void download() throws IOException {
        logger.trace("[IN]  download");

        // Set new download state
        setRecordStatus(RecordStatus.DOWNLOADING);

        Dataset dataset = DownloadManager.getDataset(instanceID);

        for (DatasetFile file : dataset.getFiles()) {

            // get file download status of a file
            FileDownloadStatus fileDownloadStatus = mapInstanceIDFileDownload
                    .get(file.getInstanceID());

            // Start download all files,only with file status is created or
            // paused. Add dataset files to executor and to map of dataset-file
            // downloads
            if (fileDownloadStatus.getRecordStatus() == RecordStatus.CREATED
                    || fileDownloadStatus.getRecordStatus() == RecordStatus.PAUSED
                    || fileDownloadStatus.getRecordStatus() == RecordStatus.UNAUTHORIZED) {

                // Configure file to download and get configured file replica.
                // Verify that exist system file assigned to current download in
                // case that the file record state were paused.
                RecordReplica fileReplica = fileDownloadStatus
                        .setToDownload(file);

                // Select download node
                String dataNode = fileReplica.getDataNode();

                // Add file download to download executor
                downloadExecutor.execute(fileDownloadStatus);
            }
        }

        // Set download start date
        setDownloadStart(new Date());

        logger.trace("[OUT] download");
    }

    /**
     * Try to download file if CREATED, PAUSED OR UNAUTHORIZED.
     * 
     * @param fileDownloadStatus
     * @throws IOException
     *             when info of file can't be loaded from file system
     * 
     * @throws IllegalArgumentException
     *             if isn't a file of this dataset
     */
    public void downloadFile(FileDownloadStatus fileDownloadStatus)
            throws IOException {

        // If fileDownloadStatus isn't in fileMap
        if (!mapInstanceIDFileDownload.containsKey(fileDownloadStatus
                .getInstanceID())) {
            throw new IllegalArgumentException();
        }

        if (fileDownloadStatus.getRecordStatus() == RecordStatus.CREATED
                || fileDownloadStatus.getRecordStatus() == RecordStatus.PAUSED
                || fileDownloadStatus.getRecordStatus() == RecordStatus.UNAUTHORIZED) {

            // Configure file to download and get configured file replica.
            // Verify that exist system file assigned to current download in
            // case that the file record state were paused.
            RecordReplica fileReplica = fileDownloadStatus.setToDownload();

            // Select download node
            String dataNode = fileReplica.getDataNode();

            // Add file download to download executor
            downloadExecutor.execute(fileDownloadStatus);
        }
    }

    /**
     * Get current approximate time to finish the download in milliseconds.
     */
    @Override
    public long getApproximateTimeToFinish() {

        // Get actual date
        Date actualDate = new Date();

        // Calculate difference in milliseconds
        // getdate() returns the number of milliseconds since January 1, 1970
        long diffTime = actualDate.getTime() - downloadStart.getTime();

        // Calculate approximate time to download finish
        long millis = (totalSize * diffTime) / currentSize;

        return millis;

    }

    /**
     * Get current progress of the download.
     */
    @Override
    public int getCurrentProgress() {
        int percent = 0;

        if (totalSize > 0) {
            percent = (int) ((currentSize * 100) / totalSize);
        }

        return percent;
    }

    /**
     * Get Current size of the file being download
     * 
     * @return the currentSize
     */
    public long getCurrentSize() {
        return currentSize;
    }

    /**
     * Get download finish date.
     * 
     * @return the downloadFinish
     */
    public Date getDownloadFinish() {
        return downloadFinish;
    }

    /**
     * Get download start date.
     * 
     * @return the downloadStart
     */
    public Date getDownloadStart() {
        return downloadStart;
    }

    /**
     * Get set of {@link FileDownloadStatus}.
     * 
     * @return set of file download status
     */
    public Set<FileDownloadStatus> getFilesDownloadStatus() {

        Set<FileDownloadStatus> fileDownloads;
        fileDownloads = new HashSet<FileDownloadStatus>(
                mapInstanceIDFileDownload.values());

        return fileDownloads;
    }

    /**
     * @return the instanceID
     */
    public String getInstanceID() {
        return instanceID;
    }

    /**
     * Get map of instance_id - dataset files to download.
     * 
     * @return the mapInstanceIDFileDownload
     */
    public Map<String, FileDownloadStatus> getMapInstanceIDFileDownload() {
        return mapInstanceIDFileDownload;
    }

    /**
     * Get dataset path
     * 
     * @return the path
     */
    public String getPath() {
        return path;
    }

    /**
     * Get download priority
     * 
     * @return the priority of download
     */
    public DownloadPriority getPriority() {
        return priority;
    }

    /**
     * Get record status.
     * 
     * @return the status Enum(created, ready, started, paused, finished and
     *         skipped)
     */
    public RecordStatus getRecordStatus() {
        return status;
    }

    /**
     * Get record status
     * 
     * @return the status
     */
    public RecordStatus getStatus() {
        return status;
    }

    /**
     * Get total size of the file
     * 
     * @return the totalSize
     */
    public long getTotalSize() {
        return totalSize;
    }

    /**
     * Increment in len dataset current download. Synchronized because
     * datasetDownloadStatus may be accessed for more than one thread
     */
    public synchronized void increment(long len) {
        currentSize = currentSize + len;
        notifyDownloadProgressObservers();

        // If all data are download
        if (currentSize >= totalSize) {
            // set download finish date
            setDownloadFinish(new Date());
            // set status of dataset to FINISHED
            setRecordStatus(RecordStatus.FINISHED);
            // notify complete download observers
            notifyDownloadCompletedObservers();
        }

    }

    public synchronized void incrementTotalSize(long increment) {
        totalSize = totalSize + increment;
    }

    /**
     * Return true if file will be download or false otherwise.
     * 
     * @param file
     * @return true for be downloaded and false to not be downloaded
     */
    public boolean isFileToDownload(FileDownloadStatus file) {

        if (file.getRecordStatus() == RecordStatus.SKIPPED) {
            return false;
        }

        return true;

    }

    /**
     * Call method onFinish() in all observers
     */
    private void notifyDownloadCompletedObservers() {
        for (DownloadObserver o : observers) {
            o.onDownloadCompleted(this);
        }
    }

    /**
     * Call method onError() in all observers
     */
    private void notifyDownloadErrorObservers() {
        for (DownloadObserver o : observers) {
            o.onError(this);
        }
    }

    /**
     * Call method FileDownloadProgress() in all observers
     */
    private void notifyDownloadProgressObservers() {
        for (DownloadObserver o : observers) {
            o.onDownloadProgress(this);
        }
    }

    /**
     * Pause dataset download. And pause its files, therefore all job files
     * (threads) will be finished.
     */
    @Override
    public void pause() {

        // Set new dataset download state
        setRecordStatus(RecordStatus.PAUSED);

        if (getRecordStatus() != RecordStatus.FINISHED) {
            // for each files to download
            for (FileDownloadStatus file : getFilesDownloadStatus()) {
                // pause download files if are in DOWNLOADING status or READY
                if (file.getRecordStatus() == RecordStatus.DOWNLOADING
                        || file.getRecordStatus() == RecordStatus.READY) {
                    // pauseDownload
                    file.pause();
                }
            }
        }

    }

    /**
     * Pause a file download.
     * 
     * @param fileDownloadStatus
     * 
     * @throws IllegalArgumentException
     *             if isn't a file of this dataset
     */
    public void pauseFile(FileDownloadStatus fileDownloadStatus) {
        // If fileDownloadStatus isn't in fileMap
        if (!mapInstanceIDFileDownload.containsKey(fileDownloadStatus
                .getInstanceID())) {
            throw new IllegalArgumentException();
        }

        fileDownloadStatus.pause();

    }

    /**
     * Put to download all files that record status are UNAUTHORIZED
     * 
     * @throws IOException
     *             if info of file don't found in file system
     */
    public void putToDownloadUnauthorizedFiles() throws IOException {
        logger.trace("[IN]  putToDownloadUnauthorizedFiles");

        logger.debug("Putting to download all unauthorized files..");
        for (Map.Entry<String, FileDownloadStatus> entry : mapInstanceIDFileDownload
                .entrySet()) {
            FileDownloadStatus fileStatus = entry.getValue();

            if (fileStatus.getRecordStatus() == RecordStatus.UNAUTHORIZED) {
                downloadFile(fileStatus);
            }
        }

        logger.trace("[OUT] putToDownloadUnauthorizedFiles");
    }

    /**
     * Register new object that implement observer for this dataset download
     * status.
     * 
     * @param observer
     */
    public void registerObserver(DownloadObserver observer) {
        observers.add(observer);
    }

    /** Reset dataset download. Therefore all datasetFiles */
    @Override
    public void reset() {
        // reset all file download status
        for (FileDownloadStatus file : getFilesDownloadStatus()) {

            // if file is downloading then pause it
            if (file.getRecordStatus() == RecordStatus.DOWNLOADING) {
                file.pause();
            }
            if (file.getRecordStatus() != RecordStatus.SKIPPED) {
                // reset this file download
                file.reset();
            }
        }

        setRecordStatus(RecordStatus.CREATED);
        setCurrentSize(0);
    }

    /**
     * Reset {@link FileDownloadStatus}
     * 
     * @param fileDownloadStatus
     *            whose download will be reset
     * @throws IllegalArgumentException
     *             if isn't a file of this dataset
     */
    public void resetFile(FileDownloadStatus fileDownloadStatus) {
        // If fileDownloadStatus isn't in fileMap
        if (!mapInstanceIDFileDownload.containsKey(fileDownloadStatus
                .getInstanceID())) {
            throw new IllegalArgumentException();
        }

        // if file is downloading then pause it
        if (fileDownloadStatus.getRecordStatus() == RecordStatus.DOWNLOADING) {
            fileDownloadStatus.pause();
        }

        decrementCurrentSize(fileDownloadStatus.getCurrentSize());
        fileDownloadStatus.reset();
        if (getRecordStatus() == RecordStatus.FINISHED) {
            setRecordStatus(RecordStatus.DOWNLOADING);
        }

    }

    /**
     * Restore datasets and files in dataset download status and file download
     * status from local restartable data by EHCache
     * 
     * @throws IOException
     *             if dataset isn't in cache
     */
    public void restoreData() throws IOException {
        logger.trace("[IN]  restoreData");

        try {
            // Create path
            this.path = System.getProperty("user.home") + File.separator
                    + DATA_DIRECTORY_NAME + File.separator
                    + doAESGFSyntaxPath();

            // Fill dataset files
            for (DatasetFile file : DownloadManager.getDataset(instanceID)
                    .getFiles()) {
                if (mapInstanceIDFileDownload.containsKey(file.getInstanceID())) {
                    mapInstanceIDFileDownload.get(file.getInstanceID())
                            .restoreDatasetFile(this, path);
                }
            }

            logger.debug("Dataset {} find in cache", instanceID);

        } catch (Exception e) {
            logger.error("Saved info of datasets in download manager can't be loaded");
            throw new IOException("dataset isn't in cache" + instanceID);
        }
        logger.trace("[OUT] restoreData");
    }

    /**
     * Set Current size of the file being download
     */
    public void setCurrentSize(long currentSize) {
        this.currentSize = currentSize;
    }

    /**
     * Set executor that schedules and executes file downloads. Thread pool
     * 
     * @param downloadExecutor
     *            the downloadExecutor to set
     */
    public void setDownloadExecutor(ExecutorService downloadExecutor) {
        this.downloadExecutor = downloadExecutor;
    }

    /**
     * Set download finish date.
     * 
     * @param downloadFinish
     *            the downloadFinish to set
     */
    public void setDownloadFinish(Date downloadFinish) {
        this.downloadFinish = downloadFinish;
    }

    /**
     * Set download start date.
     * 
     * @param downloadStart
     *            the downloadStart to set
     */
    public void setDownloadStart(Date downloadStart) {
        this.downloadStart = downloadStart;
    }

    /**
     * Select a file set to download.
     * 
     * @param fileInstaceIDs
     *            set of file_instanceIDs of {@link DatasetFile}
     * @throws IOException
     *             if info of some file don't found in system file
     * @throws NullPointerException
     *             if fileInstaceIDs is null
     * @throws IllegalArgumentException
     *             if some instance id of set doesn't belong to the dataset
     */
    public void setFilesToDownload(Set<String> fileInstaceIDs)
            throws IOException {

        if (!mapInstanceIDFileDownload.keySet().containsAll(fileInstaceIDs)) {
            throw new IllegalArgumentException(
                    "Some instance id of set doesn't belong to the dataset");
        }

        if (fileInstaceIDs != null) {

            if (getRecordStatus() == RecordStatus.CREATED) {
                // Set all fileDownloadStatus to not download
                for (FileDownloadStatus fDStatus : getFilesDownloadStatus()) {
                    fDStatus.setRecordStatus(RecordStatus.SKIPPED);
                    downloadFile(fDStatus);
                }

                // Configure to download only the status files of files in set
                for (String instanceID : fileInstaceIDs) {
                    FileDownloadStatus fDStatus = mapInstanceIDFileDownload
                            .get(instanceID);
                    fDStatus.setRecordStatus(RecordStatus.CREATED);
                    // sum file size to total size
                    incrementTotalSize(fDStatus.getTotalSize());
                    downloadFile(fDStatus);
                }
            } else if (getRecordStatus() == RecordStatus.DOWNLOADING) {
                for (String instanceID : fileInstaceIDs) {

                    FileDownloadStatus fDStatus = mapInstanceIDFileDownload
                            .get(instanceID);
                    if (fDStatus.getRecordStatus() == RecordStatus.SKIPPED) {
                        fDStatus.setRecordStatus(RecordStatus.CREATED);
                        // sum file size to total size
                        incrementTotalSize(fDStatus.getTotalSize());
                    }

                    downloadFile(fDStatus);

                }
            } else if (getRecordStatus() == RecordStatus.PAUSED) {
                for (String instanceID : fileInstaceIDs) {

                    FileDownloadStatus fDStatus = mapInstanceIDFileDownload
                            .get(instanceID);
                    if (fDStatus.getRecordStatus() == RecordStatus.SKIPPED) {
                        fDStatus.setRecordStatus(RecordStatus.CREATED);
                        // sum file size to total size
                        incrementTotalSize(fDStatus.getTotalSize());
                    }
                    downloadFile(fDStatus);
                }
            } else if (getRecordStatus() == RecordStatus.FINISHED) {

                for (String instanceID : fileInstaceIDs) {

                    boolean newFile = false;
                    FileDownloadStatus fDStatus = mapInstanceIDFileDownload
                            .get(instanceID);
                    if (fDStatus.getRecordStatus() == RecordStatus.SKIPPED) {
                        fDStatus.setRecordStatus(RecordStatus.CREATED);
                        // sum file size to total size
                        incrementTotalSize(fDStatus.getTotalSize());
                        newFile = true;
                    }
                    downloadFile(fDStatus);

                    // Change FINISHED State if find a new file to download
                    if (newFile) {
                        setRecordStatus(RecordStatus.DOWNLOADING);
                    }

                }
            } else {
                throw new IllegalStateException(
                        "DatasetDownloadStatus is in an illegal state: "
                                + getRecordStatus());
            }

        } else {
            throw new NullPointerException();
        }
    }

    /**
     * Select a file in any {@link RecordStatus} to be download. download.
     * 
     * @param fDStatus
     * @throws IOException
     *             if some info of file don't found in system file
     * 
     * @throws IllegalArgumentException
     *             if fDStatus doesn't belong to the dataset
     * 
     */
    public void setFileToDownload(FileDownloadStatus fDStatus)
            throws IOException {

        if (!mapInstanceIDFileDownload.containsKey(fDStatus.getInstanceID())) {
            throw new IllegalArgumentException();
        }

        if (getRecordStatus() == RecordStatus.CREATED) {
            fDStatus.setRecordStatus(RecordStatus.CREATED);
            // sum file size to total size
            incrementTotalSize(fDStatus.getTotalSize());
        } else if (getRecordStatus() == RecordStatus.DOWNLOADING) {
            if (fDStatus.getRecordStatus() == RecordStatus.SKIPPED) {
                fDStatus.setRecordStatus(RecordStatus.CREATED);
                // sum file size to total size
                incrementTotalSize(fDStatus.getTotalSize());
            }
        } else if (getRecordStatus() == RecordStatus.PAUSED) {

            if (fDStatus.getRecordStatus() == RecordStatus.SKIPPED) {
                fDStatus.setRecordStatus(RecordStatus.CREATED);
                // sum file size to total size
                incrementTotalSize(fDStatus.getTotalSize());
            }
        } else { // FINISHED {
            if (fDStatus.getRecordStatus() == RecordStatus.SKIPPED) {
                fDStatus.setRecordStatus(RecordStatus.CREATED);
                // sum file size to total size
                incrementTotalSize(fDStatus.getTotalSize());
                // Change FINISHED State if find a new file to download
                setRecordStatus(RecordStatus.DOWNLOADING);
            }

        }

        downloadFile(fDStatus);

    }

    /**
     * @param instanceID
     *            the instanceID to set
     */
    public void setInstanceID(String instanceID) {
        this.instanceID = instanceID;
    }

    /**
     * Set map of instance_id - dataset files to download.
     * 
     * @param mapInstanceIDFileDownload
     *            the mapInstanceIDFileDownload to set
     */
    public void setMapInstanceIDFileDownload(
            Map<String, FileDownloadStatus> mapInstanceIDFileDownload) {
        this.mapInstanceIDFileDownload = mapInstanceIDFileDownload;
    }

    /**
     * Set dataset path
     * 
     * @param path
     *            the path to set
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Set download priority
     * 
     * @param priority
     *            the priority to set
     */
    public void setPriority(DownloadPriority priority) {
        this.priority = priority;
    }

    /**
     * Set record status.
     * 
     * @param downloadStatus
     *            the new status
     */
    public void setRecordStatus(RecordStatus downloadStatus) {
        status = downloadStatus;
    }

    /**
     * Set record status
     * 
     * @param status
     *            the status to set
     */
    public void setStatus(RecordStatus status) {
        this.status = status;
    }

    /**
     * Set total size of the file
     */
    public void setTotalSize(long size) {
        this.totalSize = size;
    }

    /**
     * Skip download of file
     * 
     * @param fileStatus
     * 
     * @throws IllegalStateException
     *             if file status is skipped
     */
    public void skipFile(FileDownloadStatus fileStatus)
            throws IllegalStateException {
        if (fileStatus.getRecordStatus() == RecordStatus.SKIPPED) {
            throw new IllegalStateException();
        }

        RecordStatus oldStatus = fileStatus.getRecordStatus();

        if (fileStatus.getRecordStatus() != RecordStatus.CREATED) {
            // remove size of current and total size of
            // datasetDownloadStatus
            fileStatus.setRecordStatus(RecordStatus.SKIPPED);
            decrementCurrentSize(fileStatus.getCurrentSize());
            decrementTotalSize(fileStatus.getTotalSize());
            fileStatus.setCurrentSize(0);
        } else {
            decrementTotalSize(fileStatus.getTotalSize());
        }

        // if old file status isn't complete then reset (remove file in system)
        if (oldStatus != RecordStatus.FINISHED) {
            if (oldStatus != RecordStatus.CHECKSUM_FAILED) {
                fileStatus.reset();
                // set skipped another time because reset put in status created
                fileStatus.setRecordStatus(RecordStatus.SKIPPED);
            }
        }

        // if dataset hasn't finished now It could be finished
        if (getRecordStatus() != RecordStatus.FINISHED) {
            if (getCurrentSize() == getTotalSize()) {
                setRecordStatus(RecordStatus.FINISHED);
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "DatasetDownloadStatus [currentSize=" + currentSize + ", path="
                + path + ", status=" + status + ", totalSize=" + totalSize
                + "]";
    }
}
