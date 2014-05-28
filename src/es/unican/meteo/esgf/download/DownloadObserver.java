package es.unican.meteo.esgf.download;


/**
 * Observer of downloads interface. Observes if download has been progressed,
 * has been completed or an error has happened.
 * 
 * Must be implemented in the class where they will carry out the necessary
 * changes consistent with the observed in the download.
 * 
 * Observer is a software design pattern in which an object, called the subject,
 * maintains a list of its dependents, called observers, and notifies them
 * automatically of any state changes, usually by calling one of their methods.
 * It is mainly used to implement distributed event handling systems
 * 
 * @author Karem Terry
 * 
 */
public interface DownloadObserver {

    /**
     * If a download progress is notified
     * 
     * @param download
     *            a download
     */
    public void onDownloadProgress(Download download);

    /**
     * If a complete download is notified
     * 
     * @param download
     *            a download
     */
    public void onDownloadCompleted(Download download);

    /**
     * If an error in download is notified
     * 
     * @param download
     *            a download
     */
    public void onError(Download download);

    /**
     * If an error of permissions in download is notified
     * 
     * @param download
     *            a download
     */
    public void onUnauthorizedError(Download download);
}
