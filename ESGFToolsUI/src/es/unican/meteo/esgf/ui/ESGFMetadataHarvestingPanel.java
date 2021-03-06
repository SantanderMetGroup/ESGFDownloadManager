package es.unican.meteo.esgf.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.text.html.HTMLDocument;
import javax.xml.stream.XMLStreamException;

import ucar.util.prefs.PreferencesExt;
import es.unican.meteo.esgf.download.Download;
import es.unican.meteo.esgf.download.DownloadManager;
import es.unican.meteo.esgf.download.DownloadObserver;
import es.unican.meteo.esgf.search.Dataset;
import es.unican.meteo.esgf.search.DatasetFile;
import es.unican.meteo.esgf.search.HarvestStatus;
import es.unican.meteo.esgf.search.Metadata;
import es.unican.meteo.esgf.search.MetalinkGenerator;
import es.unican.meteo.esgf.search.SearchManager;
import es.unican.meteo.esgf.search.SearchResponse;

public class ESGFMetadataHarvestingPanel extends JPanel implements
DownloadObserver {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private static final String SEARCH_RESPONSES_FILE_NAME = "search_responses.data";

    private static final String ENCODE_FORMAT = "UTF-8";
    /**
     * Logger
     */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(ESGFMetadataHarvestingPanel.class);
    /** Preferences of configuration. */
    private PreferencesExt prefs;

    /** Request Manager. Manage restful services */
    private SearchManager searchManager;

    /** Download Manager. Manage download of datasets */
    private DownloadManager downloadManager;

    /** Main Panel */
    private JPanel mainPanel;

    private JPanel boxOfSearchResponses;

    private JScrollPane dataViewContentPanel;

    /** ESGF data chooser dialog. */
    DataChooserDialog dataChooserDialog;

    private SearchResponseExplorerDialog searchResponseExplorerDialog;
    private String searchResponsesPath;

    /**
     * Constructor
     *
     * @param prefs
     *            preferences
     */
    public ESGFMetadataHarvestingPanel(PreferencesExt prefs,
            SearchManager searchManager, DownloadManager downloadManager) {
        super();
        logger.trace("[IN]  ESGFMetadataHarvesting");

        // Request manager an download manager are shared in all ESGF tabs
        this.searchManager = searchManager;
        this.downloadManager = downloadManager;

        this.prefs = prefs;
        this.setLayout(new BorderLayout());

        this.searchResponsesPath = System.getProperty("user.home")
                + File.separator + ".esgData" + File.separator
                + SEARCH_RESPONSES_FILE_NAME;

        // ---------------------------------------------------------------------
        // North section
        // ---------------------------------------------------------------------
        JToolBar harvToolBar = new JToolBar();

        JButton doNewSearch = new JButton("New search...");
        doNewSearch.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {

                ESGFMetadataHarvestingPanel.this.firePropertyChange(
                        "newSearch", null, null);
            }
        });

        harvToolBar.add(doNewSearch);

        // ---------------------------------------------------------------------
        // Box of search responses of saved searches
        // ---------------------------------------------------------------------
        // Create a box search responses
        boxOfSearchResponses = new JPanel(new GridLayout(0, 1));
        dataViewContentPanel = new JScrollPane();

        // Added with setViewportView for to be able shows all big list
        dataViewContentPanel.setViewportView(boxOfSearchResponses);

        // ---------------------------------------------------------------------
        // Main panel
        // ---------------------------------------------------------------------

        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(harvToolBar, BorderLayout.NORTH);
        mainPanel.add(dataViewContentPanel, BorderLayout.CENTER);

        // ---------------------------------------------------------------------
        // ---------------------------------------------------------------------
        // add main Panel
        add(mainPanel, BorderLayout.CENTER);

        update();
        logger.trace("[OUT] ESGFMetadataHarvesting");
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        update();
    }

    private void update() {
        logger.trace("[IN]  update");
        updateSearchResponses();
        logger.trace("[OUT] update");
    }

    void save() {
        // save configuration
        // Save searchResponses, not necessary
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
    }

    /**
     * Update search responses
     */
    private void updateSearchResponses() {
        logger.trace("[IN]  updateSearchResponses");

        logger.debug("Removing all search responses in showed box container");
        // remove all search responses in showed box container
        boxOfSearchResponses.removeAll();

        // predetermined font
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

        // for each search response
        int num = searchManager.getSearchResponses().size();
        logger.debug("Adding search responses");
        for (int i = 0; i < num; i++) {

            final int index = i;
            final SearchResponse searchResponse = searchManager
                    .getSearchResponses().get(i);
            JPanel searchResponsePanel = new JPanel(new BorderLayout());
            searchResponsePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                    140));
            JPanel boxElement = new JPanel(new FlowLayout(FlowLayout.LEFT));

            // attributes-------------------------------------------------
            JLabel name = new JLabel();
            try {
                name.setText(URLDecoder.decode(searchResponse.getName(),
                        ENCODE_FORMAT));
            } catch (UnsupportedEncodingException e2) {
                name.setText(searchResponse.getName());
            }

            // Create component that contains description with format
            JEditorPane searchParameters = new JEditorPane();
            searchParameters.setContentType("text/html");
            searchParameters.setOpaque(false);
            searchParameters.setEditable(false);
            searchParameters.setText("<html>"
                    + searchResponse.getSearch().getParameters()
                    .getConstraintParametersString() + "<html>");

            // Set predefined font in component
            // When JEditorPane content type is HTML, setFont do nothing
            // Only can set style of HTMLDocument
            String bodyRule = "body { font-family: " + font.getFamily() + "; "
                    + "font-size: " + font.getSize() + "pt; }";
            ((HTMLDocument) searchParameters.getDocument()).getStyleSheet()
            .addRule(bodyRule);

            JPanel centerPanel = new JPanel(new GridLayout(1, 2));

            JPanel infoPanel = new JPanel(new GridLayout(2, 1));
            JScrollPane scrollSearchParameters = new JScrollPane(
                    searchParameters);
            infoPanel.setPreferredSize(new Dimension(150, 80));
            infoPanel.add(name);
            infoPanel.add(scrollSearchParameters);
            infoPanel.setForeground(getForeground());
            infoPanel.setForeground(getForeground());

            JPanel downloadOptions = new JPanel(new FlowLayout());

            final JButton playPause = new JButton();
            if (searchResponse.isHarvestingActive()) {
                playPause.setText("pause");
            } else {
                playPause.setText("start");
            }

            playPause.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent arg0) {
                    logger.trace("[IN]  actionPerformed");

                    if (playPause.getText().equals("pause")) {
                        playPause.setText("start");
                        updateUI();
                        ESGFMetadataHarvestingPanel.this.searchManager
                        .getSearchResponses().get(index).pause();

                    } else { // Button play
                        playPause.setText("pause");
                        updateUI();
                        SearchResponse searchResponse = ESGFMetadataHarvestingPanel.this.searchManager
                                .getSearchResponses().get(index);
                        updateUI();
                        searchResponse.startCompleteHarvesting();
                        searchResponse
                        .registerObserver(ESGFMetadataHarvestingPanel.this);
                        update();
                    }

                    logger.trace("[OUT] actionPerformed");
                }
            });

            JButton reset = new JButton("reset");
            reset.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent arg0) {
                    logger.trace("[IN]  actionPerformed");
                    ESGFMetadataHarvestingPanel.this.searchManager
                    .getSearchResponses().get(index).reset();

                    update();
                    logger.trace("[OUT] actionPerformed");
                }
            });

            JButton remove = new JButton("remove");
            remove.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent arg0) {
                    logger.trace("[IN] buttonRemove actionPerformed");

                    int confirm = JOptionPane.showConfirmDialog(
                            ESGFMetadataHarvestingPanel.this,
                            "Sure you want remove this saved search?",
                            "Remove", JOptionPane.YES_NO_OPTION);

                    if (confirm == JOptionPane.YES_OPTION) {
                        SearchResponse search = ESGFMetadataHarvestingPanel.this.searchManager
                                .getSearchResponses().get(index);
                        search.pause();
                        ESGFMetadataHarvestingPanel.this.searchManager
                        .getSearchResponses().remove(index);
                        save();
                        update();
                    }

                    logger.trace("[OUT] buttonRemove actionPerformed");
                }
            });

            if (!searchResponse.isCompleted()) {
                downloadOptions.add(playPause);
            }
            downloadOptions.add(remove);
            downloadOptions.setForeground(getForeground());

            JPanel downloadInfoPanel = new JPanel(new GridLayout(2, 1));
            downloadInfoPanel.setForeground(getForeground());
            JLabel timeToFinish;

            if (searchResponse.getDatasetTotalCount() > 0
                    || searchResponse.getHarvestingStart() != null) {
                downloadOptions.add(reset);
            }

            if (searchResponse.getDatasetTotalCount() > 0) {

                downloadInfoPanel = new JPanel(new GridLayout(2, 1));
                downloadInfoPanel.setForeground(getForeground());

                int completed = searchResponse.getProcessedDatasets();
                int total = searchResponse.getDatasetTotalCount();

                int numberOfFiles = 0;
                int numberOfSelectedFiles = 0;
                long totalSize = 0;
                long selectedSize = 0;
                for (Map.Entry<String, HarvestStatus> entry : searchResponse
                        .getDatasetHarvestingStatus().entrySet()) {

                    try {
                        String instanceID = entry.getKey();
                        HarvestStatus status = entry.getValue();

                        // If dataset has been harvested
                        if (status == HarvestStatus.COMPLETED) {
                            Dataset dataset = searchResponse
                                    .getDataset(instanceID);

                            if (dataset != null) {

                                // Copy set of file predetermined to download
                                Set<String> files = searchResponse
                                        .getFilesToDownload(dataset
                                                .getInstanceID());

                                Set<String> filesToDownload = null;
                                if (files != null) {
                                    // filesToDownload=files.copy()
                                    filesToDownload = new HashSet<String>(files);
                                } else {
                                    logger.warn(
                                            "Files of download of dataset {} are null"
                                                    + " in search response map",
                                                    instanceID);
                                    filesToDownload = new HashSet<String>();
                                }
                                for (DatasetFile file : dataset.getFiles()) {
                                    numberOfFiles = numberOfFiles + 1;
                                    totalSize = totalSize
                                            + (Long) file
                                            .getMetadata(Metadata.SIZE);
                                    if (filesToDownload
                                            .contains(standardizeESGFFileInstanceID(file
                                                    .getInstanceID()))) {
                                        numberOfSelectedFiles = numberOfSelectedFiles + 1;
                                        selectedSize = selectedSize
                                                + (Long) file
                                                .getMetadata(Metadata.SIZE);
                                    }
                                }
                            } else {
                                logger.error(
                                        "Dataset {} isn't in file system and search harvesting is COMPLETED",
                                        entry.getKey());
                            }
                        }
                    } catch (IllegalArgumentException e1) {
                        logger.error(
                                "Dataset {} don't belongs to SearchResponse or if dataset hasn't been harvested",
                                entry.getKey());
                    } catch (IOException e1) {
                        logger.error(
                                "Some error happens when dataset {} has been obtained from file system",
                                entry.getKey());
                    }
                }

                String message = "harvested " + completed + "/" + total
                        + " Datasets (files:" + numberOfSelectedFiles + "/"
                        + numberOfFiles + " size:"
                        + bytesToString(selectedSize) + "/"
                        + bytesToString(totalSize);
                JLabel currentDatasetsMesagge;
                if (searchResponse.isCompleted()) {
                    currentDatasetsMesagge = new JLabel(message + ")");
                } else {
                    currentDatasetsMesagge = new JLabel(message + " so far)");
                }

                long millis = searchResponse.getApproximateTimeToFinish();

                timeToFinish = new JLabel(
                        "ETA:"
                                + String.format(
                                        "%d min, %d sec",
                                        TimeUnit.MILLISECONDS.toMinutes(millis),
                                        TimeUnit.MILLISECONDS.toSeconds(millis)
                                        - TimeUnit.MINUTES
                                        .toSeconds(TimeUnit.MILLISECONDS
                                                .toMinutes(millis))));

                JPanel datasetInfoPanel = new JPanel(new FlowLayout());
                datasetInfoPanel.add(currentDatasetsMesagge);
                downloadInfoPanel.add(datasetInfoPanel);

                if (!searchResponse.isCompleted()
                        && searchResponse.isHarvestingActive()) {
                    if (searchResponse.getProcessedDatasets() > 0) {
                        JPanel timePanel = new JPanel(new FlowLayout());
                        timePanel.add(timeToFinish);
                        downloadInfoPanel.add(timePanel);
                    } else {
                        JPanel tempPanel = new JPanel(new FlowLayout());
                        tempPanel.add(new JLabel("Harvesting..."));
                        downloadInfoPanel.add(tempPanel);
                    }
                } else {
                    if (searchResponse.isCompleted()) {
                        JPanel completedPanel = new JPanel(new FlowLayout());
                        completedPanel.add(new JLabel("Completed"));
                        downloadInfoPanel.add(completedPanel);
                    }
                }
            } else {
                // if harvesting is active and totalCount<1
                if (searchResponse.isHarvestingActive()) {
                    downloadInfoPanel
                    .add(new JLabel(
                            "      Getting harvesting info and preparing harvesting..."));
                }
            }

            JProgressBar progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            progressBar.setValue(searchResponse.getCurrentProgress());

            if (searchResponse.getCurrentProgress() == 100) {
                progressBar.setForeground(new Color(00, 160, 00));
                timeToFinish = new JLabel("");
            }

            JPanel progressBarAndOptions = new JPanel(new GridLayout(3, 30));
            progressBarAndOptions.setPreferredSize(new Dimension(500, 150));
            progressBarAndOptions.add(progressBar);
            progressBarAndOptions.add(downloadOptions);
            progressBarAndOptions.add(downloadInfoPanel);

            if (searchResponse.getHarvestStatus() == HarvestStatus.FAILED) {
                progressBar.setBackground(Color.RED);
                progressBar.setForeground(Color.RED);
                timeToFinish = new JLabel("");
            }

            JButton editSearch = new JButton("Edit Search");
            editSearch.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent arg0) {
                    ESGFMetadataHarvestingPanel.this.firePropertyChange(
                            "editSearch", null, searchResponse);
                }
            });

            JButton toMetalink = new JButton("Export to Metalink");
            toMetalink.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    JFileChooser fileChooser = new JFileChooser(System
                            .getProperty("user.dir"));
                    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                    int returnVal = fileChooser.showSaveDialog(null);
                    if (returnVal == JFileChooser.APPROVE_OPTION) {
                        File file = fileChooser.getSelectedFile();
                        String filePath = file.getAbsolutePath();
                        if (!(filePath.endsWith(".metalink"))) {
                            filePath = filePath + ".metalink";
                        }

                        try {
                            MetalinkGenerator
                            .exportToMetalink(
                                    ESGFMetadataHarvestingPanel.this.searchManager
                                    .getSearchResponses().get(
                                            index),
                                            new FileOutputStream(filePath));
                        } catch (IllegalArgumentException e1) {
                            JOptionPane.showMessageDialog(
                                    ESGFMetadataHarvestingPanel.this,
                                    "The search isn't complete", "Warning",
                                    JOptionPane.WARNING_MESSAGE);
                        } catch (XMLStreamException e1) {
                            JOptionPane.showMessageDialog(
                                    ESGFMetadataHarvestingPanel.this,
                                    "Error writing Metalink in XMLStream. "
                                            + e1.getMessage(), "Error",
                                    JOptionPane.ERROR_MESSAGE);
                            e1.printStackTrace();
                        } catch (IOException e1) {
                            JOptionPane.showMessageDialog(
                                    ESGFMetadataHarvestingPanel.this,
                                    "Error writing Metalink in IO. "
                                            + e1.getMessage(), "Error",
                                    JOptionPane.ERROR_MESSAGE);
                            e1.printStackTrace();
                        }
                    }
                }
            });

            JButton download = new JButton("Download ...");
            download.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    SearchResponse searchResponse = ESGFMetadataHarvestingPanel.this.searchManager
                            .getSearchResponses().get(index);
                    dataChooserDialog = new DataChooserDialog(
                            (JFrame) ESGFMetadataHarvestingPanel.this
                            .getTopLevelAncestor(), prefs,
                            searchResponse, downloadManager);
                    dataChooserDialog.setVisible(true);
                }
            });

            JButton exploreSearch = new JButton("Explore search");
            exploreSearch.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    SearchResponse searchResponse = ESGFMetadataHarvestingPanel.this.searchManager
                            .getSearchResponses().get(index);
                    searchResponseExplorerDialog = new SearchResponseExplorerDialog(
                            (JFrame) ESGFMetadataHarvestingPanel.this
                            .getTopLevelAncestor(), prefs,
                            searchResponse, downloadManager);
                }
            });

            JButton retryHarvestInAllFailedDataset = new JButton(
                    "Retry Harvesting in failed datasets");
            retryHarvestInAllFailedDataset
            .addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    SearchResponse searchResponse = ESGFMetadataHarvestingPanel.this.searchManager
                            .getSearchResponses().get(index);
                    searchResponse.retryFailedDatasets();
                    update();
                }
            });

            JPanel otherOptions = new JPanel(new FlowLayout());

            otherOptions.add(exploreSearch);
            otherOptions.add(editSearch);
            otherOptions.setForeground(getForeground());

            JPanel southPanel = new JPanel(new FlowLayout());
            southPanel.add(otherOptions, BorderLayout.SOUTH);
            southPanel.setForeground(getForeground());
            if (searchResponse.isCompleted()) {
                otherOptions.add(toMetalink);
                otherOptions.add(download);
                if (searchResponse.hasErrorsInHarvesting()) {
                    otherOptions.add(retryHarvestInAllFailedDataset);
                }
            }
            // --------------------------------------------------------------

            // Panel hierarchy
            centerPanel.add(infoPanel);
            centerPanel.add(progressBarAndOptions);
            centerPanel.setForeground(getForeground());

            searchResponsePanel.add(centerPanel, BorderLayout.CENTER);
            searchResponsePanel.add(southPanel, BorderLayout.SOUTH);
            searchResponsePanel.setBorder(BorderFactory
                    .createLoweredBevelBorder());

            // Finally add
            boxElement.add(searchResponsePanel);
            boxOfSearchResponses.add(boxElement);
            logger.debug("New search response added. Name:{}",
                    searchResponse.getName());
        }

        updateJScrollPaneOfBox();
        logger.trace("[OUT] updateSearchResponses");

    }

    @Override
    public void onDownloadChange(Download download) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                repaint();
            }
        });

    }

    private void updateJScrollPaneOfBox() {

        dataViewContentPanel.revalidate();
        dataViewContentPanel.repaint();

    }

    @Override
    public void onDownloadCompleted(Download download) {
        // TODO Auto-generated method stub

        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                repaint();
            }
        });

        updateUI();

        SearchResponse searchResponse = (SearchResponse) download;
        logger.info("Metadata Harvesting of {} is completed",
                searchResponse.getName());
    }

    @Override
    public void onError(Download download) {
        SearchResponse searchResponse = (SearchResponse) download;
        logger.info("Metadata Harvesting of {} had an error",
                searchResponse.getName());
        logger.error("Error in download of{} : {}", searchResponse.getName(),
                searchResponse.getSearch().generateServiceURL());

        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                repaint();
            }
        });

    }

    @Override
    public void onUnauthorizedError(Download download) {
        // TODOfor now, not necessary

    }

    /**
     * Private method that converts long bytes un readable string of bytes
     *
     * @param bytes
     * @return
     */
    private String bytesToString(long bytes) {

        String strBytes = "";
        if (bytes < 1024) {
            strBytes = bytes + " B";
        }

        // Prefixes of multiples of bits
        String prefixes = "KMGTPE";
        int exp = (int) (Math.log(bytes) / Math.log(1024));

        if (exp > 0) {
            strBytes = String.format("%.1f %sB", bytes / Math.pow(1024, exp),
                    prefixes.charAt(exp - 1));
        }

        return strBytes;
    }

    /**
     * Verify if instance ID of ESGF file is correct and if id is corrupted then
     * it corrects the id
     *
     * @param instanceID
     *            instance_id of file
     * @return the same instance_id if it is a valid id or a new corrected
     *         instance_id , otherwise
     */
    private String standardizeESGFFileInstanceID(String instanceID) {
        // file instane id have this form
        //
        // project.output.model[...]_2000010106-2006010100.nc
        // dataset id have this form
        //
        // project.output.model[...]_2000010106-2006010100

        // If id have ".nc_0" or others instead of .nc
        // Then warning and return correct id

        if (instanceID.matches(".*\\.nc_\\d$")) {
            String[] splitted = instanceID.split(".nc_\\d$");
            instanceID = splitted[0] + ".nc";
        }

        return instanceID;
    }

}
