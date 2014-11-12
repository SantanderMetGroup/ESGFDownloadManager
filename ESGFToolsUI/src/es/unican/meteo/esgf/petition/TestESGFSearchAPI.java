package es.unican.meteo.esgf.petition;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;

import org.json.JSONException;
import org.json.JSONObject;

import es.unican.meteo.esgf.search.Format;
import es.unican.meteo.esgf.search.Parameters;
import es.unican.meteo.esgf.search.RESTfulSearch;
import es.unican.meteo.esgf.search.RecordType;
import es.unican.meteo.esgf.search.Replica;

public class TestESGFSearchAPI {

    public static void main(String[] args) {

        /* Using builder of an esgf-search petitions . */
        System.out.println("-------------------------------------------");
        System.out.println(" Test builder of an esgf-search petitions ");
        System.out.println("-------------------------------------------");

        /*
         * 1. Request that list count of experiments of datasets in project
         * CMIP5 in format XML.
         */

        // create a search request
        RESTfulSearch search1 = new RESTfulSearch("pcmdi9.llnl.gov");

        // set parameters
        Parameters parameters1 = new Parameters();
        parameters1.setFormat(Format.XML);
        parameters1.setDistrib(true);
        parameters1.setLimit(0); // not return datasets
        parameters1.setType(RecordType.DATASET);
        parameters1.setReplica(Replica.MASTER); // only count original replicas
        parameters1.setProject(Arrays.asList(new String[] { "CMIP5" }));
        // configure list of counts
        parameters1.setFacets(new HashSet<String>(Arrays
                .asList(new String[] { "experiment" })));

        // configure search with parameters and generate URL
        search1.setParameters(parameters1);
        URL url = search1.generateServiceURL();

        // print search 1
        System.out
                .println("1.Request that list experiments of datasets in project "
                        + "CMIP5 in format XML");// print search
        System.out.println(url);
        System.out.println();

        /*
         * 2. Request that list count of experiments of datasets in project
         * CMIP5 in format JSON.
         */

        // that search is equal to the before search but with different
        // parameter format=null;
        RESTfulSearch search2 = null;
        try {
            // copy search 1 and generate url
            search2 = (RESTfulSearch) search1.clone();
            search2.getParameters().setFormat(Format.JSON);
            url = search2.generateServiceURL();

            // print search 2
            System.out
                    .println("2.Request that list experiments of datasets in project "
                            + "CMIP5 in format JSON");// print search
            System.out.println(url);
            System.out.println();

        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            System.out
                    .println("ERROR: The second search can't be print because CloneNotSupportedException has been raised");
            System.out.println();
        }

        /*
         * 3. Request that list count of models of datasets that project=CMIP5
         * AND experiment=1pctCO2 in format XML.
         */

        // create a search request
        RESTfulSearch search3 = new RESTfulSearch("pcmdi9.llnl.gov");

        // set parameters
        Parameters parameters3 = new Parameters();
        parameters3.setFormat(Format.XML);
        parameters3.setDistrib(true);
        parameters3.setLimit(0); // not return datasets
        parameters3.setType(RecordType.DATASET);
        parameters3.setReplica(Replica.MASTER); // only count original replicas
        parameters3.setProject(Arrays.asList(new String[] { "CMIP5" }));
        parameters3.setExperiment(Arrays.asList(new String[] { "1pctCO2" }));

        // configure list of counts
        parameters3.setFacets(new HashSet<String>(Arrays
                .asList(new String[] { "model" })));

        // configure search with parameters and generate URL
        search3.setParameters(parameters3);
        url = search3.generateServiceURL();

        // print search 3
        System.out
                .println("3. Request that list count of models of datasets that"
                        + " project=CMIP5 AND experiment=1pctCO2 in format XML.");
        System.out.println(url);
        System.out.println();

        /*
         * 4. Request that list count of models of datasets that project=CMIP5
         * AND experiment=1pctCO2 and have zg in dayly or ta in format 6hr all
         * in format XML.
         */

        // for this example we will clone search3
        RESTfulSearch search4 = null;
        try {
            // copy search 4
            search4 = (RESTfulSearch) search3.clone();

            // add variable constraint
            search4.getParameters().setVariable(
                    Arrays.asList(new String[] { "zg", "ta" }));
            // add query constraint

            search4.getParameters().setQuery(
                    "(variable:zg AND time_frequency:day)"
                            + "OR(variable:ta AND time_frequency:6hr)");

            // generate url
            url = search4.generateServiceURL();

            // print search 4
            System.out
                    .println("4. Request that list count of models of datasets that project=CMIP5"
                            + "AND experiment=1pctCO2 and have zg in dayly \"OR\" ta in format 6h"
                            + "all in format XML.");
            System.out.println(url);
            System.out.println();

        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            System.out
                    .println("ERROR: The four search can't be print because CloneNotSupportedException has been raised");
            System.out.println();
        }

        /*
         * 5. Request that list count of models of datasets that project=CMIP5
         * AND experiment=1pctCO2 and have:
         * 
         * - pr AND tas
         * 
         * - pr in daily and tas in 6hr
         * 
         * all in format XML.
         */

        // This search only must change the query and values in search4 then
        // for this example we will clone search4
        RESTfulSearch search5 = null;
        try {
            // copy search 4
            search5 = (RESTfulSearch) search4.clone();

            // change variables
            search5.getParameters().setVariable(
                    Arrays.asList(new String[] { "pr", "tas" }));

            // change query constraint
            search5.getParameters().setQuery(
                    "(variable:pr AND variable:tas)AND"
                            + "((variable:pr AND time_frequency:day)"
                            + "OR(variable:tas AND time_frequency:6hr))");

            // generate url
            url = search5.generateServiceURL();

            // print search 5
            System.out
                    .println("5. Request that list count of models of datasets that"
                            + " project=CMIP5 AND experiment=1pctCO2 and have:\n"
                            + "- pr AND ta \n- pr in daily and ta in 6hr"
                            + " \nall in format XML.");
            System.out.println(url);
            System.out.println();

        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            System.out
                    .println("ERROR: The five search can't be print because CloneNotSupportedException has been raised");
            System.out.println();
        }

        /*
         * 6. Note that request pr in dayly and ta in 6hr in the search 5 is the
         * same that search datasets in day and 6hr. In this case, its equal to
         * a request that list count of models of datasets that project=CMIP5
         * AND experiment=1pctCO2 AND time_frequency= daily OR 6hr and have:
         * 
         * - pr AND tas variables
         * 
         * in format XML.
         */
        // create a search request
        RESTfulSearch search6 = new RESTfulSearch("pcmdi9.llnl.gov");

        // set parameters
        Parameters parameters6 = new Parameters();
        parameters6.setFormat(Format.XML);
        parameters6.setDistrib(true);
        parameters6.setLimit(0); // not return datasets
        parameters6.setType(RecordType.DATASET);
        parameters6.setReplica(Replica.MASTER); // only count original replicas

        parameters6.setProject(Arrays.asList(new String[] { "CMIP5" }));
        parameters6.setExperiment(Arrays.asList(new String[] { "1pctCO2" }));
        parameters6.setVariable(Arrays.asList(new String[] { "pr", "tas" }));
        parameters6.setTimeFrequency(Arrays
                .asList(new String[] { "day", "6hr" }));
        parameters6.setQuery("(variable:pr AND variable:tas)");

        // configure list of counts
        parameters6.setFacets(new HashSet<String>(Arrays
                .asList(new String[] { "model" })));

        // configure search with parameters and generate URL
        search6.setParameters(parameters6);
        url = search6.generateServiceURL();

        // print search 6
        System.out
                .println("6. Note that request pr in dayly and ta in 6hr in the search 5 is the"
                        + "same that search datasets in day and 6hr. In this case, its equal"
                        + " to a request that list count of models of datasets that "
                        + "project=CMIP5 AND experiment=1pctCO2 AND time_frequency= daily"
                        + " OR 6hr and have:\n - pr AND tas variables\n in format XML.");
        System.out.println(url);
        System.out.println();

        /* Using request manager of an esgf-search petitions . */
        System.out.println();
        System.out.println("------------------------------------------------");
        System.out.println("Test request manager of an esgf-search petitions");
        System.out.println("------------------------------------------------");
        System.out.println();

        // Print content returned for URL in string
        System.out.println("\n Print content of responses in String format ");
        try {
            System.out.println("\nResponse of search 1");
            System.out.println(RequestManager.getContentFromSearch(search1));
            System.out.println("\nResponse of search 2");
            System.out.println(RequestManager.getContentFromSearch(search2));
            System.out.println("\nResponse of search 3");
            System.out.println(RequestManager.getContentFromSearch(search3));
            System.out.println("\nResponse of search 4");
            System.out.println(RequestManager.getContentFromSearch(search4));
            System.out.println("\nResponse of search 5");
            System.out.println(RequestManager.getContentFromSearch(search5));
            System.out.println("\nResponse of search 6");
            System.out.println(RequestManager.getContentFromSearch(search6));
        } catch (HTTPStatusCodeException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        // Print content returned for URL in string
        System.out
                .println("\n\n Print content of responses of search 5 and 6 in JSON format ");
        JSONObject json;
        try {
            System.out.println("\n Print search 5 in JSON");
            search5.getParameters().setFormat(Format.JSON);
            json = new JSONObject(RequestManager.getContentFromSearch(search5));

            System.out.println(json.toString(3));

            System.out.println("\n Print search 6 in JSON");
            search6.getParameters().setFormat(Format.JSON);
            json = new JSONObject(RequestManager.getContentFromSearch(search6));

            System.out.println(json.toString(3));
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (HTTPStatusCodeException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
