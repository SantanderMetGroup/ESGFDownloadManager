package es.unican.meteo.esgf.search;

/**
 * Indicates the harvesting status of a element in a harvesting process.
 * CREATING, HARVESTING, PAUSED, COMPLETED and FAILED
 * 
 * @author Karem Terry
 * 
 */
public enum HarvestStatus {
    /** Just created. */
    CREATED,
    /** Harvesting. */
    HARVESTING,
    /** Harvest is paused. */
    PAUSED,
    /** Harvest is completed. */
    COMPLETED,
    /** Harvest is failed. */
    FAILED;
}
