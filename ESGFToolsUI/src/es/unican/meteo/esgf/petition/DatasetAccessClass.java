package es.unican.meteo.esgf.petition;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.globus.util.Util;
import org.springframework.util.SerializationUtils;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

import es.unican.meteo.esgf.search.Dataset;

/**
 * Class to access datasets in cache and database. Singleton class.
 * 
 * @author terryk
 * 
 */
public class DatasetAccessClass {

    private static org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(DatasetAccessClass.class);
    private static final String BERKELEY_DB_DIRECTORY = "BerkeleyDB";
    private static final String DB_NAME = "dataset_db";
    private static final String CACHE_CONFIGURATION_XML = "ehcache_Dataset.xml";
    private static final String CACHE_NAME = "cache";

    /** Cache manager. */
    private CacheManager cacheManager;
    /** Cache of {@link Dataset}. */
    private Cache cache;
    /** Database of datasets. */
    private Database myDatabase;
    /** Database environment. */
    private Environment myDbEnvironment;
    /** Path of database configuration. */
    private String dBPath;

    /** Singleton instance. */
    private static DatasetAccessClass INSTANCE = null;

    /**
     * Create a thread-safe singleton.
     */
    private static void createInstance() {
        logger.trace("[IN]  createInstance");

        logger.debug("Checking if exist an instance of DatasetAccessClass");
        // creating a thread-safe singleton
        if (INSTANCE == null) {

            // Only the synchronized block is accessed when the instance hasn't
            // been created.
            synchronized (DatasetAccessClass.class) {
                // Inside the block it must check again that the instance has
                // not been created.
                if (INSTANCE == null) {
                    logger.debug("Creating new instance of DatasetAccessClass");
                    INSTANCE = new DatasetAccessClass();
                }
            }
        }
        logger.trace("[OUT] createInstance");
    }

    /**
     * Get singleton instance of {@link DatasetAccessClass}. This instance is
     * the only that exists.
     * 
     * @return the unique instance of {@link DatasetAccessClass}.
     */
    public static DatasetAccessClass getInstance() {
        logger.trace("[IN]  getInstance");
        createInstance();
        logger.trace("[OUT] getInstance");
        return INSTANCE;
    }

    /**
     * Constructor. Creates the Dataset Access Class.
     */
    private DatasetAccessClass() {
        logger.trace("[IN]  DatasetAccessClass");

        logger.debug("Generating DB configuration directory...");
        this.dBPath = System.getProperty("user.home") + File.separator
                + ".esgData" + File.separator + "BERKELEY_DB_DIRECTORY";
        // if user.home/.esgData directory doesn't exist then create new
        File directory = new File(dBPath);
        if (!directory.exists()) {
            directory.mkdir();
            // drwxrwxr-x
            Util.setFilePermissions(directory.getPath(), 775);
        }

        logger.debug("Configuring DB...");
        try {
            // Open enviroment
            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setAllowCreate(true); // create it if dosn't exist
            envConfig.setTransactional(true);
            this.myDbEnvironment = new Environment(new File(dBPath), envConfig);

            // Open database
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true); // create it if dosn't exist
            dbConfig.setTransactional(true);
            this.myDatabase = myDbEnvironment.openDatabase(null, DB_NAME,
                    dbConfig);
        } catch (DatabaseException dbe) {
            dbe.printStackTrace();
        }

        logger.debug("Configuring cache...");
        this.cacheManager = CacheManager.create(CACHE_CONFIGURATION_XML);
        this.cache = cacheManager.getCache(CACHE_NAME);

        logger.debug("Cache {} configuration is: \n {}",
                cacheManager.getName(),
                cacheManager.getActiveConfigurationText());

        logger.trace("[OUT] DatasetAccessClass");
    }

    /* read some data, check cache first, otherwise read from sor */
    /**
     * Get {@link Dataset} object
     * 
     * @param instanceID
     * @return the dataset or null if dataset isn't in DB
     * 
     * @throws IOException
     *             if some error happens in cache read or DB read
     */
    public Dataset getDataset(String instanceID) throws IOException {
        logger.trace("[IN]  getDataset");

        logger.debug("Get dataset {} from cache", instanceID);
        try {
            synchronized (cache) {
                if (cache.isKeyInCache(instanceID)) {
                    if (cache.get(instanceID) != null) {
                        Dataset dataset = (Dataset) cache.get(instanceID)
                                .getObjectValue();
                        logger.debug("Dataset {} found in cache", instanceID);
                        logger.trace("[OUT] getDataset");
                        return dataset;
                    }
                }
            }
        } catch (Exception e) {
            logger.error(
                    "Error happens when dataset {} has been obtained from cache {}",
                    instanceID);
            throw new IOException(
                    "Error happens when data has been obtained from cache"
                            + e.getMessage());
        }

        // If dataset isn't in cache get dataset from DB
        logger.debug("Get dataset {} from DB", instanceID);
        Dataset dataset = readDataFromDataStore(instanceID);

        if (dataset != null) {
            logger.debug("Insert dataset {} in cache", instanceID);
            if (dataset != null) {
                synchronized (cache) {
                    cache.put(new Element(instanceID, dataset));
                }
            }
        }

        logger.trace("[OUT] getDataset");
        return dataset;
    }

    /**
     * Get {@link Dataset} from database
     * 
     * @param instanceID
     * @return the dataset or null if dataset isn't in DB
     * @throws UnsupportedEncodingException
     */
    private Dataset readDataFromDataStore(String instanceID)
            throws UnsupportedEncodingException {
        logger.trace("[IN]  readDataFromDataStore");

        DatabaseEntry key = new DatabaseEntry(instanceID.getBytes("UTF-8"));
        DatabaseEntry valueEntry = new DatabaseEntry();
        OperationStatus opStatus = myDatabase.get(null, key, valueEntry, null);

        if (opStatus == OperationStatus.NOTFOUND) {
            return null;
        }

        Dataset dataset = (Dataset) SerializationUtils.deserialize(valueEntry
                .getData());
        logger.debug("Dataset {} found in DB", instanceID);

        logger.trace("[OUT] readDataFromDataStore");
        return dataset;
    }

    /**
     * Put {@link Dataset} in DB and cache
     * 
     * @param dataset
     * @throws IOException
     *             if some error happens in cache read or DB read
     */
    public void putDataset(Dataset dataset) throws IOException {
        logger.trace("[IN]  putDataset");

        logger.debug("Storing dataset in DB..");
        writeDataToDataStore(dataset);

        logger.debug("Store dataset {} in cache", dataset.getInstanceID());
        synchronized (cache) {
            cache.put(new Element(dataset.getInstanceID(), dataset));
        }

        logger.trace("[OUT] putDataset");
    }

    /**
     * Write {@link Dataset} on database
     * 
     * @param dataset
     * @throws IOException
     *             if some error happens writing in BD
     */
    private void writeDataToDataStore(Dataset dataset) throws IOException {
        logger.trace("[IN]  writeDataToDataStore");

        String instanceID = dataset.getInstanceID();
        Transaction txn = myDbEnvironment.beginTransaction(null, null);

        try {
            DatabaseEntry keyEntry = new DatabaseEntry(
                    instanceID.getBytes("UTF-8"));
            DatabaseEntry valueEntry = new DatabaseEntry(
                    SerializationUtils.serialize(dataset));

            myDatabase.put(txn, keyEntry, valueEntry);
            txn.commit();
            logger.debug("Dataset {} has stored in DB", instanceID);
        } catch (IOException e) {
            txn.abort();
            logger.error("Error {} wrinting dataset {}. Write aborted", e,
                    instanceID);
            throw e;
        }

        logger.trace("[OUT] writeDataToDataStore");
    }

    /**
     * Remove {@link Dataset} from DB and cache
     * 
     * @param instanceID
     *            of dataset
     * 
     * @return the removed dataset or null if dataset wasn't in DB
     * @throws IOException
     *             if some error happens in BD
     */
    public Dataset removeDataset(String instanceID) throws IOException {
        logger.trace("[IN]  removeDataset");

        logger.debug("Checking id dataset {} exists in DB..", instanceID);
        Dataset dataset = readDataFromDataStore(instanceID);
        // If dataset is null then not exists in DB
        if (dataset == null) {
            logger.debug("Dataset {} isn't in DB");
            logger.trace("[OUT] removeDataset");
            return null;
        }

        // Blocks cache
        synchronized (cache) {
            logger.debug("Removing dataset...");
            if (cache.isKeyInCache(instanceID)) {
                cache.remove(instanceID);
            }
            logger.debug("Dataset {} removed from cache");

            Transaction txn = myDbEnvironment.beginTransaction(null, null);
            try {
                DatabaseEntry keyEntry = new DatabaseEntry(
                        instanceID.getBytes("UTF-8"));

                myDatabase.delete(txn, keyEntry);
                txn.commit();
                logger.debug("Dataset {} has stored in DB", instanceID);
            } catch (IOException e) {
                txn.abort();
                logger.error("Error {} wrinting dataset {}. Write aborted", e,
                        instanceID);
                throw e;
            }
            logger.debug("Dataset {} removed from DB");
        }

        logger.trace("[OUT] removeDataset");
        return dataset;
    }
}
