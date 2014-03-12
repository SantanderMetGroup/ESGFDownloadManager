/**
 * 
 */
package es.unican.meteo.esgf.search;

/**
 * @author terryk
 * 
 */
public enum Parameter {
    /** Access of record. */
    ACCESS("Access"),

    /** BBox. To spacial search */
    BBOX("Bbox"),

    /** CF standard name. */
    CF_STANDARD_NAME("CF standard name"),

    /** The file checksum, if available. */
    CHECKSUM("checksum"),

    /** The file checksum type, if available. */
    CHECKSUM_TYPE("checksum type"),

    /** CMIP table. */
    CMOR_TABLE("CMIP table"),

    /** Indicates the Data Node where the data is stored. */
    DATA_NODE("Data node"),

    /** The identifier of the containing dataset (Only File). */
    DATASET_ID("Dataset id"),

    /** Distrib. */
    DISTRIB("distrib"),

    /** Temporal range of data coverage (Start-End). End. */
    END("End"),

    /** Ensemble. */
    ENSEMBLE("Ensemble"),

    /** Experiment. */
    EXPERIMENT("Experiment"),

    /** Experiment family. */
    EXPERIMENT_FAMILY("Experiment family"),

    /** Facets */
    FACETS("Facets"),

    /** Fields. */
    FIELDS("Fields"),

    /** To specify the response document output format. */
    FORMAT("Format"),

    /** Lower and upper limit of the last timestamp update (From-to). From. */
    FROM("Lower/upper limit of the last timestamp"),

    /**
     * Universally unique for each record across the federation, i.e. specific
     * to each dataset or file, version and replica (and the data node storing
     * the data). It is intended to be "opaque", i.e. it should not be parsed by
     * clients to extract any information.
     */
    ID("id"),

    /** The Index Node where the data is published. */
    INDEX_NODE("index node"),

    /**
     * Same for all replicas across federation, but specific to each version.
     * When parsing THREDDS catalogs, it is extracted from ID attribute of
     * <dataset> tag in THREDDS.
     */
    INSTANCE_ID("Instance id"),
    /** Institute. */
    INSTITUTE("Institute"),

    /**
     * Indicates wether the record is the latest available version, or a
     * previous version.
     */
    LATEST("Latest"),

    /**
     * Limit. Pagination
     */
    LIMIT("Limit"),

    /**
     * String that is identical for the master and all replicas of a given
     * logical record (Dataset or File).
     */
    MASTER_ID("Master id"),

    /** Model. */
    MODEL("Model"),

    /**
     * Offset. Pagination
     */
    OFFSET("Offset"),

    /** Product. */
    PRODUCT("Product"),

    /** Project. */
    PROJECT("Project"),

    /** Query. For free text searches */
    QUERY("Query"),

    /** Realm. */
    REALM("Realm"),
    /**
     * A flag that is set to false for master records, true for replica records.
     */
    REPLICA("Replica"),

    /** Instrument. */
    SOURCE_ID("Instrument"),

    /** Temporal range of data coverage (Start-End). Start. */
    START("Temporal range of data coverage (Start-End)"),

    /** Time frequency. */
    TIME_FREQUENCY("Time frequency"),

    /** Lower and upper limit of the last timestamp update (From-to). To. */
    TO("From-to"),

    /** The date and time when the record was last modified . */
    TRACKING_ID("Tracking id"),
    /**
     * Intrinsic type of the record. Currently supported values: Dataset, File,
     * Aggregation.
     */
    TYPE("Type"),

    /** Variable. */
    VARIABLE("Variable"),

    /** Variable long name. */
    VARIABLE_LONG_NAME("Variable long name"),

    /** Record version. */
    VERSION("Version"),

    /** List of shards to be queried (index nodes). */
    SHARDS("Shards"),

    /** XLink. */
    XLINK("Xlink");

    /** Parameter string */
    private String parameter;

    // Constructor, initialize the search category
    private Parameter(String parameter) {
        this.parameter = parameter;
    }

    // Return
    @Override
    public String toString() {
        return parameter;
    }
}
