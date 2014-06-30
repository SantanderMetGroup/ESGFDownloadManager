package es.unican.meteo.esgf.ui;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JCheckBoxMenuItem;
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
        super("File menu");

        this.parent = parent;
        this.downloadManager = downloadManager;
        RecordStatus status = fileStatus.getRecordStatus();

        // opendap replicas
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

        // http replicas
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

        // gridFTP replicas
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

        // Download options----------------------------------------------
        // Start download option-----------------------------------------
        JMenuItem download = new JMenuItem("Start download");
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

        if (status == RecordStatus.CREATED || status == RecordStatus.PAUSED) {
            download.setEnabled(true);
        } else {
            download.setEnabled(false);
        }
        // end start download-------------------------------------------------
        // Pause option-------------------------------------------------------
        JMenuItem pause = new JMenuItem("Pause download");
        pause.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                downloadManager.pauseFile(fileStatus);
            }
        });

        add(pause);
        if (status == RecordStatus.DOWNLOADING) {
            pause.setEnabled(true);
        } else {
            pause.setEnabled(false);
        }
        // end pause option--------------------------------------------------
        // Reset option------------------------------------------------------

        JMenuItem reset = new JMenuItem("Reset");
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
        // end reset option--------------------------------------------------
        // Retry option------------------------------------------------------
        JMenu retry = new JMenu("Retry");

        // retry another replica
        if (httpReplicas != null) {

            // Add all replicas data nodes
            for (final RecordReplica replica : httpReplicas) {
                JMenuItem replicaOption = null;

                if (replica == fileStatus.getCurrentFileReplica()) {
                    // if it is current
                    replicaOption = new JCheckBoxMenuItem(replica.getDataNode()
                            .substring(7));
                    replicaOption.setSelected(true);
                } else {
                    replicaOption = new JMenuItem(replica.getDataNode()
                            .substring(7));
                }

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
                            JOptionPane.showMessageDialog(
                                    parent,
                                    "Error reading info of file: "
                                            + fileStatus.getInstanceID()
                                            + ". File can't be download");
                        }
                    }
                });
                retry.add(replicaOption);
            }
        }
        add(retry);
        // end retry option--------------------------------------------------

        // remove of download list option.
        JMenuItem remove = new JMenuItem("Remove of downloads queue");
        remove.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                downloadManager.skipFile(fileStatus);
            }
        });

        add(remove);

        // Open download url in browser--------------------------------------
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
        // End open download url in browser----------------------------------
        // Download info option----------------------------------------------
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
        // End download info option------------------------------------------
        // End download options----------------------------------------------
        // Access services option--------------------------------------------

        // ________________________________________________________________
        // ----------------------------------------------------------------
        addSeparator(); // SEPARATOR--------------------------------------
        // ----------------------------------------------------------------
        // _______________________________________________________________

        // Access services option---------------------------------------------
        JMenu accessServices = new JMenu("Services");

        JMenu thredds = new JMenu("THREDDS");
        accessServices.add(thredds);
        thredds.setEnabled(false);

        JMenu las = new JMenu("LAS");
        accessServices.add(las);
        las.setEnabled(false);

        // Local sub-option--------------------------------------------
        JMenu local = new JMenu("Local");
        accessServices.add(local);

        local.setEnabled(true);
        createLocalOptionMenu(local, fileStatus);

        // End local sub-option----------------------------------------
        // OPeNDAP sub-option------------------------------------------
        JMenu opendap = new JMenu("OPeNDAP");
        accessServices.add(opendap);

        if (openDapReplicas != null) {
            opendap.setEnabled(true);
            createOpendapOptionMenu(opendap, openDapReplicas);
        } else {
            opendap.setEnabled(false);
        }
        // End OPeNDAP sub-option-------------------------------------

        // HTTP sub-option--------------------------------------------
        JMenu http = new JMenu("HTTP");
        accessServices.add(http);

        if (httpReplicas != null) {
            http.setEnabled(true);
            createHttpOptionMenu(http, httpReplicas);
        } else {
            http.setEnabled(false);
        }
        // End HTTP sub-option---------------------------------------

        // GridFTP sub-option----------------------------------------
        JMenu gridFTP = new JMenu("GridFTP");
        accessServices.add(gridFTP);

        if (gridFTPReplicas != null) {
            gridFTP.setEnabled(true);
            createGridFtpOptionMenu(gridFTP, gridFTPReplicas);
        } else {
            gridFTP.setEnabled(false);
        }
        // End GridFTP sub-option---------------------------------------
        add(accessServices);
        // End Access Services option-----------------------------------------
        // End download status options----------------------------------------

        // XXX tempInfo of download list option.
        JMenuItem tempInfo = new JMenuItem("tempInfo");
        tempInfo.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println(fileStatus);
            }
        });

        // add(tempInfo);

        show(parent, x, y);
    }

    private void createGridFtpOptionMenu(JMenu gridFTPMenu,
            List<RecordReplica> gridFTPReplicas) {
        // Open in browser option---------------------------
        JMenuItem browser = new JMenu("Open URL in browser");

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
            browser.add(replicaOption);
        }
        gridFTPMenu.add(browser);

        // Copy to clipboard option---------------------------
        JMenu clipboard = new JMenu("Copy URL to clipboard");
        for (final RecordReplica replica : gridFTPReplicas) {
            JMenuItem replicaOption = new JMenuItem(replica.getDataNode()
                    .substring(7));
            replicaOption.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {

                    String urlGrid = replica
                            .getUrlEndPointOfService(Service.GRIDFTP);

                    Clipboard clipBoard = Toolkit.getDefaultToolkit()
                            .getSystemClipboard();
                    StringSelection data = new StringSelection(urlGrid);
                    clipBoard.setContents(data, data);

                }
            });
            clipboard.add(replicaOption);
        }
        gridFTPMenu.add(clipboard);
    }

    private void createOpendapOptionMenu(JMenu opendapMenu,
            List<RecordReplica> openDapReplicas) {

        // Viewer option--------------------------------------
        JMenu viewer = new JMenu("Open in Viewer Panel");
        for (final RecordReplica replica : openDapReplicas) {
            JMenuItem replicaOption = new JMenuItem(replica.getDataNode()
                    .substring(7));
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
                    RecordPopupMenu.this.firePropertyChange("openFile", null,
                            urlOpenDap);

                }
            });
            viewer.add(replicaOption);
        }
        opendapMenu.add(viewer);

        // Featured types option------------------------------
        JMenu featuresTypes = new JMenu("Open in Features Types Panel");
        for (final RecordReplica replica : openDapReplicas) {
            JMenuItem replicaOption = new JMenuItem(replica.getDataNode()
                    .substring(7));
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
            featuresTypes.add(replicaOption);
        }
        opendapMenu.add(featuresTypes);

        // Open in browser option---------------------------
        JMenuItem browser = new JMenu("Open URL in browser");

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
            browser.add(replicaOption);
        }
        opendapMenu.add(browser);

        // Copy to clipboard option---------------------------
        JMenu clipboard = new JMenu("Copy URL to clipboard");
        for (final RecordReplica replica : openDapReplicas) {
            JMenuItem replicaOption = new JMenuItem(replica.getDataNode()
                    .substring(7));
            replicaOption.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {

                    String urlOpenDap = replica
                            .getUrlEndPointOfService(Service.OPENDAP);

                    Clipboard clipBoard = Toolkit.getDefaultToolkit()
                            .getSystemClipboard();
                    StringSelection data = new StringSelection(urlOpenDap);
                    clipBoard.setContents(data, data);

                }
            });
            clipboard.add(replicaOption);
        }
        opendapMenu.add(clipboard);
    }

    private void createHttpOptionMenu(JMenu httpMenu,
            List<RecordReplica> httpReplicas) {

        // Viewer option--------------------------------------
        JMenu viewer = new JMenu("Open in Viewer Panel");
        for (final RecordReplica replica : httpReplicas) {
            JMenuItem replicaOption = new JMenuItem(replica.getDataNode()
                    .substring(7));
            replicaOption.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {

                    String url = replica
                            .getUrlEndPointOfService(Service.HTTPSERVER);

                    // Component.firePropertyChange(String propertyName,
                    // Object oldValue, Object newValue) this method
                    // fire new event with a name, old object and new
                    // object this event is catch and processed by main
                    // ESGF
                    RecordPopupMenu.this.firePropertyChange("openFile", null,
                            url);

                }
            });
            viewer.add(replicaOption);
        }
        httpMenu.add(viewer);

        // Featured types option------------------------------
        JMenu featuresTypes = new JMenu("Open in Features Types Panel");
        for (final RecordReplica replica : httpReplicas) {
            JMenuItem replicaOption = new JMenuItem(replica.getDataNode()
                    .substring(7));
            replicaOption.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {

                    String url = replica
                            .getUrlEndPointOfService(Service.HTTPSERVER);

                    // Component.firePropertyChange(String propertyName,
                    // Object oldValue, Object newValue) this method
                    // fire new event with a name, old object and new
                    // object this event is catch and processed by main
                    // ESGF
                    RecordPopupMenu.this.firePropertyChange(
                            "openFileInGridFeatureTypes", null, url);

                }
            });
            featuresTypes.add(replicaOption);
        }
        httpMenu.add(featuresTypes);

        // Open in browser option---------------------------
        JMenuItem browser = new JMenu("Open URL in browser");

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
            browser.add(replicaOption);
        }
        httpMenu.add(browser);

        // Copy to clipboard option---------------------------
        JMenu clipboard = new JMenu("Copy URL to clipboard");
        for (final RecordReplica replica : httpReplicas) {
            JMenuItem replicaOption = new JMenuItem(replica.getDataNode()
                    .substring(7));
            replicaOption.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {

                    String urlhttp = replica
                            .getUrlEndPointOfService(Service.HTTPSERVER);

                    Clipboard clipBoard = Toolkit.getDefaultToolkit()
                            .getSystemClipboard();
                    StringSelection data = new StringSelection(urlhttp);
                    clipBoard.setContents(data, data);

                }
            });
            clipboard.add(replicaOption);
        }
        httpMenu.add(clipboard);
    }

    private void createLocalOptionMenu(JMenu localMenu,
            final FileDownloadStatus fileStatus) {

        final String path = fileStatus.getFilePath();

        RecordStatus status = fileStatus.getRecordStatus();

        // Viewer option--------------------------------------

        JMenuItem viewer = new JMenuItem("Open in Viewer Panel");
        viewer.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                // Component.firePropertyChange(String propertyName,
                // Object oldValue, Object newValue) this method fire
                // new event with a name, old object and new object this
                // event is catch and processed by main ESGF
                RecordPopupMenu.this.firePropertyChange("openFile", null, path);

            }
        });

        localMenu.add(viewer);

        if (status == RecordStatus.FINISHED) {
            viewer.setEnabled(true);
        } else {
            viewer.setEnabled(false);
        }

        // Featured types option------------------------------

        JMenuItem featuresTypes = new JMenuItem("Open in Features Types Panel");
        featuresTypes.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                // Component.firePropertyChange(String propertyName,
                // Object oldValue, Object newValue) this method fire
                // new event with a name, old object and new object this
                // event is catch and processed by main ESGF
                RecordPopupMenu.this.firePropertyChange(
                        "openFileInGridFeatureTypes", null, path);

            }
        });
        localMenu.add(featuresTypes);

        if (status == RecordStatus.FINISHED) {
            featuresTypes.setEnabled(true);
        } else {
            featuresTypes.setEnabled(false);
        }

        // Copy to clipboard option---------------------------

        JMenuItem clipboard = new JMenuItem("Copy file path to clipboard");
        clipboard.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                Clipboard clipBoard = Toolkit.getDefaultToolkit()
                        .getSystemClipboard();
                // print the last copied thing
                // Transferable t = clipBoard.getContents(null);
                // if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                // System.out.println(t
                // .getTransferData(DataFlavor.stringFlavor));
                // }
                StringSelection data = new StringSelection(path);
                clipBoard.setContents(data, data);
            }
        });
        localMenu.add(clipboard);

        JMenuItem directory = new JMenuItem("Open directory");
        directory.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                // inspired in:
                // package org.appwork.utils.os.CrossSystem
                // org.appwork.utils.os.DesktopSupportLinux.java
                if (Desktop.isDesktopSupported()) {
                    try {
                        // supported in windows and mac 1.6 +
                        final Desktop desktop = Desktop.getDesktop();
                        desktop.open(fileStatus.getFile().getParentFile());

                    } catch (IOException e1) {
                        try {
                            Runtime.getRuntime().exec(
                                    "gnome-open "
                                            + fileStatus.getFile()
                                                    .getParentFile()
                                                    .getAbsolutePath());
                        } catch (IOException e2) {
                            try {
                                Runtime.getRuntime().exec(
                                        "kde-open "
                                                + fileStatus.getFile()
                                                        .getParentFile()
                                                        .getAbsolutePath());
                            } catch (IOException e3) {
                                try {
                                    Runtime.getRuntime().exec(
                                            "xdg-open "
                                                    + fileStatus.getFile()
                                                            .getParentFile()
                                                            .getAbsolutePath());
                                } catch (IOException e4) {
                                    JOptionPane
                                            .showMessageDialog(
                                                    parent,
                                                    "This action is not supported in your system",
                                                    "Info",
                                                    JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        }
                    }
                }

            }
        });
        localMenu.add(directory);
    }

    public RecordPopupMenu(final DatasetDownloadStatus datasetStatus,
            final Component parent, final DownloadManager downloadManager,
            int x, int y) {
        super("Dataset menu");

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

        // Start download option-----------------------------------------
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

        if (status != RecordStatus.FINISHED) {
            play.setEnabled(true);
        } else {
            play.setEnabled(false);
        }
        // End start download option--------------------------------------
        // Pause option---------------------------------------------------
        JMenuItem pause = new JMenuItem("Pause all file downloads");
        pause.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                downloadManager.pauseDataSetDownload(datasetStatus);
            }
        });

        add(pause);
        if (status == RecordStatus.DOWNLOADING) {
            pause.setEnabled(true);
        } else {
            pause.setEnabled(false);
        }
        // End pause option----------------------------------------------

        // Reset option----------------------------------------------
        JMenuItem reset = new JMenuItem("Reset");
        reset.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                int confirm = JOptionPane
                        .showConfirmDialog(
                                parent,
                                "Sure you want delete all progress of file downloads in file system?",
                                "reset", JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    downloadManager.resetDataSetDownload(datasetStatus);
                }
            }
        });
        add(reset);
        // End reset option-----------------------------------------

        // retry failed files option--------------------------------
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
        retryFailedFiles.setEnabled(false);
        // End retry option-----------------------------------------

        // Remove option--------------------------------------------
        JMenuItem remove = new JMenuItem("Remove of downloads queue");
        remove.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                downloadManager.removeDataset(datasetStatus);
            }
        });
        add(remove);
        // End remove option-----------------------------------------

        // Download info option----------------------------------------------
        JMenuItem info = new JMenuItem("Dataset info");
        info.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                DatasetStatusInfoDialog dialog = new DatasetStatusInfoDialog(
                        datasetStatus, (Frame) ((JComponent) parent)
                                .getTopLevelAncestor());
                dialog.setVisible(true);
            }
        });
        add(info);

        // ________________________________________________________________
        // ----------------------------------------------------------------
        addSeparator(); // SEPARATOR--------------------------------------
        // ----------------------------------------------------------------
        // _______________________________________________________________

        // Access Services option-----------------------------------------
        JMenu accessServices = new JMenu("Services");

        // thredds sub-option--------------------------------
        JMenu thredds = new JMenu("THREDDS");
        if (threddsReplicas != null) {
            thredds.setEnabled(true);
            createThreddsOptionMenu(thredds, threddsReplicas);
        } else {
            thredds.setEnabled(false);
        }
        accessServices.add(thredds);
        // end thredds sub-option----------------------------

        // las sub-option
        JMenu las = new JMenu("LAS");
        if (lasReplicas != null) {
            las.setEnabled(true);
            createLASOptionMenu(las, lasReplicas);
        } else {
            las.setEnabled(false);
        }
        accessServices.add(las);
        // end las sub-option-------------------------------

        JMenu local = new JMenu("Local");
        JMenuItem directory = new JMenuItem("Open directory");
        directory.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                // inspired in:
                // package org.appwork.utils.os.CrossSystem
                // org.appwork.utils.os.DesktopSupportLinux.java
                if (Desktop.isDesktopSupported()) {
                    try {
                        // supported in windows and mac 1.6 +
                        final Desktop desktop = Desktop.getDesktop();
                        desktop.open(new File(datasetStatus.getPath()));

                    } catch (IOException e1) {
                        try {
                            Runtime.getRuntime().exec(
                                    "gnome-open " + datasetStatus.getPath());
                        } catch (IOException e2) {
                            try {
                                Runtime.getRuntime().exec(
                                        "kde-open " + datasetStatus.getPath());
                            } catch (IOException e3) {
                                try {
                                    Runtime.getRuntime().exec(
                                            "xdg-open "
                                                    + datasetStatus.getPath());
                                } catch (IOException e4) {
                                    JOptionPane
                                            .showMessageDialog(
                                                    parent,
                                                    "This action is not supported in your system",
                                                    "Info",
                                                    JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        }
                    }
                }

            }
        });
        local.add(directory);
        accessServices.add(local);

        JMenu opendap = new JMenu("OPeNDAP");
        accessServices.add(opendap);
        opendap.setEnabled(false);

        JMenu http = new JMenu("HTTP");
        accessServices.add(http);
        http.setEnabled(false);

        JMenu griftp = new JMenu("GridFTP");
        accessServices.add(griftp);
        griftp.setEnabled(false);

        add(accessServices);
        // End access Services option--------------------------------------

        // XXX temp info option
        JMenuItem tempInfo = new JMenuItem("tempInfo");
        tempInfo.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                String info = datasetStatus.toString();
                System.out.println(info);

            }
        });
        // add(tempInfo);

        show(parent, x, y);
    }

    private void createLASOptionMenu(JMenu las, List<RecordReplica> lasReplicas) {
        // Open URL in browser-------------------------------------------
        JMenuItem browser = new JMenu("Open URL in browser");

        // Add all replicas data nodes
        for (final RecordReplica replica : lasReplicas) {
            JMenuItem replicaOption = new JMenuItem(replica.getDataNode()
                    .substring(7));
            replicaOption.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    // open in browser
                    String url = replica.getUrlEndPointOfService(Service.LAS);
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
            browser.add(replicaOption);
        }
        las.add(browser);

        // Copy URL to
        // clipboard--------------------------------------------------
        // Add all replicas data nodes
        JMenuItem clipboard = new JMenu("Copy URL to clipboard");
        for (final RecordReplica replica : lasReplicas) {
            JMenuItem replicaOption = new JMenuItem(replica.getDataNode()
                    .substring(7));
            replicaOption.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    // open in browser
                    String url = replica.getUrlEndPointOfService(Service.LAS);
                    Clipboard clipBoard = Toolkit.getDefaultToolkit()
                            .getSystemClipboard();
                    StringSelection data = new StringSelection(url);
                    clipBoard.setContents(data, data);

                }
            });
            clipboard.add(replicaOption);
        }
        las.add(clipboard);

    }

    private void createThreddsOptionMenu(JMenu thredds,
            List<RecordReplica> threddsReplicas) {

        // Open in THREDDS panel option-------------------------------
        JMenuItem threddspanel = new JMenu("Open in THREDDS Panel");

        for (final RecordReplica replica : threddsReplicas) {
            JMenuItem replicaOption = new JMenuItem(replica.getDataNode()
                    .substring(7));
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
            threddspanel.add(replicaOption);
        }
        thredds.add(threddspanel);

        // Open URL in browser-------------------------------------------
        JMenuItem browser = new JMenu("Open URL in browser");

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
            browser.add(replicaOption);
        }
        thredds.add(browser);

        // Copy URL to
        // clipboard--------------------------------------------------
        // Add all replicas data nodes
        JMenuItem clipboard = new JMenu("Copy URL to clipboard");
        for (final RecordReplica replica : threddsReplicas) {
            JMenuItem replicaOption = new JMenuItem(replica.getDataNode()
                    .substring(7));
            replicaOption.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    // open in browser
                    String url = replica
                            .getUrlEndPointOfService(Service.CATALOG);
                    Clipboard clipBoard = Toolkit.getDefaultToolkit()
                            .getSystemClipboard();
                    StringSelection data = new StringSelection(url);
                    clipBoard.setContents(data, data);

                }
            });
            clipboard.add(replicaOption);
        }
        thredds.add(clipboard);
    }
}
