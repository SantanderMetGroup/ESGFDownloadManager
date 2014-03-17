package es.unican.meteo.esgf.download;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import es.unican.meteo.esgf.petition.CredentialsManager;
import es.unican.meteo.esgf.search.DatasetFile;
import es.unican.meteo.esgf.search.Metadata;
import es.unican.meteo.esgf.search.RecordReplica;
import es.unican.meteo.esgf.search.Service;

/**
 * Status of a dataset file download. Also, implements {@link Runnable} to
 * download the {@link DatasetFile} in separated thread.
 * 
 * @author Karem Terry
 * 
 */
public class FileDownloadStatus implements Runnable, Download, Serializable {

    /** Logger. */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(FileDownloadStatus.class);

    /** Bytes that are requested in each petition of download. */
    private static final int BYTES_FOR_EACH_PETITION = 32768; // 32K
    // private static final int BYTES_FOR_EACH_PETITION = 131072; // 128K

    /**
     * Current file replica configured to download file.
     */
    private RecordReplica currentFileReplica;

    /**
     * Instance_id of file. Id of a specific file in ESGF
     */
    private String instanceID;

    /** Current size of the file being downloaded. */
    private long currentSize;

    /** Reference to dataset download status that the file belongs. */
    private DatasetDownloadStatus datasetDownloadStatus;

    /** Download finish date. */
    private Date downloadFinish;

    /** Download start date. */
    private Date downloadStart;

    /** System file. */
    private File file;

    /** List of file observers. */
    private LinkedList<DownloadObserver> observers;

    /** Download priority. */
    private DownloadPriority priority;

    /** Record status, to specify download status */
    private RecordStatus status;

    /** Total file size. */
    private long totalSize;

    /** Checksum. */
    private String checksum;

    /** Checksum Algorithm. */
    private ChecksumType checksumType;

    /**
     * Constructor
     */
    public FileDownloadStatus() {
        logger.trace("[IN]  FileDownloadStatus");
        // Initialize observers
        observers = new LinkedList<DownloadObserver>();
        logger.trace("[OUT] FileDownloadStatus");
    }

    /**
     * Constructor
     */
    public FileDownloadStatus(String fileInstanceID, long size,
            DatasetDownloadStatus datasetDownloadStatus,
            String dataDirectoryPath) {
        logger.trace("[IN]  FileDownloadStatus");

        this.instanceID = fileInstanceID;
        this.datasetDownloadStatus = datasetDownloadStatus;
        this.status = RecordStatus.CREATED;
        this.currentSize = 0;
        this.totalSize = size;
        this.priority = DownloadPriority.MEDIUM;

        // XXX name file drs_id o id??
        this.file = new File(dataDirectoryPath + File.separator + instanceID);

        // Initialize observers
        observers = new LinkedList<DownloadObserver>();

        logger.trace("[OUT] FileDownloadStatus");
    }

    /**
     * Return true if observer {@link DownloadObserver} is registered for this
     * file and false otherwise
     * 
     * @param observer
     * @return true if observer is registered for this file and false otherwise
     */
    public boolean containsObserver(DownloadObserver observer) {
        logger.trace("[IN]  containsObserver");

        boolean find = false;

        // search observer in list of observers
        for (DownloadObserver dObserver : observers) {
            if (dObserver == observer) {
                find = true;
                logger.debug("Observer are already in list of observers");
            }
        }

        logger.trace("[OUT] containsObserver");
        return find;
    }

    /**
     * Private method. All logic of a file download.
     */
    @Override
    public void download() throws IOException {
        logger.trace("[IN]  download");

        logger.debug("Checking if state of file is READY...");
        if (getRecordStatus() != RecordStatus.READY) {

            // if download is in state PAUSE do nothing
            if (getRecordStatus() == RecordStatus.PAUSED) {
                if (datasetDownloadStatus.getRecordStatus() == RecordStatus.PAUSED) {
                    logger.trace("[OUT] download");
                    return;
                }
            } else {
                logger.error(
                        "Illegal state for file download. File {} are in {} state",
                        instanceID, getRecordStatus());
                throw new IllegalStateException();
            }
        }

        logger.debug("Setting download status of file to DOWNLOADING..");
        setRecordStatus(RecordStatus.DOWNLOADING);

        logger.debug("Checking state of download of file in system...");
        // Check if file has resumed to download or are a new download
        boolean resumed = false;
        if (getCurrentSize() > 0) {
            resumed = true;
        }

        // verifies that parent path name be a system directory or isn't
        // null. If not, creates the directory named by pathname
        final File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs();
        }

        // If is a new download
        if (!resumed) {
            logger.debug("Checking if file already download."
                    + " And in that case validate file with checksum");
            // If file exits, check if its valid (removed an re-added in
            // download list)
            if (file.exists()) {
                // if haven't checksum then always preset not valid
                if (checksum != null && checksumType != null) {

                    // Check if download file isn't corrupted
                    boolean valid = validateChecksum(checksum, checksumType);

                    if (valid) {
                        // set file download status to FINISHED
                        setRecordStatus(RecordStatus.FINISHED);
                        currentSize = totalSize;

                        // set download finish date
                        setDownloadFinish(new Date());

                        // notify download completed
                        notifyDownloadCompletedObservers();

                        // increment dataset size
                        datasetDownloadStatus.increment(file.length());
                        logger.debug("File {} already downloaded", instanceID);
                        logger.trace("[OUT] download");
                        return;
                    }
                }

            }

            logger.debug("Creating new system file...");
            // if file is not valid or in't exist
            // create new empty file system, if the named file already exists do
            // nothing
            file.createNewFile();
        }

        logger.debug("Configuring connection to download {} file", instanceID);
        // output stream of the file
        FileOutputStream fos = null;
        BufferedOutputStream output = null;

        // input stream of the file
        BufferedInputStream input = null;
        GZIPInputStream gzi = null; // for gzip case

        // to make a get request to the node server
        HttpURLConnection con = null;

        try {
            // Get replica file url
            String urlMetadata = currentFileReplica.getServices().get(
                    Service.HTTPSERVER);
            if (urlMetadata == null) {
                logger.error("File {} haven't a HTTP service", instanceID);
                throw new IllegalStateException(
                        "This file haven't a HTTP service");
            }

            logger.debug("File {} will be download of {}", instanceID,
                    urlMetadata);

            // Dataset file url are formated: url|mime type|service name
            String urlStr = urlMetadata.substring(0, urlMetadata.indexOf("|"));
            logger.debug("Url of download: ", urlStr);
            URL url = new URL(urlStr);

            logger.debug(
                    "Checking if are necessary have permissions for access "
                            + "the file {}", instanceID);
            // open connection from url and set HttpURLConnection values
            con = (HttpURLConnection) url.openConnection();
            con.setInstanceFollowRedirects(true);
            con.setUseCaches(false);
            // Set headers of http connection request
            con.setRequestProperty("Connection", "close");

            boolean needPermissions = false;

            int responseCode = con.getResponseCode();
            if (responseCode != 200) {
                needPermissions = true;
            }

            if (needPermissions) {
                logger.debug("Getting permissions for file {}...", instanceID);
                con.disconnect();
                CredentialsManager credentialsManager = CredentialsManager
                        .getInstance();
                if (!credentialsManager.hasInitiated()) {
                    throw new UnauthorizedException(0);
                }
                con = credentialsManager.getAuthenticatedConnection(url);
                con.setInstanceFollowRedirects(true);
                // con.setConnectTimeout(15000);
                // con.setReadTimeout(30000);
                con.setUseCaches(false);
                // Set headers of http connection request
                con.setRequestProperty("Connection", "close");
            } else {
                // open connection from url and set HttpURLConnection values
                con = (HttpURLConnection) url.openConnection();
                con.setInstanceFollowRedirects(true);
                con.setUseCaches(false);
                // Set headers of http connection request
                con.setRequestProperty("Connection", "close");
                con.disconnect();
            }

            // if file download status has resumed to download
            if (resumed) {
                logger.debug("Configuring for a resume download");
                // new http header for download from where it was
                // con.setRequestProperty("Range", "bytes=" + file.length() +
                // "-");
                con.setRequestProperty("Range", "bytes=" + getCurrentSize()
                        + "-");

                // inicialize file output stream
                fos = new FileOutputStream(file, true); // append
                output = new BufferedOutputStream(fos);
            } else {
                logger.debug("Configuring for a new download");
                // inicialize file output stream
                fos = new FileOutputStream(file, false); // this reset file to 0
                output = new BufferedOutputStream(fos);
            }

            // initialize file input stream for two possible cases. Gzip
            // content and others
            if (con.getHeaderField("Content-Encoding") != null
                    && con.getHeaderField("Content-Encoding").equalsIgnoreCase(
                            "gzip")) {
                gzi = new GZIPInputStream(con.getInputStream());
                input = new BufferedInputStream(gzi);
            } else {
                input = new BufferedInputStream(con.getInputStream());
            }

            logger.debug("Checking if file {} have correct size metadata...",
                    instanceID);
            // If size metadata isn't in ESGF response document
            if (totalSize <= 0) {
                totalSize = con.getContentLength();
                logger.debug(" {} file size calculate is: {}", instanceID,
                        totalSize);

                // If is a new download
                if (!resumed) {
                    datasetDownloadStatus.incrementTotalSize(totalSize);
                }
            }

            // Read 32K
            final byte[] b = new byte[BYTES_FOR_EACH_PETITION];
            int len = input.read(b);

            if (needPermissions) {
                logger.debug("Checking if obtained permissions are valid...");
                int httpResponseCode = con.getResponseCode();
                if (httpResponseCode == 401 || httpResponseCode == 302
                        || httpResponseCode == 500) {
                    logger.info(
                            "User haven't permissions to access to file {}",
                            instanceID);
                    throw new UnauthorizedException(httpResponseCode);
                }
            }

            logger.debug("Code of response from file {} is: {}", instanceID,
                    con.getResponseCode());

            logger.debug("Start download process of file {}...", instanceID);
            // While there are bytes to read and file is in state DOWNLOADING
            while (len != -1 && getRecordStatus() == RecordStatus.DOWNLOADING) {

                // Write readed bytes to output and increment current size
                output.write(b, 0, len);
                incrementCurrentSize(len);

                // Read 32K more
                len = input.read(b);
            }

            // if download has been paused when file was in state
            // DOWNLOADING
            if (getRecordStatus() == RecordStatus.PAUSED) {
                logger.debug("Download of file {} was paused", instanceID);
                // finish thread and IO buffers. Disconnect HTTP connection
                try {
                    input.close();
                } catch (final Exception e) {
                }
                try {
                    gzi.close();
                } catch (final Exception e) {
                }
                try {
                    output.flush();
                    output.close();
                    fos.flush();
                } catch (final Exception e) {
                    e.getStackTrace();
                }
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                    e.getStackTrace();
                }
                // end of thread
                logger.trace("[OUT] download");
                return;
            }
            // if download has been skipped when file was in state
            // DOWNLOADING
            if (getRecordStatus() == RecordStatus.SKIPPED) {
                logger.debug("Download of file {} was skipped", instanceID);
                // finish thread and IO buffers. Disconnect HTTP connection
                try {
                    input.close();
                } catch (final Exception e) {
                }
                try {
                    gzi.close();
                } catch (final Exception e) {
                }
                try {
                    output.flush();
                    output.close();
                    fos.flush();
                } catch (final Exception e) {
                    e.getStackTrace();
                }
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                    e.getStackTrace();
                }
                // end thread
                logger.trace("[OUT] download");
                return;
            }

        } catch (UnauthorizedException e1) {
            logger.warn(
                    "Unauthorized exception in middle of download of file {}"
                            + "Maybe the user certificate has expired.",
                    instanceID);

            // pause this instance before notify error
            setRecordStatus(RecordStatus.UNAUTHORIZED);

            notifyDownloadUnauthorizedErrorObservers();

            // end thread
            logger.trace("[OUT] download");
            return;
        } catch (Exception e) {

            logger.error("Exception in middle of download of file {}: \n{}",
                    instanceID, e.getMessage() + " " + e.getStackTrace());

            setRecordStatus(RecordStatus.FAILED);
            notifyDownloadErrorObservers();

            // end thread
            logger.trace("[OUT] download");
            return;
        } finally {
            // At the end of the download process always close all IO buffers.
            // Disconnect HTTP connection.

            try {
                input.close();
            } catch (final Exception e) {
            }
            try {
                gzi.close();
            } catch (final Exception e) {
            }
            try {
                output.flush();
                output.close();
                fos.flush();
            } catch (final Exception e) {
                e.getStackTrace();
            }
            try {
                con.disconnect();

            } catch (final Throwable e) {
                e.getStackTrace();
            }
        }

        // if download has been successful and checksum is defined
        if (checksum != null && checksumType != null) {
            if (currentSize == totalSize && totalSize != 0) {

                logger.debug("Checking if file downloaded isn't corrupted.");
                // Check if download file isn't corrupted
                boolean valid = validateChecksum(checksum, checksumType);

                // if checksum isn't corrupted
                if (valid) {
                    // set file download status to FINISHED
                    setRecordStatus(RecordStatus.FINISHED);
                    // set download finish date
                    setDownloadFinish(new Date());

                    // notify download completed
                    notifyDownloadCompletedObservers();

                    logger.debug("File {} has been validated by checksum.",
                            instanceID);
                } else {

                    // if failed checksum
                    logger.debug("Checksum of file {} failed.", instanceID);
                    setRecordStatus(RecordStatus.CHECKSUM_FAILED);
                }

                // end thread
                logger.trace("[OUT] download");
                return;
            } else {
                logger.debug("Download of file {} failed.", instanceID);
                setRecordStatus(RecordStatus.FAILED);
                notifyDownloadErrorObservers();

                // end thread
                logger.trace("[OUT] download");
                return;
            }
        }

        logger.warn("File hasn't checksum");

        if (currentSize == totalSize && totalSize != 0) {
            // set file download status to FINISHED
            setRecordStatus(RecordStatus.FINISHED);

            // set download finish date
            setDownloadFinish(new Date());

            // notify download completed
            notifyDownloadCompletedObservers();

            logger.debug(
                    "Download of file {} has been completed without checksum.",
                    instanceID);
        } else {
            // error
            logger.debug("Download of file {} failed.", instanceID);
            setRecordStatus(RecordStatus.FAILED);
            notifyDownloadErrorObservers(); // notify error

            // end thread
            logger.trace("[OUT] download");
            return;
        }

        // end thread
        logger.trace("[OUT] download");
        return;

    }

    /**
     * Get approximate time to finish the download in milliseconds.
     * 
     * @return time to finish in milliseconds
     */
    @Override
    public long getApproximateTimeToFinish() {
        // TODO Auto-generated method stub
        return 0;
    }

    /**
     * Get current file replica configured to download file.
     * 
     * @return the currentFileReplica
     */
    public RecordReplica getCurrentFileReplica() {
        logger.trace("[IN]  getCurrentFileReplica");
        logger.trace("[OUT] getCurrentFileReplica");
        return currentFileReplica;
    }

    /**
     * Get current progress of the download.
     * 
     * @return a integer an integer representing the percent of download in a
     *         range from 0 to 100
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
     * Get Current size of the file being download
     * 
     * @return the currentSize
     */
    public synchronized long getCurrentSize() {
        logger.trace("[IN]  getCurrentSize");
        logger.trace("[OUT] getCurrentSize");
        return currentSize;
    }

    /**
     * @return the downloadFinish
     */
    public synchronized Date getDownloadFinish() {
        logger.trace("[IN]  getDownloadFinish");
        logger.trace("[OUT] getDownloadFinish");
        return downloadFinish;
    }

    /**
     * @return the downloadStart
     */
    public synchronized Date getDownloadStart() {
        logger.trace("[IN]  getDownloadStart");
        logger.trace("[OUT] getDownloadStart");
        return downloadStart;
    }

    /**
     * Get system file
     * 
     * @return the file
     */
    public synchronized String getFilePath() {
        logger.trace("[IN]  getFilePath");
        logger.trace("[OUT] getFilePath");
        return file.getAbsolutePath();
    }

    /**
     * Get download priority
     * 
     * @return the priority
     */
    public synchronized DownloadPriority getPriority() {
        logger.trace("[IN]  getPriority");
        logger.trace("[OUT] getPriority");
        return priority;
    }

    /**
     * Set a replica source to download (random)
     * 
     * Finds out the replicas of a file and choose one of random of them
     * 
     * @return random file replica
     * @throws IOException
     *             when replicas can't be find out
     */
    private RecordReplica getRandomReplicaSource() throws IOException {
        logger.trace("[IN]  getRandomReplicaSource");

        logger.debug("Getting file from file system...");
        DatasetFile file = DownloadManager.getFile(
                datasetDownloadStatus.getInstanceID(), instanceID);

        logger.debug("Choosing a random replica of file {}", instanceID);
        List<RecordReplica> fileReplicas = file.getReplicas();
        int numberOfReplicas = fileReplicas.size();

        // random index
        int index = (int) (Math.random() * numberOfReplicas);

        logger.trace("[OUT] getRandomReplicaSource");
        return fileReplicas.get(index);
    }

    /**
     * Set a replica source to download (random)
     * 
     * @param replicas
     *            List<RecordReplica> of replicas of a {@link DatasetFile}
     * @return random file replica
     */
    private RecordReplica getRandomReplicaSource(
            List<RecordReplica> fileReplicas) {
        logger.trace("[IN]  getRandomReplicaSource");
        int numberOfReplicas = fileReplicas.size();

        logger.debug("Choosing a random replica of file {}", instanceID);
        int index = (int) (Math.random() * numberOfReplicas);

        logger.trace("[OUT] getRandomReplicaSource");
        return fileReplicas.get(index);
    }

    /**
     * Get record status.
     * 
     * @return the status Enum(created, ready, started, paused and skipped)
     */
    public synchronized RecordStatus getRecordStatus() {
        logger.trace("[IN]  getRecordStatus");
        logger.trace("[OUT] getRecordStatus");
        return status;
    }

    /**
     * Get total size of the file
     * 
     * @return the totalSize
     */
    public synchronized long getTotalSize() {
        logger.trace("[IN]  getTotalSize");
        logger.trace("[OUT] getTotalSize");
        return totalSize;
    }

    /**
     * Increment current size in len bytes
     * 
     * @param len
     *            the bytes to increment
     */
    private void incrementCurrentSize(long len) {
        logger.trace("[IN]  incrementCurrentSize");

        // increment dataset current size synchronize method
        // because datasetDownloadStatus may be accessed for
        // more than one thread
        datasetDownloadStatus.increment(len);

        // increment file current size
        currentSize = currentSize + len;

        // notify observers
        notifyDownloadProgressObservers();

        logger.trace("[OUT] incrementCurrentSize");

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
     * Call method onUnauthorizedError() in all observers
     */
    private void notifyDownloadUnauthorizedErrorObservers() {
        logger.trace("[IN]  notifyDownloadUnauthorizedErrorObservers");

        for (DownloadObserver o : observers) {
            o.onUnauthorizedError(this);
        }

        logger.trace("[OUT] notifyDownloadUnauthorizedErrorObservers");
    }

    /** Pause current file download. When file is paused his job runnable finish */
    @Override
    public synchronized void pause() {
        logger.trace("[IN]  pause");
        setRecordStatus(RecordStatus.PAUSED);
        logger.trace("[OUT] pause");
    }

    /**
     * Register new object that implement observer for this file download
     * status.
     * 
     * @param observer
     */
    public void registerObserver(DownloadObserver observer) {
        logger.trace("[IN]  registerObserver");
        observers.add(observer);
        logger.trace("[OUT] registerObserver");
    }

    /**
     * Reset this intance of {@link FileDownloadStatus} to predefined values and
     * delete file system
     */
    @Override
    public synchronized void reset() {
        logger.trace("[IN]  reset");

        logger.debug("Reseting file an file download status...");
        // reset status and current size
        setRecordStatus(RecordStatus.CREATED);
        setCurrentSize(0);

        // remove file or directory
        file.delete();

        logger.trace("[OUT] reset");
    }

    /**
     * Start file download
     */
    @Override
    public void run() {
        logger.trace("[IN]  run");
        // start download
        try {
            download();
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.trace("[OUT] run");
    }

    /**
     * @param currentFileReplica
     *            the currentFileReplica to set
     */
    public void setCurrentFileReplica(RecordReplica currentFileReplica) {
        logger.trace("[IN]  setCurrentFileReplica");
        this.currentFileReplica = currentFileReplica;
        logger.trace("[OUT] setCurrentFileReplica");
    }

    /**
     * Set current size of the file being download. Synchronized.
     * 
     * @param currentSize
     *            the actual size of file that are being download
     */
    public synchronized void setCurrentSize(long currentSize) {
        logger.trace("[IN]  setCurrentSize");
        this.currentSize = currentSize;
        logger.trace("[OUT] setCurrentSize");
    }

    /**
     * Set totak size of the file being download. Synchronized.
     * 
     * @param totalSize
     *            the total size of file that are being download
     */
    public synchronized void setTotalSize(long totalSize) {
        logger.trace("[IN]  setTotalSize");
        this.totalSize = totalSize;
        logger.trace("[OUT] setTotalSize");
    }

    /**
     * @param downloadFinish
     *            the downloadFinish to set
     */
    public synchronized void setDownloadFinish(Date downloadFinish) {
        logger.trace("[IN]  setDownloadFinish");
        this.downloadFinish = downloadFinish;
        logger.trace("[OUT] setDownloadFinish");
    }

    /**
     * @param downloadStart
     *            the downloadStart to set
     */
    public synchronized void setDownloadStart(Date downloadStart) {
        logger.trace("[IN]  setDownloadStart");
        this.downloadStart = downloadStart;
        logger.trace("[OUT] setDownloadStart");
    }

    /**
     * Set download priority
     * 
     * @param priority
     *            the priority to set
     */
    public synchronized void setPriority(DownloadPriority priority) {
        logger.trace("[IN]  setPriority");
        this.priority = priority;
        logger.trace("[OUT] setPriority");
    }

    /**
     * Set record status. Synchronized.
     * 
     * @param status
     *            the new status
     */
    public synchronized void setRecordStatus(RecordStatus status) {
        logger.trace("[IN]  setRecordStatus");
        this.status = status;
        logger.trace("[OUT] setRecordStatus");
    }

    /**
     * Finds out the info of file in file system and configure file to download
     * and get configured file replica. Verify that exist system file assigned
     * to current download in case that the file record state were paused. If a
     * file replica isn't configured, choose a random replica.
     * 
     * @return {@link RecordReplica} that is a replica from where the file will
     *         be downloaded
     * @throws IOException
     *             when info of file can't be loaded from file system
     * 
     * @throws IllegalStateException
     *             if file isn't in state CREATED OR PAUSED
     */
    public synchronized RecordReplica setToDownload() throws IOException {
        logger.trace("[IN]  setToDownload");

        // If is new file
        if (getRecordStatus() == RecordStatus.CREATED) {

            logger.debug("Getting info of file of local system...");
            DatasetFile datasetFile = DownloadManager.getFile(
                    datasetDownloadStatus.instanceID, instanceID);

            logger.debug("Getting checksum info...");
            if (datasetFile.contains(Metadata.CHECKSUM)) {
                setChecksum((String) datasetFile.getMetadata(Metadata.CHECKSUM));
            } else {
                this.checksum = null;
            }

            if (datasetFile.contains(Metadata.CHECKSUM_TYPE)) {
                String strChecksumType = datasetFile
                        .getMetadata(Metadata.CHECKSUM_TYPE);
                setChecksumType(ChecksumType.valueOf(strChecksumType
                        .toUpperCase()));
            } else {
                this.checksumType = null;
            }

            // if not setted. Get random
            if (currentFileReplica == null) {
                logger.debug("Configuring new download replica...");
                this.currentFileReplica = getRandomReplicaSource(datasetFile
                        .getReplicas());
            }
            // Set download start date
            setDownloadStart(new Date());

            // if file has been paused or has been unauthorized
        } else if (getRecordStatus() == RecordStatus.PAUSED
                || getRecordStatus() == RecordStatus.UNAUTHORIZED) {
            // Verify that exist system file assigned to current download
            if (!file.exists()) {
                setCurrentSize(0); // if not exists, reset current size
            }

        } else {
            logger.error("File download status is in unexpected state",
                    getRecordStatus());
            throw new IllegalStateException();
        }

        logger.debug("Putting state of download to READY");
        setRecordStatus(RecordStatus.READY);

        logger.trace("[OUT] setToDownload");
        return currentFileReplica;

    }

    /**
     * Configure file to download and get configured file replica. Verify that
     * exist system file assigned to current download in case that the file
     * record state were paused. If a file replica isn't configured, choose a
     * random replica.
     * 
     * @param datasetFile
     *            {@link DatasetFile} that will be download
     * 
     * @return {@link RecordReplica} that is a replica from where the file will
     *         be downloaded
     * 
     * @throws IOException
     *             when info of file can't be loaded from file system
     * 
     * @throws IllegalStateException
     *             if file isn't in state CREATED OR PAUSED
     */
    public synchronized RecordReplica setToDownload(DatasetFile datasetFile)
            throws IOException {
        logger.trace("[IN]  setToDownload");

        // If is new file
        if (getRecordStatus() == RecordStatus.CREATED) {

            logger.debug("Getting checksum info...");
            if (datasetFile.contains(Metadata.CHECKSUM)) {
                setChecksum((String) datasetFile.getMetadata(Metadata.CHECKSUM));
            } else {
                this.checksum = null;
            }

            if (datasetFile.contains(Metadata.CHECKSUM_TYPE)) {
                String strChecksumType = datasetFile
                        .getMetadata(Metadata.CHECKSUM_TYPE);
                setChecksumType(ChecksumType.valueOf(strChecksumType
                        .toUpperCase()));
            } else {
                this.checksumType = null;
            }

            // if not setted. Get random
            if (currentFileReplica == null) {
                logger.debug("Configuring new download replica...");
                this.currentFileReplica = getRandomReplicaSource(datasetFile
                        .getReplicas());
            }
            // Set download start date
            setDownloadStart(new Date());

            // if file has been paused or has been unauthorized
        } else if (getRecordStatus() == RecordStatus.PAUSED
                || getRecordStatus() == RecordStatus.UNAUTHORIZED) {
            // Verify that exist system file assigned to current download
            if (!file.exists()) {
                setCurrentSize(0); // if not exists, reset current size
            }

        } else {
            logger.error("File download status is in unexpected state",
                    getRecordStatus());
            throw new IllegalStateException();
        }

        logger.debug("Putting state of download to READY");
        setRecordStatus(RecordStatus.READY);

        logger.trace("[OUT] setToDownload");
        return currentFileReplica;

    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "FileDownloadStatus [ currentSize=" + currentSize + ", status="
                + status + ", totalSize=" + totalSize + "]";
    }

    /**
     * Validate if the checksum of file download is correct. Synchronized.
     * 
     * @param checksum
     *            checksum of file
     * @param checksumType
     *            type of checksum
     * @return true if is correct and false otherwise
     */
    private synchronized boolean validateChecksum(String checksum,
            ChecksumType checksumType) {
        logger.trace("[IN]  validateChecksum");

        boolean valid = false;
        String hash = "";

        try {

            logger.debug("Configuring correctly message digest to"
                    + " {} algorithm... ", checksumType.toString());
            // new instance of message digest with the appropriated
            // algorithm
            MessageDigest messageDigest = MessageDigest
                    .getInstance(checksumType.toString());

            // Creates a digest input stream from file
            InputStream is = new FileInputStream(file);
            byte[] dataBytes = new byte[1024];
            int nread = is.read(dataBytes);
            while (nread > 0) {
                messageDigest.update(dataBytes, 0, nread);
                nread = is.read(dataBytes);
            }

            is.close();

            // Completes the hash computation
            byte[] digest = messageDigest.digest();

            // Cast byte[] to hexadecimal string to compare with the
            // checksum given from dataset metadata
            for (byte aux : digest) {
                int b = aux & 0xff;
                if (Integer.toHexString(b).length() == 1) {
                    hash += "0";
                }
                hash += Integer.toHexString(b);
            }

            if (hash.equals(checksum)) {
                valid = true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        logger.trace("[OUT] validateChecksum");
        return valid;
    }

    /**
     * Get reference to dataset download status that the file belongs.
     * 
     * @return the datasetDownloadStatus
     */
    public DatasetDownloadStatus getDatasetDownloadStatus() {
        logger.trace("[IN]  getDatasetDownloadStatus");
        logger.trace("[OUT] getDatasetDownloadStatus");
        return datasetDownloadStatus;
    }

    /**
     * Get system file
     * 
     * @return the file
     */
    public File getFile() {
        logger.trace("[IN]  getFile");
        logger.trace("[OUT] getFile");
        return file;
    }

    /**
     * Get file status
     * 
     * @return the status
     */
    public RecordStatus getStatus() {
        logger.trace("[IN]  getStatus");
        logger.trace("[OUT] getStatus");
        return status;
    }

    /**
     * Set reference to dataset download status that the file belongs.
     * 
     * @param datasetDownloadStatus
     *            the datasetDownloadStatus to set
     */
    public void setDatasetDownloadStatus(
            DatasetDownloadStatus datasetDownloadStatus) {
        logger.trace("[IN]  setDatasetDownloadStatus");
        this.datasetDownloadStatus = datasetDownloadStatus;
        logger.trace("[OUT] setDatasetDownloadStatus");
    }

    /**
     * Set status of file.
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
     * Restore dataset file from local system (reinit program).
     * 
     * @param dataStatus
     *            dataset download status
     * @param dataDirectoryPath
     *            path of directory of dataset downloads
     */
    public void restoreDatasetFile(DatasetDownloadStatus dataStatus,
            String dataDirectoryPath) {
        logger.trace("[IN]  restoreDatasetFile");

        this.datasetDownloadStatus = dataStatus;
        // XXX name file drs_id or id??
        this.file = new File(dataDirectoryPath + File.separator + instanceID);

        // check if files can't remove from system. If these has been removed
        // then reset file status
        if (getRecordStatus() != RecordStatus.CREATED
                && getRecordStatus() != RecordStatus.SKIPPED) {

            // If file don't exits in system files
            if (!file.exists()) {
                logger.debug(
                        "File {} isn't in file system. Reseting values... ",
                        instanceID);
                // if file system not exist
                // decrement dataset parent
                datasetDownloadStatus.decrementCurrentSize(getCurrentSize());

                if (datasetDownloadStatus.getRecordStatus() == RecordStatus.FINISHED) {
                    datasetDownloadStatus.setRecordStatus(RecordStatus.PAUSED);
                }

                // reset file status
                reset();

            }
        }

        logger.trace("[OUT] restoreDatasetFile");
    }

    /**
     * Get instance_id of file
     * 
     * @return the instanceID
     */
    public String getInstanceID() {
        logger.trace("[IN]  getInstanceID");
        logger.trace("[OUT] getInstanceID");
        return instanceID;
    }

    /**
     * Set instance_id of file
     * 
     * @param instanceID
     *            the instanceID to set
     */
    public void setInstanceID(String instanceID) {
        logger.trace("[IN]  setInstanceID");
        this.instanceID = instanceID;
        logger.trace("[OUT] setInstanceID");
    }

    /**
     * @return the checksumType
     */
    public ChecksumType getChecksumType() {
        logger.trace("[IN]  getChecksumType");
        logger.trace("[OUT] getChecksumType");
        return checksumType;
    }

    /**
     * @param checksumType
     *            the checksumType to set
     */
    public void setChecksumType(ChecksumType checksumType) {
        logger.trace("[IN]  setChecksumType");
        this.checksumType = checksumType;
        logger.trace("[OUT] setChecksumType");
    }

    /**
     * @return the checksum
     */
    public String getChecksum() {
        logger.trace("[IN]  getChecksum");
        logger.trace("[OUT] getChecksum");
        return checksum;
    }

    /**
     * @param checksum
     *            the checksum to set
     */
    public void setChecksum(String checksum) {
        logger.trace("[IN]  setChecksum");
        this.checksum = checksum;
        logger.trace("[OUT] setChecksum");
    }

}
