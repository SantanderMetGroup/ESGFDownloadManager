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

    private Component parent;
    private DownloadManager downloadManager;

    /**
     * Constructor for popup menu in downloads for FileDownloadStatus
     * 
     * @param recordStatus
     *            object of file download status
     * @param parent
     *            component where the menu must be shown
     * @param downloadManager
     *            manager of downloads
     * @param x
     *            point
     * @param y
     *            point
     */
    public RecordPopupMenu(final FileDownloadStatus fileStatus,
            final Component parent, final DownloadManager downloadManager,
            int x, int y) {
        super();

        this.parent = parent;
        this.downloadManager = downloadManager;

        RecordStatus status = fileStatus.getRecordStatus();
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
            JMenuItem pause = new JMenuItem("Pause download");
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
                    RecordPopupMenu.this.firePropertyChange("openFile", null,
                            path);

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
            httpReplicas = DownloadManager.getFileReplicasOfService(fileStatus
                    .getDatasetDownloadStatus().getInstanceID(), fileStatus
                    .getInstanceID(), Service.HTTPSERVER);
        } catch (IOException e1) {
            logger.warn("File {} hasn't been obtained from file system",
                    fileStatus.getInstanceID());
            httpReplicas = null;
        }

        if (httpReplicas != null) {
            JMenuItem remoteOpenFile = new JMenu("File URL");

            for (final RecordReplica replica : httpReplicas) {
                JMenuItem replicaOption = new JMenuItem(replica.getDataNode());
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
                JMenuItem replicaOption = new JMenuItem(replica.getDataNode());
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
                JMenuItem replicaOption = new JMenuItem(replica.getDataNode());
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
                        RecordPopupMenu.this
                                .firePropertyChange(
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
                JMenuItem replicaOption = new JMenuItem(replica.getDataNode());
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
                                "openFileInGridFeatureTypes", null, urlOpenDap);

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

        // Reset, retry and remove
        // options--------------------------------------

        addSeparator();
        // reset option

        JMenuItem reset = new JMenuItem("Reset download");
        reset.addActionListener(new ActionListener() {

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
        add(reset);

        // retry option
        JMenu retry = new JMenu("Retry download");

        JMenuItem resetInCurrentReplica = new JMenuItem("Current data node");
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
            }
        });
        retry.add(resetInCurrentReplica);

        // reset in another replica
        if (httpReplicas != null) {

            if (httpReplicas.size() > 1) {

                retry.addSeparator();
                JMenuItem changeReplica = new JMenu("Select data node");

                // Add all replicas data nodes
                for (final RecordReplica replica : httpReplicas) {
                    if (replica != fileStatus.getCurrentFileReplica()) {
                        JMenuItem replicaOption = new JMenuItem(replica
                                .getDataNode().substring(7));
                        replicaOption.addActionListener(new ActionListener() {

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                // reset
                                downloadManager.resetFile(fileStatus);
                                // set new replica
                                fileStatus.setCurrentFileReplica(replica);

                                try {
                                    downloadManager.downloadFile(fileStatus);
                                } catch (IOException e1) {
                                    JOptionPane
                                            .showMessageDialog(
                                                    parent,
                                                    "Error reading info of file: "
                                                            + fileStatus
                                                                    .getInstanceID()
                                                            + ". File can't be download");
                                }
                            }
                        });
                        changeReplica.add(replicaOption);
                    }
                }
                retry.add(changeReplica);
            }
        }
        add(retry);

        // remove of download list option.
        JMenuItem remove = new JMenuItem("Remove of downloads queue");
        remove.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                downloadManager.skipFile(fileStatus);
            }
        });

        add(remove);
        // End reset and remove options-----------------------------------

        // download status options--------------------------------------
        addSeparator();
        // open download url in browser
        JMenuItem openURLInBrowser = new JMenuItem(
                "Open download URL in browser");
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

        // download info option
        JMenuItem info = new JMenuItem("Download info");
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
        // End download status options-----------------------------------

        // Browser options--------------------------------------
        addSeparator();
        // if exists replicas with http service
        if (httpReplicas != null) {
            JMenuItem openHTTPInBrowser = new JMenu(
                    "Open HTTP Service URL in browser");

            // Add all replicas data nodes
            for (final RecordReplica replica : httpReplicas) {
                JMenuItem replicaOption = new JMenuItem(replica.getDataNode()
                        .substring(7));
                replicaOption.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        // open in browser
                        String url = replica
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
                            // supports Mac, Windows, and
                            // Unix/Linux.
                            logger.error(
                                    "UnsupportedOperatingSystemException with url: {}",
                                    url);
                            e1.printStackTrace();
                        }

                    }
                });
                openHTTPInBrowser.add(replicaOption);
            }

            add(openHTTPInBrowser);
        }

        // if exists replicas with opendap service
        if (openDapReplicas != null) {
            JMenuItem openOpenDapInBrowser = new JMenu(
                    "Open OPeNDAP Service URL in browser");

            // Add all replicas data nodes
            for (final RecordReplica replica : openDapReplicas) {
                JMenuItem replicaOption = new JMenuItem(replica.getDataNode()
                        .substring(7));
                replicaOption.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        // open in browser
                        String url = replica
                                .getUrlEndPointOfService(Service.OPENDAP);
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
                            // supports Mac, Windows, and
                            // Unix/Linux.
                            logger.error(
                                    "UnsupportedOperatingSystemException with url: {}",
                                    url);
                            e1.printStackTrace();
                        }

                    }
                });
                openOpenDapInBrowser.add(replicaOption);
            }

            add(openOpenDapInBrowser);
        }

        List<RecordReplica> gridFTPReplicas;
        try {
            gridFTPReplicas = DownloadManager.getFileReplicasOfService(
                    fileStatus.getDatasetDownloadStatus().getInstanceID(),
                    fileStatus.getInstanceID(), Service.GRIDFTP);
        } catch (IOException e1) {
            logger.warn("File {} hasn't been obtained from file system",
                    fileStatus.getInstanceID());
            gridFTPReplicas = null;

        }

        // if exists replicas with gridFTP service
        if (gridFTPReplicas != null) {
            JMenuItem openGridFTPInBrowser = new JMenu(
                    "Open GridFTP Service URL in browser");

            // Add all replicas data nodes
            for (final RecordReplica replica : gridFTPReplicas) {
                JMenuItem replicaOption = new JMenuItem(replica.getDataNode()
                        .substring(7));
                replicaOption.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        // open in browser
                        String url = replica
                                .getUrlEndPointOfService(Service.GRIDFTP);
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
                            // supports Mac, Windows, and
                            // Unix/Linux.
                            logger.error(
                                    "UnsupportedOperatingSystemException with url: {}",
                                    url);
                            e1.printStackTrace();
                        }

                    }
                });
                openGridFTPInBrowser.add(replicaOption);
            }

            add(openGridFTPInBrowser);
        }

        show(parent, x, y);
    }

    public RecordPopupMenu(final DatasetDownloadStatus datasetStatus,
            final Component parent, final DownloadManager downloadManager,
            int x, int y) {
        super();

        this.parent = parent;
        this.downloadManager = downloadManager;

        RecordStatus status = datasetStatus.getRecordStatus();

        // THREDDS replicas
        List<RecordReplica> threddsReplicas;
        try {
            threddsReplicas = DownloadManager.getDatasetReplicasOfService(
                    datasetStatus.getInstanceID(), Service.CATALOG);
        } catch (IOException e1) {
            logger.warn("dataset {} hasn't been obtained from file system",
                    datasetStatus.getInstanceID());
            threddsReplicas = null;
        }

        // LAS replicas
        List<RecordReplica> lasReplicas;
        try {
            lasReplicas = DownloadManager.getDatasetReplicasOfService(
                    datasetStatus.getInstanceID(), Service.LAS);
        } catch (IOException e1) {
            logger.warn("dataset {} hasn't been obtained from file system",
                    datasetStatus.getInstanceID());
            lasReplicas = null;
        }

        if (status != RecordStatus.FINISHED) {
            // Download option
            JMenuItem play = new JMenuItem("Start all file downloads");
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

        // retry failed files option
        JMenuItem retryFailedFiles = new JMenuItem(
                "Retry download in failed files");
        retryFailedFiles.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                RecordPopupMenu.this.downloadManager
                        .retryAllFailedDownloads(datasetStatus);
            }
        });

        add(retryFailedFiles);

        addSeparator();

        if (threddsReplicas != null) {
            JMenuItem catalog = new JMenu("open catalog in THREDDS Panel");

            for (final RecordReplica replica : threddsReplicas) {
                JMenuItem replicaOption = new JMenuItem(replica.getDataNode());
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
                        RecordPopupMenu.this.firePropertyChange("openCatalog",
                                null, urlCatalog);

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
        JMenuItem remove = new JMenuItem("Remove of downloads queue");
        remove.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                downloadManager.removeDataset(datasetStatus);
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

        // end reset and remove options------

        // Open in browser options--------
        addSeparator();

        // if exists replicas with THREDDS service
        if (threddsReplicas != null) {
            JMenuItem openThreddsInBrowser = new JMenu(
                    "Open THREDDS Service URL in browser");

            // Add all replicas data nodes
            for (final RecordReplica replica : threddsReplicas) {
                JMenuItem replicaOption = new JMenuItem(replica.getDataNode()
                        .substring(7));
                replicaOption.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        // open in browser
                        String url = replica
                                .getUrlEndPointOfService(Service.CATALOG);
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
                            // supports Mac, Windows, and
                            // Unix/Linux.
                            logger.error(
                                    "UnsupportedOperatingSystemException with url: {}",
                                    url);
                            e1.printStackTrace();
                        }

                    }
                });
                openThreddsInBrowser.add(replicaOption);
            }

            add(openThreddsInBrowser);
        }

        // if exists replicas with LAS service
        if (lasReplicas != null) {
            JMenuItem openLASInBrowser = new JMenu(
                    "Open LAS Service URL in browser");

            // Add all replicas data nodes
            for (final RecordReplica replica : lasReplicas) {
                JMenuItem replicaOption = new JMenuItem(replica.getDataNode()
                        .substring(7));
                replicaOption.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        // open in browser
                        String url = replica
                                .getUrlEndPointOfService(Service.LAS);
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
                            // supports Mac, Windows, and
                            // Unix/Linux.
                            logger.error(
                                    "UnsupportedOperatingSystemException with url: {}",
                                    url);
                            e1.printStackTrace();
                        }

                    }
                });
                openLASInBrowser.add(replicaOption);
            }

            add(openLASInBrowser);
        }

        show(parent, x, y);
    }
}
