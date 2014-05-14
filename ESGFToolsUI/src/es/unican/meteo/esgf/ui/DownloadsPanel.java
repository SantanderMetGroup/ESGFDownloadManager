package es.unican.meteo.esgf.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreePath;

import org.jdesktop.swingx.JXTreeTable;
import org.jdesktop.swingx.tree.TreeModelSupport;

import ucar.util.prefs.PreferencesExt;
import es.unican.meteo.esgf.download.DatasetDownloadStatus;
import es.unican.meteo.esgf.download.DownloadManager;
import es.unican.meteo.esgf.download.FileDownloadStatus;
import es.unican.meteo.esgf.download.RecordStatus;

/**
 * Panel that shows progress of current downloads. Implements DownloadObserver
 * for be notified of file download progress
 * 
 * @author terryk
 * 
 */
public class DownloadsPanel extends JPanel {

    /**
     * Logger
     */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(DownloadsPanel.class);

    /**
     * SerialVersionUID
     */
    private static final long serialVersionUID = -6544810788283927158L;

    /** Preferences of configuration. */
    private final PreferencesExt prefs;

    /** Download Manager. Manage download of datasets */
    private final DownloadManager downloadManager;

    /** Model of tree List. */
    private JXTreeTable treeTable;

    /** Model of tree List. */
    private DownloadsTableModel treeModel;

    private TreeModelSupport treeModelSupport;

    String prueba;
    JTextField text;

    private Point lastMousePoint;

    // private FileMenu fileMenu;
    // private DatasetMenu datasetMenu;

    /**
     * Constructor
     * 
     * @param prefs
     *            preferences
     */
    public DownloadsPanel(PreferencesExt prefs, DownloadManager downloadManager) {
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
                DownloadsPanel.this.downloadManager.startAllDownloads();
            }
        });

        JButton pauseAllDownloads = new JButton("Pause all downloads");
        pauseAllDownloads.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                DownloadsPanel.this.downloadManager.pauseActiveDownloads();
            }
        });

        JButton removeAllDownloads = new JButton("Remove all downloads");
        removeAllDownloads.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {

                int confirm = JOptionPane.showConfirmDialog(
                        DownloadsPanel.this,
                        "Sure you want remove all downloads?", "Remove",
                        JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    DownloadsPanel.this.downloadManager.reset();
                    treeModel = new DownloadsTableModel(
                            new ArrayList<DatasetDownloadStatus>(
                                    DownloadsPanel.this.downloadManager
                                            .getDatasetDownloads()));
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
        // Tre table of downloads------------------------------------------

        treeModel = new DownloadsTableModel(
                new ArrayList<DatasetDownloadStatus>(
                        DownloadsPanel.this.downloadManager
                                .getDatasetDownloads()));

        treeTable = new JXTreeTable(treeModel);
        treeTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        treeTable.setRootVisible(false); // hide the root
        treeTable.getColumnModel().getColumn(1)
                .setCellRenderer(new ProgressBarRenderer());

        this.treeModelSupport = treeModel.getTreeModelSupport();

        treeModelSupport.addTreeModelListener(new TreeModelListener() {

            @Override
            public void treeStructureChanged(TreeModelEvent arg0) {
                // treeTable.repaint();
                // System.out.println("X treeStructureChanged " + arg0);
            }

            @Override
            public void treeNodesRemoved(TreeModelEvent arg0) {
                // System.out.println("X treeNodesRemoved " + arg0);

            }

            @Override
            public void treeNodesInserted(TreeModelEvent arg0) {
                // System.out.println("X treeNodesInserted " + arg0);

            }

            @Override
            public void treeNodesChanged(TreeModelEvent arg0) {
                treeTable.repaint();
                // System.out.println("X treeNodesChanged " + arg0);
            }
        });

        treeTable.addMouseListener(new MouseListener() {

            @Override
            public void mouseReleased(MouseEvent e) {

                // Popup menus are triggered differently on different systems.
                // Therefore, isPopupTrigger should be checked in both
                // mousePressed and mouseReleased for proper cross-platform
                // functionality.
                if (e.isPopupTrigger()) {

                    int x = e.getX();
                    int y = e.getY();

                    TreePath path = treeTable.getPathForLocation(x, y);
                    Object object = path.getLastPathComponent();

                    // int row = treeTable.getSelectedRow();
                    // treeTable.getPo
                    // System.out.println(arg0.getPath());
                    // System.out.println(arg0.getPath().getLastPathComponent());
                    RecordPopupMenu popupMenu = new RecordPopupMenu(object,
                            DownloadsPanel.this.treeTable,
                            DownloadsPanel.this.downloadManager, x, y);
                    popupMenu
                            .addPropertyChangeListener(new PropertyChangeListener() {

                                @Override
                                public void propertyChange(
                                        PropertyChangeEvent evt) {
                                    // Component.firePropertyChange(String
                                    // propertyName, Object oldValue, Object
                                    // newValue)
                                    // this method fire new event with a name,
                                    // old object and new object this event is
                                    // catch and
                                    // processed by main ESGF

                                    DownloadsPanel.this.firePropertyChange(
                                            evt.getPropertyName(),
                                            evt.getOldValue(),
                                            evt.getNewValue());

                                }
                            });
                }// end if(isPopUpTrigger)

            }

            @Override
            public void mousePressed(MouseEvent e) {

                // Popup menus are triggered differently on different systems.
                // Therefore, isPopupTrigger should be checked in both
                // mousePressed and mouseReleased for proper cross-platform
                // functionality.
                if (e.isPopupTrigger()) {

                    int x = e.getX();
                    int y = e.getY();

                    TreePath path = treeTable.getPathForLocation(x, y);
                    Object object = path.getLastPathComponent();

                    // int row = treeTable.getSelectedRow();
                    // treeTable.getPo
                    // System.out.println(arg0.getPath());
                    // System.out.println(arg0.getPath().getLastPathComponent());
                    RecordPopupMenu popupMenu = new RecordPopupMenu(object,
                            DownloadsPanel.this.treeTable,
                            DownloadsPanel.this.downloadManager, x, y);
                    popupMenu
                            .addPropertyChangeListener(new PropertyChangeListener() {

                                @Override
                                public void propertyChange(
                                        PropertyChangeEvent evt) {
                                    // Component.firePropertyChange(String
                                    // propertyName, Object oldValue, Object
                                    // newValue)
                                    // this method fire new event with a name,
                                    // old object and new object this event is
                                    // catch and
                                    // processed by main ESGF

                                    DownloadsPanel.this.firePropertyChange(
                                            evt.getPropertyName(),
                                            evt.getOldValue(),
                                            evt.getNewValue());

                                }
                            });
                }// end if(isPopUpTrigger)
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // nothing

            }

            @Override
            public void mouseEntered(MouseEvent e) {
                // nothing

            }

            @Override
            public void mouseClicked(MouseEvent e) {
                // nothing
            }
        });

        // treeTable.add

        // treeTable.getSelectionModel().addListSelectionListener(
        // new ListSelectionListener() {
        //
        // @Override
        // public void valueChanged(ListSelectionEvent arg0) {
        // System.out.println("lol" + arg0);
        // }
        // });

        // --- MENU TREE LISTENER-----------------------------------------------

        // Mouse Listener, controls double click in facet values
        /*
         * MouseListener mouseListener = new MouseAdapter() {
         * 
         * @Override public void mousePressed(MouseEvent e) {
         * 
         * // Path of tree element ->son path [grandparent, father, son]
         * TreePath selPath = treeTable.getPathForLocation(e.getX(), e.getY());
         * 
         * // If path objects from mouse location is not null if (selPath !=
         * null) {
         * 
         * // if secondary click if (e.getButton() == MouseEvent.BUTTON3) { //
         * Path of parent tree node TreePath parentPath =
         * selPath.getParentPath();
         * 
         * // If parent is no root if (parentPath.getParentPath() != null) {
         * 
         * // Get value of tree node selected Object treeNode =
         * ((DefaultMutableTreeNode) selPath
         * .getLastPathComponent()).getUserObject();
         * 
         * if (treeNode instanceof FileDownloadStatus) {
         * 
         * fileMenu = new FileMenu( (FileDownloadStatus) treeNode, e.getX(),
         * e.getY()); // expand(parentPath);
         * 
         * }
         * 
         * } else {
         * 
         * // Get value of tree node selected Object treeNode =
         * ((DefaultMutableTreeNode) selPath
         * .getLastPathComponent()).getUserObject();
         * 
         * if (treeNode instanceof DatasetDownloadStatus) {
         * 
         * datasetMenu = new DatasetMenu( (DatasetDownloadStatus) treeNode,
         * e.getX(), e.getY()); // expand(selPath); } }
         * 
         * treeTable.expandPath(parentPath); } } } };
         */

        // treeTable.addMouseListener(mouseListener);

        // ---------------------------------------------------------------------

        // Add Jtree a scrollable panel with a viewport view
        // JScrollPane treePanel = new JScrollPane();
        // treePanel.setViewportView(treeOfDownloads);
        // add(treePanel, BorderLayout.CENTER);
        add(toolBar, BorderLayout.NORTH);
        add(new JScrollPane(treeTable), BorderLayout.CENTER);

        // -end center------------------------------------------------
    }

    // /**
    // *
    // */
    // public void update() {
    //
    // // Add new node for each dataset and its files for download
    // // Also add an observer of each new download
    // for (DatasetDownloadStatus dDStatus : downloadManager
    // .getDatasetDownloads()) {
    //
    // DefaultMutableTreeNode parentNode = null;
    //
    // if (!instanceIDNodeMap.containsKey(dDStatus.getInstanceID())) {
    //
    // parentNode = new DefaultMutableTreeNode(dDStatus);
    //
    // synchronized (instanceIDNodeMap) {
    // instanceIDNodeMap.put(dDStatus.getInstanceID(), parentNode);
    // }
    //
    // // insert new parent (dataset status) in tree
    // // insertNodeinto(childNode, parentNode, index)
    // treeModel.insertNodeInto(parentNode, root,
    // treeModel.getChildCount(root));
    //
    // } else {
    // parentNode = instanceIDNodeMap.get(dDStatus.getInstanceID());
    // }
    //
    // if (parentNode != null) {
    // for (FileDownloadStatus fileStatus : dDStatus
    // .getFilesDownloadStatus()) {
    //
    // // Add file in tree of downloads if file status isn't
    // // SKIPPED
    // if (fileStatus.getRecordStatus() != RecordStatus.SKIPPED) {
    //
    // // if file doesn't have an register observer, register
    // // new
    // // observer
    // if (!fileStatus.containsObserver(this)) {
    // fileStatus.registerObserver(this);
    //
    // }
    //
    // // if there aren't a node for this file in tree of
    // // downloads
    // // add new
    // if (!instanceIDNodeMap.containsKey(fileStatus
    // .getInstanceID())) {
    // // Add to list and add to tree of downloads
    // DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(
    // fileStatus);
    //
    // synchronized (instanceIDNodeMap) {
    // instanceIDNodeMap.put(
    // fileStatus.getInstanceID(), newNode);
    // }
    // // insert new child (file status) in tree
    // // insertNodeinto(childNode, parentNode, index)
    // // parent node is node of dataset of file
    // treeModel.insertNodeInto(newNode, parentNode,
    // parentNode.getChildCount());
    // }
    // }
    // }
    // }
    // }
    //
    // // reload tree model
    // treeModel.reload();
    // }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
    }

    // /**
    // * On download progress of file
    // */
    // @Override
    // public void onDownloadProgress(Download download) {
    //
    // repaint();
    //
    // }

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

    //
    // private class DatasetMenu extends JPopupMenu {
    // private DatasetDownloadStatus datasetStatus;
    //
    // public DatasetMenu(DatasetDownloadStatus datasetStatus, int x, int y) {
    // super();
    // this.datasetStatus = datasetStatus;
    //
    // RecordStatus status = datasetStatus.getRecordStatus();
    //
    // if (status != RecordStatus.FINISHED) {
    // // Download option
    // JMenuItem play = new JMenuItem("Download all");
    // play.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //
    // try {
    // DatasetMenu.this.datasetStatus.download();
    // } catch (IOException e1) {
    // JOptionPane.showMessageDialog(DownloadsPanel.this,
    // "Error reading info of dataset."
    // + " Dataset can't be download");
    // }
    // }
    // });
    // add(play);
    // }
    //
    // // pause option
    // if (status == RecordStatus.DOWNLOADING) {
    // JMenuItem pause = new JMenuItem("Pause all downloads");
    // pause.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //
    // downloadManager
    // .pauseDataSetDownload(DatasetMenu.this.datasetStatus);
    // }
    // });
    //
    // add(pause);
    // }
    //
    // addSeparator();
    //
    // // THREDDS Catalog option
    //
    // List<RecordReplica> replicas;
    // try {
    // replicas = DownloadManager.getDatasetReplicasOfService(
    // DatasetMenu.this.datasetStatus.getInstanceID(),
    // Service.CATALOG);
    // } catch (IOException e1) {
    // logger.warn("dataset {} hasn't been obtained from file system",
    // DatasetMenu.this.datasetStatus.getInstanceID());
    // replicas = null;
    // }
    //
    // if (replicas != null) {
    // JMenuItem catalog = new JMenu("open catalog in THREDDS Panel");
    //
    // for (final RecordReplica replica : replicas) {
    // JMenuItem replicaOption = new JMenuItem(
    // replica.getDataNode());
    // replicaOption.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //
    // String urlCatalog = replica
    // .getUrlEndPointOfService(Service.CATALOG);
    //
    // // Component.firePropertyChange(String propertyName,
    // // Object
    // // oldValue, Object newValue)
    // // this method fire new event with a name, old
    // // object and
    // // new object
    // // this event is catch and processed by main ESGF
    // DownloadsPanel.this.firePropertyChange(
    // "openCatalog", null, urlCatalog);
    //
    // }
    // });
    // catalog.add(replicaOption);
    // }
    // add(catalog);
    // addSeparator();
    // }
    //
    // // Reset option
    // JMenuItem reset = new JMenuItem("Reset");
    // reset.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    // downloadManager
    // .resetDataSetDownload(DatasetMenu.this.datasetStatus);
    // }
    // });
    // add(reset);
    //
    // // remove of download list option.
    // JMenuItem remove = new JMenuItem("Remove");
    // remove.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //
    // downloadManager
    // .removeDataset(DatasetMenu.this.datasetStatus);
    //
    // // remove of tree of downloads
    // DefaultMutableTreeNode nodeToRemove = instanceIDNodeMap
    // .get(DatasetMenu.this.datasetStatus.getInstanceID());
    // treeModel.removeNodeFromParent(nodeToRemove);
    // instanceIDNodeMap.remove(DatasetMenu.this.datasetStatus
    // .getInstanceID());
    //
    // // remove all file nodes
    // int index = 0;
    // while (index < nodeToRemove.getChildCount()) {
    // DefaultMutableTreeNode fileNode = (DefaultMutableTreeNode) nodeToRemove
    // .getChildAt(index);
    // treeModel.removeNodeFromParent(fileNode);
    // instanceIDNodeMap.remove(((FileDownloadStatus) fileNode
    // .getUserObject()).getInstanceID());
    // }
    // updateUI();
    // }
    // });
    //
    // add(remove);
    //
    // // info option
    // JMenuItem info = new JMenuItem("tempInfo");
    // info.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    // String info = DatasetMenu.this.datasetStatus.toString();
    // System.out.println(info);
    //
    // }
    // });
    // // add(info);
    //
    // show(treeTable, x, y);
    // }
    // }
    //
    // private class FileMenu extends JPopupMenu {
    // private FileDownloadStatus fileStatus;
    //
    // public FileMenu(FileDownloadStatus fileStatus, int x, int y) {
    // super();
    // this.fileStatus = fileStatus;
    //
    // RecordStatus status = fileStatus.getRecordStatus();
    //
    // // File flow options-----------------------------------------------
    // // download option. Configurated file, set READY and put in queue
    // if (status == RecordStatus.CREATED || status == RecordStatus.PAUSED) {
    // JMenuItem download = new JMenuItem("Resume download");
    // download.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //
    // try {
    // downloadManager
    // .downloadFile(FileMenu.this.fileStatus);
    // } catch (IOException e1) {
    // JOptionPane.showMessageDialog(
    // DownloadsPanel.this,
    // "Error reading info of file: "
    // + FileMenu.this.fileStatus
    // .getInstanceID()
    // + ". File can't be download");
    // }
    // }
    // });
    //
    // add(download);
    // }
    //
    // // pause option
    // if (status == RecordStatus.DOWNLOADING) {
    // JMenuItem pause = new JMenuItem("Pause");
    // pause.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //
    // downloadManager.pauseFile(FileMenu.this.fileStatus);
    // }
    // });
    //
    // add(pause);
    // }
    // // End file flow options-----------------------------------------
    //
    // // Viewer option-------------------------------------------------
    // addSeparator();
    // JMenu viewerMenu = new JMenu("Open in Viewer Panel");
    //
    // // local file sub-option
    // if (status == RecordStatus.FINISHED) {
    // JMenuItem openFile = new JMenuItem("Local");
    // openFile.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //
    // String path = FileMenu.this.fileStatus.getFilePath();
    //
    // // Component.firePropertyChange(String propertyName,
    // // Object oldValue, Object newValue) this method fire
    // // new event with a name, old object and new object this
    // // event is catch and processed by main ESGF
    // DownloadsPanel.this.firePropertyChange("openFile",
    // null, path);
    //
    // }
    // });
    // viewerMenu.add(openFile);
    // viewerMenu.addSeparator();
    // }
    //
    // // Remote options
    // JMenuItem remoteOptions = new JMenu("Remote");
    //
    // // remote openfile
    // List<RecordReplica> httpReplicas;
    // try {
    // httpReplicas = DownloadManager.getFileReplicasOfService(
    // FileMenu.this.fileStatus.getDatasetDownloadStatus()
    // .getInstanceID(), FileMenu.this.fileStatus
    // .getInstanceID(), Service.HTTPSERVER);
    // } catch (IOException e1) {
    // logger.warn("File {} hasn't been obtained from file system",
    // FileMenu.this.fileStatus.getInstanceID());
    // httpReplicas = null;
    // }
    //
    // if (httpReplicas != null) {
    // JMenuItem remoteOpenFile = new JMenu("File URL");
    //
    // for (final RecordReplica replica : httpReplicas) {
    // JMenuItem replicaOption = new JMenuItem(
    // replica.getDataNode());
    // replicaOption.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //
    // String urlOpenFile = replica
    // .getUrlEndPointOfService(Service.HTTPSERVER);
    // // Component.firePropertyChange(String propertyName,
    // // Object oldValue, Object newValue) this method
    // // fire new event with a name, old object and new
    // // object
    // // this event is catch and processed by main ESGF
    // DownloadsPanel.this.firePropertyChange("openFile",
    // null, urlOpenFile);
    //
    // }
    // });
    // remoteOpenFile.add(replicaOption);
    // }
    //
    // remoteOptions.add(remoteOpenFile);
    // }
    //
    // // remoteOpenDAP
    //
    // List<RecordReplica> openDapReplicas;
    // try {
    // openDapReplicas = DownloadManager.getFileReplicasOfService(
    // FileMenu.this.fileStatus.getDatasetDownloadStatus()
    // .getInstanceID(), FileMenu.this.fileStatus
    // .getInstanceID(), Service.OPENDAP);
    // } catch (IOException e1) {
    // logger.warn("File {} hasn't been obtained from file system",
    // FileMenu.this.fileStatus.getInstanceID());
    // openDapReplicas = null;
    //
    // }
    // if (openDapReplicas != null) {
    // JMenuItem remoteOpenDap = new JMenu("OPeNDAP URL");
    //
    // for (final RecordReplica replica : openDapReplicas) {
    // JMenuItem replicaOption = new JMenuItem(
    // replica.getDataNode());
    // replicaOption.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //
    // String urlOpenDap = replica
    // .getUrlEndPointOfService(Service.OPENDAP);
    //
    // // Quit .html in the endpoint because
    // // this .html only have sense in the browser
    // urlOpenDap = urlOpenDap.substring(0,
    // urlOpenDap.indexOf(".html"));
    //
    // // Component.firePropertyChange(String propertyName,
    // // Object oldValue, Object newValue) this method
    // // fire new event with a name, old object and new
    // // object this event is catch and processed by main
    // // ESGF
    // DownloadsPanel.this.firePropertyChange("openFile",
    // null, urlOpenDap);
    //
    // }
    // });
    // remoteOpenDap.add(replicaOption);
    // }
    //
    // remoteOptions.add(remoteOpenDap);
    // }
    //
    // viewerMenu.add(remoteOptions);
    // add(viewerMenu);
    // // End viewer option--------------------------------------------
    //
    // // FeatureTypes
    // // option-------------------------------------------------
    // JMenu fTypesMenu = new JMenu("Open in FeatureTypes Panel");
    //
    // // local file sub-option
    // if (status == RecordStatus.FINISHED) {
    // JMenuItem openFile = new JMenuItem("Local");
    // openFile.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //
    // String path = FileMenu.this.fileStatus.getFilePath();
    //
    // // Component.firePropertyChange(String propertyName,
    // // Object oldValue, Object newValue) this method fire
    // // new event with a name, old object and new object this
    // // event is catch and processed by main ESGF
    // DownloadsPanel.this.firePropertyChange(
    // "openFileInGridFeatureTypes", null, path);
    //
    // }
    // });
    // fTypesMenu.add(openFile);
    // fTypesMenu.addSeparator();
    // }
    //
    // // Remote options
    // JMenuItem remoteFTypesOptions = new JMenu("Remote");
    //
    // // remote openfile
    // if (httpReplicas != null) {
    // JMenuItem remoteOpenFile = new JMenu("File URL");
    //
    // for (final RecordReplica replica : httpReplicas) {
    // JMenuItem replicaOption = new JMenuItem(
    // replica.getDataNode());
    // replicaOption.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //
    // String urlOpenFile = replica
    // .getUrlEndPointOfService(Service.HTTPSERVER);
    // // Component.firePropertyChange(String propertyName,
    // // Object oldValue, Object newValue) this method
    // // fire new event with a name, old object and new
    // // object
    // // this event is catch and processed by main ESGF
    // DownloadsPanel.this.firePropertyChange(
    // "openFileInGridFeatureTypes", null,
    // urlOpenFile);
    //
    // }
    // });
    // remoteOpenFile.add(replicaOption);
    // }
    //
    // remoteFTypesOptions.add(remoteOpenFile);
    // }
    //
    // // remoteOpenDAP
    //
    // if (openDapReplicas != null) {
    // JMenuItem remoteOpenDap = new JMenu("OPeNDAP URL");
    //
    // for (final RecordReplica replica : openDapReplicas) {
    // JMenuItem replicaOption = new JMenuItem(
    // replica.getDataNode());
    // replicaOption.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //
    // String urlOpenDap = replica
    // .getUrlEndPointOfService(Service.OPENDAP);
    //
    // // Quit .html in the endpoint because
    // // this .html only have sense in the browser
    // urlOpenDap = urlOpenDap.substring(0,
    // urlOpenDap.indexOf(".html"));
    //
    // // Component.firePropertyChange(String propertyName,
    // // Object oldValue, Object newValue) this method
    // // fire new event with a name, old object and new
    // // object this event is catch and processed by main
    // // ESGF
    // DownloadsPanel.this.firePropertyChange(
    // "openFileInGridFeatureTypes", null,
    // urlOpenDap);
    //
    // }
    // });
    // remoteOpenDap.add(replicaOption);
    // }
    //
    // remoteFTypesOptions.add(remoteOpenDap);
    // }
    //
    // fTypesMenu.add(remoteFTypesOptions);
    // add(fTypesMenu);
    // // End Feature types
    // // option--------------------------------------------
    //
    // // Reset and remove options--------------------------------------
    // // reset option
    // addSeparator();
    // JMenu reset = new JMenu("Reset download");
    //
    // JMenuItem resetInCurrentReplica = new JMenuItem("Current replica");
    // resetInCurrentReplica.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //
    // int confirm = JOptionPane
    // .showConfirmDialog(
    // DownloadsPanel.this,
    // "Sure you want delete all progress of file download in file system?",
    // "reset", JOptionPane.YES_NO_OPTION);
    //
    // if (confirm == JOptionPane.YES_OPTION) {
    // downloadManager.resetFile(FileMenu.this.fileStatus);
    // }
    // }
    // });
    // reset.add(resetInCurrentReplica);
    //
    // // reset in another replica
    // if (httpReplicas != null) {
    //
    // if (httpReplicas.size() > 1) {
    //
    // reset.addSeparator();
    // JMenuItem changeReplica = new JMenu("Select replica");
    //
    // // Add all replicas data nodes
    // for (final RecordReplica replica : httpReplicas) {
    // if (replica != FileMenu.this.fileStatus
    // .getCurrentFileReplica()) {
    // JMenuItem replicaOption = new JMenuItem(replica
    // .getDataNode().substring(7));
    // replicaOption
    // .addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(
    // ActionEvent e) {
    // // reset
    // downloadManager
    // .resetFile(FileMenu.this.fileStatus);
    // // set new replica
    // FileMenu.this.fileStatus
    // .setCurrentFileReplica(replica);
    // }
    // });
    // changeReplica.add(replicaOption);
    // }
    // }
    // reset.add(changeReplica);
    // }
    // }
    // add(reset);
    //
    // // remove of download list option.
    // JMenuItem remove = new JMenuItem("Remove");
    // remove.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //
    // downloadManager.skipFile(FileMenu.this.fileStatus);
    //
    // // remove of tree of downloads
    // DefaultMutableTreeNode nodeToRemove = instanceIDNodeMap
    // .get(FileMenu.this.fileStatus.getInstanceID());
    // treeModel.removeNodeFromParent(nodeToRemove);
    // instanceIDNodeMap.remove(FileMenu.this.fileStatus
    // .getInstanceID());
    // updateUI();
    // }
    // });
    //
    // add(remove);
    // // End reset and remove options-----------------------------------
    //
    // // info option
    // JMenuItem info = new JMenuItem("File info");
    // info.addActionListener(new ActionListener() {
    //
    // @Override
    // public void actionPerformed(ActionEvent e) {
    //
    // FileStatusInfoDialog dialog = new FileStatusInfoDialog(
    // FileMenu.this.fileStatus,
    // (Frame) DownloadsPanel.this.getTopLevelAncestor());
    // dialog.setVisible(true);
    // }
    // });
    // add(info);
    //
    // show(treeTable, x, y);
    //
    // // TreePath path = treeOfDownloads.getPathForLocation(x, y);
    // // treeOfDownloads.expandPath(path.getParentPath());
    // }
    // }

    public class ProgressBarRenderer extends JProgressBar implements
            TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object recordStatus, boolean isSelected, boolean hasFocus,
                int row, int column) {

            if (recordStatus instanceof DatasetDownloadStatus) {
                DatasetDownloadStatus dDStatus = (DatasetDownloadStatus) recordStatus;

                // calculate % and reset init values of progress bar
                setStringPainted(true);
                setBackground(new Color(238, 238, 238));
                setForeground(new Color(163, 184, 204));
                setString(null);
                setValue(dDStatus.getCurrentProgress());

                if (dDStatus.getRecordStatus() == RecordStatus.FINISHED) {
                    // light green
                    setForeground(new Color(0, 204, 102));
                }

            } else if (recordStatus instanceof FileDownloadStatus) {

                FileDownloadStatus fDStatus = (FileDownloadStatus) recordStatus;

                // calculate % and reset init values of progress bar
                setStringPainted(true);
                setBackground(new Color(238, 238, 238));
                setForeground(new Color(163, 184, 204));
                setString(null);
                setValue(fDStatus.getCurrentProgress());

                if (fDStatus.getRecordStatus() == RecordStatus.UNAUTHORIZED) {
                    setValue(0);
                    setString("UNAUTHORIZED");
                    setStringPainted(true);
                    // light yellow
                    setBackground(new Color(252, 255, 134));
                } else if (fDStatus.getRecordStatus() == RecordStatus.CREATED
                        || fDStatus.getRecordStatus() == RecordStatus.READY) {
                    setStringPainted(false);
                } else if (fDStatus.getRecordStatus() == RecordStatus.FAILED) {

                    // progressBar.setValue(0);
                    setValue(100);
                    setString("FAILED");
                    setStringPainted(true);
                    // red
                    setForeground(new Color(204, 0, 0));
                } else if (fDStatus.getRecordStatus() == RecordStatus.FINISHED) {
                    // green
                    setForeground(new Color(0, 204, 102));
                    setStringPainted(true);
                } else if (fDStatus.getRecordStatus() == RecordStatus.CHECKSUM_FAILED) {
                    setString("CHECKSUM_FAILED");
                    // gray
                    setForeground(new Color(96, 96, 96));
                    setStringPainted(true);
                }
            }

            return this;
        }
    }
}
