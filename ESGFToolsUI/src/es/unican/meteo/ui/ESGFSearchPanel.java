package es.unican.meteo.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import ucar.util.prefs.PreferencesExt;

import com.toedter.calendar.JCalendar;

import es.unican.meteo.esgf.download.DownloadManager;
import es.unican.meteo.esgf.petition.HTTPStatusCodeException;
import es.unican.meteo.esgf.search.Facet;
import es.unican.meteo.esgf.search.Parameter;
import es.unican.meteo.esgf.search.ParameterValue;
import es.unican.meteo.esgf.search.RESTfulSearch;
import es.unican.meteo.esgf.search.Record;
import es.unican.meteo.esgf.search.SearchCategoryFacet;
import es.unican.meteo.esgf.search.SearchCategoryValue;
import es.unican.meteo.esgf.search.SearchManager;
import es.unican.meteo.esgf.search.SearchResponse;
import es.unican.meteo.esgf.search.Service;

public class ESGFSearchPanel extends JPanel {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String ENCODE_FORMAT = "UTF-8";

    /**
     * Logger
     */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(ESGFSearchPanel.class);
    /** Preferences of configuration. */
    private PreferencesExt prefs;

    /** Search Manager. Manage restful services */
    private SearchManager searchManager;

    /** Download Manager. Manage download of datasets */
    private DownloadManager downloadManager;

    /** Main pane. Divided in two resizable pane */
    private JSplitPane centerPanel;

    /** Main Left Panel */
    private JPanel parametersPanel;

    /** Left node pane, subpanel of main Left Pane. */
    private JPanel nodePanel;

    /** JCombo box to select base url node */
    JComboBox nodeList;

    /** Left filters Panel, sub panel of main Left Panel. */
    private JPanel filtersPanel;

    /** JList contains facet selected filters. */
    private JList filterList;

    /** JList model, for change selected filters */
    private DefaultListModel filterListModel;
    /**
     * JTree for displays current facets categorys for searching datasets. In
     * left scroll panel.
     */
    private SearchCategoryTree categoryTree;

    /** Jtree model. */
    private DefaultTreeModel facetTreeModel;

    /** Map of search category facets and values selected. */
    private Map<SearchCategoryFacet, List<String>> searchCategoryFacetsToAdd;
    private Map<SearchCategoryFacet, List<String>> searchCategoryFacetsToRemove;

    private JPanel treeFiltersPanel;

    /** Main Right Panel */
    private JPanel configurationViewerPanel;

    /** Right query panel */
    private JPanel queryPanel;

    /** Right panel query text box */
    private JTextField queryTextBox;

    /** Dialog that shows dataset metadata. */
    protected MetadataDialog metadataDialog;

    /** JTextFields that shows number of datasets. */
    JTextField numOfRecords;

    private NameOfSearchDialog nameOfSearchDialog;
    private JDialog errorNodeDialog;

    private JComboBox parameterComboBox;

    private JPanel comboPanel;

    private JPanel introComboPanel;

    private JPanel searchCategoryComboPanel;

    private DefaultComboBoxModel comboModel;

    private JComboBox searchCombo;

    /** Predetermined search not saved. */
    private SearchResponse newSearchResponse;

    private DefaultComboBoxModel searchComboModel;

    /**
     * Current search seleted in combo box of searchs
     */
    private SearchResponse currentSearch;

    private JDialog progressDialog;

    /**
     * Constructor
     * 
     * @param prefs
     *            preferences
     */
    public ESGFSearchPanel(PreferencesExt prefs, SearchManager searchManager,
            DownloadManager downloadManager) {
        super();
        logger.trace("[IN]  ESGFSearchPanel");

        // Request manager an download manager are shared in all ESGF tabs
        this.searchManager = searchManager;
        this.downloadManager = downloadManager;

        this.prefs = prefs;
        this.searchCategoryFacetsToAdd = Collections
                .synchronizedMap(new HashMap<SearchCategoryFacet, List<String>>());
        this.searchCategoryFacetsToRemove = Collections
                .synchronizedMap(new HashMap<SearchCategoryFacet, List<String>>());
        this.setLayout(new BorderLayout());

        // Dialogs---------------------------------------------------------------
        // Metadata Dialog, not visible
        metadataDialog = new MetadataDialog(new Record(),
                (JFrame) this.getTopLevelAncestor());
        metadataDialog.setVisible(false);

        nameOfSearchDialog = new NameOfSearchDialog(searchManager,
                (JFrame) this.getTopLevelAncestor());
        nameOfSearchDialog.setVisible(false);

        // update combo box of search esponses before save new search
        nameOfSearchDialog
                .addPropertyChangeListener(new PropertyChangeListener() {

                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        if (evt.getPropertyName().equals("update")) {
                            SearchResponse newSearch = (SearchResponse) evt
                                    .getNewValue();
                            if (newSearch != null) {
                                currentSearch = newSearch;
                                update();
                            } else {
                                String message = "Already exists a search with the same name";
                                JOptionPane.showMessageDialog(
                                        ESGFSearchPanel.this, message, "Warn",
                                        JOptionPane.WARNING_MESSAGE);

                            }
                        }

                    }
                });

        // Error in index node dialog
        errorNodeDialog = new JDialog((JFrame) this.getTopLevelAncestor(),
                false);
        errorNodeDialog.setVisible(false);
        errorNodeDialog.setLayout(new BorderLayout());
        errorNodeDialog.setLocationRelativeTo(this.getTopLevelAncestor());
        errorNodeDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JLabel errorDialogMsg = new JLabel(
                "Failed to connect to the selected node. Try another node.");
        JButton acceptDialog = new JButton("Ok");
        acceptDialog.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                errorNodeDialog.dispose();
                errorNodeDialog.setVisible(false);
            }
        });
        errorNodeDialog.add(errorDialogMsg, BorderLayout.CENTER);
        errorNodeDialog.add(acceptDialog, BorderLayout.SOUTH);
        errorNodeDialog.pack();

        // Progress Dialog

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setIndeterminate(true);

        progressDialog = new JDialog((JFrame) this.getTopLevelAncestor(), true);
        progressDialog.setLayout(new BorderLayout());
        progressDialog.add(progressBar, BorderLayout.CENTER);

        progressDialog.setVisible(false);
        progressDialog.setLocationRelativeTo(this.getTopLevelAncestor());
        progressDialog.setUndecorated(true); // quit upper bar
        progressDialog.pack();

        // End
        // Dialog-------------------------------------------------------------

        // ---------------------------------------------------------------------
        // ---------------------------------------------------------------------
        // Main panel
        // ---------------------------------------------------------------------
        // ---------------------------------------------------------------------

        // ----------------------------------------------------------------------
        // NORTH
        // PANEL--------------------------------
        // ----------------------------------------------------------------------
        // Node (url base)panel-------------------------------------------------
        nodePanel = new JPanel();
        // ESGF nodes
        List<String> nodes = (List<String>) prefs.getBean("nodes", null);

        // Create the combo box, select item at index in predertermined ESGF
        // node.
        nodeList = new JComboBox(nodes.toArray());
        if (searchManager != null) {

            String url = searchManager.getIndexNode().toString();

            // select item of list that corresponds with URL of search manager
            // but without "http://"
            nodeList.setSelectedItem(url.split("http://")[1]);
        }
        nodeList.setEditable(true);

        nodeList.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                // Set url base search to actual selected node
                final String urlBase = (String) nodeList.getSelectedItem();

                try {
                    // SwingWorker worker = new SwingWorker<Void, Boolean>() {
                    //
                    // @Override
                    // protected Void doInBackground() throws IOException,
                    // HTTPStatusCodeException {
                    // publish(true);
                    // ESGFSearchPanel.this.searchManager
                    // .setIndexNode(urlBase);
                    // update();
                    // publish(false);
                    // return null;
                    // }
                    //
                    // @Override
                    // protected void process(java.util.List<Boolean> chunks) {
                    // progressDialog.setVisible(chunks.get(0));
                    // System.out.println(chunks.get(0));
                    // };
                    //
                    // };
                    // worker.execute();

                    ESGFSearchPanel.this.searchManager.setIndexNode(urlBase);
                    update();

                } catch (IOException e) {
                    logger.warn("Failed to connect to the selected node {}.",
                            urlBase);
                    errorNodeDialog.setVisible(true);
                } catch (HTTPStatusCodeException e) {
                    logger.warn("Failed to connect to the selected node {}.",
                            urlBase);
                    errorNodeDialog.setVisible(true);
                } catch (IllegalArgumentException e) {
                    logger.warn("Failed to connect to the selected node {}.",
                            urlBase);
                    errorNodeDialog.setVisible(true);
                }

            }
        });

        nodePanel.add(nodeList);
        // end nodes panel
        // ------------------------------------------------------

        // Sub right panels
        // Query panel----------------------------------------------------------
        queryPanel = new JPanel(new BorderLayout());

        // Query textbox label
        JLabel queryTextBoxLabel = new JLabel("Free text: ");

        // Query text box non editable at first
        // TODO regular expression restriction
        queryTextBox = new JTextField();
        queryTextBox.setEditable(false);

        // Search button - Begins dataset search and put query keyword
        final JButton search = new JButton("Edit");

        ActionListener queryTextListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                // If action in text box or button pressed is in state "Save"
                if (arg0.getSource() == queryTextBox
                        || search.getText().equals("Save")) {

                    String query = queryTextBox.getText();

                    // If query text box isn't empty
                    if (query.equals("") != true) {
                        // set query keyword
                        try {
                            ESGFSearchPanel.this.searchManager.setQuery(query);
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (HTTPStatusCodeException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    } else {
                        // If query text box is empty
                        try {
                            ESGFSearchPanel.this.searchManager.setQuery(null);
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (HTTPStatusCodeException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }

                    // Set query text box non editable and green
                    queryTextBox.setEditable(false);
                    queryTextBox.setBackground(Color.LIGHT_GRAY);

                    // Change name of button to Save
                    search.setText("Edit");

                    // Update
                    update();

                } else {
                    // Set query text box editable
                    queryTextBox.setEditable(true);
                    queryTextBox.setBackground(Color.WHITE);

                    // Change name of button to Save
                    search.setText("Save");
                }
            }
        };

        // Set listener in button and text box
        search.addActionListener(queryTextListener);
        queryTextBox.addActionListener(queryTextListener);

        // Add text box and button to query panel
        queryPanel.add(queryTextBoxLabel, BorderLayout.WEST);
        queryPanel.add(queryTextBox, BorderLayout.CENTER);
        queryPanel.add(search, BorderLayout.EAST);

        // End Query Panel------------------------------------------------------

        // Selection of search panel--------------------------------------------
        // Create the combo box, select item at index in predetermined ESGF
        // node.
        try {
            // empty search response, must have predetermined search of
            // searchManager (clone)
            newSearchResponse = new SearchResponse("<< New search >>",
                    (RESTfulSearch) searchManager.getSearch().clone(),
                    searchManager.getExecutor(), searchManager.getCache());
        } catch (CloneNotSupportedException e2) {
            // this must not happen
            e2.printStackTrace();
        }

        List<SearchResponse> searchResponse = searchManager
                .getSearchResponses();

        searchComboModel = new DefaultComboBoxModel();
        searchCombo = new JComboBox(searchComboModel);

        // shows decode string
        searchCombo.setRenderer(new ComboBoxRenderer());
        searchCombo.setBackground(Color.WHITE);
        currentSearch = newSearchResponse;

        // combo listener
        searchCombo.addItemListener(new ItemListener() {

            @Override
            public void itemStateChanged(ItemEvent e) {
                if (ItemEvent.SELECTED == e.getStateChange()) {
                    if (!e.getItem().equals("no invoice selected")) {

                        SearchResponse selectedSearch = (SearchResponse) searchCombo
                                .getSelectedItem();

                        if (selectedSearch != currentSearch) {
                            try {

                                // put in current search saved SearchResponse
                                currentSearch = selectedSearch;

                                // Set search petition in SearchManager but put
                                // index node in current index node
                                ESGFSearchPanel.this.searchManager
                                        .setSearch((RESTfulSearch) currentSearch
                                                .getSearch().clone());
                            } catch (CloneNotSupportedException e1) {
                                errorNodeDialog.setVisible(true);
                            } catch (IOException e1) {
                                errorNodeDialog.setVisible(true);
                            } catch (HTTPStatusCodeException e1) {
                                errorNodeDialog.setVisible(true);
                            }
                            update();
                        }
                    }
                }
            }

        });

        // End selection of search panel----------------------------------------

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(nodePanel, BorderLayout.WEST);
        northPanel.add(queryPanel, BorderLayout.CENTER);
        northPanel.add(searchCombo, BorderLayout.EAST);

        // END NORTH PANEL------------------------------------------------------

        // ---------------------------------------------------------------------
        // Parameters panel----------------(CENTER)-----------------------------
        // ---------------------------------------------------------------------
        parametersPanel = new JPanel(new BorderLayout());

        // Search Category tree panel------------------------------------------
        // Create new Panel that contains the tree
        facetTreeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
        categoryTree = new SearchCategoryTree(facetTreeModel);

        // Listener for selectedAndDeselect in map
        categoryTree.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals("SelectedFacetValue")) {
                    SearchCategoryFacet facet = SearchCategoryFacet
                            .valueOf(((Facet) evt.getNewValue()).getName());
                    String value = ((Facet) evt.getNewValue()).getValue();

                    // Check if this facet-value is in
                    // currentTreeCategoryFacetsToRemove
                    if (searchCategoryFacetsToRemove.containsKey(facet)) {
                        List<String> values = searchCategoryFacetsToRemove
                                .get(facet);
                        values.remove(value); // remove this value
                    }

                    // Add new facet-value
                    // Check if this facet has some value
                    if (searchCategoryFacetsToAdd.containsKey(facet)) {
                        List<String> values = searchCategoryFacetsToAdd
                                .get(facet);
                        values.add(value);
                    } else {
                        List<String> values = new LinkedList<String>();
                        values.add(value);
                        searchCategoryFacetsToAdd.put(facet, values);
                    }

                } else if (evt.getPropertyName().equals("DeselectedFacetValue")) {
                    SearchCategoryFacet facet = SearchCategoryFacet
                            .valueOf(((Facet) evt.getNewValue()).getName());
                    String value = ((Facet) evt.getNewValue()).getValue();

                    // Check if this facet-value is in
                    // currentTreeCategoryFacetsToAdd
                    if (searchCategoryFacetsToAdd.containsKey(facet)) {
                        List<String> values = searchCategoryFacetsToAdd
                                .get(facet);
                        values.remove(value); // remove this value
                    }

                    // Add new facet-value
                    // Check if this facet has some value
                    if (searchCategoryFacetsToRemove.containsKey(facet)) {
                        List<String> values = searchCategoryFacetsToRemove
                                .get(facet);
                        values.add(value);
                    } else {
                        List<String> values = new LinkedList<String>();
                        values.add(value);
                        searchCategoryFacetsToRemove.put(facet, values);
                    }
                }
            }
        });

        JPanel facetTreePanel = new JPanel(new BorderLayout());
        JButton addTreeParametersButton = new JButton("Add Parameters");
        addTreeParametersButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                updateFacets();
                update();
            }
        });
        treeFiltersPanel = new JPanel(new BorderLayout());
        treeFiltersPanel
                .add(new JScrollPane(categoryTree), BorderLayout.CENTER);

        facetTreePanel.setBackground(categoryTree.getBackground());
        facetTreePanel.add(addTreeParametersButton, BorderLayout.SOUTH);
        facetTreePanel.add(treeFiltersPanel, BorderLayout.CENTER);
        // end Search Category tree panel--------------------------------------

        // Parameters combo box panel------------------------------------------
        Set<Parameter> availableParameters = new HashSet<Parameter>();

        for (Parameter parameter : Parameter.values()) {
            if (parameter != Parameter.QUERY && parameter != Parameter.LATEST
                    && parameter != Parameter.REPLICA
                    && parameter != Parameter.SHARDS
                    && parameter != Parameter.LIMIT
                    && parameter != Parameter.OFFSET
                    && parameter != Parameter.FIELDS
                    && parameter != Parameter.FACETS
                    && parameter != Parameter.CHECKSUM
                    && parameter != Parameter.CHECKSUM_TYPE
                    && parameter != Parameter.DATASET_ID
                    && parameter != Parameter.END && parameter != Parameter.TO
                    && parameter != Parameter.DISTRIB
                    && parameter != Parameter.FORMAT
                    && parameter != Parameter.TYPE
                    && parameter != Parameter.TRACKING_ID
                    && parameter != Parameter.XLINK) {
                availableParameters.add(parameter);
            }
        }

        Parameter[] sortParametersArray = availableParameters
                .toArray(new Parameter[availableParameters.size()]);

        Arrays.sort(sortParametersArray);

        // components of combo panel
        parameterComboBox = new JComboBox(sortParametersArray);
        comboPanel = new JPanel(new BorderLayout());

        introComboPanel = new JPanel(new GridLayout());

        // Listener of parameter combo box
        parameterComboBox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {

                Parameter parameter = (Parameter) parameterComboBox
                        .getSelectedItem();

                introComboPanel.setVisible(true);
                loadIntroComboPanel(parameter);
            }
        });

        comboPanel.add(parameterComboBox, BorderLayout.NORTH);
        comboPanel.add(introComboPanel, BorderLayout.CENTER);

        // ---------------------------------------------------------------------

        JSplitPane confParameterSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, facetTreePanel, comboPanel);

        // set split resize separator
        confParameterSplit.setResizeWeight(0.20);
        confParameterSplit.setOneTouchExpandable(true);
        confParameterSplit.setContinuousLayout(true);

        parametersPanel.add(confParameterSplit, BorderLayout.CENTER);
        // End Parameters
        // Panel------------------------------------------------------

        // ---------------------------------------------------------------------
        // Configuration Viewer Panel ------------------------------------------
        // ---------------------------------------------------------------------
        configurationViewerPanel = new JPanel(new BorderLayout());

        // Filters panel--------------------------------------------------------
        filterListModel = new DefaultListModel();
        filterList = new JList(filterListModel);
        filterList.setCellRenderer(new FilterRenderer());

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        final JButton remove = new JButton("Remove");
        final JButton removeAll = new JButton("Remove all");
        buttonPanel.add(remove);
        buttonPanel.add(removeAll);

        // buttons listener
        ActionListener buttonListListener = new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                // If actual buttons is remove
                if (e.getSource() == remove) {

                    // if any element is selected
                    if (!filterList.isSelectionEmpty()) {
                        // remove facet
                        try {
                            List<ParameterValue> list = new LinkedList<ParameterValue>();
                            list.add((ParameterValue) filterList
                                    .getSelectedValue());

                            ESGFSearchPanel.this.searchManager
                                    .removeParameterValues(list);

                        } catch (IOException e1) {
                            // TODO Auto-generated catch block
                            e1.printStackTrace();
                        } catch (HTTPStatusCodeException e1) {
                            // TODO Auto-generated catch block
                            e1.printStackTrace();
                        }

                        // update tree
                        update();

                    }
                } else {
                    // else remove all

                    // if list isn't empty
                    if (!filterListModel.isEmpty()) {

                        List<ParameterValue> list = new LinkedList<ParameterValue>();

                        // For each element in list model
                        for (int i = 0; i < filterListModel.getSize(); i++) {
                            list.add((ParameterValue) filterListModel.get(i));
                        }

                        // Remove
                        try {

                            ESGFSearchPanel.this.searchManager
                                    .removeParameterValues(list);
                        } catch (IOException e1) {
                            // TODO Auto-generated catch block
                            e1.printStackTrace();
                        } catch (HTTPStatusCodeException e1) {
                            // TODO Auto-generated catch block
                            e1.printStackTrace();
                        }

                        try {
                            ESGFSearchPanel.this.searchManager
                                    .resetConfiguration();
                        } catch (IOException e1) {
                            // TODO Auto-generated catch block
                            e1.printStackTrace();
                        } catch (HTTPStatusCodeException e1) {
                            // TODO Auto-generated catch block
                            e1.printStackTrace();
                        }

                        // Update tree
                        update();
                    }
                }
            }

        };
        remove.addActionListener(buttonListListener);
        removeAll.addActionListener(buttonListListener);

        filtersPanel = new JPanel(new BorderLayout());
        filtersPanel.add(new JLabel("Configured parameters:"),
                BorderLayout.NORTH);
        filtersPanel.add(new JScrollPane(filterList), BorderLayout.CENTER);
        filtersPanel.add(buttonPanel, BorderLayout.SOUTH);
        // End Filters panel ---------------------------------------------------

        // Number of datasets and start harvesting panel-----------------------

        numOfRecords = new JTextField();
        JButton saveButton = new JButton("Save Search");
        saveButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                // current search is new search do the same of Save As button
                if (currentSearch == newSearchResponse) {
                    ESGFSearchPanel.this.nameOfSearchDialog.setVisible(true);
                } else {

                    // for put yes, no, cancel and title of JOptionPane always
                    // in English
                    UIManager.put("OptionPane.yesButtonText", "Yes");
                    UIManager.put("OptionPane.noButtonText", "No");
                    UIManager.put("OptionPane.cancelButtonText", "Cancel");
                    UIManager.put("OptionPane.titleText", "Select an option");

                    int confirmed = JOptionPane
                            .showConfirmDialog(
                                    ESGFSearchPanel.this,
                                    "Are you sure you want overwrite it? All data harvested associated with the search will be deleted.");

                    if (JOptionPane.OK_OPTION == confirmed) {
                        currentSearch.reset();
                        currentSearch
                                .setSearch(ESGFSearchPanel.this.searchManager
                                        .getSearch());
                    }
                }
            }
        });

        JButton saveAsButton = new JButton("Save Search As...");
        saveAsButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                ESGFSearchPanel.this.nameOfSearchDialog.setVisible(true);
            }
        });

        JPanel verticalPanel = new JPanel();
        verticalPanel.setLayout(new GridLayout(3, 1));
        JPanel auxPanel1 = new JPanel(new FlowLayout());
        auxPanel1.add(saveAsButton);
        JPanel auxPanel2 = new JPanel(new FlowLayout());
        auxPanel2.add(saveButton);

        verticalPanel.add(numOfRecords);
        verticalPanel.add(auxPanel1);
        verticalPanel.add(auxPanel2);

        JPanel currentSearchPanel = new JPanel(new BorderLayout());
        currentSearchPanel.add(filtersPanel, BorderLayout.CENTER);

        JSplitPane confViewerSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, currentSearchPanel, verticalPanel);

        // set split resize separator
        confViewerSplit.setResizeWeight(1);
        confViewerSplit.setOneTouchExpandable(true);
        confViewerSplit.setContinuousLayout(true);

        // end Page panel-------------------------------------------------------
        // ---------------------------------------------------------------------
        configurationViewerPanel.add(confViewerSplit, BorderLayout.CENTER);

        // END Configuration Viewer Panel --------------------------------------

        // Add left and right panels in main panel
        centerPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                parametersPanel, configurationViewerPanel);

        // set split resize separator
        centerPanel.setResizeWeight(0.75);
        centerPanel.setOneTouchExpandable(true);
        centerPanel.setContinuousLayout(true);

        // END CENTER PANEL________________________________________________
        add(northPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        // add(leftPanel, BorderLayout.WEST);
        // add(rightPanel, BorderLayout.CENTER);

        // update configuration
        if (searchManager != null) {
            update();
        }
        // END MAIN-------------------------------------------------------------

        logger.trace("[OUT] ESGFSearchPanel");
    }

    /**
     * Private method, update UI (tree, filters, searchResponses)
     */
    private void update() {
        numOfRecords.setText("Number of records: "
                + searchManager.getNumberOfRecords());

        // update filters
        parameterComboBox.setSelectedIndex(-1);
        updateSearchResponses();
        updateFilters();
        updateTree();
        logger.trace("[OUT] update");
    }

    public void updateSearchResponses() {

        List<SearchResponse> searchList = searchManager.getSearchResponses();

        searchComboModel.removeAllElements();

        // the informational item
        String noInvoice = "no invoice selected";
        searchComboModel.setSelectedItem(noInvoice);

        // boolean for check if currentSearch has been removed
        boolean currentRemoved = true;

        searchComboModel.addElement(newSearchResponse);
        if (searchList.size() > 0) {
            for (SearchResponse search : searchList) {
                searchComboModel.addElement(search);

                // If search is the current search
                // then currentSearch hasn't been removed
                if (search.equals(currentSearch)) {
                    currentRemoved = false;
                }
            }
        }

        // If current search has been removed then put
        // Combo box index to 0
        if (currentRemoved) {
            searchComboModel.setSelectedItem(newSearchResponse);
        } else { // else put index to currentSearch
            searchComboModel.setSelectedItem(currentSearch);
        }
    }

    private void updateTree() {
        // Fill search category tree with new values.
        categoryTree.fillTree(searchManager.getFacetMap());
    }

    /**
     * Update filter list
     */
    private void updateFilters() {
        logger.trace("[IN]  updateFilters");
        filterListModel.clear();

        List<ParameterValue> paramValueList = searchManager
                .getListOfParameterValues();

        logger.debug("Filling list of configured parameters");
        int index = 0; // index of listModel
        for (int i = 0; i < paramValueList.size(); i++) {
            ParameterValue paramValue = paramValueList.get(i);

            // Only add in filter list if is a parameter displayable
            if (isParameterDisplayable(paramValue.getParameter())) {
                filterListModel.add(index, paramValue);
                index++;
            }
        }

        logger.trace("[OUT] updateFilters");

    }

    private boolean isParameterDisplayable(Parameter param) {
        logger.trace("[IN]  isParameterDisplayable");
        boolean displayable = false;

        // if parameter isn't limit, offset, distrob, fields, facets, type or
        // format
        if (param != Parameter.LIMIT && param != Parameter.OFFSET
                && param != Parameter.DISTRIB && param != Parameter.FIELDS
                && param != Parameter.FACETS && param != Parameter.TYPE
                && param != Parameter.FORMAT) {
            displayable = true;
        }
        logger.trace("[OUT] isParameterDisplayable");
        return displayable;
    }

    private void generateStringTreeTip() {

        String filtersStr = "";
        /**
         * for (Map.Entry<SearchCategoryFacet, List<String>> element :
         * currentTreeFilters .entrySet()) { filtersStr = filtersStr + "
         * <p>
         * " + element.getKey() + ": ("; if (element.getValue() != null) { for
         * (String value : element.getValue()) { filtersStr = filtersStr + value
         * + ", "; } } filtersStr = filtersStr + ")
         * </p>
         * "; } filtersStr = filtersStr;
         * 
         * currentFilters.setText(filtersStr);
         */
    }

    /**
     * Load panel of intro values of parametersComboBox
     * 
     * @param parameter
     */
    private void loadIntroComboPanel(Parameter parameter) {

        introComboPanel.removeAll();
        searchCategoryComboPanel = new JPanel(new GridLayout(3, 1));

        // Aux variables
        JButton addParameter = new JButton("Add parameter");
        JPanel auxFlowPanel = new JPanel(new FlowLayout());

        try {

            switch (parameter) {

                case ACCESS:

                    // Combo box panel
                    JPanel comboPanel = new JPanel(new FlowLayout());
                    final JComboBox services = new JComboBox(Service.values());
                    JButton addService = new JButton("Add service");
                    comboPanel.add(services);
                    comboPanel.add(addService);

                    // Other components
                    final JTextArea infoServices;
                    final List<Service> accessList;

                    if (searchManager.getSearch().getParameters().getAccess() == null) {
                        accessList = new LinkedList<Service>();
                        infoServices = new JTextArea();
                    } else {
                        accessList = searchManager.getSearch().getParameters()
                                .getAccess();
                        infoServices = new JTextArea("Values: "
                                + accessList.toString());
                    }

                    infoServices.setEditable(false);
                    infoServices.setLineWrap(true);
                    infoServices.setBackground(comboPanel.getBackground());

                    JPanel servicePanel = new JPanel(new BorderLayout());
                    servicePanel.add(comboPanel, BorderLayout.NORTH);
                    servicePanel.add(infoServices, BorderLayout.CENTER);
                    servicePanel.add(addParameter, BorderLayout.SOUTH);

                    introComboPanel.add(servicePanel);
                    // Listeners----------------------------------------------
                    // Add service button
                    addService.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {

                            Service service = (Service) services
                                    .getSelectedItem();
                            if (service != null
                                    && !accessList.contains(service)) {
                                accessList.add(service);
                                infoServices.setText("Values: "
                                        + accessList.toString());
                                updateUI();
                            }
                        }
                    });

                    // Add parameter button
                    addParameter.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {
                            if (accessList.size() != 0) {
                                try {
                                    searchManager.setAccess(accessList);
                                    introComboPanel.setVisible(false);
                                    update();
                                    updateUI();
                                } catch (IOException e) {
                                    infoServices
                                            .setText("IOException, values: "
                                                    + accessList);
                                } catch (HTTPStatusCodeException e) {
                                    infoServices
                                            .setText("HTTPStatusCodeException, values: "
                                                    + accessList);
                                }
                            } else {
                                infoServices.setText("Incorrect values: "
                                        + accessList);
                            }
                        }
                    });

                break;
                case BBOX:

                    // Degrees panel
                    JPanel degreesPanel = new JPanel(new GridLayout(5, 2));
                    JLabel north = new JLabel("North:");
                    JLabel south = new JLabel("South:");
                    JLabel east = new JLabel("East:");
                    JLabel west = new JLabel("West:");
                    final JTextField northText = new JTextField("0.0");
                    final JTextField southText = new JTextField("0.0");
                    final JTextField eastText = new JTextField("0.0");
                    final JTextField westText = new JTextField("0.0");

                    degreesPanel.add(new JPanel());
                    degreesPanel.add(new JPanel());
                    degreesPanel.add(north);
                    degreesPanel.add(northText);
                    degreesPanel.add(south);
                    degreesPanel.add(southText);
                    degreesPanel.add(east);
                    degreesPanel.add(eastText);
                    degreesPanel.add(west);
                    degreesPanel.add(westText);

                    // Other components
                    final JLabel infoBBox = new JLabel(
                            "The box is defined by west, south, east, north coordinates of longitude & latitude in a EPSG:4326 decimal degrees");
                    final float[] bbox = new float[4];

                    JPanel bboxPanel = new JPanel(new BorderLayout());
                    JPanel auxButtonPanel = new JPanel(new FlowLayout());
                    auxButtonPanel.add(addParameter);
                    bboxPanel.add(infoBBox, BorderLayout.NORTH);
                    bboxPanel.add(degreesPanel, BorderLayout.WEST);
                    bboxPanel.add(auxButtonPanel, BorderLayout.SOUTH);

                    auxFlowPanel.add(bboxPanel);
                    introComboPanel.add(auxFlowPanel);

                    // Listeners----------------------------------------------
                    // Add parameter button
                    addParameter.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {
                            String northStr = northText.getText();
                            String southStr = southText.getText();
                            String eastStr = eastText.getText();
                            String westStr = westText.getText();

                            if (northStr != null & southStr != null
                                    & eastStr != null & westStr != null) {
                                try {

                                    bbox[0] = Float.parseFloat(westStr);
                                    bbox[1] = Float.parseFloat(southStr);
                                    bbox[2] = Float.parseFloat(eastStr);
                                    bbox[3] = Float.parseFloat(northStr);

                                    searchManager.setBbox(bbox);
                                    introComboPanel.setVisible(false);
                                    update();
                                    updateUI();
                                } catch (IOException e) {
                                    infoBBox.setText("IOException, values: "
                                            + northStr + southStr + eastStr
                                            + southStr + westStr);
                                } catch (HTTPStatusCodeException e) {
                                    infoBBox.setText("HTTPStatusCodeException, values: "
                                            + northStr
                                            + southStr
                                            + eastStr
                                            + southStr + westStr);
                                } catch (NumberFormatException e) {
                                    infoBBox.setText("Incorrect values: "
                                            + northStr + southStr + eastStr
                                            + southStr + westStr);
                                }

                            } else {
                                infoBBox.setText("Incorrect values: "
                                        + northStr + southStr + eastStr
                                        + southStr + westStr);
                            }

                        }
                    });

                break;
                case CF_STANDARD_NAME:
                    loadSearchCategoryIntroComboPanel(
                            SearchCategoryFacet.CF_STANDARD_NAME,
                            getParamValues(Parameter.CF_STANDARD_NAME));
                break;
                case CMOR_TABLE:
                    loadSearchCategoryIntroComboPanel(
                            SearchCategoryFacet.CMOR_TABLE,
                            getParamValues(Parameter.CMOR_TABLE));
                break;
                case DATA_NODE:
                    // data node panel
                    JPanel dataNodeIntroPanel = new JPanel(new GridLayout(1, 2));
                    final JTextField dataNodeField = new JTextField();
                    JButton addDataNode = new JButton("Add dataNode");
                    dataNodeIntroPanel.add(dataNodeField);
                    dataNodeIntroPanel.add(addDataNode);

                    final JTextArea infoDataNode;
                    final List<String> dataNodeList;

                    if (searchManager.getSearch().getParameters().getDataNode() == null) {
                        dataNodeList = new LinkedList<String>();
                        infoDataNode = new JTextArea();
                    } else {
                        dataNodeList = searchManager.getSearch()
                                .getParameters().getDataNode();
                        infoDataNode = new JTextArea("Values: "
                                + dataNodeList.toString());
                    }

                    infoDataNode.setEditable(false);
                    infoDataNode.setLineWrap(true);
                    infoDataNode.setBackground(dataNodeIntroPanel
                            .getBackground());

                    JPanel dataNodePanel = new JPanel(new BorderLayout());
                    dataNodePanel.add(dataNodeIntroPanel, BorderLayout.NORTH);
                    dataNodePanel.add(infoDataNode, BorderLayout.CENTER);
                    dataNodePanel.add(addParameter, BorderLayout.SOUTH);
                    introComboPanel.add(dataNodePanel);

                    // Listeners----------------------------------------------
                    // Add service button
                    addDataNode.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {

                            String text = dataNodeField.getText();

                            if (text != null && !dataNodeList.contains(text)) {
                                dataNodeList.add(text);
                                infoDataNode.setText("Values: "
                                        + dataNodeList.toString());
                                updateUI();
                            }
                        }
                    });

                    // Add parameter button
                    addParameter.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {
                            if (dataNodeList.size() != 0) {
                                try {
                                    searchManager.setDataNodes(dataNodeList);
                                    introComboPanel.setVisible(false);
                                    update();
                                    updateUI();
                                } catch (IOException e) {
                                    infoDataNode
                                            .setText("IOException, values: "
                                                    + dataNodeList);
                                } catch (HTTPStatusCodeException e) {
                                    infoDataNode
                                            .setText("HTTPStatusCodeException, values: "
                                                    + dataNodeList);
                                }
                            } else {
                                infoDataNode.setText("Incorrect values: "
                                        + dataNodeList);
                            }

                        }
                    });
                break;
                case ENSEMBLE:
                    loadSearchCategoryIntroComboPanel(
                            SearchCategoryFacet.ENSEMBLE,
                            getParamValues(Parameter.ENSEMBLE));
                break;
                case EXPERIMENT:
                    loadSearchCategoryIntroComboPanel(
                            SearchCategoryFacet.EXPERIMENT,
                            getParamValues(Parameter.EXPERIMENT));
                break;
                case EXPERIMENT_FAMILY:
                    loadSearchCategoryIntroComboPanel(
                            SearchCategoryFacet.EXPERIMENT_FAMILY,
                            getParamValues(Parameter.EXPERIMENT_FAMILY));
                break;
                case FROM:

                    JPanel fromDatePanel = new JPanel(new BorderLayout());

                    final JCalendar from = new JCalendar();
                    from.setToolTipText("Lower limit of the last timestamp");
                    from.setBorder(BorderFactory.createTitledBorder("From"));
                    final JCalendar to = new JCalendar();
                    to.setToolTipText("Upper limit of the last timestamp");
                    to.setBorder(BorderFactory.createTitledBorder("To"));
                    JButton addFromTo = new JButton("Add timestamp parameter");

                    final JLabel infoCalendar = new JLabel(
                            " Lower and upper limit of the last timestamp  update (From-to)");

                    fromDatePanel.add(infoCalendar, BorderLayout.NORTH);
                    JPanel dataAuxPanel = new JPanel(new GridLayout(1, 2));
                    dataAuxPanel.add(from);
                    dataAuxPanel.add(to);
                    auxFlowPanel.add(dataAuxPanel);
                    fromDatePanel.add(auxFlowPanel, BorderLayout.CENTER);
                    fromDatePanel.add(addFromTo, BorderLayout.SOUTH);

                    introComboPanel.add(fromDatePanel);

                    final Calendar calendarFrom = Calendar.getInstance();
                    calendarFrom.setTime(from.getDate());
                    final Calendar calendarTo = Calendar.getInstance();
                    calendarTo.setTime(to.getDate());

                    // listeners
                    PropertyChangeListener fromListener = new PropertyChangeListener() {

                        @Override
                        public void propertyChange(PropertyChangeEvent e) {
                            if ("day".equals(e.getPropertyName())) {
                                calendarFrom.setTime(from.getDate());
                            } else if ("month".equals(e.getPropertyName())) {
                                calendarFrom.setTime(from.getDate());
                            } else if ("year".equals(e.getPropertyName())) {
                                calendarFrom.setTime(from.getDate());
                            }
                        }
                    };

                    PropertyChangeListener toListener = new PropertyChangeListener() {

                        @Override
                        public void propertyChange(PropertyChangeEvent e) {
                            if ("day".equals(e.getPropertyName())) {
                                calendarTo.setTime(to.getDate());
                            } else if ("month".equals(e.getPropertyName())) {
                                calendarTo.setTime(to.getDate());
                            } else if ("year".equals(e.getPropertyName())) {
                                calendarTo.setTime(to.getDate());
                            }
                        }
                    };

                    addFromTo.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {
                            try {
                                searchManager.setFrom(calendarFrom);
                                searchManager.setTo(calendarTo);
                                introComboPanel.setVisible(false);
                                update();
                                updateUI();
                            } catch (IOException e) {
                                infoCalendar.setText("IOException, values: "
                                        + from.toString() + to.toString());
                            } catch (HTTPStatusCodeException e) {
                                infoCalendar
                                        .setText("HTTPStatusCodeException, values: "
                                                + from.toString()
                                                + to.toString());
                            } catch (NumberFormatException e) {
                                infoCalendar.setText("Incorrect values: "
                                        + from.toString() + to.toString());
                            }

                        }
                    });

                    from.getDayChooser()
                            .addPropertyChangeListener(fromListener);
                    from.getMonthChooser().addPropertyChangeListener(
                            fromListener);
                    from.getYearChooser().addPropertyChangeListener(
                            fromListener);

                    to.getDayChooser().addPropertyChangeListener(toListener);
                    to.getMonthChooser().addPropertyChangeListener(toListener);
                    to.getYearChooser().addPropertyChangeListener(toListener);

                break;
                case ID:
                    // id panel
                    JPanel idIntroPanel = new JPanel(new GridLayout(1, 2));
                    final JTextField idField = new JTextField();
                    JButton addId = new JButton("Add id");
                    idIntroPanel.add(idField);
                    idIntroPanel.add(addId);

                    final JTextArea infoId;
                    final List<String> idList;

                    if (searchManager.getSearch().getParameters().getId() == null) {
                        idList = new LinkedList<String>();
                        infoId = new JTextArea();
                    } else {
                        idList = searchManager.getSearch().getParameters()
                                .getId();
                        infoId = new JTextArea("Values: " + idList.toString());
                    }

                    infoId.setEditable(false);
                    infoId.setLineWrap(true);
                    infoId.setBackground(idIntroPanel.getBackground());

                    JPanel idPanel = new JPanel(new BorderLayout());
                    idPanel.add(idIntroPanel, BorderLayout.NORTH);
                    idPanel.add(infoId, BorderLayout.CENTER);
                    idPanel.add(addParameter, BorderLayout.SOUTH);
                    introComboPanel.add(idPanel);

                    // Listeners----------------------------------------------
                    // Add service button
                    addId.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {

                            String text = idField.getText();

                            if (text != null && !idList.contains(text)) {
                                idList.add(text);
                                infoId.setText("Values: " + idList.toString());
                                updateUI();
                            }
                        }
                    });

                    // Add parameter button
                    addParameter.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {
                            if (idList.size() != 0) {
                                try {
                                    searchManager.setIds(idList);
                                    introComboPanel.setVisible(false);
                                    update();
                                    updateUI();
                                } catch (IOException e) {
                                    infoId.setText("IOException, values: "
                                            + idList);
                                } catch (HTTPStatusCodeException e) {
                                    infoId.setText("HTTPStatusCodeException, values: "
                                            + idList);
                                }
                            } else {
                                infoId.setText("Incorrect values: " + idList);
                            }

                        }
                    });
                break;
                case INDEX_NODE:
                    // index node panel
                    JPanel indexNodeIntroPanel = new JPanel(
                            new GridLayout(1, 2));
                    final JTextField indexNodeField = new JTextField();
                    JButton addIndexNode = new JButton("Add indexNode");
                    indexNodeIntroPanel.add(indexNodeField);
                    indexNodeIntroPanel.add(addIndexNode);

                    final JTextArea infoIndexNode;
                    final List<String> indexNodeList;

                    if (searchManager.getSearch().getParameters()
                            .getIndexNode() == null) {
                        indexNodeList = new LinkedList<String>();
                        infoIndexNode = new JTextArea();
                    } else {
                        indexNodeList = searchManager.getSearch()
                                .getParameters().getIndexNode();
                        infoIndexNode = new JTextArea("Values: "
                                + indexNodeList.toString());
                    }

                    infoIndexNode.setEditable(false);
                    infoIndexNode.setLineWrap(true);
                    infoIndexNode.setBackground(indexNodeIntroPanel
                            .getBackground());

                    JPanel indexNodePanel = new JPanel(new BorderLayout());
                    indexNodePanel.add(indexNodeIntroPanel, BorderLayout.NORTH);
                    indexNodePanel.add(infoIndexNode, BorderLayout.CENTER);
                    indexNodePanel.add(addParameter, BorderLayout.SOUTH);

                    introComboPanel.add(indexNodePanel);

                    // Listeners----------------------------------------------
                    // Add service button
                    addIndexNode.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {

                            String text = indexNodeField.getText();

                            if (text != null && !indexNodeList.contains(text)) {
                                indexNodeList.add(text);
                                infoIndexNode.setText("Values: "
                                        + indexNodeList.toString());
                                updateUI();
                            }
                        }
                    });

                    // Add parameter button
                    addParameter.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {
                            if (indexNodeList.size() != 0) {
                                try {
                                    searchManager.setIndexNodes(indexNodeList);
                                    introComboPanel.setVisible(false);
                                    update();
                                    updateUI();
                                } catch (IOException e) {
                                    infoIndexNode
                                            .setText("IOException, values: "
                                                    + indexNodeList);
                                } catch (HTTPStatusCodeException e) {
                                    infoIndexNode
                                            .setText("HTTPStatusCodeException, values: "
                                                    + indexNodeList);
                                }
                            } else {
                                infoIndexNode.setText("Incorrect values: "
                                        + indexNodeList);
                            }

                        }
                    });
                break;
                case INSTANCE_ID:
                    // instanceId panel
                    JPanel instanceIdIntroPanel = new JPanel(new GridLayout(1,
                            2));
                    final JTextField instanceIdField = new JTextField();
                    JButton addInstanceId = new JButton("Add instanceId");
                    instanceIdIntroPanel.add(instanceIdField);
                    instanceIdIntroPanel.add(addInstanceId);

                    final JTextArea infoInstanceId;
                    final List<String> instanceIdList;

                    if (searchManager.getSearch().getParameters()
                            .getInstanceId() == null) {
                        instanceIdList = new LinkedList<String>();
                        infoInstanceId = new JTextArea();
                    } else {
                        instanceIdList = searchManager.getSearch()
                                .getParameters().getInstanceId();
                        infoInstanceId = new JTextArea("Values: "
                                + instanceIdList.toString());
                    }

                    infoInstanceId.setEditable(false);
                    infoInstanceId.setLineWrap(true);
                    infoInstanceId.setBackground(instanceIdIntroPanel
                            .getBackground());

                    JPanel instanceIdPanel = new JPanel(new BorderLayout());
                    instanceIdPanel.add(instanceIdIntroPanel,
                            BorderLayout.NORTH);
                    instanceIdPanel.add(infoInstanceId, BorderLayout.CENTER);
                    instanceIdPanel.add(addParameter, BorderLayout.SOUTH);
                    introComboPanel.add(instanceIdPanel);

                    // Listeners----------------------------------------------
                    // Add service button
                    addInstanceId.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {

                            String text = instanceIdField.getText();

                            if (text != null && !instanceIdList.contains(text)) {
                                instanceIdList.add(text);
                                infoInstanceId.setText("Values: "
                                        + instanceIdList.toString());
                                updateUI();
                            }
                        }
                    });

                    // Add parameter button
                    addParameter.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {
                            if (instanceIdList.size() != 0) {
                                try {
                                    searchManager
                                            .setInstanceIds(instanceIdList);
                                    introComboPanel.setVisible(false);
                                    update();
                                    updateUI();
                                } catch (IOException e) {
                                    infoInstanceId
                                            .setText("IOException, values: "
                                                    + instanceIdList);
                                } catch (HTTPStatusCodeException e) {
                                    infoInstanceId
                                            .setText("HTTPStatusCodeException, values: "
                                                    + instanceIdList);
                                }
                            } else {
                                infoInstanceId.setText("Incorrect values: "
                                        + instanceIdList);
                            }

                        }
                    });
                break;
                case INSTITUTE:
                    loadSearchCategoryIntroComboPanel(
                            SearchCategoryFacet.INSTITUTE,
                            getParamValues(Parameter.INSTITUTE));
                break;
                case MASTER_ID:
                    // masterId panel
                    JPanel masterIdIntroPanel = new JPanel(new GridLayout(1, 2));
                    final JTextField masterIdField = new JTextField();
                    JButton addMasterId = new JButton("Add masterId");
                    masterIdIntroPanel.add(masterIdField);
                    masterIdIntroPanel.add(addMasterId);

                    final JTextArea infoMasterId;
                    final List<String> masterIdList;

                    if (searchManager.getSearch().getParameters().getMasterId() == null) {
                        masterIdList = new LinkedList<String>();
                        infoMasterId = new JTextArea();
                    } else {
                        masterIdList = searchManager.getSearch()
                                .getParameters().getMasterId();
                        infoMasterId = new JTextArea("Values: "
                                + masterIdList.toString());
                    }

                    infoMasterId.setEditable(false);
                    infoMasterId.setLineWrap(true);
                    infoMasterId.setBackground(masterIdIntroPanel
                            .getBackground());

                    JPanel masterIdPanel = new JPanel(new BorderLayout());
                    masterIdPanel.add(masterIdIntroPanel, BorderLayout.NORTH);
                    masterIdPanel.add(infoMasterId, BorderLayout.CENTER);
                    masterIdPanel.add(addParameter, BorderLayout.SOUTH);
                    introComboPanel.add(masterIdPanel);

                    // Listeners----------------------------------------------
                    // Add service button
                    addMasterId.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {

                            String text = masterIdField.getText();

                            if (text != null && !masterIdList.contains(text)) {
                                masterIdList.add(text);
                                infoMasterId.setText("Values: "
                                        + masterIdList.toString());
                                updateUI();
                            }
                        }
                    });

                    // Add parameter button
                    addParameter.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {
                            if (masterIdList.size() != 0) {
                                try {
                                    searchManager.setMasterIds(masterIdList);
                                    introComboPanel.setVisible(false);
                                    update();
                                    updateUI();
                                } catch (IOException e) {
                                    infoMasterId
                                            .setText("IOException, values: "
                                                    + masterIdList);
                                } catch (HTTPStatusCodeException e) {
                                    infoMasterId
                                            .setText("HTTPStatusCodeException, values: "
                                                    + masterIdList);
                                }
                            } else {
                                infoMasterId.setText("Incorrect values: "
                                        + masterIdList);
                            }

                        }
                    });
                break;
                case MODEL:
                    loadSearchCategoryIntroComboPanel(
                            SearchCategoryFacet.MODEL,
                            getParamValues(Parameter.MODEL));
                break;
                case PRODUCT:
                    loadSearchCategoryIntroComboPanel(
                            SearchCategoryFacet.PRODUCT,
                            getParamValues(Parameter.PRODUCT));
                break;
                case PROJECT:
                    loadSearchCategoryIntroComboPanel(
                            SearchCategoryFacet.PROJECT,
                            getParamValues(Parameter.PROJECT));
                break;
                case REALM:
                    loadSearchCategoryIntroComboPanel(
                            SearchCategoryFacet.REALM,
                            getParamValues(Parameter.REALM));
                break;
                case SOURCE_ID:
                    loadSearchCategoryIntroComboPanel(
                            SearchCategoryFacet.SOURCE_ID,
                            getParamValues(Parameter.SOURCE_ID));
                break;
                case START:
                    JPanel startDatePanel = new JPanel(new BorderLayout());

                    final JCalendar start = new JCalendar();
                    start.setToolTipText("start date range coverage");
                    start.setBorder(BorderFactory.createTitledBorder("start"));
                    final JCalendar end = new JCalendar();
                    end.setToolTipText("end date range coverage");
                    end.setBorder(BorderFactory.createTitledBorder("end"));
                    JButton addStartDate = new JButton(
                            "Add temporal range parameter");

                    final JLabel infoStart = new JLabel(
                            "Select temporal range of data coverage (Start-End)");

                    startDatePanel.add(infoStart, BorderLayout.NORTH);
                    JPanel startAuxPanel = new JPanel(new GridLayout(1, 2));
                    startAuxPanel.add(start);
                    startAuxPanel.add(end);
                    auxFlowPanel.add(startAuxPanel);
                    startDatePanel.add(auxFlowPanel, BorderLayout.CENTER);
                    startDatePanel.add(addStartDate, BorderLayout.SOUTH);

                    introComboPanel.add(startDatePanel);

                    final Calendar calendarStart = Calendar.getInstance();
                    calendarStart.setTime(start.getDate());
                    final Calendar calendarEnd = Calendar.getInstance();
                    calendarEnd.setTime(end.getDate());

                    // listeners
                    PropertyChangeListener startListener = new PropertyChangeListener() {

                        @Override
                        public void propertyChange(PropertyChangeEvent e) {
                            if ("day".equals(e.getPropertyName())) {
                                calendarStart.setTime(start.getDate());
                            } else if ("month".equals(e.getPropertyName())) {
                                calendarStart.setTime(start.getDate());
                            } else if ("year".equals(e.getPropertyName())) {
                                calendarStart.setTime(start.getDate());
                            }
                        }
                    };

                    PropertyChangeListener endListener = new PropertyChangeListener() {

                        @Override
                        public void propertyChange(PropertyChangeEvent e) {
                            if ("day".equals(e.getPropertyName())) {
                                calendarEnd.setTime(end.getDate());
                            } else if ("month".equals(e.getPropertyName())) {
                                calendarEnd.setTime(end.getDate());
                            } else if ("year".equals(e.getPropertyName())) {
                                calendarEnd.setTime(end.getDate());
                            }
                        }
                    };

                    addStartDate.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {
                            try {
                                searchManager.setStart(calendarStart);
                                searchManager.setEnd(calendarEnd);
                                introComboPanel.setVisible(false);
                                update();
                                updateUI();
                            } catch (IOException e) {
                                infoStart.setText("IOException, values: "
                                        + start.toString() + end.toString());
                            } catch (HTTPStatusCodeException e) {
                                infoStart
                                        .setText("HTTPStatusCodeException, values: "
                                                + start.toString()
                                                + end.toString());
                            } catch (NumberFormatException e) {
                                infoStart.setText("Incorrect values: "
                                        + start.toString() + end.toString());
                            }

                        }
                    });

                    start.getDayChooser().addPropertyChangeListener(
                            startListener);
                    start.getMonthChooser().addPropertyChangeListener(
                            startListener);
                    start.getYearChooser().addPropertyChangeListener(
                            startListener);

                    end.getDayChooser().addPropertyChangeListener(endListener);
                    end.getMonthChooser()
                            .addPropertyChangeListener(endListener);
                    end.getYearChooser().addPropertyChangeListener(endListener);
                break;
                case TIME_FREQUENCY:
                    loadSearchCategoryIntroComboPanel(
                            SearchCategoryFacet.TIME_FREQUENCY,
                            getParamValues(Parameter.TIME_FREQUENCY));
                break;
                case VARIABLE:
                    loadSearchCategoryIntroComboPanel(
                            SearchCategoryFacet.VARIABLE,
                            getParamValues(Parameter.VARIABLE));
                break;
                case VARIABLE_LONG_NAME:
                    loadSearchCategoryIntroComboPanel(
                            SearchCategoryFacet.VARIABLE_LONG_NAME,
                            getParamValues(Parameter.VARIABLE_LONG_NAME));
                break;
                case VERSION:
                    // version panel
                    JPanel versionIntroPanel = new JPanel(new GridLayout(1, 2));
                    final JTextField versionField = new JTextField();
                    JButton addVersion = new JButton("Add version");
                    versionIntroPanel.add(versionField);
                    versionIntroPanel.add(addVersion);

                    final JTextArea infoVersion;
                    final List<String> versionList;

                    if (searchManager.getSearch().getParameters().getVersion() == null) {
                        versionList = new LinkedList<String>();
                        infoVersion = new JTextArea();
                    } else {
                        versionList = searchManager.getSearch().getParameters()
                                .getVersion();
                        infoVersion = new JTextArea("Values: "
                                + versionList.toString());
                    }

                    infoVersion.setEditable(false);
                    infoVersion.setLineWrap(true);
                    infoVersion
                            .setBackground(versionIntroPanel.getBackground());

                    JPanel versionPanel = new JPanel(new BorderLayout());
                    versionPanel.add(versionIntroPanel, BorderLayout.NORTH);
                    versionPanel.add(infoVersion, BorderLayout.CENTER);
                    versionPanel.add(addParameter, BorderLayout.SOUTH);
                    introComboPanel.add(versionPanel);

                    // Listeners----------------------------------------------
                    // Add service button
                    addVersion.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {

                            String text = versionField.getText();

                            if (text != null && !versionList.contains(text)) {
                                versionList.add(text);
                                infoVersion.setText("Values: "
                                        + versionList.toString());
                                updateUI();
                            }
                        }
                    });

                    // Add parameter button
                    addParameter.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent arg0) {
                            if (versionList.size() != 0) {
                                try {
                                    searchManager.setVersions(versionList);
                                    introComboPanel.setVisible(false);
                                    update();
                                    updateUI();
                                } catch (IOException e) {
                                    infoVersion.setText("IOException, values: "
                                            + versionList);
                                } catch (HTTPStatusCodeException e) {
                                    infoVersion
                                            .setText("HTTPStatusCodeException, values: "
                                                    + versionList);
                                }
                            } else {
                                infoVersion.setText("Incorrect values: "
                                        + versionList);
                            }

                        }
                    });
                break;
                default:
                break;

            }

        } catch (Exception e) {

        }
        updateUI();
    }

    private List<String> getParamValues(Parameter parameter) {
        logger.trace("[IN]  getParamValues");

        // Values of ParameterValue search categorys are always List<String>
        List<ParameterValue> totalParametersAndValues = searchManager
                .getListOfParameterValues();

        List<String> parameterValues = new LinkedList<String>();

        for (ParameterValue paramValue : totalParametersAndValues) {
            if (paramValue.getParameter() == parameter) {
                parameterValues = (List<String>) paramValue.getValue();
            }
        }

        logger.trace("[OUT] getParamValues");
        return parameterValues;
    }

    private void loadSearchCategoryIntroComboPanel(
            final SearchCategoryFacet category,
            final List<String> parameterValues) {

        searchCategoryComboPanel.removeAll();

        Map<SearchCategoryFacet, List<SearchCategoryValue>> facetMap = searchManager
                .getFacetMap();
        final JButton addParameter = new JButton("Add parameter");
        JPanel auxFlowPanel = new JPanel(new FlowLayout());

        // category values box panel
        JPanel categoryComboPanel = new JPanel(new FlowLayout());
        final JComboBox categoryValuesComboBox = new JComboBox(facetMap.get(
                category).toArray());
        categoryValuesComboBox.setPreferredSize(new Dimension(400, 30));

        JButton addCategory = new JButton("Add " + category.toString());
        categoryComboPanel.add(categoryValuesComboBox);
        categoryComboPanel.add(addCategory);

        // Other components

        final JLabel infoCategory = new JLabel();
        if (parameterValues.size() > 0) {
            infoCategory.setText(parameterValues.toString());
        }

        final List<String> categoryList = new LinkedList<String>();

        searchCategoryComboPanel.add(categoryComboPanel);
        searchCategoryComboPanel.add(infoCategory);
        auxFlowPanel.add(addParameter);
        searchCategoryComboPanel.add(auxFlowPanel);
        introComboPanel.add(searchCategoryComboPanel);

        addParameter.setVisible(false);

        // Listeners----------------------------------------------
        // Add service button
        addCategory.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {

                String value = ((SearchCategoryValue) categoryValuesComboBox
                        .getSelectedItem()).getValue();
                if (value != null && !categoryList.contains(value)
                        && !parameterValues.contains(value)) {

                    categoryList.add(value);

                    String strValues = "[";

                    if (parameterValues.size() > 0) {

                        // configured values
                        for (String categoryConfStr : parameterValues) {

                            strValues = strValues + "," + categoryConfStr;
                        }

                        // add new possible values
                        for (String categoryStr : categoryList) {
                            strValues = strValues + "," + categoryStr;
                        }
                        strValues = strValues + "]"; // add ]
                    } else {
                        strValues = categoryList.toString();
                    }

                    infoCategory.setText(strValues);
                    addParameter.setVisible(true);
                    updateUI();
                }
            }
        });

        // Add parameter button
        addParameter.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                if (categoryList.size() != 0) {

                    List<String> totalValues = searchCategoryFacetsToAdd
                            .get(category);
                    if (totalValues == null) {
                        searchCategoryFacetsToAdd.put(category, categoryList);
                    } else {
                        totalValues.addAll(categoryList);
                    }

                    updateFacets();

                    introComboPanel.setVisible(false);
                    update();
                    updateUI();

                } else {
                    infoCategory.setText("Incorrect values: " + categoryList);
                }
            }
        });
        updateUI();

    }

    void save() {
        // save configuration
        // prefs.putInt("splitPos", split.getDividerLocation());
    }

    private void updateFacets() {
        try {
            ESGFSearchPanel.this.searchManager.updateSearchCategoryFacetValues(
                    searchCategoryFacetsToAdd, searchCategoryFacetsToRemove);
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (HTTPStatusCodeException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        // reset aux maps
        ESGFSearchPanel.this.searchCategoryFacetsToAdd = new HashMap<SearchCategoryFacet, List<String>>();
        ESGFSearchPanel.this.searchCategoryFacetsToRemove = new HashMap<SearchCategoryFacet, List<String>>();

    }

    /**
     * Cell rendered for filters list.
     * 
     */
    private class FilterRenderer extends JLabel implements ListCellRenderer {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        /**
         * Constructor
         */
        public FilterRenderer() {
            setOpaque(true);
        }

        /**
         * Return a JLabel with text facet : valueFacet
         * 
         * @param list
         *            List of elements
         * @param value
         *            of element list
         * @param index
         *            element index in the list
         * @param isSelected
         *            if this element is selected
         * @param cellHasFocus
         *            if the is focus or not
         * @return
         */

        @Override
        public Component getListCellRendererComponent(JList list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {

            ParameterValue paramValue = (ParameterValue) value;

            if (paramValue.getValue() instanceof Calendar) {
                // Format date to human view
                SimpleDateFormat dateFormat = new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss'Z'");
                Calendar calendar = (Calendar) paramValue.getValue();
                String stringDate = dateFormat.format(calendar.getTime());
                setText(paramValue.getParameter().name().toLowerCase() + ":"
                        + stringDate);
            } else {
                setText(value.toString());
            }

            // Set background and foreground of list element
            // according is selected or not
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }

            return this;
        }
    }

    private class ComboBoxRenderer extends JLabel implements ListCellRenderer {
        public ComboBoxRenderer() {
            setOpaque(true);
            setHorizontalAlignment(LEFT);
            setVerticalAlignment(CENTER);
        }

        /*
         * This method finds the image and text corresponding to the selected
         * value and returns the label, set up to display the text and image.
         */
        @Override
        public Component getListCellRendererComponent(JList list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            try {
                setText(URLDecoder.decode(((SearchResponse) value).getName(),
                        ENCODE_FORMAT));
            } catch (UnsupportedEncodingException e) {
                setText(((SearchResponse) value).getName());
            }

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }

            return this;
        }
    }

}
