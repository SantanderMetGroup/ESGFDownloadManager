package es.unican.meteo.esgf.search;

import java.io.Serializable;

/**
 * To specify harvesting status of {@link Dataset}. EMPTY, PARTIAL_HARVESTED,
 * HARVESTED and FAILED
 */
public enum DatasetHarvestStatus implements Serializable {

    /** New dataset. Must not be saved in this state */
    EMPTY,
    /** Partial harvest is completed. */
    PARTIAL_HARVESTED,
    /** Full harvest is completed. */
    HARVESTED,
};
