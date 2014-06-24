package es.unican.meteo.esgf.download;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
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
    private transient ExecutorService downloadExecutor;

    /** Download finish date. */
    private Date downloadFinish;

    /** Download start date. */
    private Date downloadStart;

    /** Instance id of dataset. */
    String instanceID;

    /**
     * Map of "standard" instance_id files to download. The key is the standard
     * instance_id of file (without "_number" in case ".nc_number"
     */
    private Map<String, FileDownloadStatus> mapInstanceIDFileDownload;

    /** Indexed List for files added to download. */
    private transient List<FileDownloadStatus> filesToDownload;

    /** List of dataset observers. */
    private transient LinkedList<DownloadObserver> observers;

    /** File system path. */
    private String path;

    /** Download priority. */
    private DownloadPriority priority;

    /** To specify download status of dataset. */
    private RecordStatus status;

    /** Total file size. */
    private long totalSize;

    /**
     * Empty constructor.
     */
    public DatasetDownloadStatus() {
        logger.trace("[IN]  DatasetDownloadStatus");
        this.observers = new LinkedList<DownloadObserver>();
        this.filesToDownload = new ArrayList<FileDownloadStatus>();
        logger.trace("[OUT] DatasetDownloadStatus");
    }

    /**
     * Constructor.
     * 
     * @param datasetInstanceID
     *            instance_id of dataset to download
     * @param files
     *            map key-value where key is the instance_id of file and value
     *            its size in bytes
     * @param path
     *            path of downloads. If path parameter is null then path =
     *            user.home/ESGF_DATA
     * @param downloadExecutor
     */
    public DatasetDownloadStatus(String datasetInstanceID,
            Map<String, Long> files, Map<String, String> fileNames,
            String path, ExecutorService downloadExecutor) {
        logger.trace("[IN]  DatasetDownloadStatus");

        this.status = RecordStatus.CREATED;
        this.currentSize = 0;
        this.totalSize = 0;
        this.priority = DownloadPriority.MEDIUM;
        this.observers = new LinkedList<DownloadObserver>();
        this.downloadExecutor = downloadExecutor;
        this.instanceID = datasetInstanceID;

        // path attribute
        try {
            if (path == null) {
                this.path = System.getProperty("user.home") + File.separator
                        + DATA_DIRECTORY_NAME + File.separator
                        + doAESGFSyntaxPath();
            } else {
                this.path = path + File.separator + doAESGFSyntaxPath();
                System.out.println(this.path);
            }
        } catch (Exception e) { // if some error occurs
            // path will do with instanceID of dataset
            logger.warn("Error doing the path of dataset: {}", instanceID);
            this.path = System.getProperty("user.home") + File.separator
                    + DATA_DIRECTORY_NAME + File.separator + instanceID;
        }

        // Initialize files map & indexed list
        mapInstanceIDFileDownload = new HashMap<String, FileDownloadStatus>();
        filesToDownload = new ArrayList<FileDownloadStatus>();

        // Fill set of files
        for (Map.Entry<String, Long> entry : files.entrySet()) {

            String fileInstanceID = entry.getKey();

            // some files may not have size metadata. In these cases size=0
            long size = 0;
            if (entry.getValue() != null) {
                size = entry.getValue();
            }
            FileDownloadStatus fileStatus = new FileDownloadStatus(
                    fileInstanceID, size, this, this.path,
                    fileNames.get(fileInstanceID));

            mapInstanceIDFileDownload.put(
                    standardizeESGFFileInstanceID(fileInstanceID), fileStatus);
            filesToDownload.add(fileStatus);
        }
        logger.trace("[OUT] DatasetDownloadStatus");
    }

    /**
     * Decrement current size of the file being downloaded
     * 
     * @param len
     *            size to decrement
     */
    public synchronized void decrementCurrentSize(long len) {
        logger.trace("[IN]  decrementCurrentSize");
        logger.debug("Decrementing current size in {}...", len);
        this.currentSize = this.currentSize - len;
        logger.trace("[OUT] decrementCurrentSize");
    }

    /**
     * Decrement total size of the file being downloaded
     * 
     * @param len
     *            size to decrement
     */
    public synchronized void decrementTotalSize(long len) {
        logger.trace("[IN]  decrementTotalSize");
        logger.debug("Decrementing total size in {}...", len);
        this.totalSize = this.totalSize - len;
        logger.trace("[OUT] decrementTotalSize");
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
        logger.trace("[IN]  doAESGFSyntaxPath");

        logger.debug("Getting dataset {}...", instanceID);
        Dataset dataset;
        try {
            dataset = DownloadManager.getDataset(instanceID);
        } catch (Exception e) {
            logger.error("Error reading of cache dataset: {}", instanceID);
            throw e;
        }

        logger.debug("Creating {} path...", instanceID);
        String path = dataset.getInstanceID(); // init value
        // If contains metadata necessary for CMIP5 DRS format
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

            // Its rare but for now...
            // In ESGF response:
            // project, product, model, etc are arrays.
            // However always have only 1 data.
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
                    + File.separator + "v"
                    + dataset.getMetadata(Metadata.VERSION);
        }

        logger.trace("[OUT] doAESGFSyntaxPath");
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

        logger.debug("Setting dataset {} state to DOWNLOADING", instanceID);
        setRecordStatus(RecordStatus.DOWNLOADING);

        // Configure download
        Dataset dataset = DownloadManager.getDataset(instanceID);
        for (DatasetFile file : dataset.getFiles()) {

            // Get file download status of a file
            FileDownloadStatus fileDownloadStatus = mapInstanceIDFileDownload
                    .get(standardizeESGFFileInstanceID(file.getInstanceID()));

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

        logger.debug("Setting download start date...");
        setDownloadStart(new Date());

        logger.info("Dataset {} has been put to download", instanceID);

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
        logger.trace("[IN]  downloadFile");

        logger.debug("Checking if file {} belongs to dataset",
                fileDownloadStatus.getInstanceID());
        // If fileDownloadStatus isn't in fileMap
        if (!mapInstanceIDFileDownload
                .containsKey(standardizeESGFFileInstanceID(fileDownloadStatus
                        .getInstanceID()))) {
            logger.error("File {} don't belong to dataset",
                    fileDownloadStatus.getInstanceID());
            throw new IllegalArgumentException();
        }

        // Only put to download if file is in correct status
        if (fileDownloadStatus.getRecordStatus() == RecordStatus.CREATED
                || fileDownloadStatus.getRecordStatus() == RecordStatus.PAUSED
                || fileDownloadStatus.getRecordStatus() == RecordStatus.UNAUTHORIZED) {

            logger.debug("Setting dataset {} state to DOWNLOADING", instanceID);
            setRecordStatus(RecordStatus.DOWNLOADING);

            // Configure file to download and get configured file replica.
            // Verify that exist system file assigned to current download in
            // case that the file record state were paused.
            RecordReplica fileReplica = fileDownloadStatus.setToDownload();

            // Select download node
            String dataNode = fileReplica.getDataNode();

            // Add file download to download executor
            downloadExecutor.execute(fileDownloadStatus);
            logger.info("File {} has been put to download",
                    fileDownloadStatus.getInstanceID());
        }

        logger.trace("[OUT] downloadFile");
    }

    /**
     * Get current approximate time to finish the download in milliseconds.
     */
    @Override
    public long getApproximateTimeToFinish() {
        logger.trace("[IN]  getApproximateTimeToFinish");

        // Get actual date
        Date actualDate = new Date();

        // Calculate difference in milliseconds
        // getdate() returns the number of milliseconds since January 1, 1970
        long diffTime = actualDate.getTime() - downloadStart.getTime();

        // Calculate approximate time to download finish
        long millis = (totalSize * diffTime) / currentSize;

        logger.trace("[OUT] getApproximateTimeToFinish");
        return millis;
    }

    /**
     * Get the number of files that are put to download
     * 
     * @return a number
     */
    public int getNumberOfFilesToDownload() {
        return filesToDownload.size();
    }

    /**
     * Get current progress of the download.
     */
    @Override
    public int getCurrentProgress() {
        logger.trace("[IN]  getCurrentProgress");
        int percent = 0;

        if (totalSize > 0) {
            percent = (int) ((currentSize * 100) / totalSize);
        }

        logger.trace("[OUT] getCurrentProgress");
        return percent;
    }

    /**
     * Get Current size of the dataset being download
     * 
     * @return the currentSize
     */
    public long getCurrentSize() {
        logger.trace("[IN]  getCurrentSize");
        logger.trace("[OUT] getCurrentSize");
        return currentSize;
    }

    /**
     * Get download finish date.
     * 
     * @return the downloadFinish
     */
    public Date getDownloadFinish() {
        logger.trace("[IN]  getDownloadFinish");
        logger.trace("[OUT] getDownloadFinish");
        return downloadFinish;
    }

    /**
     * Get download start date.
     * 
     * @return the downloadStart
     */
    public Date getDownloadStart() {
        logger.trace("[IN]  getDownloadStart");
        logger.trace("[OUT] getDownloadStart");
        return downloadStart;
    }

    /**
     * Get set of {@link FileDownloadStatus}.
     * 
     * @return set of file download status
     */
    public Set<FileDownloadStatus> getFilesDownloadStatus() {
        logger.trace("[IN]  getFilesDownloadStatus");
        Set<FileDownloadStatus> fileDownloads;
        fileDownloads = new HashSet<FileDownloadStatus>(
                mapInstanceIDFileDownload.values());

        logger.trace("[OUT] getFilesDownloadStatus");
        return fileDownloads;
    }

    /**
     * @return the instanceID
     */
    public String getInstanceID() {
        logger.trace("[IN]  getInstanceID");
        logger.trace("[OUT] getInstanceID");
        return instanceID;
    }

    /**
     * Get map of instance_id - dataset files to download.
     * 
     * @return the mapInstanceIDFileDownload
     */
    public Map<String, FileDownloadStatus> getMapInstanceIDFileDownload() {
        logger.trace("[IN]  getMapInstanceIDFileDownload");
        logger.trace("[OUT] getMapInstanceIDFileDownload");
        return mapInstanceIDFileDownload;
    }

    /**
     * Get download path of dataset
     * 
     * @return the path
     */
    public String getPath() {
        logger.trace("[IN]  getPath");
        logger.trace("[OUT] getPath");
        return path;
    }

    /**
     * Get download priority
     * 
     * @return the priority of download
     */
    public DownloadPriority getPriority() {
        logger.trace("[IN]  getPriority");
        logger.trace("[OUT] getPriority");
        return priority;
    }

    /**
     * Get dataset download status.
     * 
     * @return the status Enum(created, ready, started, paused, finished and
     *         skipped)
     */
    public RecordStatus getRecordStatus() {
        logger.trace("[IN]  getRecordStatus");
        logger.trace("[OUT] getRecordStatus");
        return status;
    }

    /**
     * Get total size of the dataset
     * 
     * @return the totalSize
     */
    public long getTotalSize() {
        return totalSize;
    }

    /**
     * Increment in len dataset current download. Synchronized because
     * datasetDownloadStatus may be accessed for more than one thread
     * 
     * @param len
     *            size to increment
     */
    public synchronized void increment(long len) {
        logger.trace("[IN]  increment");

        logger.debug("Incrementing current size in {}...", len);
        currentSize = currentSize + len;

        // Notify observers
        notifyDownloadProgressObservers();

        // If all data are download
        if (currentSize >= totalSize) {
            // set download finish date
            setDownloadFinish(new Date());
            // set status of dataset to FINISHED
            setRecordStatus(RecordStatus.FINISHED);
            // notify complete download observers
            notifyDownloadCompletedObservers();
            logger.info("Download of dataset {} is completed", instanceID);
        }

        logger.trace("[OUT] increment");
    }

    /**
     * Increment in increment dataset total download. Synchronized because
     * datasetDownloadStatus may be accessed for more than one thread
     * 
     * @param increment
     *            size to increment
     */
    public synchronized void incrementTotalSize(long increment) {
        logger.trace("[IN]  incrementTotalSize");

        logger.debug("Incrementing total size in {}...", increment);
        totalSize = totalSize + increment;

        logger.trace("[OUT] incrementTotalSize");
    }

    /**
     * Return true if file will be download or false otherwise.
     * 
     * @param file
     * @return true for be downloaded and false to not be downloaded
     */
    public boolean isFileToDownload(FileDownloadStatus file) {
        logger.trace("[IN]  isFileToDownload");

        if (file.getRecordStatus() == RecordStatus.SKIPPED) {
            logger.trace("[OUT] isFileToDownload");
            return false;
        }

        logger.trace("[OUT] isFileToDownload");
        return true;

    }

    /**
     * Call method onFinish() in all observers
     */
    private void notifyDownloadCompletedObservers() {
        logger.trace("[IN]  notifyDownloadCompletedObservers");

        for (DownloadObserver o : observers) {
            o.onDownloadCompleted(this);
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
        }

        logger.trace("[OUT] notifyDownloadErrorObservers");
    }

    /**
     * Call method FileDownloadProgress() in all observers
     */
    private void notifyDownloadProgressObservers() {
        logger.trace("[IN]  notifyDownloadProgressObservers");

        for (DownloadObserver o : observers) {
            o.onDownloadProgress(this);
        }

        logger.trace("[OUT] notifyDownloadProgressObservers");
    }

    /**
     * Pause dataset download. And pause its files, therefore all job files
     * (threads) will be finished.
     */
    @Override
    public void pause() {
        logger.trace("[IN]  pause");

        logger.debug("Setting dataset dowload to pause...");
        // Set new dataset download state
        setRecordStatus(RecordStatus.PAUSED);

        if (getRecordStatus() != RecordStatus.FINISHED) {
            // for each files to download
            for (FileDownloadStatus file : getFilesDownloadStatus()) {
                // pause download files if are in DOWNLOADING status or READY
                if (file.getRecordStatus() == RecordStatus.DOWNLOADING
                        || file.getRecordStatus() == RecordStatus.WAITING) {
                    file.pause();
                }
            }
        }

        logger.trace("[OUT] pause");
    }

    /**
     * Pause a file download.
     * 
     * @param fileDownloadStatus
     *            status of a dataset file download
     * 
     * @throws IllegalArgumentException
     *             if isn't a file of this dataset
     */
    public void pauseFile(FileDownloadStatus fileDownloadStatus) {
        logger.trace("[IN]  pauseFile");

        // If fileDownloadStatus isn't in fileMap
        if (!mapInstanceIDFileDownload
                .containsKey(standardizeESGFFileInstanceID(fileDownloadStatus
                        .getInstanceID()))) {
            logger.error("File {} doesn't belongs to {}. Can't be paused",
                    fileDownloadStatus.getInstanceID(), instanceID);
            throw new IllegalArgumentException();
        }

        // pause download files if are in DOWNLOADING status or WAITING
        if (fileDownloadStatus.getRecordStatus() == RecordStatus.DOWNLOADING
                || fileDownloadStatus.getRecordStatus() == RecordStatus.WAITING) {
            fileDownloadStatus.pause();
        }
        logger.debug("File {} was paused", fileDownloadStatus.getInstanceID());
        logger.trace("[OUT] pauseFile");
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

    /** Reset dataset download. Therefore all files */
    @Override
    public void reset() {
        logger.trace("[IN]  reset");

        logger.debug("Reseting all file downloads of dataset {}", instanceID);
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

        logger.debug("Reseting values of dataset download status");
        setRecordStatus(RecordStatus.CREATED);
        setCurrentSize(0);

        logger.trace("[OUT] reset");
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
        logger.trace("[IN]  resetFile");

        // Check if file belongs to dataset
        if (!mapInstanceIDFileDownload
                .containsKey(standardizeESGFFileInstanceID(fileDownloadStatus
                        .getInstanceID()))) {
            logger.error("File {} doesn't belong to dataset",
                    fileDownloadStatus.getInstanceID());
            throw new IllegalArgumentException();
        }

        logger.debug("Pausing and reseting values of file download status");
        // if file is downloading then pause it
        if (fileDownloadStatus.getRecordStatus() == RecordStatus.DOWNLOADING) {
            fileDownloadStatus.pause();
        }

        decrementCurrentSize(fileDownloadStatus.getCurrentSize());
        fileDownloadStatus.reset();
        if (getRecordStatus() == RecordStatus.FINISHED) {
            setRecordStatus(RecordStatus.DOWNLOADING);
        }

        logger.trace("[OUT] resetFile");
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
            if (path == null) {
                this.path = System.getProperty("user.home") + File.separator
                        + DATA_DIRECTORY_NAME + File.separator
                        + doAESGFSyntaxPath();
            }

            // remove all files to download
            filesToDownload = new ArrayList<FileDownloadStatus>();

            // Fill dataset files
            for (DatasetFile file : DownloadManager.getDataset(instanceID)
                    .getFiles()) {
                if (mapInstanceIDFileDownload
                        .containsKey(standardizeESGFFileInstanceID(file
                                .getInstanceID()))) {

                    // restore FileDownloadStatus
                    mapInstanceIDFileDownload
                            .get(standardizeESGFFileInstanceID(file
                                    .getInstanceID())).restoreDatasetFile(this,
                                    path);

                    FileDownloadStatus fileStatus = mapInstanceIDFileDownload
                            .get(standardizeESGFFileInstanceID(file
                                    .getInstanceID()));

                    if (fileStatus.getRecordStatus() != RecordStatus.SKIPPED) {
                        filesToDownload.add(fileStatus);
                    }
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
        logger.trace("[IN]  setCurrentSize");
        this.currentSize = currentSize;
        logger.trace("[OUT] setCurrentSize");
    }

    /**
     * Set executor that schedules and executes file downloads. Thread pool
     * 
     * @param downloadExecutor
     *            the downloadExecutor to set
     */
    public void setDownloadExecutor(ExecutorService downloadExecutor) {
        logger.trace("[IN]  setDownloadExecutor");
        this.downloadExecutor = downloadExecutor;
        logger.trace("[OUT] setDownloadExecutor");
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
     * @throws IllegalStateException
     *             if dataset download status is in illegal state
     */
    public void setFilesToDownload(Set<String> fileInstaceIDs)
            throws IOException {
        logger.trace("[IN]  setFilesToDownload");

        if (fileInstaceIDs != null) {

            if (getRecordStatus() == RecordStatus.CREATED) {
                logger.debug("Configuring dataset download status in CREATED state");
                // Set all fileDownloadStatus to not download
                for (FileDownloadStatus fDStatus : getFilesDownloadStatus()) {
                    fDStatus.setRecordStatus(RecordStatus.SKIPPED);
                    downloadFile(fDStatus);
                }

                filesToDownload = new ArrayList<FileDownloadStatus>();

                // Configure to download only the status files of files in set
                for (String instanceID : fileInstaceIDs) {
                    FileDownloadStatus fDStatus = mapInstanceIDFileDownload
                            .get(instanceID);
                    fDStatus.setRecordStatus(RecordStatus.CREATED);
                    filesToDownload.add(fDStatus);
                    // sum file size to total size
                    incrementTotalSize(fDStatus.getTotalSize());
                    downloadFile(fDStatus);
                }
            } else if (getRecordStatus() == RecordStatus.DOWNLOADING) {
                logger.debug("Configuring dataset download status in DOWNLOADING state");
                for (String instanceID : fileInstaceIDs) {

                    FileDownloadStatus fDStatus = mapInstanceIDFileDownload
                            .get(instanceID);
                    if (fDStatus.getRecordStatus() == RecordStatus.SKIPPED) {
                        fDStatus.setRecordStatus(RecordStatus.CREATED);
                        filesToDownload.add(fDStatus);
                        // sum file size to total size
                        incrementTotalSize(fDStatus.getTotalSize());
                    }

                    downloadFile(fDStatus);

                }
            } else if (getRecordStatus() == RecordStatus.PAUSED) {
                logger.debug("Configuring dataset download status in PAUSED state");
                for (String instanceID : fileInstaceIDs) {

                    FileDownloadStatus fDStatus = mapInstanceIDFileDownload
                            .get(instanceID);
                    if (fDStatus.getRecordStatus() == RecordStatus.SKIPPED) {
                        fDStatus.setRecordStatus(RecordStatus.CREATED);
                        filesToDownload.add(fDStatus);
                        // sum file size to total size
                        incrementTotalSize(fDStatus.getTotalSize());
                    }
                    downloadFile(fDStatus);
                }
            } else if (getRecordStatus() == RecordStatus.FINISHED) {
                logger.debug("Configuring dataset download status in FINISHED state");

                for (String instanceID : fileInstaceIDs) {

                    boolean newFile = false;
                    FileDownloadStatus fDStatus = mapInstanceIDFileDownload
                            .get(instanceID);
                    if (fDStatus.getRecordStatus() == RecordStatus.SKIPPED) {
                        fDStatus.setRecordStatus(RecordStatus.CREATED);
                        filesToDownload.add(fDStatus);
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
                logger.error("DatasetDownloadStatus is in an illegal state: "
                        + getRecordStatus());
                throw new IllegalStateException(
                        "DatasetDownloadStatus is in an illegal state: "
                                + getRecordStatus());
            }

        } else {
            logger.error("File instance id have a null value");
            throw new NullPointerException();
        }

        logger.trace("[OUT] setFilesToDownload");
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
        logger.trace("[IN]  setFileToDownload");

        // Check if file belongs to dataset
        if (!mapInstanceIDFileDownload
                .containsKey(standardizeESGFFileInstanceID(fDStatus
                        .getInstanceID()))) {
            throw new IllegalArgumentException();
        }

        if (getRecordStatus() == RecordStatus.CREATED) {
            fDStatus.setRecordStatus(RecordStatus.CREATED);

            if (filesToDownload.contains(fDStatus)) {
                filesToDownload.add(fDStatus);
            }

            // sum file size to total size
            incrementTotalSize(fDStatus.getTotalSize());
        } else if (getRecordStatus() == RecordStatus.DOWNLOADING) {
            if (fDStatus.getRecordStatus() == RecordStatus.SKIPPED) {
                fDStatus.setRecordStatus(RecordStatus.CREATED);
                filesToDownload.add(fDStatus);
                // sum file size to total size
                incrementTotalSize(fDStatus.getTotalSize());
            }
        } else if (getRecordStatus() == RecordStatus.PAUSED) {

            if (fDStatus.getRecordStatus() == RecordStatus.SKIPPED) {
                fDStatus.setRecordStatus(RecordStatus.CREATED);
                filesToDownload.add(fDStatus);
                // sum file size to total size
                incrementTotalSize(fDStatus.getTotalSize());
            }
        } else { // FINISHED {
            if (fDStatus.getRecordStatus() == RecordStatus.SKIPPED) {
                fDStatus.setRecordStatus(RecordStatus.CREATED);
                filesToDownload.add(fDStatus);
                // sum file size to total size
                incrementTotalSize(fDStatus.getTotalSize());
                // Change FINISHED State if find a new file to download
                setRecordStatus(RecordStatus.DOWNLOADING);
            }

        }

        downloadFile(fDStatus);
        logger.debug("File {} was put to download");

        logger.trace("[OUT] setFileToDownload");
    }

    /**
     * @param instanceID
     *            the instanceID to set
     */
    public void setInstanceID(String instanceID) {
        logger.trace("[IN]  setInstanceID");
        this.instanceID = instanceID;
        logger.trace("[OUT] setInstanceID");
    }

    /**
     * Set map of instance_id - dataset files to download.
     * 
     * @param mapInstanceIDFileDownload
     *            the mapInstanceIDFileDownload to set
     */
    public void setMapInstanceIDFileDownload(
            Map<String, FileDownloadStatus> mapInstanceIDFileDownload) {
        logger.trace("[IN]  setMapInstanceIDFileDownload");
        this.mapInstanceIDFileDownload = mapInstanceIDFileDownload;
        logger.trace("[OUT] setMapInstanceIDFileDownload");
    }

    /**
     * Set dataset path
     * 
     * @param path
     *            the path to set
     */
    public void setPath(String path) {
        logger.trace("[IN]  setPath");
        this.path = path;
        logger.trace("[OUT] setPath");
    }

    /**
     * Set download priority
     * 
     * @param priority
     *            the priority to set
     */
    public void setPriority(DownloadPriority priority) {
        logger.trace("[IN]  setPriority");
        this.priority = priority;
        logger.trace("[OUT] setPriority");
    }

    /**
     * Set record status.
     * 
     * @param downloadStatus
     *            the new status
     */
    public void setRecordStatus(RecordStatus downloadStatus) {
        logger.trace("[IN]  setRecordStatus");
        status = downloadStatus;
        logger.trace("[OUT] setRecordStatus");
    }

    /**
     * Set record status
     * 
     * @param status
     *            the status to set
     */
    public void setStatus(RecordStatus status) {
        logger.trace("[IN]  setStatus");
        this.status = status;
        logger.trace("[OUT] setStatus");
    }

    /**
     * Set total size of the file
     */
    public void setTotalSize(long size) {
        logger.trace("[IN]  setTotalSize");
        this.totalSize = size;
        logger.trace("[OUT] setTotalSize");
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
        logger.trace("[IN]  skipFile");

        // Check if file status is already skipped
        if (fileStatus.getRecordStatus() == RecordStatus.SKIPPED) {
            logger.error("File {} already skipped", fileStatus.getInstanceID());
            throw new IllegalStateException();
        }

        logger.debug("Skipping file {}", fileStatus.getInstanceID());
        RecordStatus oldStatus = fileStatus.getRecordStatus();

        if (fileStatus.getRecordStatus() != RecordStatus.CREATED) {
            // remove size of current and total size of
            // datasetDownloadStatus
            fileStatus.setRecordStatus(RecordStatus.SKIPPED);
            filesToDownload.remove(fileStatus);
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

        logger.trace("[OUT] skipFile");
    }

    /**
     * Verify if instance ID of ESGF file is correct and if id is corrupted then
     * it corrects the id (avoid ".nc_number" issue in instance id of files)
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
     * Overwrites read object of object serialization
     * 
     * @param is
     */
    private void readObject(ObjectInputStream is) throws IOException,
            ClassNotFoundException {

        // default read object
        is.defaultReadObject();

        // quit null values in some transient attributes
        this.observers = new LinkedList<DownloadObserver>();
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

    /**
     * Get a indexed list of files that are put to download
     * 
     * @return the filesToDownload
     */
    public List<FileDownloadStatus> getFilesToDownload() {
        return filesToDownload;
    }
}
