package es.unican.meteo.esgf.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import es.unican.meteo.esgf.download.DatasetDownloadStatus;

/** Dialog that shows file status info. */
public class DatasetStatusInfoDialog extends JDialog {

    /** Main panel. */
    private JPanel mainPanel;

    /** Table that contains all dataset metadata. */
    private JTable metaTable;

    public DatasetStatusInfoDialog(DatasetDownloadStatus datasetStatus,
            Frame parent) {

        // Call super class(JDialog) and set parent frame and modal true
        // for lock other panels
        super(parent, true);
        setLayout(new BorderLayout());

        // Initialize main panel
        mainPanel = new JPanel(new BorderLayout());

        // Initialize rowData -> will contain metadata table data
        String[][] rowData = new String[5][2];
        rowData[0][0] = "Instance ID";
        rowData[0][1] = datasetStatus.getInstanceID();
        rowData[1][0] = "Total size (sum of selected files size)";
        rowData[1][1] = bytesToString(datasetStatus.getTotalSize());
        rowData[2][0] = "Path";
        rowData[2][1] = datasetStatus.getPath();
        rowData[3][0] = "Selected files";
        rowData[3][1] = datasetStatus.getNumberOfFilesToDownload() + "/"
                + datasetStatus.getFilesDownloadStatus().size();
        // Model of metadata table
        DefaultTableModel tableModel = new DefaultTableModel() {
            @Override
            public Class<String> getColumnClass(int columnIndex) {
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // Fill table with with the values introduced in matrix
        // String[][] rowData previously
        tableModel.setDataVector(rowData, new String[] { "", "" });
        // Create metadata table with rowData
        metaTable = new JTable(tableModel);

        // Panel of JTable
        // / JScrollPane metaPanel = new JScrollPane(metaTable);
        // metaTable.setFillsViewportHeight(true);

        // Set prefered size of JTable
        // metaTable.setPreferredScrollableViewportSize(new Dimension(400,
        // 200));

        // Set cell renderer
        metaTable.setDefaultRenderer(String.class,
                new MultiLineTableCellRenderer());

        // Button Listener
        JButton closeButton = new JButton("OK");
        closeButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        JPanel closePanel = new JPanel(new FlowLayout());
        closePanel.add(closeButton);

        add(new JScrollPane(metaTable), BorderLayout.CENTER);
        add(closePanel, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(650, 200));
        setTitle("Dataset info");
        pack();

        // Center dialog
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

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
     * Multiline Table Cell Renderer.
     * 
     * @warning set the width of the column to the textarea in adjustRowHeight
     * 
     * @autor 
     *        http://blog.botunge.dk/post/2009/10/09/JTable-multiline-cell-renderer
     *        .aspx
     */
    private class MultiLineTableCellRenderer extends JTextArea implements
            TableCellRenderer {
        /**
         * 
         */
        private static final long serialVersionUID = 1L;
        private List<List<Integer>> rowColHeight = new ArrayList<List<Integer>>();

        public MultiLineTableCellRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean isSelected, boolean hasFocus, int row,
                int column) {
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
                setBackground(table.getBackground());
            }
            setFont(table.getFont());
            if (hasFocus) {
                setBorder(UIManager.getBorder("Table.focusCellHighlightBorder"));
                if (table.isCellEditable(row, column)) {
                    setForeground(UIManager
                            .getColor("Table.focusCellForeground"));
                    setBackground(UIManager
                            .getColor("Table.focusCellBackground"));
                }
            } else {
                setBorder(new EmptyBorder(1, 2, 1, 2));
            }
            if (value != null) {
                setText(value.toString());
            } else {
                setText("");
            }

            adjustRowHeight(table, row, column);

            return this;
        }

        /**
         * Calculate the new preferred height for a given row, and sets the
         * height on the table.
         */
        private void adjustRowHeight(JTable table, int row, int column) {
            // The trick to get this to work properly is to set the width of the
            // column to the textarea. The reason for this is that
            // getPreferredSize(), without a width tries to place all the text
            // in one line. By setting the size with the with of the column,
            // getPreferredSize() returnes the proper height which the row
            // should have in order to make room for the text.
            int cWidth = table.getTableHeader().getColumnModel()
                    .getColumn(column).getWidth();
            setSize(new Dimension(cWidth, 1000));
            int prefH = getPreferredSize().height;
            while (rowColHeight.size() <= row) {
                rowColHeight.add(new ArrayList<Integer>(column));
            }
            List<Integer> colHeights = rowColHeight.get(row);
            while (colHeights.size() <= column) {
                colHeights.add(0);
            }
            colHeights.set(column, prefH);
            int maxH = prefH;
            for (Integer colHeight : colHeights) {
                if (colHeight > maxH) {
                    maxH = colHeight;
                }
            }
            if (table.getRowHeight(row) != maxH) {
                table.setRowHeight(row, maxH);
            }
        }
    }

}
