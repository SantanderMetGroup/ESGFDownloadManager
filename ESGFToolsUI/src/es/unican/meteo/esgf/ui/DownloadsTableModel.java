package es.unican.meteo.esgf.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.jdesktop.swingx.tree.TreeModelSupport;
import org.jdesktop.swingx.treetable.AbstractTreeTableModel;

import es.unican.meteo.esgf.download.DatasetDownloadStatus;
import es.unican.meteo.esgf.download.Download;
import es.unican.meteo.esgf.download.DownloadObserver;
import es.unican.meteo.esgf.download.FileDownloadStatus;
import es.unican.meteo.esgf.search.RecordReplica;

public class DownloadsTableModel extends AbstractTreeTableModel implements
        DownloadObserver {
    private final static String[] COLUMN_NAMES = { "Name", "Progress",
            "Status", "Current Size", "TotalSize", "Data node" };

    private List<DatasetNode> datasetStatusList;
    private TreeModelSupport treeModelSupport;

    private Map<String, DatasetNode> fileDataNodeMap;

    public DownloadsTableModel(List<DatasetDownloadStatus> dataStatusList) {
        super(new Object());
        this.treeModelSupport = new TreeModelSupport(this);

        this.fileDataNodeMap = new HashMap<String, DatasetNode>();
        this.datasetStatusList = new ArrayList<DownloadsTableModel.DatasetNode>(
                dataStatusList.size());
        for (DatasetDownloadStatus datasetStatus : dataStatusList) {
            DatasetNode node = new DatasetNode(datasetStatus);
            datasetStatusList.add(node);
            registerObserverInFiles(datasetStatus);
            addFileInstanceIDsInFileDataNodeMap(node);
        }
    }

    private void addFileInstanceIDsInFileDataNodeMap(DatasetNode datasetNode) {
        for (FileDownloadStatus fileStatus : ((DatasetDownloadStatus) datasetNode
                .getUserObject()).getFilesToDownload()) {
            fileDataNodeMap.put(
                    standardizeESGFFileInstanceID(fileStatus.getInstanceID()),
                    datasetNode);
        }

    }

    private void registerObserverInFiles(DatasetDownloadStatus datasetStatus) {
        for (FileDownloadStatus fileStatus : datasetStatus.getFilesToDownload()) {
            if (!fileStatus.containsObserver(this)) {
                fileStatus.registerObserver(this);
            }
        }
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public boolean isCellEditable(Object node, int column) {
        return false;
    }

    @Override
    public boolean isLeaf(Object node) {
        return node instanceof FileDownloadStatus;
    }

    @Override
    public Object getValueAt(Object node, int column) {
        if (node instanceof DatasetNode) {
            DatasetDownloadStatus datasetStatus = (DatasetDownloadStatus) ((DatasetNode) node)
                    .getUserObject();
            // "Name", "Progress", "Current Size", "TotalSize", "Data node"
            switch (column) {
                case 0: // name
                    return datasetStatus.getInstanceID();
                case 1:// progress
                    return datasetStatus;
                case 2:// status
                    return datasetStatus.getRecordStatus();
                case 3:// current size
                    return bytesToString(datasetStatus.getCurrentSize());
                case 4:// total size
                    return bytesToString(datasetStatus.getTotalSize());
            }
        } else if (node instanceof FileDownloadStatus) {
            FileDownloadStatus fileStatus = (FileDownloadStatus) node;
            switch (column) {
                case 0:// name
                    return getFileName(fileStatus);
                case 1:// progress
                    return fileStatus;
                case 2:// status
                    return fileStatus.getRecordStatus();
                case 3:// currentSize
                    return bytesToString(fileStatus.getCurrentSize());
                case 4:// totalSize
                    return bytesToString(fileStatus.getTotalSize());
                case 5:// data node
                    RecordReplica replica = fileStatus.getCurrentFileReplica();
                    String dataNode = "";
                    if (replica != null) {
                        dataNode = replica.getDataNode().substring(7);
                    }
                    return dataNode;
            }
        }
        return null;
    }

    private String getFileName(FileDownloadStatus fileStatus) {
        String datasetId = fileStatus.getDatasetDownloadStatus()
                .getInstanceID();
        String fileId = fileStatus.getInstanceID();
        return fileId.substring(datasetId.length() + 1);
    }

    @Override
    public Object getChild(Object parent, int index) {
        if (parent instanceof DatasetNode) {
            DatasetDownloadStatus datasetStatus = (DatasetDownloadStatus) ((DatasetNode) parent)
                    .getUserObject();
            return datasetStatus.getFilesToDownload().get(index);
        }
        return datasetStatusList.get(index);
    }

    @Override
    public int getChildCount(Object parent) {
        if (parent instanceof DatasetNode) {
            DatasetDownloadStatus datasetStatus = (DatasetDownloadStatus) ((DatasetNode) parent)
                    .getUserObject();
            return datasetStatus.getNumberOfFilesToDownload();
        }

        return datasetStatusList.size();
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {

        try {

            // System.out.println(Thread.currentThread().getContextClassLoader());
            DatasetDownloadStatus datasetStatus = (DatasetDownloadStatus) ((es.unican.meteo.esgf.ui.DownloadsTableModel.DatasetNode) parent)
                    .getUserObject();
            FileDownloadStatus fileStatus = (FileDownloadStatus) child;
            return datasetStatus.getFilesToDownload().indexOf(fileStatus);

        } catch (ClassCastException e) {

            System.out.println("El objeto es:" + child.getClass());
            System.out.println("Objeto loader"
                    + child.getClass().getClassLoader());
            System.out.println("DownloadsTable loader"
                    + this.getClass().getClassLoader());
            System.out.println("DatasetDownloadStatus loader"
                    + DatasetDownloadStatus.class.getClassLoader());
            throw e;
        }
    }

    @Override
    public void onDownloadProgress(Download download) {

        FileDownloadStatus fileStatus = (FileDownloadStatus) download;

        DatasetNode datasetNode = getDatasetNodeOf(fileStatus);
        TreePath path = new TreePath(datasetNode);
        int index = datasetNode.getIndexOfFileToDownload(fileStatus
                .getInstanceID());

        // treeModelSupport.firePathChanged(path);
        if (path != null) {
            treeModelSupport.fireChildChanged(path, index, download);
        }
        // treeModelSupport.fireTreeStructureChanged(path);
        // treeModelSupport.fireTreeStructureChanged(new TreePath(getRoot()));
        // treeModelSupport.fireNewRoot();
    }

    public TreeModelSupport getTreeModelSupport() {
        return treeModelSupport;
    }

    private DatasetNode getDatasetNodeOf(FileDownloadStatus fileStatus) {
        return fileDataNodeMap.get(standardizeESGFFileInstanceID(fileStatus
                .getInstanceID()));
    }

    @Override
    public void onDownloadCompleted(Download download) {
        System.out.println("completed" + download.toString());
    }

    @Override
    public void onError(Download download) {
        System.out.println("error" + download.toString());

    }

    @Override
    public void onUnauthorizedError(Download download) {
        // TODO Auto-generated method stub

    }

    public class DatasetNode extends DefaultMutableTreeNode {

        Map<String, Integer> indexOfFiles;

        public DatasetNode(DatasetDownloadStatus datasetDownloadStatus) {
            setUserObject(datasetDownloadStatus);
            this.indexOfFiles = new HashMap<String, Integer>();

            List<FileDownloadStatus> filesToDownload = datasetDownloadStatus
                    .getFilesToDownload();

            for (int i = 0; i < filesToDownload.size(); i++) {
                indexOfFiles.put(standardizeESGFFileInstanceID(filesToDownload
                        .get(i).getInstanceID()), i);
            }
        }

        public int getIndexOfFileToDownload(String instanceID) {
            return indexOfFiles.get(standardizeESGFFileInstanceID(instanceID));
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
            strBytes = String.format("%.2f %sB", bytes / Math.pow(1024, exp),
                    prefixes.charAt(exp - 1));
        }

        return strBytes;
    }

    /**
     * Verify if instance ID of ESGF file is correct and if id is corrupted then
     * it corrects the id (avoid ".nc_number" issue in instance id of files)
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

    public void updateElements(Set<DatasetDownloadStatus> datasetDownloads) {

        this.fileDataNodeMap = new HashMap<String, DatasetNode>();
        this.datasetStatusList = new ArrayList<DownloadsTableModel.DatasetNode>(
                datasetDownloads.size());

        for (DatasetDownloadStatus datasetStatus : datasetDownloads) {
            DatasetNode node = new DatasetNode(datasetStatus);
            datasetStatusList.add(node);
            registerObserverInFiles(datasetStatus);
            addFileInstanceIDsInFileDataNodeMap(node);
        }

        this.modelSupport.fireNewRoot();
    }
}
