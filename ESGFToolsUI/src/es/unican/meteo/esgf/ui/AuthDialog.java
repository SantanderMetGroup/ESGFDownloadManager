package es.unican.meteo.esgf.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import ucar.util.prefs.PreferencesExt;
import es.unican.meteo.esgf.download.DownloadManager;
import es.unican.meteo.esgf.petition.CredentialsManager;

public class AuthDialog extends JDialog {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /**
     * Logger
     */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(AuthDialog.class);
    /** Preferences of configuration. */
    private PreferencesExt prefs;

    /** Download Manager. Manage download of datasets */
    private DownloadManager downloadManager;
    /** Credential Manager. Manage user credentials. */
    private CredentialsManager credentialsManager;

    /** Main Panel */
    private JPanel mainPanel;

    /** ESGF main panel. */
    private ESGFMainPanel panel;

    /** Save button. */
    private JButton saveLogin;

    private JLabel infoSucces;

    private JLabel infoRemainTime;

    /**
     * Constructor
     * 
     * @param prefs
     *            preferences
     */
    public AuthDialog(PreferencesExt prefs, ESGFMainPanel panel,
            DownloadManager downloadManager,
            CredentialsManager credentialsManager) {

        // Call super class(JDialog) and set parent frame and modal true
        // for lock other panels
        super((JFrame) panel.getTopLevelAncestor(), "ESGF Login", true);
        logger.trace("[IN]  ESGFMetadataHESGFLoginPanel");

        this.panel = panel;

        // Request manager an download manager are shared in all ESGF tabs
        this.downloadManager = downloadManager;
        this.credentialsManager = credentialsManager;

        this.prefs = prefs;
        this.setLayout(new FlowLayout());

        // ---------------------------------------------------------------------
        // Intro panel
        // ---------------------------------------------------------------------

        final JPanel introPanel = new JPanel(new GridBagLayout());

        // ----------
        // Components
        // ----------

        // providers
        JLabel providerLabel = new JLabel("Id Provider:");
        List<String> nodes = (List<String>) prefs.getBean("nodes", null);
        final JComboBox nodeListCombo = new JComboBox(nodes.toArray());
        nodeListCombo.addItem("<< Custom OpenID URL >>");

        // user & password
        String userStr = "https://" + nodeListCombo.getSelectedItem()
                + "/esgf-idp/openid/";
        final JLabel userLabel = new JLabel(userStr);
        final JTextField userField = new JTextField(20);
        JLabel passLabel = new JLabel("password:");
        final JPasswordField passField = new JPasswordField(20);
        // ---------------------------------------------

        // ---------------------
        // Build the intro panel
        // ---------------------
        GridBagConstraints constraints = new GridBagConstraints();

        // providers
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 1; // reset width
        constraints.anchor = GridBagConstraints.EAST;
        constraints.fill = GridBagConstraints.NONE;
        introPanel.add(providerLabel, constraints);
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.CENTER;// reset
        constraints.fill = GridBagConstraints.HORIZONTAL;// reset
        introPanel.add(nodeListCombo, constraints);

        // user & password
        constraints.gridx = 0;
        constraints.gridy = 1;
        introPanel.add(userLabel, constraints);
        constraints.gridx = 1;
        constraints.gridy = 1;
        introPanel.add(userField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.fill = GridBagConstraints.NONE;
        introPanel.add(passLabel, constraints);
        constraints.gridx = 1;
        constraints.gridy = 2;
        constraints.anchor = GridBagConstraints.CENTER;// reset
        constraints.fill = GridBagConstraints.HORIZONTAL;// reset
        introPanel.add(passField, constraints);
        // end build introPanel-----------------------------

        // --------------------------------------------------
        // Main panel----------------------------------------
        // --------------------------------------------------

        // -------------------
        // Other Components---
        // -------------------

        infoRemainTime = new JLabel(" ");
        infoSucces = new JLabel(" ");

        // login
        saveLogin = new JButton("  Login  ");

        // Ok
        JButton exitButton = new JButton("  Exit  ");
        exitButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                // releases this dialog, close this dialog
                dispose();
            }
        });

        // LISTENERS
        saveLogin.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                String user = userField.getText().trim();
                char[] password = passField.getPassword();
                boolean error = false;
                if (user != null && password != null && !user.equals("")
                        && password.length > 0) {

                    if (!nodeListCombo.getSelectedItem().equals(
                            "<< Custom OpenID URL >>")) {
                        String completeOpenID = "https://"
                                + nodeListCombo.getSelectedItem()
                                + "/esgf-idp/openid/" + user;
                        LoginThread loginThread = new LoginThread(
                                completeOpenID, password);
                        loginThread.start();
                    } else {
                        LoginThread loginThread = new LoginThread(user,
                                password);
                        loginThread.start();
                    }

                }

            }
        });

        nodeListCombo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                if (nodeListCombo.getSelectedItem().equals(
                        "<< Custom OpenID URL >>")) {
                    userField.setColumns(35);
                    userField
                            .setText("https://[IdPNodeName]/esgf-idp/openid/[userName]");

                    // change position in intro panel
                    GridBagConstraints constraints = new GridBagConstraints();
                    constraints.fill = GridBagConstraints.HORIZONTAL;
                    constraints.gridx = 0;
                    constraints.gridy = 1;
                    constraints.gridwidth = 2;
                    introPanel.add(userField, constraints);

                    validate();
                    repaint();
                } else {
                    String userStr = "https://"
                            + nodeListCombo.getSelectedItem()
                            + "/esgf-idp/openid/";
                    userLabel.setText(userStr);
                    userField.setColumns(20);
                    userField.setText("");

                    // change position in intro panel
                    GridBagConstraints constraints = new GridBagConstraints();
                    constraints.fill = GridBagConstraints.HORIZONTAL;
                    constraints.gridx = 0;
                    constraints.gridy = 1;
                    constraints.gridwidth = 1;
                    introPanel.add(userLabel, constraints);
                    constraints.gridx = 1;
                    constraints.gridy = 1;
                    introPanel.add(userField, constraints);

                    validate();
                    repaint();
                }
            }
        });

        // -----------------
        // Build main panel
        // -----------------

        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(introPanel, BorderLayout.CENTER);
        JPanel aux = new JPanel(new GridLayout(2, 1));
        JPanel aux2 = new JPanel(new FlowLayout());
        aux2.add(saveLogin);
        aux.add(aux2);
        aux.add(infoSucces);
        mainPanel.add(aux, BorderLayout.SOUTH);
        // end main panel-------------------------------------------

        // -----------------
        // Build Dialog
        // -----------------
        setLayout(new BorderLayout());
        add(infoRemainTime, BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
        add(exitButton, BorderLayout.SOUTH);

        update();

        int x = ((int) panel.getLocation().getX() + panel.getWidth()) / 2;
        int y = ((int) panel.getLocation().getY() + panel.getHeight()) / 2;

        setLocation(x, y);
        pack();
        logger.trace("[OUT] ESGFLoginPanel");
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        update();
    }

    private void update() {
        logger.trace("[IN]  update");
        if (credentialsManager.hasInitiated()) {
            long millis;
            try {
                millis = credentialsManager
                        .getRemainTimeOfCredentialsInMillis();

                int seconds = (int) (millis / 1000) % 60;
                int minutes = (int) ((millis / (1000 * 60)) % 60);
                int hours = (int) ((millis / (1000 * 60 * 60)) % 24);
                int days = (int) ((millis / (1000 * 60 * 60 * 24)));

                if (days > 0) {
                    infoRemainTime
                            .setText("<HTML><BR><FONT COLOR=\"green\"> You are logged.</FONT>"
                                    + "<BR>Remaining time of validity of credentials: "
                                    + "days:"
                                    + days
                                    + ",  "
                                    + hours
                                    + ":"
                                    + minutes + ":" + seconds + "<BR></HTML>");
                } else {
                    infoRemainTime
                            .setText("<HTML><BR><FONT COLOR=\"green\"> You are logged.</FONT>"
                                    + "<BR>Remaining time of validity of credentials: "
                                    + hours
                                    + ":"
                                    + minutes
                                    + ":"
                                    + seconds
                                    + "<BR></HTML>");
                }
            } catch (IOException e) {
                // do nothing
            }
        } else {
            infoRemainTime
                    .setText("<HTML><BR><FONT COLOR=\"red\"> Not logged.</FONT><BR> <BR></HTML>");
        }
        logger.trace("[OUT] update");
    }

    /**
     * Private thread class that do login.
     */
    private class LoginThread extends Thread {

        private String openId;
        private char[] pass;

        public LoginThread(String openId, char[] pass) {
            this.openId = openId;
            this.pass = pass;
        }

        @Override
        public void run() {
            boolean error = false;
            try {
                saveLogin.setEnabled(false);
                credentialsManager.initialize(openId, pass);
            } catch (Exception e) {
                error = true;
            }
            saveLogin.setEnabled(true);

            if (error) {
                infoSucces
                        .setText("<html><FONT COLOR=\"red\"> OpenId or password "
                                + "aren't valid</FONT></html>");
                panel.setLogSuccess(false);
            } else {
                infoSucces
                        .setText("<html><FONT COLOR=\"blue\">Success</FONT></html>");
                panel.setLogSuccess(true);
                try {
                    if (downloadManager != null) {
                        downloadManager.putToDownloadUnauthorizedFiles();
                    }
                } catch (IOException e) {
                    logger.warn("Put to download files that was"
                            + " unauthorized fails because info of files hasn't been"
                            + " found in system");
                }
            }

            update();
        }

    }

}
