package es.unican.meteo.esgf.search;

/**
 * Indicates the harvesting type of a {@link SearchResponse}. PARTIAL (for basic
 * harvesting to allow download process) and COMPLETE (for full harvesting)
 * 
 * @author Karem Terry
 * 
 */
public enum SearchHarvestType {
    /**
     * Partial harvesting. Only harvest basic metadata to allow download
     * process. Propertys (id, instance_id, index_node, data_node, checksum,
     * checksumType, size), replicas, file y services.
     */
    PARTIAL,
    /**
     * Complete Harvesting. All metadata in ESGF including files and
     * aggregations
     */
    COMPLETE;
}
