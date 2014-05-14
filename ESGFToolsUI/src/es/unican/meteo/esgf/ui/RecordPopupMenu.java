package es.unican.meteo.esgf.ui;

import java.awt.Component;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;

import edu.stanford.ejalbert.BrowserLauncher;
import edu.stanford.ejalbert.exception.BrowserLaunchingInitializingException;
import edu.stanford.ejalbert.exception.UnsupportedOperatingSystemException;
import es.unican.meteo.esgf.download.DatasetDownloadStatus;
import es.unican.meteo.esgf.download.DownloadManager;
import es.unican.meteo.esgf.download.FileDownloadStatus;
import es.unican.meteo.esgf.download.RecordStatus;
import es.unican.meteo.esgf.search.RecordReplica;
import es.unican.meteo.esgf.search.Service;

/**
 * Popup menu from ESGF files or datasets elements in the User Interface of
 * ESGFToolsUI
 * 
 * PropertyChange Listeners must be implemented in parent for catching some
 * events
 * 
 * @author Karem Terry
 * 
 */
public class RecordPopupMenu extends JPopupMenu {

    /**
     * Logger
     */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(RecordPopupMenu.class);

    /**
     * FileDownloadS
     */
    private Object recordDownloadStatus;
    private Component parent;
    private DownloadManager downloadManager;

    /**
     * Constructor
     * 
     * @param recordStatus
     *            object of file status or dataset status
     * @param parent
     *            component where the menu must be shown
     * @param downloadManager
     *            manager of downloads
     * @param x
     *            point
     * @param y
     *            point
     */
    public RecordPopupMenu(Object recordStatus, final Component parent,
            final DownloadManager downloadManager, int x, int y) {
        super();

        if (recordStatus instanceof DownloadsTableModel.DatasetNode) {
            this.recordDownloadStatus = ((DownloadsTableModel.DatasetNode) recordStatus)
                    .getUserObject();
        } else if (!(recordStatus instanceof DatasetDownloadStatus)
                && !(recordStatus instanceof FileDownloadStatus)) {
            throw new IllegalArgumentException("Record Status is instance of "
                    + recordStatus.getClass() + " and must be instance of "
                    + "DatasetDownloadStatus or FileDownloadStatus");
        } else {
            this.recordDownloadStatus = recordStatus;
        }

        if (recordDownloadStatus instanceof FileDownloadStatus) {
            RecordStatus status = ((FileDownloadStatus) recordDownloadStatus)
                    .getRecordStatus();

            final FileDownloadStatus fileStatus = (FileDownloadStatus) RecordPopupMenu.this.recordDownloadStatus;

            // File flow options-----------------------------------------------
            // download option. Configurated file, set READY and put in queue
            if (status == RecordStatus.CREATED || status == RecordStatus.PAUSED) {
                JMenuItem download = new JMenuItem("Resume download");
                download.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {

                        try {
                            downloadManager.downloadFile(fileStatus);
                        } catch (IOException e1) {
                            JOptionPane.showMessageDialog(
                                    parent,
                                    "Error reading info of file: "
                                            + fileStatus.getInstanceID()
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

                        downloadManager.pauseFile(fileStatus);
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

                        String path = fileStatus.getFilePath();

                        // Component.firePropertyChange(String propertyName,
                        // Object oldValue, Object newValue) this method fire
                        // new event with a name, old object and new object this
                        // event is catch and processed by main ESGF
                        RecordPopupMenu.this.firePropertyChange("openFile",
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
                        fileStatus.getDatasetDownloadStatus().getInstanceID(),
                        fileStatus.getInstanceID(), Service.HTTPSERVER);
            } catch (IOException e1) {
                logger.warn("File {} hasn't been obtained from file system",
                        fileStatus.getInstanceID());
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
                            RecordPopupMenu.this.firePropertyChange("openFile",
                                    null, urlOpenFile);

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
                        fileStatus.getDatasetDownloadStatus().getInstanceID(),
                        fileStatus.getInstanceID(), Service.OPENDAP);
            } catch (IOException e1) {
                logger.warn("File {} hasn't been obtained from file system",
                        fileStatus.getInstanceID());
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
                            RecordPopupMenu.this.firePropertyChange("openFile",
                                    null, urlOpenDap);

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

                        String path = fileStatus.getFilePath();

                        // Component.firePropertyChange(String propertyName,
                        // Object oldValue, Object newValue) this method fire
                        // new event with a name, old object and new object this
                        // event is catch and processed by main ESGF
                        RecordPopupMenu.this.firePropertyChange(
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
                            RecordPopupMenu.this.firePropertyChange(
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
                            RecordPopupMenu.this.firePropertyChange(
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
                                    parent,
                                    "Sure you want delete all progress of file download in file system?",
                                    "reset", JOptionPane.YES_NO_OPTION);

                    if (confirm == JOptionPane.YES_OPTION) {
                        downloadManager.resetFile(fileStatus);
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
                        if (replica != fileStatus.getCurrentFileReplica()) {
                            JMenuItem replicaOption = new JMenuItem(replica
                                    .getDataNode().substring(7));
                            replicaOption
                                    .addActionListener(new ActionListener() {

                                        @Override
                                        public void actionPerformed(
                                                ActionEvent e) {
                                            // reset
                                            downloadManager
                                                    .resetFile(fileStatus);
                                            // set new replica
                                            fileStatus
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
            JMenuItem remove = new JMenuItem("Remove of downloads queue");
            remove.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {

                    downloadManager.skipFile(fileStatus);

                    // remove of tree of downloads
                    // DefaultMutableTreeNode nodeToRemove = instanceIDNodeMap
                    // .get(fileStatus.getInstanceID());
                    // treeModel.removeNodeFromParent(nodeToRemove);
                    // instanceIDNodeMap.remove(fileStatus.getInstanceID());
                    // updateUI();
                }
            });

            add(remove);
            // End reset and remove options-----------------------------------

            // open download url in browser
            JMenuItem openURLInBrowser = new JMenuItem(
                    "Open HTTP URL in browser");
            openURLInBrowser.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    String url = fileStatus.getCurrentFileReplica()
                            .getUrlEndPointOfService(Service.HTTPSERVER);

                    BrowserLauncher launcher;
                    try {
                        launcher = new BrowserLauncher();
                        launcher.openURLinBrowser(url);
                    } catch (BrowserLaunchingInitializingException e1) {
                        logger.error(
                                "BrowserLaunchingInitializingException with url: {}",
                                url);
                        e1.printStackTrace();
                    } catch (UnsupportedOperatingSystemException e1) {
                        // supports Mac, Windows, and Unix/Linux.
                        logger.error(
                                "UnsupportedOperatingSystemException with url: {}",
                                url);
                        e1.printStackTrace();
                    }
                }
            });
            add(openURLInBrowser);

            // file info option
            JMenuItem info = new JMenuItem("File info");
            info.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {

                    FileStatusInfoDialog dialog = new FileStatusInfoDialog(
                            fileStatus, (Frame) ((JComponent) parent)
                                    .getTopLevelAncestor());
                    dialog.setVisible(true);
                }
            });
            add(info);
        } else if (recordDownloadStatus instanceof DatasetDownloadStatus) {
            RecordStatus status = ((DatasetDownloadStatus) recordDownloadStatus)
                    .getRecordStatus();

            final DatasetDownloadStatus datasetStatus = (DatasetDownloadStatus) recordDownloadStatus;

            if (status != RecordStatus.FINISHED) {
                // Download option
                JMenuItem play = new JMenuItem("Download all");
                play.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {

                        try {
                            datasetStatus.download();
                        } catch (IOException e1) {
                            JOptionPane.showMessageDialog(parent,
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

                        downloadManager.pauseDataSetDownload(datasetStatus);
                    }
                });

                add(pause);
            }

            addSeparator();

            // THREDDS Catalog option

            List<RecordReplica> replicas;
            try {
                replicas = DownloadManager.getDatasetReplicasOfService(
                        datasetStatus.getInstanceID(), Service.CATALOG);
            } catch (IOException e1) {
                logger.warn("dataset {} hasn't been obtained from file system",
                        datasetStatus.getInstanceID());
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
                            RecordPopupMenu.this.firePropertyChange(
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
                    downloadManager.resetDataSetDownload(datasetStatus);
                }
            });
            add(reset);

            // remove of download list option.
            JMenuItem remove = new JMenuItem("Remove");
            remove.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {

                    downloadManager.removeDataset(datasetStatus);

                    // remove of tree of downloads
                    // DefaultMutableTreeNode nodeToRemove = instanceIDNodeMap
                    // .get(datasetStatus.getInstanceID());
                    // treeModel.removeNodeFromParent(nodeToRemove);
                    // instanceIDNodeMap.remove(datasetStatus.getInstanceID());

                    // remove all file nodes
                    // int index = 0;
                    // while (index < nodeToRemove.getChildCount()) {
                    // DefaultMutableTreeNode fileNode =
                    // (DefaultMutableTreeNode) nodeToRemove
                    // .getChildAt(index);
                    // // treeModel.removeNodeFromParent(fileNode);
                    // // instanceIDNodeMap.remove(((FileDownloadStatus)
                    // // fileNode
                    // // .getUserObject()).getInstanceID());
                    // }
                    // updateUI();
                }
            });

            add(remove);

            // info option
            JMenuItem info = new JMenuItem("tempInfo");
            info.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    String info = datasetStatus.toString();
                    System.out.println(info);

                }
            });
            // add(info);
        }

        show(parent, x, y);
    }
}
