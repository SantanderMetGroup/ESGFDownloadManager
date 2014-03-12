package es.unican.meteo.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;


import org.json.JSONArray;
import org.json.JSONObject;

import es.unican.meteo.search.Dataset;

import ucar.util.prefs.PreferencesExt;

public class DatasetJsonViewDialog extends JFrame {

    /**
     * SerialVersionUID
     */
    private static final long serialVersionUID = -6544810788283927158L;

    private static final int maxNumberOfDatasetsByPage = 5;

    /** Preferences of configuration. */
    private PreferencesExt prefs;

    private Dataset dataset;

    private JFrame parent;

    private DefaultMutableTreeNode root;

    private DefaultTreeModel treeModel;

    private JTree tree;

    public DatasetJsonViewDialog(Dataset dataset, JFrame parent) {

        // Call super class(JDialog) and set parent frame and modal true
        // for lock other panels
        super();

        this.prefs = prefs;
        this.parent = parent;
        this.dataset = dataset;

        // Set main panel layout
        setLayout(new BorderLayout());

        JSONObject jsonDataset = dataset.toJSON();

        // add(new TextArea(jsonDataset.toString(3)), BorderLayout.CENTER);

        root = new JSONJTreeNode(dataset.getInstanceID(), -1, jsonDataset);
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);
        tree.setRootVisible(true);

        DefaultTreeCellRenderer render = (DefaultTreeCellRenderer) tree
                .getCellRenderer();
        render.setLeafIcon(null);
        render.setOpenIcon(null);
        render.setClosedIcon(null);

        // tree.setCellRenderer(new TreeRenderer());
        // JPanel panel= new JPanel();
        // update();

        JButton closeButton = new JButton("x");
        closeButton.setBorderPainted(false);
        closeButton.setSelected(false);
        closeButton.setFocusable(false);
        closeButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        JPanel close = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        close.add(closeButton);

        add(new JScrollPane(tree), BorderLayout.CENTER);
        // add(close, BorderLayout.NORTH);
        pack();

        // Center dialog
        setLocationRelativeTo(parent);
        setVisible(true);
        // this.setUndecorated(false);
    }

    public void update() {

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

    /**
     * Tree of downloads renderer
     */
    public class TreeRenderer extends JLabel implements TreeCellRenderer {

        // Constructor
        TreeRenderer() {
            setBackground(Color.WHITE);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row,
                boolean hasFocus) {

            Object userObject = ((DefaultMutableTreeNode) value)
                    .getUserObject();

            // if (userObject instanceof JSONObject)

            setText(value.toString());

            return this;
        }
    }

    private static class JSONJTreeNode extends DefaultMutableTreeNode {
        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        public enum DataType {
            ARRAY("[]"), OBJECT("{}"), VALUE("");

            /** record type string */
            private String dataType;

            // Constructor, initialize the dataType
            private DataType(String dataType) {
                this.dataType = dataType;
            }

            // Return
            @Override
            public String toString() {
                return dataType;
            }
        };

        final DataType dataType;
        final int index;
        String name;
        final String value;

        /**
         * Constructor
         * 
         * @param name
         *            name of node or null. Name must be key of jsonValue. Null
         *            if a child of array
         * @param index
         *            index of element in the array or -1 if not part of an
         *            array
         * @param jsonValue
         *            element to represent (JSONObject, JSONArray, Other Object)
         */
        public JSONJTreeNode(String name, int index, Object jsonValue) {
            this.index = index;
            this.name = name;
            if (jsonValue instanceof JSONArray) {
                this.dataType = DataType.ARRAY;
                this.value = jsonValue.toString();
                populateChildren(jsonValue);

            } else if (jsonValue instanceof JSONObject) {

                this.dataType = DataType.OBJECT;
                this.value = jsonValue.toString();
                populateChildren(jsonValue);
            } else if (jsonValue instanceof Calendar) {

                this.dataType = DataType.VALUE;

                Calendar calendar = (Calendar) jsonValue;
                // Format date to human view
                SimpleDateFormat dateFormat = new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss'Z'");
                this.value = dateFormat.format(calendar.getTime());
            } else {
                this.dataType = DataType.VALUE;
                this.value = jsonValue.toString();
            }

        }

        private void populateChildren(Object myJsonElement) {
            switch (dataType) {
                case ARRAY:
                    int index = 0;

                    JSONArray jsonArray = (JSONArray) myJsonElement;

                    while (index < jsonArray.length()) {
                        Object element = jsonArray.get(index);
                        JSONJTreeNode childNode = new JSONJTreeNode(null,
                                index, element);
                        this.add(childNode);
                        index++;
                    }
                break;
                case OBJECT:
                    JSONObject jsonObject = (JSONObject) myJsonElement;

                    Set<String> keys = jsonObject.keySet();
                    for (String key : keys) {

                        Object element = jsonObject.get(key);
                        JSONJTreeNode childNode = new JSONJTreeNode(key, -1,
                                element);
                        this.add(childNode);
                    }
                break;
                default:
                    throw new IllegalStateException(
                            "Internal coding error this should never happen.");
            }
        }

        @Override
        public String toString() {
            switch (dataType) {
                case ARRAY:
                case OBJECT:
                    // If JSONObject its a part of array
                    if (index >= 0) {
                        return String.format("[%d] %s", index,
                                dataType.toString());
                        // if JSONObject name isn't null
                    } else if (name != null) {
                        return String
                                .format("%s %s", name, dataType.toString());
                    } else {// if JSONObject name is null
                        return String.format("%s", dataType.name());
                    }
                default: // in case value or JSONARRAY
                    // if it's a part of array
                    if (index >= 0) {
                        return String.format("[%d] %s", index, value);
                        // if have name
                    } else if (name != null) {
                        return String.format("%s: %s", name, value);
                    } else {// if haven't name
                        return String.format("%s", value);
                    }

            }
        }
    }

    public void save() {
        // TODO Auto-generated method stub

    }
}
