package es.unican.meteo.esgf.petition;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import es.unican.meteo.esgf.search.Dataset;
import es.unican.meteo.esgf.search.DatasetFile;
import es.unican.meteo.esgf.search.Metadata;
import es.unican.meteo.esgf.search.RESTfulSearch;
import es.unican.meteo.esgf.search.Record;
import es.unican.meteo.esgf.search.RecordType;
import es.unican.meteo.esgf.search.Replica;
import es.unican.meteo.esgf.search.SearchCategoryFacet;
import es.unican.meteo.esgf.search.SearchCategoryValue;
import es.unican.meteo.esgf.search.Service;

/**
 * Class This class is responsible for sending of search requests to ESGF and
 * process the response for make the appropiate objects
 * 
 * @author Karem Terry
 * 
 */
public class RequestManager {

    /** Configuration file. */
    private static final String CONFIG_FILE = "config.txt";

    /** Logger. */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(RequestManager.class);

    /**
     * MAX_NUMBER_OF_RECORDS is the maximum number of records that a node ESGF
     * can process without to be throw a "server out of time" error. It is an
     * approximation
     */
    private static final int MAX_NUMBER_OF_RECORDS = 1000;

    /**
     * Converts a response ESGF search service with JSON format in a set of
     * {@link Record}
     * 
     * @param content
     *            response ESGF search service with JSON format
     * @return a set of {@link Record}
     * @throws IOException
     *             if exist any error in content that will be converted
     */
    private static Set<Record> convertJSONResponseInRecords(String content)
            throws IOException {
        logger.trace("[IN]  convertJSONResponseInRecords");

        // Set of records
        Set<Record> records = new HashSet<Record>();

        try {
            logger.debug("Getting JSON object from source");
            JSONObject json = new JSONObject(content);

            // Get response dictionary
            JSONObject response = json.getJSONObject("response");

            // Documents, jsonRecords is an array of dictionaries
            // where each dictionary is a record
            JSONArray jsonDatasets = response.getJSONArray("docs");

            // For each record in jsonDatasets
            for (int i = 0; i < jsonDatasets.length(); i++) {
                // Aux
                Record record = new Record();

                // Converts a JSON document to a record object
                fillRecord(jsonDatasets.getJSONObject(i), record);

                // Add record
                records.add(record);
            }
        } catch (JSONException e) {
            logger.error("json exception is thrown");
            e.printStackTrace();
            throw new IOException();
        }

        logger.trace("[OUT] convertJSONResponseInRecords");
        return records;
    }

    /**
     * Private static method that fills a instance of a parent class
     * {@link Record}
     * 
     * Check if id, instance_id and master_id of file records haven't a wrong id
     * (ids of files that finish with ".nc_0" or "nc_1" instead of .nc). In
     * those cases correct them and notify warning.
     * 
     * @param jsonRecord
     *            JSON representation of a record
     * @param record
     *            instance of {@link Record} that will be filled with info
     *            contained in a JSONOBject
     * 
     * @return ESGF record in a {@link Record} object
     */
    private static Record fillRecord(JSONObject jsonRecord, Record record)
            throws JSONException {
        logger.trace("[IN]  fillRecord");

        // Aux
        String metaName;
        JSONArray jsonArray;
        List<String> listString;
        List<Service> listServices;
        String dateStr;
        Calendar calendar;
        Date date;

        logger.debug("Getting record metadata from json");
        // For each metadata defined in enum Metadata
        for (Metadata metadata : Metadata.values()) {

            // String of metadata
            metaName = metadata.name().toLowerCase();

            // If json document has this metadata
            if (jsonRecord.has(metaName)) {

                // Add metadata in new Document
                switch (metadata) {
                    case ACCESS:
                        // access array string
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listServices = new LinkedList<Service>();
                        // For each element in json array

                        for (int j = 0; j < jsonArray.length(); j++) {
                            String url = jsonArray.getString(j);

                            if (url != null) {
                                String serviceUrl = url.substring(url
                                        .lastIndexOf("|") + 1);

                                listServices.add(Service.valueOf(serviceUrl
                                        .toUpperCase()));
                            }

                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listServices);
                    break;
                    case CF_STANDARD_NAME:
                        // Value is array of CF Standard Name strings
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);

                    break;
                    case CHECKSUM:
                        // Checksum is returned in an array of strings
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Always this array only have one element and
                        // this element is the checksum of record
                        record.addMetadata(metadata, listString.get(0));
                    break;
                    case CHECKSUM_TYPE:
                        // Checksum type is returned in an array of strings
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Always this array only have one element and
                        // this element is the type of checksum record
                        record.addMetadata(metadata, listString.get(0));
                    break;
                    case CMOR_TABLE:
                        // Value is MIP Table String
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case DATASET_ID_TEMPLATE_:
                        // Value is template of dataset id
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case DATA_NODE:

                        String adressDataNode = jsonRecord.getString(metaName);

                        if (adressDataNode.length() > 7) {
                            // if isn't complete url
                            if (!adressDataNode.substring(0, 7).equals(
                                    "http://")) {
                                adressDataNode = "http://" + adressDataNode;
                            }
                        } else {
                            adressDataNode = "http://" + adressDataNode;
                        }

                        // Data Node string
                        record.addMetadata(metadata, adressDataNode);
                    break;
                    case DATETIME_START:
                        // Dataset simulation start
                        dateStr = jsonRecord.getString(metaName);

                        // Converts string to calendar
                        date = parseDate(dateStr);
                        calendar = Calendar.getInstance();
                        calendar.setTime(date);

                        // Add metadata in new document
                        record.addMetadata(metadata, calendar);

                    break;
                    case DATETIME_STOP:
                        // Dataset simulation stop
                        dateStr = jsonRecord.getString(metaName);

                        // Converts string to calendar
                        date = parseDate(dateStr);
                        calendar = Calendar.getInstance();
                        calendar.setTime(date);

                        // Add metadata in new document
                        record.addMetadata(metadata, calendar);
                    break;
                    case DESCRIPTION:
                        // Record (longer) description
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case DRS_ID:
                        // Templated string assigned to a Dataset
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case EAST_DEGREES:
                        // East degrees of dataset (type double)
                        record.addMetadata(metadata,
                                jsonRecord.getDouble(metaName));

                    break;
                    case ENSEMBLE:
                        // ensemble of dataset
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case EXPERIMENT:
                        // Experiment String
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case EXPERIMENT_FAMILY:
                        // Experiment family String
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case FORCING:
                        // ? String
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case FORMAT:
                        // format of dataset
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case ID:
                        // // only when record is a file
                        // // If id have ".nc_0" or others instead of .nc
                        // // Then warning and correct id
                        //
                        // // file id have this form
                        // //
                        // project.output.model[...]_2000010106-2006010100.nc|dataNode
                        // // dataset id have this form
                        // //
                        // project.output.model[...]_2000010106-2006010100|dataNode
                        //
                        // String id = jsonRecord.getString(metaName);
                        // String auxString = id.substring(0,
                        // id.lastIndexOf("|"));
                        //
                        // if()
                        //
                        // String nc = auxString.substring(auxString
                        // .lastIndexOf(".nc"));
                        // if (!nc.equals(".nc")) {
                        // // warn
                        // logger.warn("File {} hasn't a correct id", id);
                        //
                        // logger.debug("Correcting an incorrect id", id);
                        // // correct id
                        // String newID = id.substring(0, id.lastIndexOf("|"));
                        // newID = newID
                        // .substring(0, newID.lastIndexOf(".nc"));
                        // newID = newID + ".nc";
                        // newID = newID + id.substring(id.lastIndexOf("|"));
                        //
                        // record.addMetadata(metadata,
                        // jsonRecord.getString(newID));
                        // } else {
                        // // if id is correct then
                        // record.addMetadata(metadata,
                        // jsonRecord.getString(id));
                        // }

                        // Universally id String
                        record.addMetadata(metadata,
                                jsonRecord.getString(metaName));

                    break;
                    case INDEX_NODE:
                        String adressIndexNode = jsonRecord.getString(metaName);

                        if (adressIndexNode.length() > 7) {
                            // if isn't complete url
                            if (!adressIndexNode.substring(0, 7).equals(
                                    "http://")) {
                                adressIndexNode = "http://" + adressIndexNode;
                            }
                        } else {
                            adressIndexNode = "http://" + adressIndexNode;
                        }

                        // the node where the data is published String
                        record.addMetadata(metadata, adressIndexNode);
                    break;
                    case INSTITUTE:
                        // Institute that datasets belongs
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case INSTANCE_ID:

                        // // if hasn't metadata type not correct incorrect id
                        // if (!jsonRecord.has(Metadata.TYPE.name())) {
                        // record.setInstanceID(jsonRecord.getString(metaName));
                        // record.addMetadata(metadata,
                        // jsonRecord.getString(metaName));
                        // break;
                        // }
                        //
                        // // If record is file
                        // if (jsonRecord.getString(
                        // Metadata.TYPE.name().toLowerCase()).equals(
                        // "File")) {
                        // // only when record is a file
                        // // If instance_id have ".nc_0" or others instead of
                        // // .nc then warning and correct id
                        //
                        // // get file id that have this form
                        // // project.output.model[...]_2000010106-2006010100.nc
                        // String id = jsonRecord.getString(metaName);
                        //
                        // String nc = id.substring(id.lastIndexOf(".nc"));
                        // if (!nc.equals(".nc")) {
                        // // warn
                        // logger.warn(
                        // "File {} hasn't a correct instance_id",
                        // id);
                        //
                        // logger.debug(
                        // "Correcting an incorrect instance_id",
                        // id);
                        //
                        // // correct id
                        // String newID = id.substring(0,
                        // id.lastIndexOf(".nc"));
                        // newID = newID + ".nc";
                        //
                        // record.setInstanceID(newID);
                        // record.addMetadata(metadata,
                        // jsonRecord.getString(newID));
                        // } else {
                        // // if id is correct then
                        // record.setInstanceID(id);
                        // record.addMetadata(metadata,
                        // jsonRecord.getString(id));
                        // }
                        // } else { // If record isn't file
                        // Id same of all replicas but different for
                        // each version. String
                        record.setInstanceID(jsonRecord.getString(metaName));
                        record.addMetadata(metadata,
                                jsonRecord.getString(metaName));

                    break;
                    case LATEST:
                        // Indicates wether the record is the latest
                        // available version, or a previous version
                        record.addMetadata(metadata,
                                jsonRecord.getBoolean(metaName));
                    break;
                    case MASTER_ID:

                        // // if hasn't metadata type not correct incorrect id
                        // if (!jsonRecord.has(Metadata.TYPE.name())) {
                        // record.addMetadata(metadata,
                        // jsonRecord.getString(metaName));
                        // break;
                        // }
                        //
                        // // If record is file
                        // if (jsonRecord.getString(
                        // Metadata.TYPE.name().toLowerCase()).equals(
                        // "File")) {
                        // // only when record is a file
                        // // If master_id have ".nc_0" or others instead of
                        // // .nc then warning and correct id
                        //
                        // // get file id that have this form
                        // // project.output.model[...]_2000010106-2006010100.nc
                        // String id = jsonRecord.getString(metaName);
                        //
                        // String nc = id.substring(id.lastIndexOf(".nc"));
                        // if (!nc.equals(".nc")) {
                        // // warn
                        // logger.warn(
                        // "File {} hasn't a correct master_id",
                        // id);
                        //
                        // logger.debug(
                        // "Correcting an incorrect master_id", id);
                        //
                        // // correct id
                        // String newID = id.substring(0,
                        // id.lastIndexOf(".nc"));
                        // newID = newID + ".nc";
                        //
                        // record.addMetadata(metadata,
                        // jsonRecord.getString(newID));
                        // } else {
                        // // if id is correct then
                        // record.addMetadata(metadata,
                        // jsonRecord.getString(id));
                        // }
                        // } else { // If record isn't file
                        // String that is identical for the master and
                        // all replicas String
                        record.addMetadata(metadata,
                                jsonRecord.getString(metaName));

                    break;
                    case METADATA_FORMAT:
                        // Format of metadata String
                        record.addMetadata(metadata,
                                jsonRecord.getString(metaName));
                    break;
                    case MODEL:
                        // Model of dataset String
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case NORTH_DEGREES:
                        // North degrees of dataset (type double)
                        record.addMetadata(metadata,
                                jsonRecord.getDouble(metaName));
                    break;
                    case NUMBER_OF_FILES:
                        // number of files contained in a dataset int
                        record.addMetadata(metadata,
                                jsonRecord.getInt(metaName));
                    break;
                    case NUMBER_OF_AGGREGATIONS:
                        // number of aggregations in a dataset int
                        record.addMetadata(metadata,
                                jsonRecord.getInt(metaName));
                    break;
                    case PRODUCT:
                        // Product cmpi5 array string?
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case PROJECT:
                        // Project
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case REALM:
                        // Realm of dataset array of string
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case REPLICA:
                        // A flag that is set to false for master
                        // records, true for replica records. boolean
                        record.addMetadata(metadata,
                                jsonRecord.getBoolean(metaName));
                    break;
                    case SIZE:
                        // bytes size of dataset. Int coverted
                        // in string with human sense (b,Gb,Tb,...)
                        long bytes = Math.abs(jsonRecord.getLong(metaName));

                        record.addMetadata(metadata, bytes);

                    break;
                    case SOUTH_DEGREES:
                        // South degrees of dataset (type double)
                        record.addMetadata(metadata,
                                jsonRecord.getDouble(metaName));
                    break;
                    case SOURCE_ID:
                        // Time frequency data string
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case TIMESTAMP:
                        // The date and time when the record was last
                        // modified. Calendar
                        dateStr = jsonRecord.getString(metaName);

                        // Converts string to calendar
                        date = parseDate(dateStr);
                        calendar = Calendar.getInstance();
                        calendar.setTime(date);

                        // Add metadata in new document
                        record.addMetadata(metadata, calendar);
                    break;
                    case TIME_FREQUENCY:
                        // Time frequency data string
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case TITLE:
                        // Short title for dataset. String
                        record.addMetadata(metadata,
                                jsonRecord.getString(metaName));
                    break;
                    case TYPE:
                        // Denotes the intrinsic type of the record.
                        // String
                        record.addMetadata(metadata,
                                jsonRecord.getString(metaName));
                    break;
                    case URL:
                        // Specific url to access dataset
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        record.addMetadata(metadata, listString);
                    break;
                    case VARIABLE:
                        // Variables of datataset. Array of string
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case VARIABLE_LONG_NAME:
                        // Variable long names of datataset. Array of
                        // string
                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    case VERSION:
                        // Record version. String
                        record.addMetadata(metadata,
                                jsonRecord.getString(metaName));
                    break;
                    case WEST_DEGREES:
                        // West degrees of dataset (type double)
                        record.addMetadata(metadata,
                                jsonRecord.getDouble(metaName));
                    break;
                    case XLINK:
                        // Record version. Array of String

                        jsonArray = jsonRecord.getJSONArray(metaName);

                        // clear array of strings
                        listString = new LinkedList<String>();
                        // For each element in json array
                        for (int j = 0; j < jsonArray.length(); j++) {
                            listString.add(jsonArray.getString(j));
                        }

                        // Add metadata in new document
                        record.addMetadata(metadata, listString);
                    break;
                    default:
                    break;
                }
            }
        }

        logger.debug("Record has been created");
        logger.trace("[OUT] fillRecord");
        return record;
    }

    /**
     * Get content from an ESGF search service request
     * 
     * @param search
     *            search service request
     * @return content in an {@link String} object
     * @throws IOException
     *             if happens an error in http request
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200
     */

    private static String getContentFromSearch(RESTfulSearch search)
            throws HTTPStatusCodeException, IOException {

        logger.trace("[IN]  getContentFromSearch");

        logger.debug("Generate service url");
        String url = search.generateServiceURL().toString();
        logger.debug("The service url are generated. {}", url);

        logger.debug("Set and configure http conection");
        // Set http client
        HttpClient client = new HttpClient();
        // Prepare http request
        GetMethod request = new GetMethod(url);

        StringBuilder stringBuilder = new StringBuilder();

        logger.debug("Executing http request");

        try {
            // Status code of request response
            int statusCode = client.executeMethod(request);
            logger.debug("Request executed");

            logger.debug("Putting response in buffer");
            BufferedReader rd = new BufferedReader(new InputStreamReader(
                    request.getResponseBodyAsStream()));

            // If response is successful get the response
            if (statusCode == HttpStatus.SC_OK) {
                // Do the string
                String line = "";

                // while read line is not null
                while ((line = rd.readLine()) != null) {
                    stringBuilder.append(line);

                }
            } else { // else if response isn't successful
                logger.error(
                        "HTTP request isn't successful. Status code : {} in search {}",
                        statusCode, url);
                throw new HTTPStatusCodeException(statusCode);
            }
        } catch (Exception e) {
            logger.error("Exception : {} in {}", e.getStackTrace(), url);
            throw new IOException();
        }

        // When HttpClient instance is no longer needed,
        // shut down the connection manager to ensure
        // immediate deallocation of all system resources
        // client.getConnectionManager().shutdown();
        request.releaseConnection();

        logger.trace("[OUT] getContentFromSearch");
        return stringBuilder.toString();
    }

    /**
     * Get all instance_id of records that satisfy the constraints of search
     * 
     * @param search
     * @return all instance_id of records that satisfy the constraints of search
     * @throws IOException
     * @throws HTTPStatusCodeException
     */
    public static Set<String> getDatasetInstanceIDsFromSearch(
            RESTfulSearch search) throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  getInstanceIdOfFilesToDownload");
        Set<String> instanceIds = new HashSet<String>();

        logger.debug("Create new search");
        RESTfulSearch newSearch;

        try {
            newSearch = (RESTfulSearch) search.clone();

            logger.debug("Set parameters:type=dataset, fields=instance_id");
            // Set parameters for search files. This files must belong to a
            // dataset with actual id value and only must contain his
            // instance_id When we limit the request faster
            newSearch.getParameters().setType(RecordType.DATASET); // type=dataset
            Set<Metadata> fields = new HashSet<Metadata>(); // fields=instance_id
            fields.add(Metadata.INSTANCE_ID);
            newSearch.getParameters().setFields(fields);

            logger.debug("Doing the request:  {}",
                    newSearch.generateServiceURL());
            Set<Record> fileRecords = new HashSet<Record>();
            try {
                fileRecords = getRecordsFromSearch(newSearch);
            } catch (IOException e) {
                logger.error(
                        "Error in getInstanceIdOfFilesToDownload. {} : \n",
                        newSearch.generateServiceURL(), e.getStackTrace());
                throw e;
            } catch (HTTPStatusCodeException e) {
                logger.error(
                        "Error in getInstanceIdOfFilesToDownload. {} : \n",
                        newSearch.generateServiceURL(), e.getStackTrace());
                throw e;
            }

            logger.debug("Getting all isntance_id of file records returned");
            for (Record record : fileRecords) {
                instanceIds.add((String) record
                        .getMetadata(Metadata.INSTANCE_ID));
            }

        } catch (CloneNotSupportedException e1) {
            logger.warn("CloneNotSupportedException in clone search, this should not happen.");
        }

        return instanceIds;
    }

    /**
     * Returns facet values and its counts that exist in ESGF from a search
     * service of ESGF
     * 
     * @param search
     *            search service request
     * @return a {@link Map} where the key is a {@link SearchCategoryFacet} and
     *         the value is a {@link List} of {@link SearchCategoryValue}
     * 
     * @throws IOException
     *             if happens an error in ESGF search service
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200
     */
    public static Map<SearchCategoryFacet, List<SearchCategoryValue>> getFacetsAndValuesFromSearch(
            RESTfulSearch search) throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  getFacetsAndValuesFromSearch");

        // Initialize facet map
        Map<SearchCategoryFacet, List<SearchCategoryValue>> facetMap = new HashMap<SearchCategoryFacet, List<SearchCategoryValue>>();

        logger.debug("Create new search");
        RESTfulSearch newSearch;

        try {
            newSearch = (RESTfulSearch) search.clone();
            newSearch.getParameters().setLimit(0);

            // Facet parameters to all facets, save previous
            Set<String> facets = newSearch.getParameters().getFacets();
            HashSet<String> allFacets = new HashSet<String>();
            allFacets.add("*");
            newSearch.getParameters().setFacets(allFacets);

            // XXX esto hace falta? O_ó
            // Set fields parameter in blank to obtains correct documents,
            // because predefined field value in ESGF is *, save previous
            Set<Metadata> fields = newSearch.getParameters().getFields();
            newSearch.getParameters().setFields(new HashSet<Metadata>());

            logger.debug("Getting content from an ESGF search service request");
            // Get string from RESTfulSearch
            String responseContent = getContentFromSearch(newSearch);

            logger.debug("Getting number of records");
            // Initialize num of records
            int numOfRecords = 0;
            // JSON object
            try {

                // Create json object from response content and get response
                // dictionary
                JSONObject json = new JSONObject(responseContent);

                // Fill facet tree
                JSONObject facetCounts = json.getJSONObject("facet_counts");
                JSONObject facetFields = facetCounts
                        .getJSONObject("facet_fields");

                // Aux
                List<SearchCategoryValue> listFacetValue = null;

                // Object facetFields= { facetName: array of values, ...:...]
                // for each CMIP5 facetfacet
                for (SearchCategoryFacet facet : SearchCategoryFacet.values()) {
                    JSONArray facetValues = facetFields.getJSONArray(facet
                            .name().toLowerCase());

                    // New list of facet values
                    listFacetValue = new LinkedList<SearchCategoryValue>();

                    // JSON ARRAY -> (nameValue, count, nameValue2, count2, ...)
                    // for each [namevalue, countOfdatasets] element add new
                    // FacetValue to list
                    int numValues = 0;
                    for (int i = 0; i < facetValues.length(); i = i + 2) {
                        SearchCategoryValue facetValue = new SearchCategoryValue();
                        facetValue.setValue(facetValues.getString(i));
                        facetValue.setCount(facetValues.getInt(i + 1));

                        // Add facet value into list of facet values
                        listFacetValue.add(facetValue);

                        // NumValues= NumValues + 1
                        numValues++;
                    }

                    // Put list of facet values in facetMap
                    facetMap.put(facet, listFacetValue);
                }

            } catch (JSONException e) {
                logger.error("JSON exception is thrown");
                e.printStackTrace();
                throw new IOException();
            }
        } catch (CloneNotSupportedException e1) {
            logger.warn("CloneNotSupportedException in clone search, this should not happen.");
        }

        logger.trace("[OUT] getFacetsAndValuesFromSearch");
        return facetMap;
    }

    /**
     * Get all file instance id of file that satisfy the constraints of search
     * and also belongs to a specific dataset
     * 
     * @param search
     *            search service request
     * @param id
     *            dataset id
     * @return all file instance id of file that satisfy the constraints of
     *         search and also belongs to a specific dataset
     * @throws IOException
     * @throws HTTPStatusCodeException
     */
    public static Set<String> getInstanceIDOfFilesToDownload(
            RESTfulSearch search, String id) throws IOException,
            HTTPStatusCodeException {
        logger.trace("[IN]  getInstanceIdOfFilesToDownload");
        Set<String> instanceIds = new HashSet<String>();

        logger.debug("Create new search");
        RESTfulSearch newSearch;

        try {
            newSearch = (RESTfulSearch) search.clone();

            logger.debug("Set parameters:type=file, dataset_id=id, fields=instance_id");
            // Set parameters for search files. This files must belong to a
            // dataset with actual id value and only must contain his
            // instance_id When we limit the request faster
            newSearch.getParameters().setType(RecordType.FILE); // type=file
            newSearch.getParameters().setDatasetId(id); // dataset_id=id
            Set<Metadata> fields = new HashSet<Metadata>(); // fields=instance_id
            fields.add(Metadata.INSTANCE_ID);
            newSearch.getParameters().setFields(fields);

            logger.debug("Doing the request:  {}",
                    newSearch.generateServiceURL());
            Set<Record> fileRecords = new HashSet<Record>();
            try {
                fileRecords = getRecordsFromSearch(newSearch);
            } catch (IOException e) {
                logger.error(
                        "Error in getInstanceIdOfFilesToDownload. {} : \n",
                        newSearch.generateServiceURL(), e.getStackTrace());
                throw e;
            } catch (HTTPStatusCodeException e) {
                logger.error(
                        "Error in getInstanceIdOfFilesToDownload. {} : \n",
                        newSearch.generateServiceURL(), e.getStackTrace());
                throw e;
            }

            logger.debug("Getting all isntance_id of file records returned");
            for (Record record : fileRecords) {
                instanceIds.add((String) record
                        .getMetadata(Metadata.INSTANCE_ID));
            }

        } catch (CloneNotSupportedException e1) {
            logger.warn("CloneNotSupportedException in clone search, this should not happen.");
        }

        return instanceIds;
    }

    public static List<String> getESGFNodes() throws Exception {

        // TODO get from prefs or from configuration file? :S
        File file = null;
        BufferedReader br = null;
        FileReader fr = null;

        List<String> nodeList = new LinkedList<String>();

        try {
            // Open file and create a BufferedReader for read
            file = new File(CONFIG_FILE);
            fr = new java.io.FileReader(file);
            br = new BufferedReader(fr);

            // read file
            String line;
            while ((line = br.readLine()) != null) {

                // tag nodes:
                if (line.substring(0, line.indexOf(":")).equalsIgnoreCase(
                        "nodes")) {
                    String nodesStr = line.substring(line.indexOf(":") + 1);
                    String[] nodes = nodesStr.split(",");
                    for (String node : nodes) {
                        nodeList.add(node.trim());

                    }

                }
            }
        } catch (Exception e) {
            throw e;
        } finally {
            // In finally close file for be sure that file is closed if is
            // thrown an Exception
            try {
                if (null != fr) {
                    fr.close();
                }
            } catch (Exception e2) {
                throw e2;
            }
        }

        return nodeList;
    }

    /**
     * Get number of records that are returned by a request from search service
     * of ESGF
     * 
     * @param search
     *            search service request
     * @param allReplicas
     *            <ul>
     *            <li><strong>true</strong> for get number of records (all
     *            replicas in different nodes) that are returned by a request
     *            from search</li>
     *            <li><strong>false</strong> for get number of master records
     *            (original replica in a node) that are returned by a request
     *            from search</li>
     *            </ul>
     * @param retryInAllESGFNodes
     *            <ul>
     *            <li><strong>true</strong> to retrying search request in
     *            another index nodes of ESGF if the search request fails</li>
     *            <li><strong>false</strong> to only send one request to the
     *            index node that was configured in search (
     *            {@link RESTfulSearch} )</li>
     *            </ul>
     * 
     * @return number for files for this search service request
     * @throws IOException
     *             <ul>
     *             <li>if parameter <strong>
     *             <em>retryInAllESGFNodes is false</em> </strong> and happens
     *             some error in ESGF search service in the index node that was
     *             configured in search</li>
     *             <li>if parameter <strong><em>retryInAllESGFNodes is true</em>
     *             </strong>and exist some error in the configuration file or if
     *             the request fails in all known nodes of ESGF</li>
     *             <ul>
     */
    public static int getNumOfRecordsFromSearch(RESTfulSearch search,
            boolean allReplicas, boolean retryInAllESGFNodes)
            throws IOException {
        logger.trace("[IN]  getNumOfRecordsFromSearch");

        logger.debug("Setting new service search. Limit=0");

        RESTfulSearch newSearch;

        // Initialize num of records
        int numOfRecords = -1;
        try {
            newSearch = (RESTfulSearch) search.clone();
            newSearch.getParameters().setLimit(0);
            newSearch.getParameters().setFacets(null);

            if (!allReplicas) {
                newSearch.getParameters().setReplica(Replica.MASTER);
            }

            logger.debug("Getting content from an ESGF search service request");
            // Get string from RESTfulSearch
            String responseContent = getContentFromSearch(newSearch);

            logger.debug("Getting number of records");
            // JSON object
            // Create json object from response content and get response
            // dictionary
            JSONObject json = new JSONObject(responseContent);
            JSONObject response = json.getJSONObject("response");

            // Number of datasets found. This corresponds with the element
            // numFound in "response"
            numOfRecords = response.getInt("numFound");

        } catch (Exception e) {
            if (retryInAllESGFNodes) {
                logger.warn(
                        "Exception obtaining numberOfRecords in the search: {}.",
                        search.generateServiceURL());
                // try in other nodes. Throws IOException if fails in all nodes
                numOfRecords = getNumOfRecordsFromSearchInSomeAnotherNode(
                        search, allReplicas);
                return numOfRecords;
            } else {
                logger.error(
                        "Exception obtaining numberOfRecords in the search: {}.",
                        search.generateServiceURL());
                throw new IOException(
                        "Exception obtaining numberOfRecords in the search "
                                + search.generateServiceURL());
            }
        }

        logger.trace("[OUT] getNumOfRecordsFromSearch");
        return numOfRecords;

    }

    /**
     * Get records that are returned by a request from search service of ESGF.
     * Search in all nodes if is neccesary
     * 
     * @param search
     *            search service request
     * @return set of records that are returned by ESGF
     * @throws IOException
     *             if happens an error in ESGF search service an not be avoided
     *             by reducing the size of the request or searching in all nodes
     * @throws HTTPStatusCodeException
     *             if http status code isn't OK/200
     */
    public static Set<Record> getRecordsFromSearch(RESTfulSearch search)
            throws IOException, HTTPStatusCodeException {
        logger.trace("[IN]  getRecordsFromSearch");

        // Initialize
        Set<Record> records = new HashSet<Record>();
        RESTfulSearch newSearch;

        try {
            newSearch = (RESTfulSearch) search.clone();

            // Get number of recordsthat are returned by a request
            String searchStr = newSearch.generateServiceURL().toString();
            logger.debug("Getting number of records in search {}", searchStr);
            int numberOfRecords = getNumOfRecordsFromSearch(search, true, true);
            logger.debug("Number of records: {}", numberOfRecords);

            if (numberOfRecords == 0) {
                logger.debug("This search haven't records. {}", searchStr);
                return records;
            }

            logger.debug("Generating records");
            // Private method that get the ESGF records by consecutive requests.

            try {
                records = getRecordsFromSearch(newSearch, numberOfRecords);

            } catch (IOException e1) {
                logger.warn(
                        "Error that can not be avoided by reducing the size of the request in Search{}, \n{}",
                        newSearch, e1);
                records = new HashSet<Record>();
                getRecordsFromSearchInSomeAnotherNode(newSearch, records);

                if (records != null & !records.isEmpty()) {
                    return records;
                } else {
                    logger.error(
                            "Error that can not be avoided by reducing the size of the request or searching in all nodes{}, \n{}",
                            newSearch);
                    throw new IOException(
                            "Error that can not be avoided by reducing the size of the request or searching in all nodes");
                }
            }

        } catch (CloneNotSupportedException e2) {
            logger.warn("CloneNotSupportedException in clone search, this should not happen.");
        }

        logger.debug("Records have been generated successfully");
        logger.trace("[OUT] getRecordsFromSearch");

        return records;
    }

    /**
     * Get records that are returned by a request from search service of ESGF.
     * Private method for get the ESGF records by consecutive requests in case
     * that the number of records are more than the max number of records that
     * can be processed. Always is called for the public method
     * getRecordsFromSearch(Search).
     * 
     * 
     * @param search
     *            search service request
     * @param numberOfRecords
     *            number of records that are returned by a request
     * @return set of records that are returned by ESGF
     * @throws IOException
     *             if happens an error that can not be avoided by reducing the
     *             size of the request
     */
    private static Set<Record> getRecordsFromSearch(RESTfulSearch search,
            int numberOfRecords) throws IOException {

        logger.trace("[IN]  private getRecordsFromSearch({})", numberOfRecords);
        // Initialize set of records
        Set<Record> records = new HashSet<Record>();

        logger.debug("Generating records");
        // If number of records of a search are more than the max number of
        // records that can be processed
        if (numberOfRecords > MAX_NUMBER_OF_RECORDS) {
            try {
                // Calls another method that it can call itself recursively in
                // case that necessary to be reduced the number of records
                // requested in each ESGF request.
                records = getRecordsFromSearch(search, numberOfRecords,
                        MAX_NUMBER_OF_RECORDS);
            } catch (IOException e) {
                logger.error(
                        "Error that can not be avoided by reducing the size of the request \n {}",
                        e.getStackTrace());
                throw e;
            }

        } else {
            try {

                // If the number of records does not exceed the maximum number
                // of them than can be processed, the request is sent directly.
                logger.debug("Getting content from an ESGF search service request");
                search.getParameters().setLimit(numberOfRecords);
                // Get content of search request and converts the response in a
                // set of Records
                String responseContent = getContentFromSearch(search);
                records = convertJSONResponseInRecords(responseContent);
            } catch (Exception e) {

                // The maximum number of records that can be processed by
                // petition is set approximately. If an unexpected error occurs
                // calls another method that it can call itself recursively in
                // case that necessary to be reduced the number of records
                // requested in each ESGF request. The method will be called
                // with half the value of max records.
                try {
                    records = getRecordsFromSearch(search, numberOfRecords,
                            MAX_NUMBER_OF_RECORDS / 2);
                    System.out.println("Por esto: " + MAX_NUMBER_OF_RECORDS / 2
                            + ":-/");
                } catch (IOException e1) {
                    logger.error(
                            "Error that can not be avoided by reducing the size of the request \n {}",
                            e1.getStackTrace());
                    throw e1;
                }
            }
        }

        logger.debug("Records have been generated successfully");
        logger.trace("[OUT] private getRecordsFromSearch({})", numberOfRecords);
        return records;
    }

    /**
     * Get records that are returned by a request from search service of ESGF.
     * Private method that it can call itself recursively in case that necessary
     * to be reduced the number of records requested in each ESGF request. Can
     * be called for the private method getRecordsFromSearch(Search,
     * numberOfRecords).
     * 
     * @param search
     *            search service request
     * @param totalNumberOfRecords
     *            number of records that are returned by a request
     * @param maxNumberOfRecords
     *            the maximum number of records than can be processed in a
     *            request
     * @return set of records that are returned by ESGF
     * @throws IOException
     *             if happens an error that can not be avoided by reducing the
     *             size of the request or changing index node
     */
    private static Set<Record> getRecordsFromSearch(RESTfulSearch search,
            int totalNumberOfRecords, int maxNumberOfRecords)
            throws IOException {

        logger.trace("[IN]  private getRecordsFromSearch(Search,{},{})",
                totalNumberOfRecords, maxNumberOfRecords);

        // if max number of records can't be less than 1
        if (maxNumberOfRecords < 1) {
            logger.error("Error that can not be avoided by reducing the size of the request");
            throw new IOException();
        }

        // Initialize set of records
        Set<Record> records = new HashSet<Record>();
        Set<Record> totalRecords = new HashSet<Record>();

        logger.debug("Calculing number of requests.");
        int numberOfRequests = (int) (Math.ceil((double) totalNumberOfRecords
                / (double) (maxNumberOfRecords)));
        search.getParameters().setLimit(maxNumberOfRecords);

        logger.debug("Number of requests are: {}", numberOfRequests);
        for (int i = 0; i < numberOfRequests; i++) {
            logger.debug("Processing request number {} with offset: {}", i,
                    maxNumberOfRecords * i);
            search.getParameters().setOffset(maxNumberOfRecords * i);

            try {
                logger.debug("Getting content from an ESGF search service request");
                String responseContent = getContentFromSearch(search);
                records = convertJSONResponseInRecords(responseContent);
            } catch (Exception e) {
                // If an error happens, it calls itself with half the value of
                // max records
                records = getRecordsFromSearch(search, totalNumberOfRecords,
                        maxNumberOfRecords / 2);
                logger.warn("Unexpeted error in the request: {}",
                        search.generateServiceURL());
            }

            totalRecords.addAll(records);
        }

        logger.trace("[OUT] private getRecordsFromSearch(Search,{},{})",
                totalNumberOfRecords, maxNumberOfRecords);
        return totalRecords;
    }

    /**
     * <p>
     * Private method that fills a set of instance a parent class {@link Record}
     * or child classes ({@link Dataset} or {@link DatasetFile}) with the info
     * of a global search in all ESGF nodes.
     * </p>
     * 
     * <p>
     * If the search fails in all nodes the variable records will be null.
     * </p>
     * 
     * @param search
     *            search service request
     * 
     * @param records
     *            Set of instance of {@link Record} or {@link Dataset} or
     *            {@link DatasetFile} that will be filled
     * @throws IOException
     *             if exist some error in the configuration file
     */
    private static void getRecordsFromSearchInSomeAnotherNode(
            RESTfulSearch search, Set<? extends Record> records)
            throws IOException {

        logger.trace("[IN]  getRecordsFromSearchInSomeAnotherNode");

        logger.debug("Reading nodes from configuration file.");
        List<String> nodes;
        try {
            nodes = getESGFNodes();
        } catch (Exception e1) {
            logger.error("Error in read of configure file");
            // throws io exception
            throw new IOException("Error in read of configure file");
        }

        if (nodes != null) {
            int numberOfNode = 0;
            boolean cont = true;

            logger.debug("Searching records in indexNode: {}",
                    nodes.get(numberOfNode));
            while (cont && numberOfNode < nodes.size()) {

                RESTfulSearch newSearch;
                try {
                    newSearch = (RESTfulSearch) search.clone();

                    newSearch.setIndexNode(nodes.get(numberOfNode));

                    try {

                        int numberOfRecords = RequestManager
                                .getNumOfRecordsFromSearch(newSearch, true,
                                        true);
                        records = getRecordsFromSearch(newSearch,
                                numberOfRecords);
                        cont = false;

                    } catch (IOException e) {
                        logger.warn("Error trying to download {}: {}",
                                newSearch.generateServiceURL(),
                                e.getStackTrace());
                    } catch (Exception e) {
                        logger.warn("Error trying to download {}: {}",
                                newSearch.generateServiceURL(),
                                e.getStackTrace());
                    }

                } catch (CloneNotSupportedException e1) {
                    logger.warn("This search {} isn't cloneable: {}", search,
                            e1.getStackTrace());
                }

                numberOfNode++;
            }

            // if end loops because there aren't more nodes
            if (cont == true) {
                records = null;
                logger.error("Error in search of all ESGF nodes for {}", search);
                // throws io exception
                throw new IOException();
            }
        } else {
            logger.error("Error in read of configure file");
            // throws io exception
            throw new IOException("Error in read of configure file");
        }

        logger.trace("[OUT] getRecordsFromSearchInSomeAnotherNode");
    }

    /**
     * Private method that search the number of records in ESGF that satisfy the
     * constraints defined in a {@link RESTfulSearch}
     * 
     * @param search
     *            search service request
     * @param allReplicas
     *            <ul>
     *            <li><strong>true</strong> for get number of records (all
     *            replicas in different nodes) that are returned by a request
     *            from search</li>
     *            <li><strong>false</strong> for get num of master records
     *            (original replica in a node) that are returned by a request
     *            from search</li>
     *            </ul>
     * 
     * @throws IOException
     *             if exist some error in the configuration file or if the
     *             request fails in all known nodes of ESGF
     */
    private static int getNumOfRecordsFromSearchInSomeAnotherNode(
            RESTfulSearch search, boolean allReplicas) throws IOException {
        logger.trace("[IN]  getNumOfRecordsFromSearchInSomeAnotherNode");

        logger.debug("Reading nodes from configuration file.");
        List<String> nodes;
        try {
            nodes = getESGFNodes();
        } catch (Exception e1) {
            logger.error("Error in read of configure file");
            // throws io exception
            throw new IOException("Error in read of configure file");
        }

        if (nodes != null) {
            int numberOfNode = 0;

            logger.debug("Searching records in indexNode: {}",
                    nodes.get(numberOfNode));
            while (numberOfNode < nodes.size()) {

                RESTfulSearch newSearch;
                int numOfRecords = -1;
                try {
                    newSearch = (RESTfulSearch) search.clone();

                    newSearch.setIndexNode(nodes.get(numberOfNode));
                    newSearch.getParameters().setLimit(0);
                    newSearch.getParameters().setFacets(null);

                    if (!allReplicas) {
                        newSearch.getParameters().setReplica(Replica.MASTER);
                    }
                    try {

                        logger.debug("Getting content from an ESGF search service request");
                        // Get string from RESTfulSearch
                        String responseContent = getContentFromSearch(newSearch);

                        logger.debug("Getting number of records");
                        // JSON object
                        // Create json object from response content and get
                        // response dictionary
                        JSONObject json = new JSONObject(responseContent);
                        JSONObject response = json.getJSONObject("response");

                        // Number of datasets found. This corresponds with the
                        // element numFound in "response"
                        numOfRecords = response.getInt("numFound");

                        logger.trace("[OUT] getNumOfRecordsFromSearchInSomeAnotherNode");
                        return numOfRecords;

                    } catch (Exception e) {
                        logger.warn("Error trying to download {}: {}",
                                newSearch.generateServiceURL(),
                                e.getStackTrace());
                    }

                } catch (CloneNotSupportedException e1) {
                    logger.warn("This search {} isn't cloneable: {}", search,
                            e1.getStackTrace());
                }

                numberOfNode++;
            }

            // if end loops because there aren't more nodes
            logger.error(
                    "Error searching number of records of a search in all ESGF nodes for {}",
                    search.generateServiceURL());

            // throws io exception
            throw new IOException();

        } else {

            logger.error("Error in read of configure file");
            // throws io exception
            throw new IOException("Error in read of configure file");
        }
    }

    /**
     * Private method that parse a {@link String} of a date to a {@link Date}
     * object
     * 
     * @param dateStr
     *            {@link String} of a date
     * @throws IllegalArgumentException
     *             if dateStr argument have an invalid format
     */
    private static Date parseDate(String dateStr)
            throws IllegalArgumentException {
        logger.trace("[IN]  parseDate");
        SimpleDateFormat[] dateFormat = {
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ'Z'"),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ'Z'"),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'") };
        for (SimpleDateFormat simpleDateFormat : dateFormat) {
            try {
                logger.trace("[OUT] parseDate");
                return simpleDateFormat.parse(dateStr);
            } catch (ParseException e) {
            }
        }

        logger.error("Invalid date format: {}", dateStr);
        throw new IllegalArgumentException("Invalid date format: " + dateStr);
    }

}
