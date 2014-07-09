package es.unican.meteo.esgf.search;

import java.io.Serializable;

/**
 * Enum values of search parameter "Replica" master, replicas or all replicas.
 */
public enum Replica implements Serializable {
    /** All replicas. */
    ALL,
    /** Only master replica. */
    MASTER,
    /** Only replicas. */
    REPLICA
}
