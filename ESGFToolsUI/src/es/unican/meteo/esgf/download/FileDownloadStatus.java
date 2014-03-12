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

    private static final int BYTES_FOR_EACH_PETITION = 32768; // 32K
    // private static final int BYTES_FOR_EACH_PETITION = 131072; // 128K

    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(FileDownloadStatus.class);

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

    /** Record status: created, ready, started, paused, finished and skipped. */
    private RecordStatus status;

    /** Total file size. */
    private long totalSize;

    private String checksum;

    private ChecksumType checksumType;

    /**
     * Constructor
     */
    public FileDownloadStatus() {
        // Initialize observers
        observers = new LinkedList<DownloadObserver>();
    }

    /**
     * Constructor
     */
    public FileDownloadStatus(String fileInstanceID, long size,
            DatasetDownloadStatus datasetDownloadStatus,
            String dataDirectoryPath) {
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
    }

    /**
     * Return true if observer {@link DownloadObserver} is registered for this
     * file and false otherwise
     * 
     * @param observer
     * @return true if observer is registered for this file and false otherwise
     */
    public boolean containsObserver(DownloadObserver observer) {

        boolean find = false;

        // search observer in list of observers
        for (DownloadObserver dObserver : observers) {
            if (dObserver == observer) {
                find = true;
            }
        }

        return find;
    }

    /**
     * Private method. All logic of a file download.
     */
    @Override
    public void download() throws IOException {

        if (getRecordStatus() != RecordStatus.READY) {
            if (getRecordStatus() == RecordStatus.PAUSED) {
                if (datasetDownloadStatus.getRecordStatus() == RecordStatus.PAUSED) {
                    return;
                }
            } else {
                throw new IllegalStateException();
            }
        }

        setRecordStatus(RecordStatus.DOWNLOADING);

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
                        return;
                    }
                }

            }

            // if file is not valid or in't exist
            // create new empty file system, if the named file already exists do
            // nothing
            file.createNewFile();
        }

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

            // Dataset file url are formated: url|mime type|service name
            String urlStr = urlMetadata.substring(0, urlMetadata.indexOf("|"));
            logger.debug("Url of download: ", urlStr);
            URL url = new URL(urlStr);

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
                // new http header for download from where it was
                // con.setRequestProperty("Range", "bytes=" + file.length() +
                // "-");
                con.setRequestProperty("Range", "bytes=" + getCurrentSize()
                        + "-");

                // inicialize file output stream
                fos = new FileOutputStream(file, true); // append
                output = new BufferedOutputStream(fos);
            } else {
                // inicialize file output stream
                fos = new FileOutputStream(file, false); // this reset file to 0
                output = new BufferedOutputStream(fos);
            }

            // initialize file input stream for two possible cases. Gzip
            // content
            // and others
            if (con.getHeaderField("Content-Encoding") != null
                    && con.getHeaderField("Content-Encoding").equalsIgnoreCase(
                            "gzip")) {
                gzi = new GZIPInputStream(con.getInputStream());
                input = new BufferedInputStream(gzi);
            } else {
                input = new BufferedInputStream(con.getInputStream());
            }

            // TODO always getContentLength?
            // If size metadata isn't in ESGF response document
            if (totalSize <= 0) {
                totalSize = con.getContentLength();

                // If is a new download
                if (!resumed) {
                    datasetDownloadStatus.incrementTotalSize(totalSize);
                }
            }

            // Read 32K
            final byte[] b = new byte[BYTES_FOR_EACH_PETITION];
            int len = input.read(b);

            if (needPermissions) {
                int httpResponseCode = con.getResponseCode();
                if (httpResponseCode == 401 || httpResponseCode == 302
                        || httpResponseCode == 500) {
                    throw new UnauthorizedException(httpResponseCode);
                }
            }

            System.out.println("con.getResponseCode() 2:"
                    + con.getResponseCode() + " len:" + len);

            // While there are bytes to read and file is in state DOWNLOADINg
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
                // end runnable
                return;
            }
            if (getRecordStatus() == RecordStatus.SKIPPED) {
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
                // end runnable
                return;
            }

        } catch (UnauthorizedException e1) {

            // pause this instance before notify error
            setRecordStatus(RecordStatus.UNAUTHORIZED);

            notifyDownloadUnauthorizedErrorObservers();

            // e1.printStackTrace();

            // end thread
            return;
        } catch (Exception e) {

            // another exceptions
            // e.printStackTrace();

            setRecordStatus(RecordStatus.FAILED);
            notifyDownloadErrorObservers();

            // end thread
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

                // Check if download file isn't corrupted
                boolean valid = validateChecksum(checksum, checksumType);

                // if checksum isn't corrupted
                if (valid) {
                    // set file download status to FINISHED
                    setRecordStatus(RecordStatus.FINISHED);

                    System.out
                            .println("__________________________________________");

                    // set download finish date
                    setDownloadFinish(new Date());

                    // notify download completed
                    notifyDownloadCompletedObservers();
                } else {
                    // if failed checksum
                    setRecordStatus(RecordStatus.CHECKSUM_FAILED);
                }

                // end thread
                return;
            } else {
                setRecordStatus(RecordStatus.FAILED);
                notifyDownloadErrorObservers();

                // end thread
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
        } else {
            // notify error
            setRecordStatus(RecordStatus.FAILED);
            notifyDownloadErrorObservers();

            // end thread
            return;
        }
        // end thread
        return;

    }

    @Override
    public long getApproximateTimeToFinish() {
        // TODO Auto-generated method stub
        return 0;
    }

    /**
     * @return the currentFileReplica
     */
    public RecordReplica getCurrentFileReplica() {
        return currentFileReplica;
    }

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
    public synchronized long getCurrentSize() {
        return currentSize;
    }

    /**
     * @return the downloadFinish
     */
    public synchronized Date getDownloadFinish() {
        return downloadFinish;
    }

    /**
     * @return the downloadStart
     */
    public synchronized Date getDownloadStart() {
        return downloadStart;
    }

    /**
     * Get system file
     * 
     * @return the file
     */
    public synchronized String getFilePath() {
        return file.getAbsolutePath();
    }

    /**
     * Get download priority
     * 
     * @return the priority
     */
    public synchronized DownloadPriority getPriority() {
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

        DatasetFile file = DownloadManager.getFile(
                datasetDownloadStatus.getInstanceID(), instanceID);

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

        // random index
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
        return status;
    }

    /**
     * Get total size of the file
     * 
     * @return the totalSize
     */
    public synchronized long getTotalSize() {
        return totalSize;
    }

    /**
     * Increment current size in len bytes
     * 
     * @param len
     *            the bytes to increment
     */
    private void incrementCurrentSize(long len) {

        // increment dataset current size
        // synchronize method because datasetDownloadStatus may be accessed for
        // more than one thread
        datasetDownloadStatus.increment(len);

        // increment file current size
        currentSize = currentSize + len;

        // notify observers
        notifyDownloadProgressObservers();

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
     * Call method onUnauthorizedError() in all observers
     */
    private void notifyDownloadUnauthorizedErrorObservers() {
        for (DownloadObserver o : observers) {
            o.onUnauthorizedError(this);
        }
    }

    /** Pause current file download. When file is paused his job runnable finish */
    @Override
    public synchronized void pause() {
        setRecordStatus(RecordStatus.PAUSED);
    }

    /**
     * Register new object that implement observer for this file download
     * status.
     * 
     * @param observer
     */
    public void registerObserver(DownloadObserver observer) {
        observers.add(observer);
    }

    /**
     * Reset this intance of {@link FileDownloadStatus} to predefined values and
     * delete file system
     */
    @Override
    public synchronized void reset() {

        // reset status and current size
        setRecordStatus(RecordStatus.CREATED);
        setCurrentSize(0);

        // remove file or directory
        file.delete();
    }

    /**
     * Start file download
     */
    @Override
    public void run() {
        // start download
        try {
            download();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * @param currentFileReplica
     *            the currentFileReplica to set
     */
    public void setCurrentFileReplica(RecordReplica currentFileReplica) {
        this.currentFileReplica = currentFileReplica;
    }

    /**
     * Set current size of the file being download. Synchronized.
     * 
     * @param currentSize
     *            the actual size of file that are being download
     */
    public synchronized void setCurrentSize(long currentSize) {
        this.currentSize = currentSize;
    }

    /**
     * Set totak size of the file being download. Synchronized.
     * 
     * @param totalSize
     *            the total size of file that are being download
     */
    public synchronized void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    /**
     * @param downloadFinish
     *            the downloadFinish to set
     */
    public synchronized void setDownloadFinish(Date downloadFinish) {
        this.downloadFinish = downloadFinish;
    }

    /**
     * @param downloadStart
     *            the downloadStart to set
     */
    public synchronized void setDownloadStart(Date downloadStart) {
        this.downloadStart = downloadStart;
    }

    /**
     * Set download priority
     * 
     * @param priority
     *            the priority to set
     */
    public synchronized void setPriority(DownloadPriority priority) {
        this.priority = priority;
    }

    /**
     * Set record status. Synchronized.
     * 
     * @param status
     *            the new status
     */
    public synchronized void setRecordStatus(RecordStatus status) {
        this.status = status;
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

        if (getRecordStatus() == RecordStatus.CREATED) {

            logger.debug("Getting info of file of local system...");
            DatasetFile datasetFile = DownloadManager.getFile(
                    datasetDownloadStatus.instanceID, instanceID);

            // Set checksum info
            setChecksum((String) datasetFile.getMetadata(Metadata.CHECKSUM));
            String strChecksumType = datasetFile
                    .getMetadata(Metadata.CHECKSUM_TYPE);
            setChecksumType(ChecksumType.valueOf(strChecksumType.toUpperCase()));

            // if not setted. Get random
            if (currentFileReplica == null) {
                this.currentFileReplica = getRandomReplicaSource(datasetFile
                        .getReplicas());
            }
            // Set download start date
            setDownloadStart(new Date());
        } else if (getRecordStatus() == RecordStatus.PAUSED
                || getRecordStatus() == RecordStatus.UNAUTHORIZED) {
            // Verify that exist system file assigned to current download
            if (!file.exists()) {
                setCurrentSize(0); // if not exists, reset current size
            }

        } else {
            throw new IllegalStateException();
        }

        // Put record status to ready
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

        if (getRecordStatus() == RecordStatus.CREATED) {

            // Set checksum info
            setChecksum((String) datasetFile.getMetadata(Metadata.CHECKSUM));
            String strChecksumType = datasetFile
                    .getMetadata(Metadata.CHECKSUM_TYPE);
            setChecksumType(ChecksumType.valueOf(strChecksumType.toUpperCase()));

            // if not setted. Get random
            if (currentFileReplica == null) {
                this.currentFileReplica = getRandomReplicaSource(datasetFile
                        .getReplicas());
            }
            // Set download start date
            setDownloadStart(new Date());
        } else if (getRecordStatus() == RecordStatus.PAUSED
                || getRecordStatus() == RecordStatus.UNAUTHORIZED) {
            // Verify that exist system file assigned to current download
            if (!file.exists()) {
                setCurrentSize(0); // if not exists, reset current size
            }

        } else {
            throw new IllegalStateException();
        }

        // Put record status to ready
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

        boolean valid = false;
        String hash = "";

        try {
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

        return valid;
    }

    /**
     * Get reference to dataset download status that the file belongs.
     * 
     * @return the datasetDownloadStatus
     */
    public DatasetDownloadStatus getDatasetDownloadStatus() {
        return datasetDownloadStatus;
    }

    /**
     * Get system file
     * 
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * Get file status
     * 
     * @return the status
     */
    public RecordStatus getStatus() {
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
        this.datasetDownloadStatus = datasetDownloadStatus;
    }

    /**
     * Set status of file
     * 
     * @param status
     *            the status to set
     */
    public void setStatus(RecordStatus status) {
        this.status = status;
    }

    /**
     * Restore dataset file from local system (reinit program)
     * 
     * @param dataStatus
     *            dataset download status
     * @param dataDirectoryPath
     *            path of directory of dataset downloads
     */
    public void restoreDatasetFile(DatasetDownloadStatus dataStatus,
            String dataDirectoryPath) {

        this.datasetDownloadStatus = dataStatus;
        // XXX name file drs_id o id??
        this.file = new File(dataDirectoryPath + File.separator + instanceID);

        // check if files can't remove from system. If these has been removed
        // then reset file status
        if (getRecordStatus() != RecordStatus.CREATED
                && getRecordStatus() != RecordStatus.SKIPPED) {

            // If file exits, check if its valid
            if (file.exists()) {
                return;
            } else {
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
    }

    /**
     * Get instance_id of file
     * 
     * @return the instanceID
     */
    public String getInstanceID() {
        return instanceID;
    }

    /**
     * Set instance_id of file
     * 
     * @param instanceID
     *            the instanceID to set
     */
    public void setInstanceID(String instanceID) {
        this.instanceID = instanceID;
    }

    /**
     * @return the checksumType
     */
    public ChecksumType getChecksumType() {
        return checksumType;
    }

    /**
     * @param checksumType
     *            the checksumType to set
     */
    public void setChecksumType(ChecksumType checksumType) {
        this.checksumType = checksumType;
    }

    /**
     * @return the checksum
     */
    public String getChecksum() {
        return checksum;
    }

    /**
     * @param checksum
     *            the checksum to set
     */
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

}
