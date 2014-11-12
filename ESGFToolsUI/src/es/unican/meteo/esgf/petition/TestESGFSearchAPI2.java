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

public class TestESGFSearchAPI2 {

    public static void main(String[] args) {

        /* Using builder of an esgf-search petitions . */
        System.out.println("-------------------------------------------");
        System.out.println("     Test esgf-search petitions            ");
        System.out.println("-------------------------------------------");

        // parameters of searches that not change in this example
        Parameters parameters = new Parameters();
        parameters.setFormat(Format.JSON); // OR Format.XML
        parameters.setDistrib(true);
        parameters.setLimit(0);// not return records
        parameters.setType(RecordType.DATASET);
        parameters.setReplica(Replica.MASTER); // only count original replicas

        // create a search request
        RESTfulSearch search = new RESTfulSearch("pcmdi9.llnl.gov");
        search.setParameters(parameters.clone()); // copy parameters

        /*
         * 1. Request that list count of experiments of datasets in project
         * CMIP5 in XML.
         */

        // set project=CMIP5
        search.getParameters().setProject(
                Arrays.asList(new String[] { "CMIP5" }));

        // configure Facets & Counts list (to return experiment)
        search.getParameters().setFacets(
                new HashSet<String>(Arrays
                        .asList(new String[] { "experiment" })));

        // print search
        System.out
        .println("1.Request that list experiments of datasets in project "
                + "CMIP5 in format JSON");// print search
        URL url = search.generateServiceURL();
        System.out.println(url);
        System.out.println();

        System.out.println("Getting response in JSON...");
        JSONObject json;
        try {
            json = new JSONObject(RequestManager.getContentFromSearch(search));

            System.out.println("Response of search 1:");
            System.out.println(json.toString(3));
        } catch (JSONException e2) {
            // TODO Auto-generated catch block
            e2.printStackTrace();
        } catch (HTTPStatusCodeException e2) {
            // TODO Auto-generated catch block
            e2.printStackTrace();
        } catch (IOException e2) {
            // TODO Auto-generated catch block
            e2.printStackTrace();
        }

        /*
         * 2. Request that list count of models of datasets that project=CMIP5
         * AND experiment=1pctCO2 in JSON.
         */

        // set experiment
        search.getParameters().setExperiment(
                Arrays.asList(new String[] { "1pctCO2" }));

        // change Facets & Counts list (to return model)
        search.getParameters().setFacets(
                new HashSet<String>(Arrays.asList(new String[] { "model" })));

        // print search
        System.out
                .println("2. Request that list count of models of datasets that"
                        + " project=CMIP5 AND experiment=1pctCO2 in JSON.");
        url = search.generateServiceURL();
        System.out.println(url);
        System.out.println();

        System.out.println("Getting response in JSON...");
        try {
            json = new JSONObject(RequestManager.getContentFromSearch(search));

            System.out.println("Response of search 2:");
            System.out.println(json.toString(3));

        } catch (JSONException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (HTTPStatusCodeException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        /*
         * 3. Request that list count of models of datasets that project=CMIP5
         * AND experiment=1pctCO2 and have:
         *
         * - zg OR ta
         *
         * - zg in daily and ta in 6hr
         *
         * all in JSON.
         */

        // set variables (isn't necessary) only do it for reduce response times
        search.getParameters().setVariable(
                Arrays.asList(new String[] { "zg", "ta" }));

        // set query constraint
        search.getParameters().setQuery(
                "(variable:zg AND time_frequency:day)"
                        + "OR(variable:ta AND time_frequency:6hr)");

        // print search
        System.out
                .println("3. Request that list count of models of datasets that"
                        + " project=CMIP5 AND experiment=1pctCO2 and have:\n."
                        + "zg OR Ta\n zg in daily and ta in 6hr\n all in JSON");
        url = search.generateServiceURL();
        System.out.println(url);
        System.out.println();

        System.out.println("Getting response in JSON...");
        try {
            json = new JSONObject(RequestManager.getContentFromSearch(search));

            System.out.println("Response of search 3:");
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
