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
import es.unican.meteo.esgf.ui.DownloadsTableModel.DatasetNode;

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
        treeTable.getColumnModel().getColumn(0).setPreferredWidth(550);
        treeTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        treeTable.getColumnModel().getColumn(4).setPreferredWidth(150);
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

                    RecordPopupMenu popupMenu = null;

                    if (object instanceof DatasetNode) {
                        DatasetDownloadStatus datasetStatus = (DatasetDownloadStatus) ((DatasetNode) object)
                                .getUserObject();
                        popupMenu = new RecordPopupMenu(datasetStatus,
                                DownloadsPanel.this.treeTable,
                                DownloadsPanel.this.downloadManager, x, y);
                        popupMenu
                                .addPropertyChangeListener(new PropertyChangeListener() {

                                    @Override
                                    public void propertyChange(
                                            PropertyChangeEvent evt) {

                                        DownloadsPanel.this.firePropertyChange(
                                                evt.getPropertyName(),
                                                evt.getOldValue(),
                                                evt.getNewValue());

                                    }
                                });
                    } else if (object instanceof FileDownloadStatus) {
                        FileDownloadStatus fileStatus = (FileDownloadStatus) object;
                        popupMenu = new RecordPopupMenu(fileStatus,
                                DownloadsPanel.this.treeTable,
                                DownloadsPanel.this.downloadManager, x, y);
                        popupMenu
                                .addPropertyChangeListener(new PropertyChangeListener() {

                                    @Override
                                    public void propertyChange(
                                            PropertyChangeEvent evt) {

                                        DownloadsPanel.this.firePropertyChange(
                                                evt.getPropertyName(),
                                                evt.getOldValue(),
                                                evt.getNewValue());

                                    }
                                });
                    }

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

                    RecordPopupMenu popupMenu = null;

                    if (object instanceof DatasetNode) {
                        DatasetDownloadStatus datasetStatus = (DatasetDownloadStatus) ((DatasetNode) object)
                                .getUserObject();
                        popupMenu = new RecordPopupMenu(datasetStatus,
                                DownloadsPanel.this.treeTable,
                                DownloadsPanel.this.downloadManager, x, y);
                        popupMenu
                                .addPropertyChangeListener(new PropertyChangeListener() {

                                    @Override
                                    public void propertyChange(
                                            PropertyChangeEvent evt) {

                                        DownloadsPanel.this.firePropertyChange(
                                                evt.getPropertyName(),
                                                evt.getOldValue(),
                                                evt.getNewValue());

                                    }
                                });
                    } else if (object instanceof FileDownloadStatus) {
                        FileDownloadStatus fileStatus = (FileDownloadStatus) object;
                        popupMenu = new RecordPopupMenu(fileStatus,
                                DownloadsPanel.this.treeTable,
                                DownloadsPanel.this.downloadManager, x, y);
                        popupMenu
                                .addPropertyChangeListener(new PropertyChangeListener() {

                                    @Override
                                    public void propertyChange(
                                            PropertyChangeEvent evt) {

                                        DownloadsPanel.this.firePropertyChange(
                                                evt.getPropertyName(),
                                                evt.getOldValue(),
                                                evt.getNewValue());

                                    }
                                });
                    }
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

        // ---------------------------------------------------------------------
        add(toolBar, BorderLayout.NORTH);
        add(new JScrollPane(treeTable), BorderLayout.CENTER);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
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
    }

    /**
     * Renderer of progress bar in tree table of downloads
     * 
     * @author terryk
     * 
     */
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
