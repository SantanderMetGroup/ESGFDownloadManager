/**
 * 
 */
package search.management;

import java.io.Serializable;

/**
 * Facets supported by most ESGF Index Nodes in the federation, and can be used
 * to discover/query/retrieve CMIP5 data.
 * 
 * @author Karem Terry Alvildo
 * 
 */
public enum SearchCategoryFacet implements Serializable {
    /** CF standard name */
    CF_STANDARD_NAME("CF standard name"),

    /** CMIP table */
    CMOR_TABLE("CMIP table"),

    /** Ensemble */
    ENSEMBLE("Ensemble"),

    /** Experiment */
    EXPERIMENT("Experiment"),

    /** Experiment family */
    EXPERIMENT_FAMILY("Experiment family"),

    /** Institute. */
    INSTITUTE("Institute"),

    /** Model. */
    MODEL("Model"),

    /** Product */
    PRODUCT("Product"),

    /** Project. */
    PROJECT("Project"),

    /** Realm */
    REALM("Realm"),

    /** Instrument. */
    SOURCE_ID("Instrument"),

    /** Time frequency */
    TIME_FREQUENCY("Time frequency"),

    /** Variable */
    VARIABLE("Variable"),

    /** Variable long name */
    VARIABLE_LONG_NAME("Variable long name");

    /** Search Category string */
    private String searchCategory;

    // Constructor, initialize the search category
    private SearchCategoryFacet(String searchCategory) {
        this.searchCategory = searchCategory;
    }

    // Return
    @Override
    public String toString() {
        return searchCategory;
    }

}
