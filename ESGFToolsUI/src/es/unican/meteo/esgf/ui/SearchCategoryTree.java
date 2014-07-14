/**
 * 
 */
package es.unican.meteo.esgf.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

import es.unican.meteo.esgf.download.DatasetDownloadStatus;
import es.unican.meteo.esgf.download.FileDownloadStatus;
import es.unican.meteo.esgf.search.Facet;
import es.unican.meteo.esgf.search.SearchCategoryFacet;
import es.unican.meteo.esgf.search.SearchCategoryValue;

/**
 * Tree that extends of Jtree de Swing. Shows all {@link SearchCategoryFacet}
 * and their possible values and allows select one ore more for each facet.
 * 
 * @author Karem Terry
 * 
 */
public class SearchCategoryTree extends JTree {

    /** Logger. */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(SearchCategoryTree.class);

    /** Model of tree. */
    private DefaultTreeModel treeModel;

    /** Root of tree model. */
    private DefaultMutableTreeNode root;

    /**
     * Map of search category facets and their values.
     */
    Map<SearchCategoryFacet, List<SearchCategoryValue>> map;

    /**
     * List of tree nodes, each node is a {@link FileDownloadStatus} or
     * {@link DatasetDownloadStatus} that are being download. This list not
     * contains root node.
     */
    private LinkedList<DefaultMutableTreeNode> treeNodes;

    public SearchCategoryTree(DefaultTreeModel treeModel) {
        super(treeModel);
        logger.trace("[IN]  SearchCategoryTree");

        // New tree model with a root with a String "root"
        // Jtree is formed by this model and each node added in node
        // will be added in an array of nodes
        root = new DefaultMutableTreeNode("root");
        treeModel.setRoot(root);

        setRootVisible(true);
        setCellRenderer(new SearchCategoryTreeRenderer());

        treeNodes = new LinkedList<DefaultMutableTreeNode>();
        this.treeModel = treeModel;

        addTreeSelectionListener(new TreeSelectionListener() {

            @Override
            public void valueChanged(TreeSelectionEvent arg0) {
                TreePath selPath = arg0.getPath();

                // If path objects from mouse location is not null AND
                // if contract a parent path (facet), TreeSelectionEvent is
                // throw with last component selected but It can detect it with
                // Component.isValid()
                if (selPath != null && ((Component) arg0.getSource()).isValid()) {

                    // Path of parent tree node
                    TreePath parentPath = selPath.getParentPath();

                    // If parent is no root
                    if (parentPath.getParentPath() != null) {

                        // Get last component, the father of this node
                        // In this case the facet of the selected value
                        SearchCategoryFacet categoryFacet = (SearchCategoryFacet) ((DefaultMutableTreeNode) parentPath
                                .getLastPathComponent()).getUserObject();

                        // Get value of facet selected
                        SearchCategoryValue value = (SearchCategoryValue) ((DefaultMutableTreeNode) selPath
                                .getLastPathComponent()).getUserObject();

                        // Change state of seleted
                        boolean selected = value.isSelected();
                        value.setSelected(!selected);

                        Facet facet = new Facet(categoryFacet.name(),
                                value.getValue());

                        // Throws a new property change
                        // If facet value is selected (previous was not
                        // selected)
                        if (selected == false) {
                            SearchCategoryTree.this.firePropertyChange(
                                    "SelectedFacetValue", null, facet);
                        } else { // If facet value is selected
                            SearchCategoryTree.this.firePropertyChange(
                                    "DeselectedFacetValue", null, facet);
                        }
                    }

                }

            }
        });

        setRootVisible(true);
        setCellRenderer(new SearchCategoryTreeRenderer());

        logger.trace("[OUT] SearchCategoryTree");

        map = new HashMap<SearchCategoryFacet, List<SearchCategoryValue>>();
    }

    /**
     * Fill search category tree with new values.
     * 
     * @param mapCategoryValue
     *            {@link Map} where the key is a {@link SearchCategoryFacet} and
     *            the value is a {@link List} of {@link SearchCategoryValue}
     */
    public void fillTree(
            Map<SearchCategoryFacet, List<SearchCategoryValue>> mapCategoryValue) {

        logger.trace("[IN]  fillTree");

        map = mapCategoryValue;
        logger.debug("Remove all children of search category tree");
        root.removeAllChildren();

        // Aux
        String aux;
        DefaultMutableTreeNode auxFacetNode;
        int index = 0;// index

        logger.debug("Filling map with new values...");
        // For each element in map
        for (Map.Entry<SearchCategoryFacet, List<SearchCategoryValue>> element : mapCategoryValue
                .entrySet()) {

            // aux = string of Search category facet
            aux = element.getKey().toString();

            // Add to string aux number of datasets in this facet
            aux = aux + " (" + element.getValue().size() + ")";

            logger.debug("Creating new father node: {}", element.getKey());
            auxFacetNode = new DefaultMutableTreeNode(aux);
            // Associate SearchCategoryFacet enum value
            auxFacetNode.setUserObject(element.getKey());

            // Insert new root child into model tree
            treeModel.insertNodeInto(auxFacetNode, root, index);

            logger.debug("Filling father ({}) with its sons", element.getKey());
            // Inset root childs "values" into model tree
            DefaultMutableTreeNode auxValueNode; // Aux
            // for each value facet in list of values
            for (int i = 0; i < element.getValue().size(); i++) {
                // logger.debug("Creating new child node");
                auxValueNode = new DefaultMutableTreeNode(element.getValue()
                        .get(i).toString());

                // Associate SearchCategoryFacet value to node
                auxValueNode.setUserObject(element.getValue().get(i));

                // Insert facet child "value" into model tree
                treeModel.insertNodeInto(auxValueNode, auxFacetNode, i);
            }

            index++;
        }

        // reload tree model
        treeModel.reload();

        logger.trace("[OUT] fillTree");
    }

    /**
     * Set select state of tree node
     * 
     * @param node
     *            node of tree
     * @param selected
     *            boolean
     * 
     * @throws IllegalArgumentException
     *             if user object of node isn't instance of
     *             {@link SearchCategoryValue} or if this
     *             {@link SearchCategoryValue} is invalid
     */
    public void setNodeSelected(DefaultMutableTreeNode node, boolean selected) {

        logger.trace("[IN]  setNodeSelected");
        if (node.getUserObject() instanceof SearchCategoryValue) {
            SearchCategoryValue facetValue = (SearchCategoryValue) node
                    .getUserObject();
            facetValue.setSelected(selected);
        } else {
            throw new IllegalArgumentException();
        }

        logger.trace("[OUT] setNodeSelected");
    }

    /**
     * Return true if node is selected and false otherwise
     * 
     * @param node
     *            node of tree
     * 
     * @throws IllegalArgumentException
     *             if user object of node isn't instance of
     *             {@link SearchCategoryValue} or if this
     *             {@link SearchCategoryValue} is invalid
     */
    public boolean isNodeSelected(DefaultMutableTreeNode node) {

        logger.trace("[IN]  isNodeSelected");

        if (node.getUserObject() instanceof SearchCategoryValue) {
            SearchCategoryValue facetValue = (SearchCategoryValue) node
                    .getUserObject();
            logger.trace("[OUT] isNodeSelected");
            return facetValue.isSelected();
        } else {
            logger.error("{} node isn't isntance of FacetValue", node);
            throw new IllegalArgumentException();
        }
    }

    /**
     * Tree of downloads renderer
     */
    private class SearchCategoryTreeRenderer extends JCheckBox implements
            TreeCellRenderer {

        private static final long serialVersionUID = 1L;

        // Constructor
        SearchCategoryTreeRenderer() {
            super();
            setLayout(new FlowLayout());
            setBackground(Color.WHITE);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row,
                boolean hasFocus) {

            Object userObject = ((DefaultMutableTreeNode) value)
                    .getUserObject();

            // if cell is for search category facet
            if (userObject instanceof SearchCategoryFacet) {

                SearchCategoryFacet searchCategory = (SearchCategoryFacet) userObject;
                setText(searchCategory.toString() + "("
                        + map.get(searchCategory).size() + ")");

                setIcon(new ImageIcon());
                setEnabled(true);

                // if cell is for facet value
            } else if (userObject instanceof SearchCategoryValue) {

                SearchCategoryValue facetValue = (SearchCategoryValue) userObject;
                setText(facetValue.getValue() + "(" + facetValue.getCount()
                        + ")");

                // Set predetermined icon for category facet value
                JCheckBox aux = new JCheckBox();
                setSelectedIcon(aux.getSelectedIcon());
                setDisabledSelectedIcon(aux.getDisabledSelectedIcon());
                setIcon(aux.getIcon());
                setEnabled(true);

                if (isNodeSelected((DefaultMutableTreeNode) value)) {
                    setSelected(true);
                } else {
                    setSelected(false);
                }

                // if cell is for root shows nothing
            } else {

                // Disable icon and set enable an visible to false
                setText("");
                setDisabledIcon(new ImageIcon());
                setSelectedIcon(new ImageIcon());
                setDisabledSelectedIcon(new ImageIcon());
                setIcon(new ImageIcon());
                setVisible(false);
                setEnabled(false);
            }
            return this;
        }
    }
}
