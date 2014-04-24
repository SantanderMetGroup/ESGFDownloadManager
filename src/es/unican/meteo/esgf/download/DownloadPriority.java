package es.unican.meteo.esgf.download;

import java.io.Serializable;

/** To specify a download priority. */
public enum DownloadPriority implements Serializable {
    HIGH, MEDIUM, LOW;
};
