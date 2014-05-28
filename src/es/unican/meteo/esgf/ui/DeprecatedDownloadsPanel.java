package es.unican.meteo.esgf.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

import ucar.util.prefs.PreferencesExt;
import es.unican.meteo.esgf.download.DatasetDownloadStatus;
import es.unican.meteo.esgf.download.Download;
import es.unican.meteo.esgf.download.DownloadManager;
import es.unican.meteo.esgf.download.DownloadObserver;
import es.unican.meteo.esgf.download.FileDownloadStatus;
import es.unican.meteo.esgf.download.RecordStatus;
import es.unican.meteo.esgf.search.Metadata;
import es.unican.meteo.esgf.search.RecordReplica;
import es.unican.meteo.esgf.search.Service;

/**
 * Panel that shows progress of current downloads. Implements DownloadObserver
 * for be notified of file download progress
 * 
 * @author terryk
 * 
 */
public class DeprecatedDownloadsPanel extends JPanel implements DownloadObserver {

    /**
     * Logger
     */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(DeprecatedDownloadsPanel.class);

    /**
     * SerialVersionUID
     */
    private static final long serialVersionUID = -6544810788283927158L;

    /** Preferences of configuration. */
    private final PreferencesExt prefs;

    /** Download Manager. Manage download of datasets */
    private final DownloadManager downloadManager;

    /** Model of tree List. */
    private JTree treeOfDownloads;

    /** Model of tree List. */
    private DefaultTreeModel treeModel;

    /** Root of tree model. */
    private DefaultMutableTreeNode root;

    String prueba;
    JTextField text;

    private JPanel cookiePanel;

    private JTextField cookieTextBox;

    /** Map instance_id of download - node. */
    Map<String, DefaultMutableTreeNode> instanceIDNodeMap;

    private FileMenu fileMenu;
    private DatasetMenu datasetMenu;

    /**
     * Constructor
     * 
     * @param prefs
     *            preferences
     */
    public DeprecatedDownloadsPanel(PreferencesExt prefs,
            DownloadManager downloadManager) {
        super();

        this.prefs = prefs;
        this.downloadManager = downloadManager;

        // Set main panel layout
        setLayout(new BorderLayout());

        // --TOP panel----------------------------------------------------
        // tree of downloads----------------------------------------------

        JToolBar toolBar = new JToolBar();

        JButton startAllDownloads = new JButton("Start all downloads");
        startAllDownloads.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                DeprecatedDownloadsPanel.this.downloadManager.startAllDownloads();
            }
        });

        JButton pauseAllDownloads = new JButton("Pause all downloads");
        pauseAllDownloads.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                DeprecatedDownloadsPanel.this.downloadManager.pauseActiveDownloads();
            }
        });

        JButton removeAllDownloads = new JButton("Remove all downloads");
        removeAllDownloads.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {

                int confirm = JOptionPane.showConfirmDialog(
                        DeprecatedDownloadsPanel.this,
                        "Sure you want remove all downloads?", "Remove",
                        JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    DeprecatedDownloadsPanel.this.downloadManager.reset();
                    // New tree model with a root with a String "root"
                    // Jtree is formed by this model and each node added in node
                    // will be added in an array of nodes
                    root = new DefaultMutableTreeNode("root");
                    treeModel.setRoot(root);
                    treeModel.reload();
                    instanceIDNodeMap = new HashMap<String, DefaultMutableTreeNode>();
                    updateUI();
                }
            }
        });

        toolBar.add(startAllDownloads);
        toolBar.addSeparator();
        toolBar.add(pauseAllDownloads);
        toolBar.addSeparator();
        toolBar.add(removeAllDownloads);
        // --Center panel--------------------------------------------------
        // tree of downloads----------------------------------------------

        // New tree model with a root with a String "root"
        // Jtree is formed by this model and each node added in node
        // will be added in an array of nodes
        root = new DefaultMutableTreeNode("root");
        treeModel = new DefaultTreeModel(root);
        treeOfDownloads = new JTree(treeModel);
        treeOfDownloads.setRootVisible(true);
        treeOfDownloads.setCellRenderer(new TreeRenderer());

        // --- MENU TREE LISTENER-----------------------------------------------

        // Mouse Listener, controls double click in facet values
        MouseListener mouseListener = new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {

                // Path of tree element ->son path [grandparent, father, son]
                TreePath selPath = treeOfDownloads.getPathForLocation(e.getX(),
                        e.getY());

                // If path objects from mouse location is not null
                if (selPath != null) {

                    // if secondary click
                    if (e.getButton() == MouseEvent.BUTTON3) {
                        // Path of parent tree node
                        TreePath parentPath = selPath.getParentPath();

                        // If parent is no root
                        if (parentPath.getParentPath() != null) {

                            // Get value of tree node selected
                            Object treeNode = ((DefaultMutableTreeNode) selPath
                                    .getLastPathComponent()).getUserObject();

                            if (treeNode instanceof FileDownloadStatus) {

                                fileMenu = new FileMenu(
                                        (FileDownloadStatus) treeNode,
                                        e.getX(), e.getY());
                                // expand(parentPath);

                            }

                        } else {

                            // Get value of tree node selected
                            Object treeNode = ((DefaultMutableTreeNode) selPath
                                    .getLastPathComponent()).getUserObject();

                            if (treeNode instanceof DatasetDownloadStatus) {

                                datasetMenu = new DatasetMenu(
                                        (DatasetDownloadStatus) treeNode,
                                        e.getX(), e.getY());
                                // expand(selPath);
                            }
                        }

                        treeOfDownloads.expandPath(parentPath);
                    }
                }
            }
        };

        treeOfDownloads.addMouseListener(mouseListener);

        // ---------------------------------------------------------------------

        instanceIDNodeMap = new HashMap<String, DefaultMutableTreeNode>();

        // Add Jtree a scrollable panel with a viewport view
        // JScrollPane treePanel = new JScrollPane();
        // treePanel.setViewportView(treeOfDownloads);
        // add(treePanel, BorderLayout.CENTER);
        add(toolBar, BorderLayout.NORTH);
        add(new JScrollPane(treeOfDownloads), BorderLayout.CENTER);

        // -end center------------------------------------------------

        update();
    }

    private void expand(TreePath path) {
        treeOfDownloads.expandPath(path);
    }

    /**
     * 
     */
    public void update() {

        // Add new node for each dataset and its files for download
        // Also add an observer of each new download
        for (DatasetDownloadStatus dDStatus : downloadManager
                .getDatasetDownloads()) {

            DefaultMutableTreeNode parentNode = null;

            if (!instanceIDNodeMap.containsKey(dDStatus.getInstanceID())) {

                parentNode = new DefaultMutableTreeNode(dDStatus);

                synchronized (instanceIDNodeMap) {
                    instanceIDNodeMap.put(dDStatus.getInstanceID(), parentNode);
                }

                // insert new parent (dataset status) in tree
                // insertNodeinto(childNode, parentNode, index)
                treeModel.insertNodeInto(parentNode, root,
                        treeModel.getChildCount(root));

            } else {
                parentNode = instanceIDNodeMap.get(dDStatus.getInstanceID());
            }

            if (parentNode != null) {
                for (FileDownloadStatus fileStatus : dDStatus
                        .getFilesDownloadStatus()) {

                    // Add file in tree of downloads if file status isn't
                    // SKIPPED
                    if (fileStatus.getRecordStatus() != RecordStatus.SKIPPED) {

                        // if file doesn't have an register observer, register
                        // new
                        // observer
                        if (!fileStatus.containsObserver(this)) {
                            fileStatus.registerObserver(this);

                        }

                        // if there aren't a node for this file in tree of
                        // downloads
                        // add new
                        if (!instanceIDNodeMap.containsKey(fileStatus
                                .getInstanceID())) {
                            // Add to list and add to tree of downloads
                            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(
                                    fileStatus);

                            synchronized (instanceIDNodeMap) {
                                instanceIDNodeMap.put(
                                        fileStatus.getInstanceID(), newNode);
                            }
                            // insert new child (file status) in tree
                            // insertNodeinto(childNode, parentNode, index)
                            // parent node is node of dataset of file
                            treeModel.insertNodeInto(newNode, parentNode,
                                    parentNode.getChildCount());
                        }
                    }
                }
            }
        }

        // reload tree model
        treeModel.reload();
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        update();
    }

    /**
     * On download progress of file
     */
    @Override
    public void onDownloadProgress(Download download) {

        // 20% aprox
        if (Math.random() > 0.8) {
            DefaultMutableTreeNode fileNode = null;

            if (download instanceof FileDownloadStatus) {
                fileNode = instanceIDNodeMap
                        .get(((FileDownloadStatus) download).getInstanceID());
            }

            if (fileNode != null) {
                final DefaultMutableTreeNode file = fileNode;
                SwingUtilities.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        treeModel.nodeChanged(file);
                        treeModel.nodeChanged(file.getParent());
                    }
                });

            }
        }

    }

    /**
     * On download completed of file
     */
    @Override
    public void onDownloadCompleted(Download download) {
        DefaultMutableTreeNode fileNode = null;

        if (download instanceof FileDownloadStatus) {
            fileNode = instanceIDNodeMap.get(((FileDownloadStatus) download)
                    .getInstanceID());
        } else if (download instanceof DatasetDownloadStatus) {
            fileNode = instanceIDNodeMap.get(((DatasetDownloadStatus) download)
                    .getInstanceID());
        }

        if (fileNode != null) {
            final DefaultMutableTreeNode file = fileNode;
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    treeModel.nodeChanged(file);
                    treeModel.nodeChanged(file.getParent());
                }
            });

        }
    }

    /**
     * On error of download progress of file
     */
    @Override
    public void onError(Download download) {

        DefaultMutableTreeNode fileNode = null;

        if (download instanceof FileDownloadStatus) {
            fileNode = instanceIDNodeMap.get(((FileDownloadStatus) download)
                    .getInstanceID());
        } else if (download instanceof DatasetDownloadStatus) {
            fileNode = instanceIDNodeMap.get(((DatasetDownloadStatus) download)
                    .getInstanceID());
        }

        if (fileNode != null) {
            final DefaultMutableTreeNode file = fileNode;
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    treeModel.nodeChanged(file);
                    treeModel.nodeChanged(file.getParent());
                }
            });

        }
    }

    /**
     * On error of permissions of download progress of file
     */
    @Override
    public synchronized void onUnauthorizedError(Download download) {
        updateUI();
    }

    /**
     * Tree of downloads renderer
     */
    public class TreeRenderer extends JPanel implements TreeCellRenderer {

        // attributes of each member tree of downloads
        /**
         * Label with ID {@link Metadata} of element if element is a dataset,
         * else if is a file then only a part of it
         */
        JLabel name;

        /** Progress bar that shows current state of download. */
        JProgressBar progressBar;

        /**
         * Current data node of download
         */
        JLabel dataNode;

        /**
         * Label with current download in bytes
         */
        JLabel currentDownload;

        /**
         * Label with total size of download in bytes
         */
        JLabel total;

        /**
         * Empty JPanel returned when cell is root
         */
        private JPanel empty;

        // Constructor
        TreeRenderer() {
            setLayout(new FlowLayout());
            setBackground(Color.WHITE);

            name = new JLabel();
            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            currentDownload = new JLabel();
            dataNode = new JLabel();
            total = new JLabel();
            empty = new JPanel();
            empty.setBackground(Color.WHITE);
            empty.setPreferredSize(new Dimension(0, 0));

            add(name);
            add(progressBar);
            add(currentDownload);
            add(total);
            add(dataNode);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row,
                boolean hasFocus) {

            Object userObject = ((DefaultMutableTreeNode) value)
                    .getUserObject();

            if (userObject instanceof DatasetDownloadStatus) {
                DatasetDownloadStatus dDStatus = (DatasetDownloadStatus) userObject;

                // Dataset id
                name.setText(dDStatus.getInstanceID());

                long currentSize = dDStatus.getCurrentSize();
                long totalSize = dDStatus.getTotalSize();

                // calculate % and reset init values of progress bar
                progressBar.setStringPainted(true);
                progressBar.setBackground(new Color(238, 238, 238));
                progressBar.setForeground(new Color(163, 184, 204));
                progressBar.setString(null);
                progressBar.setValue(dDStatus.getCurrentProgress());

                total.setText(bytesToString(totalSize));
                currentDownload.setText("   " + bytesToString(currentSize));
                dataNode.setText("");

                if (dDStatus.getRecordStatus() == RecordStatus.FINISHED) {
                    // light green
                    progressBar.setForeground(new Color(0, 204, 102));
                }

            } else if (userObject instanceof FileDownloadStatus) {

                FileDownloadStatus fDStatus = (FileDownloadStatus) userObject;
                // // file Id complete
                String fileId = fDStatus.getInstanceID();

                // dataset id complete
                String datasetId = fDStatus.getDatasetDownloadStatus()
                        .getInstanceID();

                // text, only part of id with info of file
                name.setText(fileId.substring(datasetId.length() + 1));

                long currentSize = fDStatus.getCurrentSize();
                long totalSize = fDStatus.getTotalSize();

                // calculate % and reset init values of progress bar
                progressBar.setStringPainted(true);
                progressBar.setBackground(new Color(238, 238, 238));
                progressBar.setForeground(new Color(163, 184, 204));
                progressBar.setString(null);
                progressBar.setValue(fDStatus.getCurrentProgress());

                if (fDStatus.getRecordStatus() == RecordStatus.UNAUTHORIZED) {
                    progressBar.setValue(0);
                    progressBar.setString("UNAUTHORIZED");
                    progressBar.setStringPainted(true);
                    // light yellow
                    progressBar.setBackground(new Color(252, 255, 134));
                } else if (fDStatus.getRecordStatus() == RecordStatus.CREATED
                        || fDStatus.getRecordStatus() == RecordStatus.READY) {
                    progressBar.setStringPainted(false);
                } else if (fDStatus.getRecordStatus() == RecordStatus.FAILED) {

                    // progressBar.setValue(0);
                    progressBar.setValue(100);
                    progressBar.setString("FAILED");
                    progressBar.setStringPainted(true);
                    // red
                    progressBar.setForeground(new Color(204, 0, 0));
                } else if (fDStatus.getRecordStatus() == RecordStatus.FINISHED) {
                    // green
                    progressBar.setForeground(new Color(0, 204, 102));
                    progressBar.setStringPainted(true);
                } else if (fDStatus.getRecordStatus() == RecordStatus.CHECKSUM_FAILED) {
                    progressBar.setString("CHECKSUM_FAILED");
                    // gray
                    progressBar.setForeground(new Color(96, 96, 96));
                    progressBar.setStringPainted(true);
                }

                currentDownload.setText("   " + bytesToString(currentSize));
                total.setText(bytesToString(totalSize));

                RecordReplica replica = fDStatus.getCurrentFileReplica();
                if (replica != null) {

                    // url without http
                    dataNode.setText("     "
                            + replica.getDataNode().substring(7));
                } else {
                    dataNode.setText("");
                }

            } else {
                // if is root
                return empty;
            }

            return this;
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

    private class DatasetMenu extends JPopupMenu {
        private DatasetDownloadStatus datasetStatus;

        public DatasetMenu(DatasetDownloadStatus datasetStatus, int x, int y) {
            super();
            this.datasetStatus = datasetStatus;

            RecordStatus status = datasetStatus.getRecordStatus();

            if (status != RecordStatus.FINISHED) {
                // Download option
                JMenuItem play = new JMenuItem("Download all");
                play.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {

                        try {
                            DatasetMenu.this.datasetStatus.download();
                        } catch (IOException e1) {
                            JOptionPane.showMessageDialog(
                                    DeprecatedDownloadsPanel.this,
                                    "Error reading info of dataset."
                                            + " Dataset can't be download");
                        }
                    }
                });
                add(play);
            }

            // pause option
            if (status == RecordStatus.DOWNLOADING) {
                JMenuItem pause = new JMenuItem("Pause all downloads");
                pause.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {

                        downloadManager
                                .pauseDataSetDownload(DatasetMenu.this.datasetStatus);
                    }
                });

                add(pause);
            }

            addSeparator();

            // THREDDS Catalog option

            List<RecordReplica> replicas;
            try {
                replicas = DownloadManager.getDatasetReplicasOfService(
                        DatasetMenu.this.datasetStatus.getInstanceID(),
                        Service.CATALOG);
            } catch (IOException e1) {
                logger.warn("dataset {} hasn't been obtained from file system",
                        DatasetMenu.this.datasetStatus.getInstanceID());
                replicas = null;
            }

            if (replicas != null) {
                JMenuItem catalog = new JMenu("open catalog in THREDDS Panel");

                for (final RecordReplica replica : replicas) {
                    JMenuItem replicaOption = new JMenuItem(
                            replica.getDataNode());
                    replicaOption.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {

                            String urlCatalog = replica
                                    .getUrlEndPointOfService(Service.CATALOG);

                            // Component.firePropertyChange(String propertyName,
                            // Object
                            // oldValue, Object newValue)
                            // this method fire new event with a name, old
                            // object and
                            // new object
                            // this event is catch and processed by main ESGF
                            DeprecatedDownloadsPanel.this.firePropertyChange(
                                    "openCatalog", null, urlCatalog);

                        }
                    });
                    catalog.add(replicaOption);
                }
                add(catalog);
                addSeparator();
            }

            // Reset option
            JMenuItem reset = new JMenuItem("Reset");
            reset.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    downloadManager
                            .resetDataSetDownload(DatasetMenu.this.datasetStatus);
                }
            });
            add(reset);

            // remove of download list option.
            JMenuItem remove = new JMenuItem("Remove");
            remove.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {

                    downloadManager
                            .removeDataset(DatasetMenu.this.datasetStatus);

                    // remove of tree of downloads
                    DefaultMutableTreeNode nodeToRemove = instanceIDNodeMap
                            .get(DatasetMenu.this.datasetStatus.getInstanceID());
                    treeModel.removeNodeFromParent(nodeToRemove);
                    instanceIDNodeMap.remove(DatasetMenu.this.datasetStatus
                            .getInstanceID());

                    // remove all file nodes
                    int index = 0;
                    while (index < nodeToRemove.getChildCount()) {
                        DefaultMutableTreeNode fileNode = (DefaultMutableTreeNode) nodeToRemove
                                .getChildAt(index);
                        treeModel.removeNodeFromParent(fileNode);
                        instanceIDNodeMap.remove(((FileDownloadStatus) fileNode
                                .getUserObject()).getInstanceID());
                    }
                    updateUI();
                }
            });

            add(remove);

            // info option
            JMenuItem info = new JMenuItem("tempInfo");
            info.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    String info = DatasetMenu.this.datasetStatus.toString();
                    System.out.println(info);

                }
            });
            // add(info);

            show(treeOfDownloads, x, y);
        }
    }

    private class FileMenu extends JPopupMenu {
        private FileDownloadStatus fileStatus;

        public FileMenu(FileDownloadStatus fileStatus, int x, int y) {
            super();
            this.fileStatus = fileStatus;

            RecordStatus status = fileStatus.getRecordStatus();

            // File flow options-----------------------------------------------
            // download option. Configurated file, set READY and put in queue
            if (status == RecordStatus.CREATED || status == RecordStatus.PAUSED) {
                JMenuItem download = new JMenuItem("Resume download");
                download.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {

                        try {
                            downloadManager
                                    .downloadFile(FileMenu.this.fileStatus);
                        } catch (IOException e1) {
                            JOptionPane.showMessageDialog(
                                    DeprecatedDownloadsPanel.this,
                                    "Error reading info of file: "
                                            + FileMenu.this.fileStatus
                                                    .getInstanceID()
                                            + ". File can't be download");
                        }
                    }
                });

                add(download);
            }

            // pause option
            if (status == RecordStatus.DOWNLOADING) {
                JMenuItem pause = new JMenuItem("Pause");
                pause.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {

                        downloadManager.pauseFile(FileMenu.this.fileStatus);
                    }
                });

                add(pause);
            }
            // End file flow options-----------------------------------------

            // Viewer option-------------------------------------------------
            addSeparator();
            JMenu viewerMenu = new JMenu("Open in Viewer Panel");

            // local file sub-option
            if (status == RecordStatus.FINISHED) {
                JMenuItem openFile = new JMenuItem("Local");
                openFile.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {

                        String path = FileMenu.this.fileStatus.getFilePath();

                        // Component.firePropertyChange(String propertyName,
                        // Object oldValue, Object newValue) this method fire
                        // new event with a name, old object and new object this
                        // event is catch and processed by main ESGF
                        DeprecatedDownloadsPanel.this.firePropertyChange("openFile",
                                null, path);

                    }
                });
                viewerMenu.add(openFile);
                viewerMenu.addSeparator();
            }

            // Remote options
            JMenuItem remoteOptions = new JMenu("Remote");

            // remote openfile
            List<RecordReplica> httpReplicas;
            try {
                httpReplicas = DownloadManager.getFileReplicasOfService(
                        FileMenu.this.fileStatus.getDatasetDownloadStatus()
                                .getInstanceID(), FileMenu.this.fileStatus
                                .getInstanceID(), Service.HTTPSERVER);
            } catch (IOException e1) {
                logger.warn("File {} hasn't been obtained from file system",
                        FileMenu.this.fileStatus.getInstanceID());
                httpReplicas = null;
            }

            if (httpReplicas != null) {
                JMenuItem remoteOpenFile = new JMenu("File URL");

                for (final RecordReplica replica : httpReplicas) {
                    JMenuItem replicaOption = new JMenuItem(
                            replica.getDataNode());
                    replicaOption.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {

                            String urlOpenFile = replica
                                    .getUrlEndPointOfService(Service.HTTPSERVER);
                            // Component.firePropertyChange(String propertyName,
                            // Object oldValue, Object newValue) this method
                            // fire new event with a name, old object and new
                            // object
                            // this event is catch and processed by main ESGF
                            DeprecatedDownloadsPanel.this.firePropertyChange(
                                    "openFile", null, urlOpenFile);

                        }
                    });
                    remoteOpenFile.add(replicaOption);
                }

                remoteOptions.add(remoteOpenFile);
            }

            // remoteOpenDAP

            List<RecordReplica> openDapReplicas;
            try {
                openDapReplicas = DownloadManager.getFileReplicasOfService(
                        FileMenu.this.fileStatus.getDatasetDownloadStatus()
                                .getInstanceID(), FileMenu.this.fileStatus
                                .getInstanceID(), Service.OPENDAP);
            } catch (IOException e1) {
                logger.warn("File {} hasn't been obtained from file system",
                        FileMenu.this.fileStatus.getInstanceID());
                openDapReplicas = null;

            }
            if (openDapReplicas != null) {
                JMenuItem remoteOpenDap = new JMenu("OPeNDAP URL");

                for (final RecordReplica replica : openDapReplicas) {
                    JMenuItem replicaOption = new JMenuItem(
                            replica.getDataNode());
                    replicaOption.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {

                            String urlOpenDap = replica
                                    .getUrlEndPointOfService(Service.OPENDAP);

                            // Quit .html in the endpoint because
                            // this .html only have sense in the browser
                            urlOpenDap = urlOpenDap.substring(0,
                                    urlOpenDap.indexOf(".html"));

                            // Component.firePropertyChange(String propertyName,
                            // Object oldValue, Object newValue) this method
                            // fire new event with a name, old object and new
                            // object this event is catch and processed by main
                            // ESGF
                            DeprecatedDownloadsPanel.this.firePropertyChange(
                                    "openFile", null, urlOpenDap);

                        }
                    });
                    remoteOpenDap.add(replicaOption);
                }

                remoteOptions.add(remoteOpenDap);
            }

            viewerMenu.add(remoteOptions);
            add(viewerMenu);
            // End viewer option--------------------------------------------

            // FeatureTypes
            // option-------------------------------------------------
            JMenu fTypesMenu = new JMenu("Open in FeatureTypes Panel");

            // local file sub-option
            if (status == RecordStatus.FINISHED) {
                JMenuItem openFile = new JMenuItem("Local");
                openFile.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {

                        String path = FileMenu.this.fileStatus.getFilePath();

                        // Component.firePropertyChange(String propertyName,
                        // Object oldValue, Object newValue) this method fire
                        // new event with a name, old object and new object this
                        // event is catch and processed by main ESGF
                        DeprecatedDownloadsPanel.this.firePropertyChange(
                                "openFileInGridFeatureTypes", null, path);

                    }
                });
                fTypesMenu.add(openFile);
                fTypesMenu.addSeparator();
            }

            // Remote options
            JMenuItem remoteFTypesOptions = new JMenu("Remote");

            // remote openfile
            if (httpReplicas != null) {
                JMenuItem remoteOpenFile = new JMenu("File URL");

                for (final RecordReplica replica : httpReplicas) {
                    JMenuItem replicaOption = new JMenuItem(
                            replica.getDataNode());
                    replicaOption.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {

                            String urlOpenFile = replica
                                    .getUrlEndPointOfService(Service.HTTPSERVER);
                            // Component.firePropertyChange(String propertyName,
                            // Object oldValue, Object newValue) this method
                            // fire new event with a name, old object and new
                            // object
                            // this event is catch and processed by main ESGF
                            DeprecatedDownloadsPanel.this.firePropertyChange(
                                    "openFileInGridFeatureTypes", null,
                                    urlOpenFile);

                        }
                    });
                    remoteOpenFile.add(replicaOption);
                }

                remoteFTypesOptions.add(remoteOpenFile);
            }

            // remoteOpenDAP

            if (openDapReplicas != null) {
                JMenuItem remoteOpenDap = new JMenu("OPeNDAP URL");

                for (final RecordReplica replica : openDapReplicas) {
                    JMenuItem replicaOption = new JMenuItem(
                            replica.getDataNode());
                    replicaOption.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {

                            String urlOpenDap = replica
                                    .getUrlEndPointOfService(Service.OPENDAP);

                            // Quit .html in the endpoint because
                            // this .html only have sense in the browser
                            urlOpenDap = urlOpenDap.substring(0,
                                    urlOpenDap.indexOf(".html"));

                            // Component.firePropertyChange(String propertyName,
                            // Object oldValue, Object newValue) this method
                            // fire new event with a name, old object and new
                            // object this event is catch and processed by main
                            // ESGF
                            DeprecatedDownloadsPanel.this.firePropertyChange(
                                    "openFileInGridFeatureTypes", null,
                                    urlOpenDap);

                        }
                    });
                    remoteOpenDap.add(replicaOption);
                }

                remoteFTypesOptions.add(remoteOpenDap);
            }

            fTypesMenu.add(remoteFTypesOptions);
            add(fTypesMenu);
            // End Feature types
            // option--------------------------------------------

            // Reset and remove options--------------------------------------
            // reset option
            addSeparator();
            JMenu reset = new JMenu("Reset download");

            JMenuItem resetInCurrentReplica = new JMenuItem("Current replica");
            resetInCurrentReplica.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {

                    int confirm = JOptionPane
                            .showConfirmDialog(
                                    DeprecatedDownloadsPanel.this,
                                    "Sure you want delete all progress of file download in file system?",
                                    "reset", JOptionPane.YES_NO_OPTION);

                    if (confirm == JOptionPane.YES_OPTION) {
                        downloadManager.resetFile(FileMenu.this.fileStatus);
                    }
                }
            });
            reset.add(resetInCurrentReplica);

            // reset in another replica
            if (httpReplicas != null) {

                if (httpReplicas.size() > 1) {

                    reset.addSeparator();
                    JMenuItem changeReplica = new JMenu("Select replica");

                    // Add all replicas data nodes
                    for (final RecordReplica replica : httpReplicas) {
                        if (replica != FileMenu.this.fileStatus
                                .getCurrentFileReplica()) {
                            JMenuItem replicaOption = new JMenuItem(replica
                                    .getDataNode().substring(7));
                            replicaOption
                                    .addActionListener(new ActionListener() {

                                        @Override
                                        public void actionPerformed(
                                                ActionEvent e) {
                                            // reset
                                            downloadManager
                                                    .resetFile(FileMenu.this.fileStatus);
                                            // set new replica
                                            FileMenu.this.fileStatus
                                                    .setCurrentFileReplica(replica);
                                        }
                                    });
                            changeReplica.add(replicaOption);
                        }
                    }
                    reset.add(changeReplica);
                }
            }
            add(reset);

            // remove of download list option.
            JMenuItem remove = new JMenuItem("Remove");
            remove.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {

                    downloadManager.skipFile(FileMenu.this.fileStatus);

                    // remove of tree of downloads
                    DefaultMutableTreeNode nodeToRemove = instanceIDNodeMap
                            .get(FileMenu.this.fileStatus.getInstanceID());
                    treeModel.removeNodeFromParent(nodeToRemove);
                    instanceIDNodeMap.remove(FileMenu.this.fileStatus
                            .getInstanceID());
                    updateUI();
                }
            });

            add(remove);
            // End reset and remove options-----------------------------------

            // info option
            JMenuItem info = new JMenuItem("File info");
            info.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {

                    FileStatusInfoDialog dialog = new FileStatusInfoDialog(
                            FileMenu.this.fileStatus,
                            (Frame) DeprecatedDownloadsPanel.this
                                    .getTopLevelAncestor());
                    dialog.setVisible(true);
                }
            });
            add(info);

            show(treeOfDownloads, x, y);

            // TreePath path = treeOfDownloads.getPathForLocation(x, y);
            // treeOfDownloads.expandPath(path.getParentPath());
        }
    }

}
