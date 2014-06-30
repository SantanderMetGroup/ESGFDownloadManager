package es.unican.meteo.esgf.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import es.unican.meteo.esgf.search.SearchManager;
import es.unican.meteo.esgf.search.SearchResponse;





/** Dialog to select the name of the search that will be saved. */
public class NameOfSearchDialog extends JDialog {

    private static final String ENCODE_FORMAT = "UTF-8";

    /** Parent frame. */
    private Frame parent;

    /** Main panel. */
    private JPanel mainPanel;

    /** Message Label . */
    private JLabel label;

    /** Text box for name. */
    private JTextField name;

    /** Request. */
    private SearchManager request;

    public NameOfSearchDialog(SearchManager request, Frame parent) {

        // Call super class(JDialog) and set parent frame and modal true
        // for lock other panels
        super(parent, true);
        setLayout(new FlowLayout());

        this.request = request;
        this.parent = parent;

        // Initialize main panel
        mainPanel = new JPanel(new BorderLayout());

        // name
        label = new JLabel("Enter a name for the search that will be saved.");
        name = new JTextField();

        name.addKeyListener(new KeyAdapter() {
            // @Override
            // public void keyTyped(KeyEvent e) {
            // int c = e.getKeyChar();
            //
            // System.out.println(Character.isDefined(c));
            // // If character isn't UTF8 or space or delete
            // if (!((Character.isDefined(c) || (c == KeyEvent.VK_BACK_SPACE) ||
            // (c == KeyEvent.VK_DELETE)))) {
            // getToolkit().beep();
            // e.consume();
            // }
            // }
        });

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());

        // Save search with a name
        JButton save = new JButton("Save");
        save.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String name = NameOfSearchDialog.this.name.getText();
                SearchResponse searchResponse = null;
                try {
                    try {
                        searchResponse = NameOfSearchDialog.this.request
                                .saveSearch(URLEncoder.encode(name,
                                        ENCODE_FORMAT));

                        NameOfSearchDialog.this.firePropertyChange("update",
                                null, searchResponse);
                    } catch (IllegalArgumentException e1) {
                        NameOfSearchDialog.this.firePropertyChange("update",
                                null, null);
                    } catch (CloneNotSupportedException e1) {
                        // do nothing
                    }
                } catch (UnsupportedEncodingException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
                // reset text
                NameOfSearchDialog.this.name.setText("");
                // releases this dialog, close this dialog
                dispose();
            }
        });

        // Cancel
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                // releases this dialog, close this dialog
                dispose();
            }
        });

        // add buttons to buttons panel
        buttonPanel.add(save);
        buttonPanel.add(cancel);

        mainPanel.add(label, BorderLayout.NORTH);
        mainPanel.add(name, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);

        pack();

        // Center dialog
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    }
}
