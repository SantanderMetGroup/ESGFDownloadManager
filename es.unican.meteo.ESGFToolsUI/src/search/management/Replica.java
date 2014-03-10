package search.management;

import java.io.Serializable;

/**
 * Enum values of parameter "Replica" master, replicas or all replicas.
 */
public enum Replica implements Serializable {
    /** All replicas. */
    ALL,
    /** Only master replica. */
    MASTER,
    /** Only replicas. */
    REPLICA
}
