package es.unican.meteo.esgf.download;

import java.io.IOException;

/**
 * Class that represents the data download
 * 
 * @author terryk
 */

public interface Download {

    /**
     * Start the download
     * 
     * @throws IOException
     */
    void download() throws IOException;

    /**
     * Pause download
     */
    void pause();

    /**
     * Reset download
     */
    void reset();

    /**
     * Get current progress of the download.
     * 
     * @return a integer an integer representing the percent of download in a
     *         range from 0 to 100
     */
    int getCurrentProgress();

    /**
     * Get approximate time to finish the download in milliseconds
     * 
     * @return time to finish in milliseconds
     */
    long getApproximateTimeToFinish();

}
