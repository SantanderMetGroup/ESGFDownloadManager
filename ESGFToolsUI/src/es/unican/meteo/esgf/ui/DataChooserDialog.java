package es.unican.meteo.esgf.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.text.html.HTMLDocument;

import ucar.util.prefs.PreferencesExt;
import es.unican.meteo.esgf.download.DownloadManager;
import es.unican.meteo.esgf.search.Dataset;
import es.unican.meteo.esgf.search.DatasetFile;
import es.unican.meteo.esgf.search.HarvestStatus;
import es.unican.meteo.esgf.search.Metadata;
import es.unican.meteo.esgf.search.SearchManager;
import es.unican.meteo.esgf.search.SearchResponse;
import es.unican.meteo.esgf.search.Service;

/**
 * DataChooser shows all datasets of a search and allow user selection to add
 * files to download queue. The user also add all the search to download queue
 *
 * @author Karem Terry
 *
 */
public class DataChooserDialog extends JFrame {

    /**
     * Logger
     */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(DataChooserDialog.class);

    /**
     * SerialVersionUID
     */
    private static final long serialVersionUID = -6544810788283927158L;

    /** Preferences of configuration. */
    private PreferencesExt prefs;

    /** Search Manager. Manage restful services */
    private SearchManager searchManager;

    /** Download Manager. Manage download of datasets */
    private DownloadManager downloadManager;

    /** Right data view content panel */
    private JScrollPane dataViewContentPanel;

    /** Container of selected. */
    private JPanel boxDatasets;

    /** Dialog to select dataset files to download. */
    private FileChooserDialog fileChooserDialog;

    String prueba;
    JTextField text;

    private SearchResponse searchResponse;

    private JFrame parent;

    /**
     * Constructor
     *
     * @param prefs
     *            preferences
     */
    public DataChooserDialog(JFrame parent, PreferencesExt prefs,
            SearchResponse searchResponse, DownloadManager downloadManager) {

        this.prefs = prefs;
        this.parent = parent;

        this.searchResponse = searchResponse;
        this.downloadManager = downloadManager;

        // Set main panel layout
        setLayout(new BorderLayout());

        // Data view panel------------------------------------------------------

        boxDatasets = new JPanel(new GridLayout(0, 1));
        dataViewContentPanel = new JScrollPane(boxDatasets);

        // ---------------------------------------------------------------------
        // Buttons-----------------------------------------------------

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());

        // Save search with a name
        JButton addAll = new JButton("Download");
        addAll.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                // Get instance_id of datasets to download
                Set<String> dataInstanceID = DataChooserDialog.this.searchResponse
                        .getDatasetHarvestingStatus().keySet();

                // Fill description str: id and description
                int selectedNumber = 0;
                int totalNumber = 0;

                long totalSize = 0;
                long selectedSize = 0;
                boolean someFailed = false;

                // for each dataset in search response
                for (String instanceID : dataInstanceID) {
                    // Get dataset
                    Dataset dataset;
                    try {

                        if (DataChooserDialog.this.searchResponse
                                .getHarvestStatus(instanceID) == HarvestStatus.FAILED) {
                            someFailed = true;
                        }

                        if (DataChooserDialog.this.searchResponse
                                .getHarvestStatus(instanceID) == HarvestStatus.COMPLETED) {
                            dataset = DataChooserDialog.this.searchResponse
                                    .getDataset(instanceID);

                            // Copy set of file predetermined to download
                            Set<String> filesToDownload = new HashSet<String>(
                                    DataChooserDialog.this.searchResponse
                                            .getFilesToDownload(dataset
                                                    .getInstanceID()));

                            // Fill description str: id and description
                            selectedNumber = selectedNumber
                                    + filesToDownload.size();
                            totalNumber = totalNumber
                                    + dataset.getFiles().size();
                            for (DatasetFile file : dataset.getFiles()) {
                                totalSize = totalSize
                                        + (Long) file
                                                .getMetadata(Metadata.SIZE);
                                if (filesToDownload.contains(file
                                        .getInstanceID())) {
                                    selectedSize = selectedSize
                                            + (Long) file
                                                    .getMetadata(Metadata.SIZE);
                                }
                            }
                        }
                    } catch (IllegalArgumentException e1) {
                        // TODO do nothing
                        e1.printStackTrace();
                    } catch (IOException e1) {
                        // TODO do nothing
                        e1.printStackTrace();
                    }
                }

                String downloadInfo = "";
                if (!someFailed) {
                    downloadInfo = "Number of files to download: "
                            + selectedNumber + " of " + totalNumber + " files "
                            + "\n" + "Size of download: "
                            + bytesToString(selectedSize) + " of "
                            + bytesToString(totalSize);
                } else {
                    downloadInfo = "Not all dataset of search will be download"
                            + "because some datasets have a failed harvest\n\n"
                            + "Number of files to download: " + selectedNumber
                            + " of " + totalNumber + " files " + "\n"
                            + "Size of download: "
                            + bytesToString(selectedSize) + " of "
                            + bytesToString(totalSize);
                }

                // for put yes, no, cancel and title of JOptionPane always
                // in English
                UIManager.put("OptionPane.yesButtonText", "Yes");
                UIManager.put("OptionPane.noButtonText", "No");
                UIManager.put("OptionPane.cancelButtonText", "Cancel");
                UIManager.put("OptionPane.titleText", "Select an option");

                // If there isn't files selected, warns to the user
                if (selectedNumber == 0) {
                    String message = "There aren't files selected. No file will be added"
                            + " to the download queue";
                    JOptionPane.showMessageDialog(DataChooserDialog.this,
                            message, "Warning", JOptionPane.WARNING_MESSAGE);
                } else {

                    int confirmed = JOptionPane
                            .showConfirmDialog(
                                    DataChooserDialog.this,
                                    "Are you sure that you want to download all "
                                            + "files that satisfy the constraints? \n\n"
                                            + downloadInfo + "\n");

                    if (confirmed == JOptionPane.OK_OPTION) {

                        String path = null;

                        // Ask for download in predetermined path or choose new
                        // path
                        int changePath = JOptionPane.showConfirmDialog(
                                DataChooserDialog.this,
                                "The files will be "
                                        + "downloaded at default path: "
                                        + System.getProperty("user.home")
                                        + File.separator
                                        + "ESGF_DATA.\n Do you want "
                                        + "to change the path of the downloads?",
                                "Do you want to change path of downloads?",
                                JOptionPane.YES_NO_OPTION);

                        // Ask for new path of downloads
                        if (changePath == JOptionPane.YES_OPTION) {

                            JFileChooser fileChooser = new JFileChooser(System
                                    .getProperty("user.dir"));
                            fileChooser
                                    .setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                            int returnVal = fileChooser.showSaveDialog(null);

                            if (returnVal == JFileChooser.APPROVE_OPTION) {
                                File file = fileChooser.getSelectedFile();
                                path = file.getAbsolutePath();

                            }
                        }

                        // if path is null then used the default path of
                        // downloads
                        DataChooserDialog.this.downloadManager.enqueueSearch(
                                DataChooserDialog.this.searchResponse, path);
                    }
                    // releases this dialog, close this dialog
                    dispose();
                }

            }
        });

        // Cancel
        JButton close = new JButton("Close");
        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                // releases this dialog, close this dialog
                dispose();
            }
        });

        // add buttons to buttons panel
        buttonPanel.add(addAll);
        buttonPanel.add(close);
        add(dataViewContentPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(600, 450));
        setLocation(300, 100);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        update();
        pack();

    }

    public void update() {
        updateDocumentsContainer();
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        update();
    }

    /**
     * Update documents container
     */
    private void updateDocumentsContainer() {

        // remove all dataset container in showed box container
        boxDatasets.removeAll();

        // predetermined font
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

        // Get instance_id of datasets to download
        Set<String> dataInstanceID = searchResponse
                .getDatasetHarvestingStatus().keySet();

        // for each dataset in search response
        for (String instanceID : dataInstanceID) {

            if (searchResponse.getDatasetHarvestingStatus().get(instanceID) == HarvestStatus.COMPLETED) {
                try {
                    final Dataset dataset = searchResponse
                            .getDataset(instanceID);
                    // Structure for document panel
                    JPanel dataContainer = new JPanel(new BorderLayout());
                    dataContainer.setAlignmentX(LEFT_ALIGNMENT);
                    dataContainer.setMaximumSize(new Dimension(1000,
                            Integer.MAX_VALUE));
                    dataContainer.setBorder(BorderFactory
                            .createLoweredBevelBorder());

                    // Sub-structure for document panel
                    JPanel description = new JPanel(new FlowLayout(
                            FlowLayout.LEFT));
                    description.setBorder(BorderFactory.createEtchedBorder());
                    JPanel data = new JPanel(new FlowLayout());

                    // Copy set of file predetermined to download
                    Set<String> filesToDownload = new HashSet<String>(
                            searchResponse.getFilesToDownload(dataset
                                    .getInstanceID()));

                    // Fill description str: id and description
                    int selectedNumber = filesToDownload.size();
                    int totalNumber = dataset.getFiles().size();

                    long totalSize = 0;
                    long selectedSize = 0;
                    for (DatasetFile file : dataset.getFiles()) {
                        totalSize = totalSize
                                + (Long) file.getMetadata(Metadata.SIZE);
                        if (filesToDownload.contains(file.getInstanceID())) {
                            selectedSize = selectedSize
                                    + (Long) file.getMetadata(Metadata.SIZE);
                        }
                    }

                    String strIdDescription = "<html><b>Id: </b>"
                            + dataset.getMetadata(Metadata.INSTANCE_ID)
                            + "<br/><b>Description: </b>"
                            + dataset.getMetadata(Metadata.DESCRIPTION)
                            + "<br/><b>Number of files selected: </b>"
                            + selectedNumber + "/" + totalNumber + " ("
                            + bytesToString(selectedSize) + "/"
                            + bytesToString(totalSize) + ")";

                    // Create component that contains description with format
                    JEditorPane idAndDescription = new JEditorPane();
                    idAndDescription.setContentType("text/html");
                    idAndDescription.setOpaque(false);
                    idAndDescription.setEditable(false);
                    idAndDescription.setText(strIdDescription);
                    idAndDescription.setBackground(description.getBackground());

                    // Set predefined font in component
                    // When JEditorPane content type is HTML, setFont do nothing
                    // Only can set style of HTMLDocument
                    String bodyRule = "body { font-family: " + font.getFamily()
                            + "; " + "font-size: " + font.getSize() + "pt; }";
                    ((HTMLDocument) idAndDescription.getDocument())
                            .getStyleSheet().addRule(bodyRule);

                    // Add component that contains id and description to panel
                    // description
                    description.add(idAndDescription);

                    // Panel with option dataset buttons
                    JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT));

                    // Button to open dataset catalog
                    JButton fileChooserOption = new JButton(
                            "Select files to download");

                    // listener of file chooser option
                    fileChooserOption.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {

                            // Copy set of file predeterined to download
                            Set<String> filesToDownload = new HashSet<String>(
                                    searchResponse.getFilesToDownload(dataset
                                            .getInstanceID()));

                            fileChooserDialog = new FileChooserDialog(
                                    DataChooserDialog.this.searchResponse,
                                    dataset, filesToDownload,
                                    DataChooserDialog.this.downloadManager,
                                    DataChooserDialog.this.parent);
                            fileChooserDialog.setVisible(true);
                            // Update ESGF data chooser panel
                            update();

                        }
                    });

                    // Only if its files contains HTTP service
                    if (dataset.getFileServices().contains(Service.HTTPSERVER)) {
                        options.add(fileChooserOption);
                        // Add to data panel, left and right data panels
                        data.add(options);
                    } else {
                        data.add(new JLabel("Dataset "
                                + dataset.getInstanceID()
                                + "hasn't HTTP service"));
                    }

                    // Add description and data in document container
                    dataContainer.add(description, BorderLayout.NORTH);
                    dataContainer.add(data, BorderLayout.CENTER);

                    // Finally, add in box documents current docContainer
                    boxDatasets.add(dataContainer);
                } catch (IllegalArgumentException e1) {
                    logger.error(
                            "Dataset {} don't belongs to SearchResponse or if dataset hasn't been harvested",
                            instanceID);
                } catch (IOException e1) {
                    logger.error(
                            "Some error happens when dataset {} has been obtained from file system",
                            instanceID);

                }
            }
        }
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

    public void save() {
        // TODO Auto-generated method stub

    }
}
