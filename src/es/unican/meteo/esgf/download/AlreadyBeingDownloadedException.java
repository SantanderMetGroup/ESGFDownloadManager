package es.unican.meteo.esgf.download;

/**
 * Thrown when try to change a path when already the data are being downloaded
 * 
 * @author Karem Terry
 * 
 */
public class AlreadyBeingDownloadedException extends Exception {
    public AlreadyBeingDownloadedException() {
        super("Can't change downloading path. Dataset already being downloaded");
    }
}
