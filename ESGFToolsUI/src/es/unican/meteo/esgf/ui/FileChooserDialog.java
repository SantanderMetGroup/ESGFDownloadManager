package es.unican.meteo.esgf.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;

import es.unican.meteo.esgf.download.DownloadManager;
import es.unican.meteo.esgf.search.Dataset;
import es.unican.meteo.esgf.search.DatasetFile;
import es.unican.meteo.esgf.search.Metadata;
import es.unican.meteo.esgf.search.SearchResponse;

/** Dialog to select dataset files to download Select=CREATED, Deselect=SKIPPED. */
public class FileChooserDialog extends JDialog {

    /**
     * Logger
     */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(FileChooserDialog.class);
    /** Main panel. */
    private JPanel mainPanel;

    /** Table that contains all dataset metadata. */
    private JTable fileTable;

    /** JList model, for change selected files */
    private DefaultListModel fileListModel;

    /** JList contains dataset files. */
    private JList fileList;

    /** Predetermined files to download (files that satisfy the constraints). */
    private Set<String> filesToDownload;

    /** Files that satisfy the constraints. */
    private Set<String> constraintFiles;

    /** Download Manager. Manage download of datasets */
    private DownloadManager downloadManager;

    private JLabel infoSelectedFilesMessage;

    /** Dataset. */
    private Dataset dataset;

    private int selectedNumber;

    private long selectedSize;

    private int totalNumber;

    private long totalSize;

    private Set<DatasetFile> filesDownloading;

    private long sizeOfFilesDownloading;

    private SearchResponse searchResponse;

    public FileChooserDialog(SearchResponse searchResponse, Dataset dataset,
            Set<String> filesToDownload, DownloadManager downloadManager,
            JFrame parent) {

        // Call super class(JDialog) and set parent frame and modal true
        // for lock other panels
        super(parent, true);
        setLayout(new FlowLayout());
        this.filesToDownload = filesToDownload;
        this.constraintFiles = new HashSet<String>(filesToDownload);
        this.dataset = dataset;
        this.downloadManager = downloadManager;
        this.searchResponse = searchResponse;

        // Initialize main panel
        mainPanel = new JPanel(new BorderLayout());

        // Create a list
        fileListModel = new DefaultListModel();
        fileList = new JList(fileListModel);
        fileList.setCellRenderer(new FileListRenderer());

        // Add file list in a scrollable panel
        // Added with setViewportView for to be able shows all big list
        JScrollPane fileListPanel = new JScrollPane();
        fileListPanel.setViewportView(fileList);

        // Add data set files in file list
        if (dataset.getFiles() != null) {
            Set<DatasetFile> dataFiles = dataset.getFiles();
            // Array of instance_id that can be sort
            String[] fileInstanceIDArray = new String[dataFiles.size()];
            // Map instance_id - file
            Map<String, DatasetFile> fileMap = new HashMap<String, DatasetFile>();
            // index
            int index = 0;

            // Fill array and map of instanceId-File
            for (DatasetFile file : dataset.getFiles()) {
                fileInstanceIDArray[index] = file.getInstanceID();
                fileMap.put(file.getInstanceID(), file);
                index++;
            }

            // Sort array
            Arrays.sort(fileInstanceIDArray);

            // Fill JList with sort array of instance_id and map of files
            // fileMap : <InstanceID, DatasetFile>
            for (String fileInstanceID : fileInstanceIDArray) {
                fileListModel.addElement(fileMap.get(fileInstanceID));
            }
        }

        // Add a mouse listener to handle changing selection
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                JList list = (JList) event.getSource();

                // Get index of file clicked
                int index = list.locationToIndex(event.getPoint());

                // Get file download status item
                DatasetFile file = (DatasetFile) list.getModel().getElementAt(
                        index);

                // Check if file isn't being downloaded
                if (!filesDownloading.contains(file)) {

                    // standardize file instanceID
                    String sFileInstanceID = standardizeESGFFileInstanceID(file
                            .getInstanceID());
                    // If file is selected to download, deselect it
                    if (FileChooserDialog.this.filesToDownload
                            .contains(sFileInstanceID)) {
                        FileChooserDialog.this.filesToDownload
                        .remove(sFileInstanceID);

                        long size = file.getMetadata(Metadata.SIZE);
                        selectedNumber = selectedNumber - 1;
                        selectedSize = selectedSize - size;
                        infoSelectedFilesMessage
                        .setText(makeInfoSelectedMessage());
                    } else { // else select it
                        FileChooserDialog.this.filesToDownload
                        .add(standardizeESGFFileInstanceID(file
                                .getInstanceID()));
                        long size = file.getMetadata(Metadata.SIZE);
                        selectedNumber = selectedNumber + 1;
                        selectedSize = selectedSize + size;
                        infoSelectedFilesMessage
                        .setText(makeInfoSelectedMessage());
                    }

                    // Repaint cell
                    list.repaint(list.getCellBounds(index, index));
                }
            }
        });

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());

        // Deselect all files button
        JButton deselectAll = new JButton("Deselect all");
        deselectAll.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                selectedNumber = filesDownloading.size();
                selectedSize = sizeOfFilesDownloading;
                infoSelectedFilesMessage.setText(makeInfoSelectedMessage());

                // remove all
                FileChooserDialog.this.filesToDownload = new HashSet<String>();
                // repaint list
                fileList.repaint();

            }
        });

        // Select all files button
        JButton selectAll = new JButton("Select all");
        selectAll.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                // Add all files
                if (FileChooserDialog.this.dataset.getFiles() != null) {

                    selectedNumber = totalNumber;
                    selectedSize = totalSize;
                    infoSelectedFilesMessage.setText(makeInfoSelectedMessage());

                    for (DatasetFile file : FileChooserDialog.this.dataset
                            .getFiles()) {
                        FileChooserDialog.this.filesToDownload
                        .add(standardizeESGFFileInstanceID(file
                                .getInstanceID()));
                    }

                    // repaint list
                    fileList.repaint();
                }
            }
        });

        // Select files that satisfy the constraints of search
        JButton selectFiltered = new JButton("Filter by constraints of search");
        selectFiltered.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                FileChooserDialog.this.filesToDownload = new HashSet<String>(
                        FileChooserDialog.this.constraintFiles);

                selectedNumber = FileChooserDialog.this.filesToDownload.size()
                        + FileChooserDialog.this.filesDownloading.size();
                selectedSize = 0 + sizeOfFilesDownloading;

                totalNumber = FileChooserDialog.this.dataset.getFiles().size();
                totalSize = 0;

                for (DatasetFile file : FileChooserDialog.this.dataset
                        .getFiles()) {
                    totalSize = totalSize
                            + (Long) file.getMetadata(Metadata.SIZE);
                    if (FileChooserDialog.this.filesToDownload
                            .contains(standardizeESGFFileInstanceID(file
                                    .getInstanceID()))) {
                        selectedSize = selectedSize
                                + (Long) file.getMetadata(Metadata.SIZE);
                    }
                }

                infoSelectedFilesMessage.setText(makeInfoSelectedMessage());

                // repaint list
                fileList.repaint();
            }
        });

        // Select all files button
        JButton download = new JButton("Download selected");
        download.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                try {
                    String path = null;

                    // Ask for download in predetermined path or choose new path
                    int changePath = JOptionPane.showConfirmDialog(
                            FileChooserDialog.this,
                            "The files will be "
                                    + "downloaded at default path: "
                                    + System.getProperty("user.home")
                                    + File.separator
                                    + "ESGFDATA\n Do you want "
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

                    // if path is null then used the default path of downloads
                    FileChooserDialog.this.downloadManager.enqueueDataset(
                            FileChooserDialog.this.searchResponse,
                            FileChooserDialog.this.dataset,
                            FileChooserDialog.this.filesToDownload, path);
                } catch (IOException e1) {

                    logger.error("Error reading info of dataset{}",
                            FileChooserDialog.this.dataset.getInstanceID());
                    JOptionPane.showMessageDialog(FileChooserDialog.this,
                            "Error reading info of files and dataset");

                }

                // releases this dialog, close this dialog
                dispose();
            }
        });

        // cancel button
        JButton cancel = new JButton("Close");
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                // releases this dialog, close this dialog
                dispose();
            }
        });

        // add buttons to buttons panel
        buttonPanel.add(deselectAll);
        buttonPanel.add(selectAll);
        buttonPanel.add(selectFiltered);
        buttonPanel.add(download);
        buttonPanel.add(cancel);

        // Get all the files of dataset that are being downloaded.
        sizeOfFilesDownloading = 0;
        filesDownloading = new HashSet<DatasetFile>();
        for (DatasetFile file : dataset.getFiles()) {
            if (downloadManager.isFileAddedToDownload(file.getInstanceID())) {
                filesDownloading.add(file);
                // if file have size metadata then sum
                if (file.getMetadata(Metadata.SIZE) != null) {
                    long size = file.getMetadata(Metadata.SIZE);
                    sizeOfFilesDownloading = sizeOfFilesDownloading + size;
                }
            }
        }

        // add set of filesToDownload in set of filesDownloading
        int totalFilesSelected = filesDownloading.size();
        for (String fileID : filesToDownload) {
            // if wasn't added yet
            if (!downloadManager
                    .isFileAddedToDownload(standardizeESGFFileInstanceID(fileID))) {
                totalFilesSelected++;
            }
        }

        selectedNumber = totalFilesSelected;
        selectedSize = 0 + sizeOfFilesDownloading;

        totalNumber = dataset.getFiles().size();
        totalSize = 0;

        for (DatasetFile file : dataset.getFiles()) {
            String fileID = file.getInstanceID();
            totalSize = totalSize + (Long) file.getMetadata(Metadata.SIZE);
            if (filesToDownload.contains(standardizeESGFFileInstanceID(fileID))) {
                if (!downloadManager
                        .isFileAddedToDownload(standardizeESGFFileInstanceID(fileID))) {
                    selectedSize = selectedSize
                            + (Long) file.getMetadata(Metadata.SIZE);
                }
            }
        }

        infoSelectedFilesMessage = new JLabel();
        String message = makeInfoSelectedMessage();
        infoSelectedFilesMessage.setText(message);

        String datasetInstanceId = dataset.getInstanceID();

        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.add(new JLabel(datasetInstanceId), BorderLayout.NORTH);
        infoPanel.add(infoSelectedFilesMessage, BorderLayout.SOUTH);

        mainPanel.add(infoPanel, BorderLayout.NORTH);
        mainPanel.add(fileListPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
        pack();

        // Center dialog
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    }

    private String makeInfoSelectedMessage() {
        String message = "files: (" + selectedNumber + "/" + totalNumber
                + ") size: (" + bytesToString(selectedSize) + "/"
                + bytesToString(totalSize) + ")";
        return message;
    }

    /**
     * Cell rendered for filters list.
     *
     */
    private class FileListRenderer extends JCheckBox implements
    ListCellRenderer {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

        /**
         * Constructor
         */
        public FileListRenderer() {
            // setOpaque(true);
        }

        /**
         * Return a JLabel with text facet : valueFacet
         *
         * @param list
         *            List of elements
         * @param value
         *            of element list
         * @param index
         *            element index in the list
         * @param isSelected
         *            if this element is selected
         * @param cellHasFocus
         *            if the is focus or not
         * @return
         */
        @Override
        public Component getListCellRendererComponent(JList list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {

            DatasetFile file = (DatasetFile) value;

            // File id consist in (dataset id + file id + master node)
            // [DatasetID info].[fileID info]|masterNode
            // example:
            // cmip5.(..)r1i1p1.v20120330.sfcWindmax_(..)_r1i1p1_18500101-18501231.nc|adm07.cmcc.it

            // datasetId
            String datasetId = file.getDatasetInstanceID();

            // fileId
            String fileId = file.getInstanceID();

            // Size of file
            long bytes = file.getMetadata(Metadata.SIZE);

            // Only must print file id Info
            // text = fileId-datasetId + size
            // sum 1 to erase the dot
            setText(fileId.substring(datasetId.length() + 1) + " ("
                    + bytesToString(bytes) + ")");

            boolean selected = filesToDownload
                    .contains(standardizeESGFFileInstanceID(file
                            .getInstanceID()));
            setSelected(selected);

            if (downloadManager
                    .isFileAddedToDownload(standardizeESGFFileInstanceID(fileId))) {
                setEnabled(false);
                setSelected(true);
            } else {
                setEnabled(true);
            }

            // Set background and foreground of list element
            // according is selected or not
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }

            return this;
        }
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
}
