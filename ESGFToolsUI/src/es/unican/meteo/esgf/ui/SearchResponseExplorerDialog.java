package es.unican.meteo.esgf.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.text.html.HTMLDocument;

import ucar.util.prefs.PreferencesExt;
import es.unican.meteo.esgf.search.Dataset;
import es.unican.meteo.esgf.search.DatasetFile;
import es.unican.meteo.esgf.search.HarvestStatus;
import es.unican.meteo.esgf.search.Metadata;
import es.unican.meteo.esgf.search.SearchResponse;

public class SearchResponseExplorerDialog extends JFrame {

    /**
     * Logger
     */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(SearchResponseExplorerDialog.class);

    /**
     * SerialVersionUID
     */
    private static final long serialVersionUID = -6544810788283927158L;

    private static final int maxNumberOfDatasetsByPage = 5;

    /** Preferences of configuration. */
    private PreferencesExt prefs;

    /** Right data view content panel */
    private JScrollPane dataViewContentPanel;

    /** Container of selected. */
    private JPanel boxDatasets;

    private SearchResponse searchResponse;

    private JFrame parent;

    private JTextField pageTextBox;

    private JButton buttonPrev;

    private JButton buttonNext;

    private int numberOfDatasets;
    private int currentPage;

    /**
     * Constructor
     * 
     * @param prefs
     *            preferences
     */
    public SearchResponseExplorerDialog(JFrame parent, PreferencesExt prefs,
            SearchResponse searchResponse) {

        // Call super class(JDialog) and set parent frame and modal true
        // for lock other panels
        // super(parent, true);

        this.prefs = prefs;
        this.parent = parent;

        this.searchResponse = searchResponse;
        this.numberOfDatasets = searchResponse.getDatasetTotalCount();
        this.currentPage = 1;

        // Set main panel layout
        setLayout(new BorderLayout());

        // Data view panel------------------------------------------------------

        boxDatasets = new JPanel(new GridLayout(0, 1));
        dataViewContentPanel = new JScrollPane(boxDatasets);
        // Added with setViewportView for to be able shows all big list
        // dataViewContentPanel.setViewportView(boxDocuments);
        // End Data view Panel--------------------------------------------------

        // ---------------------------------------------------------------------
        // Pagination-----------------------------------------------------------
        // Label that shows current page (1/maxPage)
        pageTextBox = new JTextField("1/"
                + Integer.toString((int) Math.ceil((double) numberOfDatasets
                        / (double) maxNumberOfDatasetsByPage)));

        pageTextBox.setEditable(false);
        pageTextBox.setFocusable(false);

        // Listeners of page text box
        // Mouse Listener, controls double click in facet values
        MouseListener pageListener = new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                System.out.println("mouse");
                // Location of pageTextBox
                Point location = e.getPoint();

                // Mouse location is not null and is over page text box
                if (location != null && pageTextBox.contains(location)) {
                    pageTextBox.setEditable(true);
                    pageTextBox.setFocusable(true);
                    pageTextBox.setText("        ");
                }
            }
        };

        pageTextBox.addMouseListener(pageListener);

        pageTextBox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("text");
                String stringPage = pageTextBox.getText();

                // If query text box isn't empty
                if (stringPage.equals("") != true) {

                    // Max possible page
                    int maxPage = (int) Math.ceil((double) numberOfDatasets
                            / (double) maxNumberOfDatasetsByPage);

                    // If introduced string is a number
                    try {
                        int newPage = Integer.parseInt(stringPage.trim());

                        if (newPage <= maxPage && newPage > 0) {
                            currentPage = newPage;
                            pageTextBox.setText(Integer.toString(currentPage)
                                    + "/" + maxPage);

                            // If new page = 1 set prev button not visible
                            if (currentPage == 1) {
                                buttonPrev.setEnabled(false);

                            } else if (currentPage == maxPage) {
                                // Else if new page is the page that precedes
                                // the final page
                                buttonNext.setEnabled(false);
                            }

                            // If button next isn't enabled and new page is
                            // < maxPage
                            if (currentPage < maxPage
                                    && !buttonNext.isEnabled()) {
                                buttonNext.setEnabled(true);
                            }

                            // If button previous isn't enabled and new page is
                            // >1
                            if (currentPage > 1 && !buttonPrev.isEnabled()) {
                                buttonPrev.setEnabled(true);
                            }

                            // Set new Page documents in request petition
                            pageTextBox.setEditable(false);
                            pageTextBox.setFocusable(false);

                            // Update configuration
                            update();

                        } else {
                            // if introduced value is < than 0
                            // or > than maxPage
                            // set value of page in text box
                            pageTextBox.setEditable(false);
                            pageTextBox.setFocusable(false);
                            pageTextBox.setText(currentPage + "/" + maxPage);
                        }

                        // if introduced value is not a number
                        // set value of page in text box
                    } catch (NumberFormatException numberException) {
                        pageTextBox.setEditable(false);
                        pageTextBox.setFocusable(false);
                        pageTextBox.setText(currentPage + "/" + maxPage);
                    }
                }
            }
        });

        // Previous button
        buttonPrev = new JButton();
        buttonPrev.setIcon(new ImageIcon("images/back.gif", "Previous page"));
        buttonPrev.setEnabled(false);

        // Next button
        buttonNext = new JButton();
        buttonNext.setIcon(new ImageIcon("images/forward.gif", "Next page"));
        buttonNext.setEnabled(true);

        // Listener of previous button
        buttonPrev.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                // maxPage is higher number of page permitted
                // maxPage=ceil(num of datasets/ num documents by page)
                int maxPage = (int) Math.ceil((double) numberOfDatasets
                        / (double) maxNumberOfDatasetsByPage);

                // If page is equal to maxPage
                if (currentPage <= maxPage) {

                    if (currentPage != 1) {
                        // if current page isn't "1" actualize page and label
                        // page
                        currentPage = currentPage - 1;
                        pageTextBox.setText(Integer.toString(currentPage) + "/"
                                + maxPage);

                        // If new page = 1 set prev button not visible
                        if (currentPage == 1) {
                            buttonPrev.setEnabled(false);
                        }
                    }

                    // If new page is the page that precedes the final page
                    if (currentPage == maxPage - 1) {
                        buttonNext.setEnabled(true);
                    }

                    // Update configuration of components
                    update();
                }
            }
        });

        // Listener of next button
        buttonNext.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {

                // maxPage is higher number of page permitted
                // maxPage=ceil(num of datasets/ num documents by page)
                int maxPage = (int) Math.ceil((double) numberOfDatasets
                        / (double) maxNumberOfDatasetsByPage);

                // If num page is 1 or more
                if (currentPage >= 1) {
                    // if is'nt final page
                    if (currentPage != maxPage) {
                        // if current page isn't "1" actualize page and label
                        // page
                        currentPage = currentPage + 1;
                        pageTextBox.setText(Integer.toString(currentPage) + "/"
                                + maxPage);

                        // If new page=maxPage then set next button not visible
                        if (currentPage == maxPage) {
                            buttonNext.setEnabled(false);
                        }
                    }

                    // If new page is next to the first page
                    if (currentPage == 2) {
                        buttonPrev.setEnabled(true);
                    }

                    // Update configuration of components
                    update();
                }

            }
        });

        // __________------------------------------------------------------------------

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

        // Toolbar, not floatable
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        toolBar.add(buttonPrev);
        toolBar.addSeparator();
        toolBar.add(pageTextBox);
        toolBar.addSeparator();
        toolBar.add(buttonNext);

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        toolBar.setBackground(southPanel.getBackground());

        // add toolbar to south panel
        southPanel.add(toolBar);

        JPanel content = new JPanel(new BorderLayout());
        content.add(dataViewContentPanel, BorderLayout.CENTER);
        content.add(southPanel, BorderLayout.SOUTH);

        add(content, BorderLayout.CENTER);
        // add(close, BorderLayout.NORTH);
        update();
        pack();

        // Center dialog
        setLocationRelativeTo(parent);
        setVisible(true);
        // this.setUndecorated(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    public void update() {
        updateDocumentsContainer();
    }

    /**
     * Update documents container
     */
    private void updateDocumentsContainer() {

        // remove all datasets container in showed box container
        boxDatasets.removeAll();

        if (searchResponse.getDatasetHarvestingStatus().size() < 1) {
            boxDatasets.add(new JLabel("Still not ready."));
            JButton ok = new JButton("Accept");
            ok.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    dispose();
                }
            });
            boxDatasets.add(ok);

            return;
        }

        // predetermined font
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

        Map<String, HarvestStatus> datasetHarvestingStatus = searchResponse
                .getDatasetHarvestingStatus();
        Set<String> datasetIds = datasetHarvestingStatus.keySet();
        String[] dataIDArray = datasetIds
                .toArray(new String[datasetIds.size()]);

        numberOfDatasets = dataIDArray.length;
        int indexIni = maxNumberOfDatasetsByPage * (currentPage - 1);
        int indexFin = indexIni + (maxNumberOfDatasetsByPage - 1);

        if (indexFin >= numberOfDatasets) {
            indexFin = numberOfDatasets - 1;
        }

        // for each dataset in current searchResponse
        for (int i = indexIni; i <= indexFin; i++) {
            try {

                String instanceID = dataIDArray[i];

                // Structure for document panel
                JPanel docContainer = new JPanel(new BorderLayout());
                docContainer.setAlignmentX(LEFT_ALIGNMENT);
                docContainer.setMaximumSize(new Dimension(1000,
                        Integer.MAX_VALUE));
                docContainer
                        .setBorder(BorderFactory.createLoweredBevelBorder());

                // Sub-structure for document panel
                JPanel description = new JPanel(new FlowLayout(FlowLayout.LEFT));
                description.setBorder(BorderFactory.createEtchedBorder());

                // Fill description str: id and description
                String strIdDescription = "<html><b>Dataset: </b>" + instanceID
                        + "</html>";

                // Create component that contains description with format
                JEditorPane idAndDescription = new JEditorPane();
                idAndDescription.setContentType("text/html");
                idAndDescription.setOpaque(false);
                idAndDescription.setEditable(false);
                idAndDescription.setText(strIdDescription);
                idAndDescription.setBackground(description.getBackground());

                // Set predefined font in component
                // When JEditorPane content type is HTML, setFont do nothing
                // Only can set style of HTMLDocument
                String bodyRule = "body { font-family: " + font.getFamily()
                        + "; " + "font-size: " + font.getSize() + "pt; }";
                ((HTMLDocument) idAndDescription.getDocument()).getStyleSheet()
                        .addRule(bodyRule);

                description.add(idAndDescription);

                JPanel data = new JPanel(new GridLayout(1, 4));
                JLabel status = new JLabel();

                switch (datasetHarvestingStatus.get(instanceID)) {
                    case COMPLETED:
                        status.setText("<html><FONT COLOR=GREEN>Completed</FONT></html>");
                    break;
                    case CREATED:
                        status.setText("Not Completed");
                    break;
                    case FAILED:
                        status.setText("<html><FONT COLOR=RED>Failed</FONT></html>");
                    break;
                    case HARVESTING:
                        status.setText("<html><FONT COLOR=BLUE>Harvesting...</FONT></html>");
                    break;
                    case PAUSED:
                        status.setText("Paused");
                    break;
                }

                data.add(status);

                // If dataset has been harvested
                if (datasetHarvestingStatus.get(instanceID) == HarvestStatus.COMPLETED) {

                    // Dataset
                    final Dataset dataset = searchResponse
                            .getDataset(instanceID);

                    long size = 0;
                    if (dataset.getMetadata(Metadata.SIZE) != null) {
                        size = dataset.getMetadata(Metadata.SIZE);
                    }

                    if (size == 0) {

                        for (DatasetFile file : dataset.getFiles()) {
                            size = size
                                    + (Long) file.getMetadata(Metadata.SIZE);
                        }

                        data.add(new JLabel("size: " + bytesToString(size)));

                    } else {
                        data.add(new JLabel("size: "
                                + bytesToString((Long) dataset
                                        .getMetadata(Metadata.SIZE))));
                    }

                    // Button to view dataset
                    JButton viewDataset = new JButton("Harvesting View");

                    // open a new Dialog that shows metadata of the selected
                    // dataset
                    viewDataset.addActionListener(new ActionListener() {
                        private DatasetJsonViewDialog datasetJsonViewDialog;

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            datasetJsonViewDialog = new DatasetJsonViewDialog(
                                    dataset, parent);

                        }
                    });

                    // Button to open dataset metadata info
                    JButton metadataOption = new JButton("Open metadata");

                    // open a new Dialog that shows metadata of the selected
                    // dataset
                    metadataOption.addActionListener(new ActionListener() {

                        private MetadataDialog metadataDialog;

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            metadataDialog = new MetadataDialog(dataset, parent);
                            metadataDialog.setVisible(true);
                        }
                    });
                    data.add(viewDataset);
                    data.add(metadataOption);
                }

                // Add description and data in document container
                docContainer.add(description, BorderLayout.CENTER);
                docContainer.add(data, BorderLayout.SOUTH);

                // Finally, add in box documents current docContainer
                boxDatasets.add(docContainer);
            } catch (IllegalArgumentException e1) {
                logger.error(
                        "Dataset {} don't belongs to SearchResponse or if dataset hasn't been harvested",
                        dataIDArray[i]);
            } catch (IOException e1) {
                logger.error(
                        "Some error happens when dataset {} has been obtained from file system",
                        dataIDArray[i]);
            }
        }
    }

    /** Reset page count in page label, and next and previous buttons. */
    private void resetPageCount() {
        // set current page to 1
        currentPage = 1;

        // maxPage is higher number of page permitted
        // maxPage=ceil(num of datasets/ num documents by page)
        int maxPage = (int) Math.ceil((double) numberOfDatasets
                / (double) maxNumberOfDatasetsByPage);

        pageTextBox.setText(Integer.toString(currentPage) + "/" + maxPage);

        // If max page is 1 or 0
        if (maxPage < 2) {
            buttonPrev.setEnabled(false);
            buttonNext.setEnabled(false);
        } else {
            buttonNext.setEnabled(true);
            buttonPrev.setEnabled(false);
        }

    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        update();
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
        // TODO Auto-generated method stub

    }
}
