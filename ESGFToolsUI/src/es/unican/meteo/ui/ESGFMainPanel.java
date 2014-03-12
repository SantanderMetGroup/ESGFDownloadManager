package es.unican.meteo.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.beans.PropertyChangeEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import ucar.util.prefs.PreferencesExt;
import es.unican.meteo.esgf.download.DatasetDownloadStatus;
import es.unican.meteo.esgf.download.DownloadManager;
import es.unican.meteo.esgf.petition.CredentialsManager;
import es.unican.meteo.esgf.petition.HTTPStatusCodeException;
import es.unican.meteo.esgf.search.SearchManager;
import es.unican.meteo.esgf.search.SearchResponse;

public class ESGFMainPanel extends JPanel {
    private static final String CONFIG_FILE = "config.txt";

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(ESGFMainPanel.class);

    /** Preferences of configuration. */
    private PreferencesExt prefs;

    /** Request Manager. Manage restful services */
    private SearchManager searchManager;

    /** Download Manager. Manage download of datasets */
    private DownloadManager downloadManager;

    /**
     * CredentialManager. Manage user's credentials.
     */
    private CredentialsManager credentialsManager;

    /** ESGF search panel. */
    private ESGFSearchPanel searchPanel;

    /** ESGF metadata harvesting panel. */
    private ESGFMetadataHarvestingPanel metadataHarvestingPanel;

    /** ESGF downloads panel. */
    private ESGFDownloadsPanel downloadsPanel;

    /** ESGF login panel. */
    private ESGFLoginPanel loginPanel;

    /** Error in all ESGF nodes dialog. */
    private JDialog error;

    /**
     * Main tabbed panel
     */
    private JTabbedPane mainTabbedPane;

    private List<String> nodes;

    /** Lazy main panel. */
    private JPanel mainPanel;

    /**
     * Progress Bar dialog
     */
    private JDialog progressDialog;

    /** Progress Bar: */
    private JProgressBar progressBar;

    /**
     * Constructor
     * 
     * @param prefs
     *            preferences
     */
    public ESGFMainPanel(PreferencesExt prefs) {
        logger.trace("[IN]  ESGFMainPanel");

        this.prefs = prefs;
        mainPanel = new JPanel(new BorderLayout());

        progressBar = new JProgressBar(0, 100);
        progressBar.setIndeterminate(true);

        progressDialog = new JDialog((JFrame) this.getTopLevelAncestor(), false);
        progressDialog.setAlwaysOnTop(true);
        progressDialog.setLayout(new BorderLayout());
        progressDialog.add(progressBar, BorderLayout.CENTER);

        progressDialog.setVisible(false);
        progressDialog.setLocationRelativeTo(this.getTopLevelAncestor());
        progressDialog.setUndecorated(true); // quit upper bar
        progressDialog.pack();

        // Create panel components without block flow program
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                progressDialog.setVisible(true);
                makePanelComponents();
                add(mainPanel, BorderLayout.CENTER);
                progressDialog.setVisible(false);
            }
        });

        thread.start();
    }

    private void makePanelComponents() {
        error = new JDialog((JFrame) this.getTopLevelAncestor(), true);
        error.setVisible(false);
        error.setLayout(new FlowLayout());
        error.setLocationRelativeTo(this.getTopLevelAncestor());
        error.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        error.add(new JLabel(
                "Not found ESGF index node active. Check your connection"));
        error.pack();

        // ESGF nodes
        List<String> nodes = getNodesFromFile(CONFIG_FILE);

        if (nodes.size() > 0) {
            prefs.putBeanObject("nodes", nodes);
        } else {
            List<String> nodesList = (List<String>) prefs
                    .getBean("nodes", null);

            if (nodesList != null) {
                nodes = nodesList;
                // modify configuration file
                setNodesToFile(nodes);
            }
        }

        int indexNode = 0;
        boolean cont = true;

        // Initialize search (Search Manager)
        // If an exception is thrown try to in other ESGF node
        while (cont && indexNode < nodes.size()) {
            try {
                // If searchManager are saved previosly
                searchManager = new SearchManager(nodes.get(indexNode));
                cont = false;

            } catch (IOException e) {
                indexNode++;

                // if all ESGF nodes fail
                if (indexNode == nodes.size()) {
                    // throw e; // raise the exception
                    logger.error("All ESGF nodes has failed");
                    error.setTitle("Index node active not found");
                    error.setVisible(true);
                }

            } catch (HTTPStatusCodeException e) {
                indexNode++;

                // if all ESGF nodes fail
                if (indexNode == nodes.size()) {
                    // throw e; // raise the exception
                    logger.error("All ESGF nodes has failed");
                    error.setTitle("Index node active not found");
                    error.setVisible(true);
                }
            }
        }

        List<SearchResponse> searchResponses = (List<SearchResponse>) prefs
                .getBean("searchResponses", null);

        if (searchResponses != null) {

            // Set cache and executor of SearchManager request in
            // searchResponses (not saved
            // in preferences)
            for (SearchResponse response : searchResponses) {
                response.setCache(searchManager.getCache());
                response.setExecutor(searchManager.getExecutor());

                try {
                    response.checkDatasets();
                } catch (IOException e) {
                    logger.warn(
                            "Can't restore from cache search response:  {}",
                            response.getSearch().generateServiceURL());
                    // if can't restored then reset all searchresponse
                    response.reset();
                }
            }
            // Reload saved search responses
            searchManager.setSearchResponses(searchResponses);
        }

        // Initialize download manager
        downloadManager = new DownloadManager(searchManager.getCache());

        Set<DatasetDownloadStatus> datasetDownloads = (Set<DatasetDownloadStatus>) prefs
                .getBean("datasetDownloads", null);
        if (datasetDownloads != null) {

            // restore records of dataset status and file status
            // from ehCache
            try {
                for (DatasetDownloadStatus dataStatus : datasetDownloads) {

                    dataStatus.setDownloadExecutor(downloadManager
                            .getDownloadExecutor());
                    dataStatus.restoreData();

                }

                downloadManager.setDatasetDownloads(datasetDownloads);
            } catch (IOException e) {
                logger.warn("Can't restore from cache all download status");
            }

        }

        // Create credential manager
        // singleton class
        credentialsManager = CredentialsManager.getInstance();

        // initialize credentials manager if it is possible
        try {
            credentialsManager.initialize();
        } catch (Exception e) {
            // if some error happen. Ignore it
            logger.info("There aren't valid credentials");
        }

        // Initialize ESGF Search panel
        searchPanel = new ESGFSearchPanel(prefs, searchManager, downloadManager);

        // Listener of ESGF Search panel that catch events from ESGFSearchPanel
        // and fire property change event again to parent panel
        searchPanel
                .addPropertyChangeListener(new java.beans.PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {

                        // Component.firePropertyChange(String propertyName,
                        // Object
                        // oldValue, Object newValue)
                        // this method fire new event with a name, old object
                        // and
                        // new object
                        // this event is catch and processed by main ESGF
                        ESGFMainPanel.this.firePropertyChange(
                                evt.getPropertyName(), evt.getOldValue(),
                                evt.getNewValue());
                    }
                });

        // Initialize ESGF metadata harvesting panel
        metadataHarvestingPanel = new ESGFMetadataHarvestingPanel(prefs,
                searchManager, downloadManager);

        // Initialize ESGF downloads panel
        downloadsPanel = new ESGFDownloadsPanel(prefs, searchManager,
                downloadManager);

        // Listener of ESGF downloads panel that catch events from
        // ESGFDownloadsPanel and fire property change event again to parent
        // panel
        downloadsPanel
                .addPropertyChangeListener(new java.beans.PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {

                        // Component.firePropertyChange(String propertyName,
                        // Object
                        // oldValue, Object newValue)
                        // this method fire new event with a name, old object
                        // and
                        // new object
                        // this event is catch and processed by main ESGF
                        ESGFMainPanel.this.firePropertyChange(
                                evt.getPropertyName(), evt.getOldValue(),
                                evt.getNewValue());
                    }
                });

        loginPanel = new ESGFLoginPanel(prefs, downloadManager,
                credentialsManager);

        mainTabbedPane = new JTabbedPane();
        mainTabbedPane.add(searchPanel, " Search ");
        mainTabbedPane.add(metadataHarvestingPanel, " Search Harvesting ");
        mainTabbedPane.add(downloadsPanel, " Downloads ");
        mainTabbedPane.add(loginPanel, " Login ");

        // Listener for each change of tab
        mainTabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent arg0) {
                if (mainTabbedPane.getSelectedComponent() instanceof ESGFSearchPanel) {
                    ((ESGFSearchPanel) mainTabbedPane.getSelectedComponent())
                            .updateSearchResponses();
                }
            }
        });
        setLayout(new BorderLayout());
        mainPanel.add(mainTabbedPane, BorderLayout.CENTER);

        logger.trace("[OUT] ESGFMainPanel");
    }

    /**
     * Save in preferences
     */
    public void save() {

        // Must save searchResponses
        if (searchManager.getSearchResponses().size() > 0) {
            List<SearchResponse> searchResponses = new ArrayList<SearchResponse>();

            for (SearchResponse searchResponse : searchManager
                    .getSearchResponses()) {
                if (searchResponse.isHarvestingActive()) {
                    searchResponse.pause();
                }
                searchResponses.add(searchResponse);
            }

            prefs.putBeanObject("searchResponses", searchResponses);
        }

        // Must save dataset download status
        // put all active downloads to pause
        downloadManager.pauseActiveDownloads();
        prefs.putBeanObject("datasetDownloads",
                downloadManager.getDatasetDownloads());

        // Configure nodes if not configured
        if (prefs.getObject("nodes") == null) {
            logger.debug("Get nodes from configuration file");
            List<String> nodes = getNodesFromFile(CONFIG_FILE);

            logger.debug("Saving nodes");
            if (nodes.size() > 0) {
                prefs.putBeanObject("nodes", nodes);
            }

        }
    }

    /**
     * Private method that write nodes in configuration file
     * 
     * @param nodes
     */
    private void setNodesToFile(List<String> nodes) {
        FileWriter fichero = null;
        PrintWriter pw = null;
        try {
            fichero = new FileWriter(CONFIG_FILE);
            pw = new PrintWriter(fichero);

            pw.print("nodes:");

            String strNodes = "";
            for (String node : nodes) {
                strNodes = strNodes + node + ", ";
            }

            strNodes = strNodes.substring(0, strNodes.length() - 2);

            pw.print(strNodes);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                // close io.file
                if (null != fichero) {
                    fichero.close();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }

    }

    /**
     * Private method that read nodes from configuration file
     * 
     * @param nodes
     */
    private List<String> getNodesFromFile(String fileName) {
        File file = null;
        BufferedReader br = null;
        FileReader fr = null;

        List<String> nodeList = new LinkedList<String>();

        try {
            // Open file and create a BufferedReader for read
            file = new File(fileName);
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
            e.printStackTrace();
        } finally {
            // In finally close file for be sure that file is closed if is
            // thrown an Exception
            try {
                if (null != fr) {
                    fr.close();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }

        return nodeList;
    }
}
