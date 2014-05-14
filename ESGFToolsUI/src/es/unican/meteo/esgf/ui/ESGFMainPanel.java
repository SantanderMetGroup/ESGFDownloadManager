package es.unican.meteo.esgf.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;

import org.globus.util.Util;

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

    private static final String SEARCH_RESPONSES_FILE_NAME = "search_responses.data";
    private static final String DATASET_DOWNLOADS_FILE_NAME = "dataset_downloads.data";
    private static final String FILEINSTANCEIDS_FILE_NAME = "fileInstanceIDs.data";

    private static final int SIMULTANEOUS_DOWNLOADS = 7;

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

    private String searchResponsesPath;

    private String datasetDownloadsPath;

    private String fileInstanceIDsPath;

    private JLabel loginInfo;

    private JLabel infoRemainTime;

    private JPanel loginNorthPanel;

    private JButton loginButton;

    private JToolBar loginBar;

    /**
     * Constructor
     * 
     * @param prefs
     *            preferences
     */
    public ESGFMainPanel(PreferencesExt prefs) {
        logger.trace("[IN]  ESGFMainPanel");

        setLayout(new BorderLayout());

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

        this.nodes = nodes;

        this.searchResponsesPath = System.getProperty("user.home")
                + File.separator + ".esgData" + File.separator
                + SEARCH_RESPONSES_FILE_NAME;
        this.datasetDownloadsPath = System.getProperty("user.home")
                + File.separator + ".esgData" + File.separator
                + DATASET_DOWNLOADS_FILE_NAME;
        this.fileInstanceIDsPath = System.getProperty("user.home")
                + File.separator + ".esgData" + File.separator
                + FILEINSTANCEIDS_FILE_NAME;

        // if user.home/.esgData directory doesn't exist then create new
        File directory = new File(System.getProperty("user.home")
                + File.separator + ".esgData");
        if (!directory.exists()) {
            directory.mkdir();
            // drwxrwxr-x
            Util.setFilePermissions(directory.getPath(), 775);
        }

        this.prefs = prefs;
        mainPanel = new JPanel(new BorderLayout());

        logger.debug("Loading credentials manager...");
        // Create credential manager
        // singleton class
        credentialsManager = CredentialsManager.getInstance();

        // initialize credentials manager if it is possible
        try {
            if (!credentialsManager.hasInitiated()) {
                credentialsManager.initialize();
            }
        } catch (Exception e) {
            // if some error happen. Ignore it
        }

        progressBar = new JProgressBar(0, 100);
        progressBar.setIndeterminate(true);

        progressDialog = new JDialog((JFrame) this.getTopLevelAncestor(), false);
        progressDialog.setAlwaysOnTop(true);
        progressDialog.setLayout(new BorderLayout());
        progressDialog.add(progressBar, BorderLayout.CENTER);

        progressDialog.setVisible(false);
        progressDialog.setLocationRelativeTo(this.getParent());
        progressDialog.setUndecorated(true); // quit upper bar
        progressDialog.pack();

        // --------------
        // North panel---
        // --------------
        // login info
        loginInfo = new JLabel(" ");
        loginInfo.setPreferredSize(new Dimension(10, 5));
        loginInfo.setOpaque(true);

        // remain time info
        infoRemainTime = new JLabel("<HTML> </HTML>");
        // Login button
        loginButton = new JButton("Login");
        loginButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                AuthDialog authDialog = new AuthDialog(
                        ESGFMainPanel.this.prefs, ESGFMainPanel.this,
                        downloadManager, credentialsManager);
                authDialog.setVisible(true);
            }
        });

        // login tool bar
        loginBar = new JToolBar();

        loginBar.add(loginButton);
        loginBar.addSeparator();
        loginBar.add(loginInfo);
        loginBar.addSeparator();
        loginBar.add(infoRemainTime);

        JPanel tempMainPanel = new JPanel();
        tempMainPanel.add(new JLabel("Loading..."));

        add(loginBar, BorderLayout.NORTH);
        // add(tempMainPanel, BorderLayout.CENTER);

        // --------------
        // Center panel---
        // --------------
        // Create panel components without block flow program
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                progressDialog.setVisible(true);
                makePanelComponents();

                add(loginBar, BorderLayout.NORTH);
                add(mainPanel, BorderLayout.CENTER);
                progressDialog.dispose();
                revalidate();
                repaint();
                updateUI();
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

        Cache cache = null;
        try {

            logger.debug("Configuring cache");
            CacheManager cacheManager = CacheManager
                    .create("ehcache_Dataset.xml");
            cache = cacheManager.getCache("restartableCache");

            logger.debug("Cache {} configuration is: \n {}",
                    cacheManager.getName(),
                    cacheManager.getActiveConfigurationText());

            ExecutorService collectorsExecutor = Executors
                    .newFixedThreadPool(SIMULTANEOUS_DOWNLOADS);

            logger.debug("Loading saved searches");
            List<SearchResponse> searchResponses = null;
            try {
                FileInputStream door = new FileInputStream(
                        this.searchResponsesPath);
                ObjectInputStream reader = new ObjectInputStream(door);
                searchResponses = (List<SearchResponse>) reader.readObject();
            } catch (FileNotFoundException e) {
                // Do nothing
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            if (searchResponses != null) {

                // Set cache and executor of SearchManager request in
                // searchResponses (not saved
                // in preferences)
                for (SearchResponse response : searchResponses) {
                    response.setCache(cache);
                    response.setExecutor(collectorsExecutor);
                    try {
                        response.checkDatasets();
                    } catch (IOException e) {
                        logger.warn(
                                "Can't restore from cache search response:  {}",
                                response.getSearch().generateServiceURL());
                        // if can't restored then reset search response records
                        response.reset();
                    }
                }
            }

            int indexNode = 0;
            boolean cont = true;

            // Initialize search (Search Manager)
            // If an exception is thrown try to in other ESGF node
            while (cont && indexNode < nodes.size()) {
                try {
                    // If searchManager are saved previosly
                    searchManager = new SearchManager(nodes.get(indexNode),
                            cache, collectorsExecutor);
                    if (searchResponses != null) {
                        // Reload saved search responses
                        searchManager.setSearchResponses(searchResponses);
                    }
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

            // Initialize download manager
            downloadManager = new DownloadManager(cache);

            logger.debug("Loading saved downloading states..");
            Set<DatasetDownloadStatus> datasetDownloads = null;
            try {
                FileInputStream door = new FileInputStream(
                        this.datasetDownloadsPath);
                ObjectInputStream reader = new ObjectInputStream(door);
                datasetDownloads = (Set<DatasetDownloadStatus>) reader
                        .readObject();
            } catch (FileNotFoundException e) {
                // Do nothing
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            if (datasetDownloads != null) {

                // // restore records of dataset status and file status
                // // from ehCache
                try {
                    for (DatasetDownloadStatus dataStatus : datasetDownloads) {

                        dataStatus.setDownloadExecutor(downloadManager
                                .getDownloadExecutor());
                        dataStatus.restoreData();

                    }

                    downloadManager.restoreDatasetDownloads(datasetDownloads);
                } catch (IOException e) {
                    logger.warn("Can't restore from cache all download status");
                }

            }

            logger.debug("Loading selected files to download...");
            Set<String> fileInstanceIDs = null;
            try {
                FileInputStream door = new FileInputStream(
                        this.fileInstanceIDsPath);
                ObjectInputStream reader = new ObjectInputStream(door);
                fileInstanceIDs = (Set<String>) reader.readObject();
            } catch (FileNotFoundException e) {
                // Do nothing
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            if (fileInstanceIDs != null) {
                downloadManager.restoreFileInstanceIDs(fileInstanceIDs);
            }

            logger.debug("Initializing search panel... "); // lazy init
            initSearchPanel();

            logger.debug("Initializing harvesting panel");
            // Initialize ESGF metadata harvesting panel
            metadataHarvestingPanel = new ESGFMetadataHarvestingPanel(prefs,
                    searchManager, downloadManager);
            metadataHarvestingPanel
                    .addPropertyChangeListener(new PropertyChangeListener() {

                        @Override
                        public void propertyChange(PropertyChangeEvent evt) {
                            if (evt.getPropertyName().equals("newSearch")) {
                                searchPanel.changeSearchSelect(null);
                                mainTabbedPane.setSelectedIndex(1);
                            } else if (evt.getPropertyName().equals(
                                    "editSearch")) {
                                searchPanel
                                        .changeSearchSelect((SearchResponse) evt
                                                .getNewValue());
                                mainTabbedPane.setSelectedIndex(1);
                            }
                        }
                    });

            logger.debug("Initializing downloads panel");
            // Initialize ESGF downloads panel
            downloadsPanel = new ESGFDownloadsPanel(prefs, downloadManager);

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
                            // this method fire new event with a name, old
                            // object
                            // and
                            // new object
                            // this event is catch and processed by main ESGF
                            ESGFMainPanel.this.firePropertyChange(
                                    evt.getPropertyName(), evt.getOldValue(),
                                    evt.getNewValue());
                        }
                    });

            JPanel tempSearchPanel = new JPanel();
            tempSearchPanel.add(new JLabel("Loading..."));

            JPanel nuevoPanel = new DownloadsPanel(prefs, downloadManager);
            nuevoPanel
                    .addPropertyChangeListener(new java.beans.PropertyChangeListener() {
                        @Override
                        public void propertyChange(PropertyChangeEvent evt) {

                            // Component.firePropertyChange(String propertyName,
                            // Object
                            // oldValue, Object newValue)
                            // this method fire new event with a name, old
                            // object
                            // and
                            // new object
                            // this event is catch and processed by main ESGF
                            ESGFMainPanel.this.firePropertyChange(
                                    evt.getPropertyName(), evt.getOldValue(),
                                    evt.getNewValue());
                        }
                    });

            mainTabbedPane = new JTabbedPane();
            mainTabbedPane.add(metadataHarvestingPanel,
                    " Saved searches & Harvesting ");
            mainTabbedPane.add(tempSearchPanel, " Search ");
            mainTabbedPane.add(downloadsPanel, " Downloads ");
            mainTabbedPane.add(nuevoPanel, " New Downloads :O ");

            // Listener for each change of tab
            mainTabbedPane.addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent arg0) {
                    if (mainTabbedPane.getSelectedComponent() instanceof ESGFSearchPanel) {
                        ((ESGFSearchPanel) mainTabbedPane
                                .getSelectedComponent())
                                .updateSearchResponses();
                    }
                }
            });
            setLayout(new BorderLayout());

            mainPanel.add(mainTabbedPane, BorderLayout.CENTER);

            update();

        } catch (Exception e) {
            logger.error("ESGF ToolsUI can't be initialized" + e);
            e.printStackTrace();

        }

        logger.trace("[OUT] ESGFMainPanel");
    }

    private void initSearchPanel() {
        logger.trace("[IN]  initSearchPanel");

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    searchManager.updateConfiguration();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (HTTPStatusCodeException e) {
                    e.printStackTrace();
                }

                // Initialize ESGF Search panel
                searchPanel = new ESGFSearchPanel(prefs, searchManager,
                        downloadManager);

                // Listener of ESGF Search panel that catch events from
                // ESGFSearchPanel
                // and fire property change event again to parent panel
                searchPanel
                        .addPropertyChangeListener(new java.beans.PropertyChangeListener() {
                            @Override
                            public void propertyChange(PropertyChangeEvent evt) {

                                // Component.firePropertyChange(String
                                // propertyName,
                                // Object
                                // oldValue, Object newValue)
                                // this method fire new event with a name, old
                                // object
                                // and
                                // new object
                                // this event is catch and processed by main
                                // ESGF
                                ESGFMainPanel.this.firePropertyChange(
                                        evt.getPropertyName(),
                                        evt.getOldValue(), evt.getNewValue());
                            }
                        });
                mainTabbedPane.remove(1);
                mainTabbedPane.insertTab(" Search ", null, searchPanel,
                        "Search panel of ESGF Data", 1);
                updateUI();
            }
        });

        thread.start();

        logger.trace("[OUT] initSearchPanel");
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        update();
    }

    private void update() {
        logger.trace("[IN]  update");
        if (credentialsManager != null) {
            if (credentialsManager.hasInitiated()) {
                long millis;
                try {
                    millis = credentialsManager
                            .getRemainTimeOfCredentialsInMillis();

                    if (millis > 1) {
                        int seconds = (int) (millis / 1000) % 60;
                        int minutes = (int) ((millis / (1000 * 60)) % 60);
                        int hours = (int) ((millis / (1000 * 60 * 60)) % 24);
                        int days = (int) ((millis / (1000 * 60 * 60 * 24)));

                        if (days > 0) {
                            loginInfo.setBackground(Color.GREEN);
                            infoRemainTime
                                    .setText("<HTML>Remaining time of validity of credentials: "
                                            + "days:"
                                            + days
                                            + ",  "
                                            + hours
                                            + ":"
                                            + minutes
                                            + ":"
                                            + seconds
                                            + "</HTML>");
                        } else {
                            loginInfo.setBackground(Color.GREEN);
                            infoRemainTime
                                    .setText("<HTML><BR><FONT COLOR=\"blue\"> Remaining time of validity of credentials: "
                                            + hours
                                            + ":"
                                            + minutes
                                            + ":"
                                            + seconds + "<BR></HTML>");
                        }
                    } else { // expired certificate
                        loginInfo.setBackground(Color.RED);
                        infoRemainTime.setText("");
                    }
                } catch (IOException e) {
                    // do nothing
                }
            } else {
                loginInfo.setBackground(Color.RED);
                infoRemainTime = new JLabel("<HTML> </HTML>");
            }
        }

        logger.trace("[OUT] update");
    }

    /**
     * Save in preferences
     */
    public void save() {

        // Save in files
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

            // Serialize search response objects in file
            ObjectOutputStream out;
            try {
                File file = new File(searchResponsesPath);
                if (!file.exists()) {
                    file.createNewFile();
                }
                out = new ObjectOutputStream(new FileOutputStream(file));
                out.writeObject(searchResponses);
                out.close();

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Must save dataset download status
        // put all active downloads to pause
        downloadManager.pauseActiveDownloads();

        // Serialize dataset downloads objects in file
        ObjectOutputStream out;
        try {
            File file = new File(datasetDownloadsPath);
            out = new ObjectOutputStream(new FileOutputStream(file));
            out.writeObject(downloadManager.getDatasetDownloads());
            out.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Serialize file instance IDs in file
        try {
            File file = new File(fileInstanceIDsPath);
            out = new ObjectOutputStream(new FileOutputStream(file));
            out.writeObject(downloadManager.getFileInstanceIDs());
            out.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Save in preferences
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

    public void setLogSuccess(boolean success) {
        if (success) {
            loginInfo.setBackground(Color.GREEN);
            update();
            updateUI();
        } else {
            loginInfo.setBackground(Color.RED);
            infoRemainTime.setText(" ");
            update();
            updateUI();
        }

    }

}
