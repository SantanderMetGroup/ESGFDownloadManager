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
import java.util.Observable;
import java.util.Observer;

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
import javax.swing.table.TableModel;
import javax.swing.tree.TreePath;

import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.JXTreeTable;
import org.jdesktop.swingx.table.ColumnFactory;
import org.jdesktop.swingx.table.TableColumnExt;
import org.jdesktop.swingx.tree.TreeModelSupport;

import ucar.util.prefs.PreferencesExt;
import es.unican.meteo.esgf.download.DatasetDownloadStatus;
import es.unican.meteo.esgf.download.DownloadManager;
import es.unican.meteo.esgf.download.FileDownloadStatus;
import es.unican.meteo.esgf.download.RecordStatus;
import es.unican.meteo.esgf.ui.DownloadsTableModel.DatasetNode;

/**
 * Panel that shows progress of current downloads. Implements Java Observer
 * implementation to be notified of addition or substraction in the download
 * manager
 * 
 * @author terryk
 * 
 */
public class ESGFDownloadsPanel extends JPanel implements Observer {

    /**
     * Logger
     */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(ESGFDownloadsPanel.class);

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
    public ESGFDownloadsPanel(PreferencesExt prefs,
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
                ESGFDownloadsPanel.this.downloadManager.startAllDownloads();
            }
        });

        JButton pauseAllDownloads = new JButton("Pause all downloads");
        pauseAllDownloads.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                ESGFDownloadsPanel.this.downloadManager.pauseActiveDownloads();
            }
        });

        JButton removeAllDownloads = new JButton("Remove all downloads");
        removeAllDownloads.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {

                int confirm = JOptionPane.showConfirmDialog(
                        ESGFDownloadsPanel.this,
                        "Sure you want remove all downloads?", "Remove",
                        JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    ESGFDownloadsPanel.this.downloadManager.reset();
                    treeModel = new DownloadsTableModel(
                            new ArrayList<DatasetDownloadStatus>(
                                    ESGFDownloadsPanel.this.downloadManager
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
                        ESGFDownloadsPanel.this.downloadManager
                                .getDatasetDownloads()));

        treeTable = new JXTreeTable();
        treeTable.setColumnFactory(new ObjColumnFactory());
        treeTable.setTreeTableModel(treeModel);
        treeTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        treeTable.setRootVisible(false); // hide the root

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
                        // Select row in list
                        int row = treeTable.rowAtPoint(new Point(x, y));
                        treeTable.setRowSelectionInterval(row, row);

                        // Create and show popup menu
                        DatasetDownloadStatus datasetStatus = (DatasetDownloadStatus) ((DatasetNode) object)
                                .getUserObject();
                        popupMenu = new RecordPopupMenu(datasetStatus,
                                ESGFDownloadsPanel.this.treeTable,
                                ESGFDownloadsPanel.this.downloadManager, x, y);
                        popupMenu
                                .addPropertyChangeListener(new PropertyChangeListener() {

                                    @Override
                                    public void propertyChange(
                                            PropertyChangeEvent evt) {

                                        ESGFDownloadsPanel.this
                                                .firePropertyChange(
                                                        evt.getPropertyName(),
                                                        evt.getOldValue(),
                                                        evt.getNewValue());

                                    }
                                });
                    } else if (object instanceof FileDownloadStatus) {
                        // Select row in list
                        int row = treeTable.rowAtPoint(new Point(x, y));
                        treeTable.setRowSelectionInterval(row, row);

                        // Create and show popup menu
                        FileDownloadStatus fileStatus = (FileDownloadStatus) object;
                        popupMenu = new RecordPopupMenu(fileStatus,
                                ESGFDownloadsPanel.this.treeTable,
                                ESGFDownloadsPanel.this.downloadManager, x, y);
                        popupMenu
                                .addPropertyChangeListener(new PropertyChangeListener() {

                                    @Override
                                    public void propertyChange(
                                            PropertyChangeEvent evt) {

                                        ESGFDownloadsPanel.this
                                                .firePropertyChange(
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
                        // Select row in list
                        int row = treeTable.rowAtPoint(new Point(x, y));
                        treeTable.setRowSelectionInterval(row, row);

                        // Create and show popup menu
                        DatasetDownloadStatus datasetStatus = (DatasetDownloadStatus) ((DatasetNode) object)
                                .getUserObject();
                        popupMenu = new RecordPopupMenu(datasetStatus,
                                ESGFDownloadsPanel.this.treeTable,
                                ESGFDownloadsPanel.this.downloadManager, x, y);
                        popupMenu
                                .addPropertyChangeListener(new PropertyChangeListener() {

                                    @Override
                                    public void propertyChange(
                                            PropertyChangeEvent evt) {

                                        ESGFDownloadsPanel.this
                                                .firePropertyChange(
                                                        evt.getPropertyName(),
                                                        evt.getOldValue(),
                                                        evt.getNewValue());

                                    }
                                });
                    } else if (object instanceof FileDownloadStatus) {
                        // Select row in list
                        int row = treeTable.rowAtPoint(new Point(x, y));
                        treeTable.setRowSelectionInterval(row, row);

                        // Create and show popup menu
                        FileDownloadStatus fileStatus = (FileDownloadStatus) object;
                        popupMenu = new RecordPopupMenu(fileStatus,
                                ESGFDownloadsPanel.this.treeTable,
                                ESGFDownloadsPanel.this.downloadManager, x, y);
                        popupMenu
                                .addPropertyChangeListener(new PropertyChangeListener() {

                                    @Override
                                    public void propertyChange(
                                            PropertyChangeEvent evt) {

                                        ESGFDownloadsPanel.this
                                                .firePropertyChange(
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

        // add it like observer in download manager
        this.downloadManager.addObserver(this);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
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

                if (fDStatus.getCurrentProgress() == 100
                        & fDStatus.getChecksum() != null
                        & fDStatus.getRecordStatus() != RecordStatus.FINISHED) {
                    setValue(100);
                    setString("Verifying checksum...");
                } else {
                    setString(null);
                    setValue(fDStatus.getCurrentProgress());
                }

                if (fDStatus.getRecordStatus() == RecordStatus.UNAUTHORIZED) {
                    setValue(0);
                    setString("UNAUTHORIZED");
                    setStringPainted(true);
                    // light yellow
                    setBackground(new Color(252, 255, 134));
                } else if (fDStatus.getRecordStatus() == RecordStatus.CREATED) {
                    setStringPainted(false);
                } else if (fDStatus.getRecordStatus() == RecordStatus.WAITING) {
                    setString(null);
                    setValue(fDStatus.getCurrentProgress());
                } else if (fDStatus.getRecordStatus() == RecordStatus.FAILED) {
                    // progressBar.setValue(0);
                    setValue(100);
                    setString("FAILED");
                    setStringPainted(true);
                    // red
                    setForeground(new Color(204, 0, 0));
                } else if (fDStatus.getRecordStatus() == RecordStatus.FINISHED) {
                    // green
                    setString(null);
                    setValue(fDStatus.getCurrentProgress());
                    setForeground(new Color(0, 204, 102));
                    setStringPainted(true);
                } else if (fDStatus.getRecordStatus() == RecordStatus.CHECKSUM_FAILED) {
                    setString("CHECKSUM_FAILED");
                    // gray
                    setForeground(new Color(96, 96, 96));
                    setStringPainted(true);
                } else if (fDStatus.getRecordStatus() == RecordStatus.PAUSED) {
                    setString(null);
                    setValue(fDStatus.getCurrentProgress());
                }
            }

            return this;
        }
    }

    /**
     * Column factory of tree table. Here all column renderers and size are
     * defined
     * 
     */
    public class ObjColumnFactory extends ColumnFactory {

        /**
         * To configure renderers
         */
        @Override
        public TableColumnExt createAndConfigureTableColumn(TableModel model,
                int modelIndex) {

            TableColumnExt column = super.createAndConfigureTableColumn(model,
                    modelIndex);

            if (modelIndex == 1) {
                column.setCellRenderer(new ProgressBarRenderer());
            }

            return column;
        }

        /**
         * To configure widths
         */
        @Override
        public void configureColumnWidths(JXTable table,
                TableColumnExt columnExt) {
            super.configureColumnWidths(table, columnExt);

            switch (columnExt.getModelIndex()) {
                case 0:
                    columnExt.setMinWidth(300);
                    columnExt.setPreferredWidth(500);
                    columnExt.setMaxWidth(Short.MAX_VALUE);
                break;
                case 1:
                    columnExt.setMinWidth(150);
                    columnExt.setPreferredWidth(200);
                    columnExt.setMaxWidth(Short.MAX_VALUE);
                break;
                case 5:
                    columnExt.setMinWidth(100);
                    columnExt.setPreferredWidth(150);
                    columnExt.setMaxWidth(Short.MAX_VALUE);
                break;

                default:
                break;
            }
        }

    }

    /* Update method of observer/observable implementation */
    @Override
    public void update(Observable o, Object arg) {
        if (o == this.downloadManager) {
            ((DownloadsTableModel) treeTable.getTreeTableModel())
                    .updateElements(((DownloadManager) o).getDatasetDownloads());
        }
    }
}
