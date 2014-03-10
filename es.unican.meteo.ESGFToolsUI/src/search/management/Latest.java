package search.management;

import java.io.Serializable;

/** Enum values of parameter "Latest" latest, previous or all versions. */
public enum Latest implements Serializable {
    /** All versions. */
    ALL,
    /** Only latest version. */
    LATEST,
    /** Only previous versions. */
    PREVIOUS
}
