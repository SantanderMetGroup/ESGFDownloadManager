package download.management;

import java.io.Serializable;

/** To specify download status */
public enum RecordStatus implements Serializable {

    /** Just created. */
    CREATED,
    /** Selected to be download. (only files). */
    READY,
    /** Downloading. */
    DOWNLOADING,
    /** Download has been paused. */
    PAUSED,
    /** Download is completed. */
    FINISHED,
    /** Isn't selected to be download (only files). */
    SKIPPED,
    /** If can't download because it haven't credentials (only files). */
    UNAUTHORIZED,
    /** If checksum fails. */
    CHECKSUM_FAILED,
    /** If the checksum of file failed(only files). */
    FAILED;

};
