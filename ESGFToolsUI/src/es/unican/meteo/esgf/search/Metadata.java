package es.unican.meteo.esgf.search;

import java.io.Serializable;

/**
 * <p>
 * The Metadata Enum class lists each of the possible metadata defined by ESGF.
 * </p>
 * 
 * <p>
 * Metadata concept is analogous to the use of indexes for locating objects
 * rather than data. For example, in a library used tokens that specify authors,
 * titles, publishers and places to find books. So metadata used to classify
 * datasets of output ESGF models.
 * </p>
 * 
 * <p>
 * Each result record contained in the response document is associated with a
 * set of metadata fields. Each field has a name, and may be single-valued or
 * multiple-valued. Some fields are meaningful for records of all types and have
 * been assigned standardized names, while other fields that are more type
 * specific and may have any name.
 * </p>
 * 
 * <p>
 * The rules for including metadata fields in the response documents are as
 * follows:
 * </p>
 * 
 * <p>
 * The following table lists the standard metadata fields, i.e. those fields
 * that represent the minimum amount of metadata that is common to records of
 * all types, and that must always be returned as part of each result record.
 * </p>
 * 
 * <table border=1><tbody><tr>  <td><p><strong>Field Name</strong></p></td>
  <td><p><strong>Description</strong></p></td>
  <td><p><strong>Multi-Valued?</strong></p></td>
  <td><p><strong>Mandatory?</strong></p></td>
  <td><p><strong>Applicable Record Type</strong></p></td>
</tr>
<tr>  <td><span></span><p> <strong>id</strong></p></td>
  <td><p> Globally unique record identifier </p></td>
  <td><p"> false             </p></td>
  <td><p"> true            </p></td>
  <td><p"> all </p></td>
</tr>
<tr>  <td><span></span><p class="line862"> <strong>title</strong> </p></td>
  <td><p> Human-readable short description of the record, usable in a summary display of results </p></td>
  <td><p> false </p></td>
  <td><p> true </p></td>
  <td><p> all </p></td>
</tr>
<tr>  <td><span></span><p> <strong>description</strong> </p></td>
  <td><p> A human-readable longer description of the record, suitable for being displayed under the record's title, possibly in a shortened form </p></td>
  <td><p> true </p></td>
  <td><p> false </p></td>
  <td><p> all </p></td>
</tr>
<tr>  <td><span></span><p> <strong>type</strong> </p></td>
  <td><p> The record's type, which should match the search requested type (if provided) </p></td>
  <td><p> false </p></td>
  <td><p> true </p></td>
  <td><p> all </p></td>
</tr>
<tr>  <td><span></span><p> <strong>timestamp</strong></p></td>
  <td><p> The date and time when the record was last updated </p></td>
  <td><p> false </p></td>
  <td><p> true </p></td>
  <td><p> all </p></td>
</tr>
<tr>  <td><span></span><p> <strong>url</strong> </p></td>
  <td><p> A URL that can be used to access the record, must include a descriptive name and the content/mime type </p></td>
  <td><p> true </p></td>
  <td><p> true </p></td>
  <td><p> all </p></td>
</tr>
<tr>  <td><span></span><p> <strong>size</strong> </p></td>
  <td><p> The file size, or total dataset size (sum of all files) </p></td>
  <td><p> false </p></td>
  <td><p> true </p></td>
  <td><p> Dataset, File </p></td>
</tr>
<tr>  <td><span></span><p> <strong>dataset_id</strong> </p></td>
  <td><p> The identifier of the containing dataset </p></td>
  <td><p> false </p></td>
  <td><p> true </p></td>
  <td><p> File </p></td>
</tr>
<tr>  <td><span></span><p> <strong>checksum</strong> </p></td>
  <td><p> The file checksum, if available </p></td>
  <td><p> true </p></td>
  <td><p> true </p></td>
  <td><p> File </p></td>
</tr>
<tr>  <td><span></span><p> <strong>checksum_type</strong> </p></td>
  <td><p> The file checksum type, if available </p></td>
  <td><p> true </p></td>
  <td><p> true </p></td>
  <td><p> File </p></td>
</tr>
</tbody></table>
 * 
 * @author Karem Terry
 * 
 */
public enum Metadata implements Serializable {
    /** Access of record. */
    ACCESS,
    /** CF Standard Name. */
    CF_STANDARD_NAME,
    /** The file checksum, if available. */
    CHECKSUM,
    /** The file checksum type, if available. */
    CHECKSUM_TYPE,
    /** CMIP Table. */
    CMOR_TABLE,
    /** Indicates the Data Node where the data is stored. */
    DATA_NODE,
    /** The identifier of the containing dataset (Only File). */
    DATASET_ID,
    /** Value is template of dataset id. */
    DATASET_ID_TEMPLATE_,
    /** Dataset simulation start. */
    DATETIME_START,
    /** Dataset simulation start. */
    DATETIME_STOP,
    /** Record (longer) description. */
    DESCRIPTION,
    /**
     * Templated string assigned to a Dataset by some special publication
     * software, if available. Note: this field is deprecated.
     */
    DRS_ID,
    /** East degrees. */
    EAST_DEGREES,
    /** Ensemble. */
    ENSEMBLE,
    /** Experiment. */
    EXPERIMENT,
    /** Experiment family. */
    EXPERIMENT_FAMILY,
    /** . */
    FORCING,
    /** Format of dataset. */
    FORMAT,
    /**
     * Universally unique for each record across the federation, i.e. specific
     * to each dataset or file, version and replica (and the data node storing
     * the data). It is intended to be "opaque", i.e. it should not be parsed by
     * clients to extract any information.
     */
    ID,
    /** The Index Node where the data is published. */
    INDEX_NODE,
    /**
     * Same for all replicas across federation, but specific to each version.
     * When parsing THREDDS catalogs, it is extracted from ID attribute of
     * <dataset> tag in THREDDS.
     */
    INSTANCE_ID,
    /** Institute that datasets belongs */
    INSTITUTE,
    /**
     * Indicates wether the record is the latest available version, or a
     * previous version.
     */
    LATEST,
    /**
     * String that is identical for the master and all replicas of a given
     * logical record (Dataset or File).
     */
    MASTER_ID,
    /** Format of metadata. */
    METADATA_FORMAT,
    /** Model of dataset. */
    MODEL,
    /** North degrees of dataset. */
    NORTH_DEGREES,
    /** Number of aggregations in a dataset . */
    NUMBER_OF_AGGREGATIONS,
    /** Number of files contained in a dataset, not always is returned. */
    NUMBER_OF_FILES,
    /** Product. */
    PRODUCT,
    /** Project. */
    PROJECT,
    /** Realm. */
    REALM,
    /**
     * A flag that is set to false for master records, true for replica records.
     */
    REPLICA,
    /** Record size (for Datasets or Files) . */
    SIZE,
    /** South degrees of dataset. */
    SOUTH_DEGREES,
    /** Instrument. */
    SOURCE_ID,
    /** Time frequency data. */
    TIME_FREQUENCY,
    /** The date and time when the record was last modified . */
    TIMESTAMP,
    /** Short title for dataset. */
    TITLE,
    /**
     * Intrinsic type of the record. Currently supported values: Dataset, File,
     * Aggregation.
     */
    TYPE,
    /**
     * Specific URL(s) to access the record . URLs that are access points for
     * Datasets and Files are encoded as 3-tuple of the form url|mime
     * type|service name, where the fields are separated by the | character, and
     * the mime type and service name are chosen from the ESGF controlled
     * vocabulary
     */
    URL,
    /** Dataset variable. */
    VARIABLE,
    /** Dataset variable long name. */
    VARIABLE_LONG_NAME,
    /** Record version. */
    VERSION,
    /** West degrees of dataset. */
    WEST_DEGREES,
    /** Xlink. */
    XLINK;

}
