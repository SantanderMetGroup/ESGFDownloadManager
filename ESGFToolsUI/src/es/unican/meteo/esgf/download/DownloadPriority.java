package es.unican.meteo.esgf.download;

import java.io.Serializable;

/** To specify download priority. */
public enum DownloadPriority implements Serializable {
    HIGH, MEDIUM, LOW;
};
