package es.unican.meteo.esgf.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
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
        super((JFrame) panel.getTopLevelAncestor(), true);
        logger.trace("[IN]  ESGFMetadataHESGFLoginPanel");

        // Request manager an download manager are shared in all ESGF tabs
        this.downloadManager = downloadManager;
        this.credentialsManager = credentialsManager;
        this.panel = panel;

        // ---------------------------------------------------------------------
        // OpenId, pass
        // ---------------------------------------------------------------------

        // Intro panel
        JPanel introPanel = new JPanel(new GridLayout(5, 1));
        // openid url
        JLabel openIdUser = new JLabel("OpenID url:");
        final JTextField openUrlField = new JTextField(15);
        // index nodes
        List<String> nodes = (List<String>) prefs.getBean("nodes", null);
        final JComboBox nodeList = new JComboBox(nodes.toArray());
        nodeList.addItem("<< Another IdP node >>");
        // password
        JLabel passLabel = new JLabel("Password:");
        final JPasswordField passField = new JPasswordField(18);

        JPanel aux1 = new JPanel(new FlowLayout());
        aux1.add(openIdUser);
        aux1.add(nodeList);
        aux1.add(openUrlField);

        JPanel aux2 = new JPanel(new FlowLayout());
        aux2.add(passLabel);
        aux2.add(passField);

        introPanel.add(aux1);
        introPanel.add(aux2);

        saveLogin = new JButton("  Login  ");

        // LISTENERS_______________________________________________________
        saveLogin.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                String openIdUser = openUrlField.getText().trim();
                char[] password = passField.getPassword();
                boolean error = false;
                if (openIdUser != null && password != null
                        && !openIdUser.equals("") && password.length > 0) {

                    if (!nodeList.getSelectedItem().equals(
                            "<< Another IdP node >>")) {
                        String completeOpenID = "https://"
                                + nodeList.getSelectedItem()
                                + "/esgf-idp/openid/" + openIdUser;
                        LoginThread loginThread = new LoginThread(
                                completeOpenID, password);
                        loginThread.start();
                    } else {
                        LoginThread loginThread = new LoginThread(openIdUser,
                                password);
                        loginThread.start();
                    }

                }

            }
        });

        nodeList.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                if (nodeList.getSelectedItem().equals("<< Another IdP node >>")) {
                    openUrlField.setColumns(35);
                    openUrlField
                            .setText("https://[IdPNodeName]/esgf-idp/openid/[userName]");
                    repaint();
                } else {
                    openUrlField.setColumns(15);
                    openUrlField.setText("");
                    repaint();
                }
            }
        });

        JButton cancel = new JButton("  Cancel  ");
        cancel.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                // releases this dialog, close this dialog
                dispose();
            }
        });

        // ---------------------------------------------------------------------
        // Main panel
        // ---------------------------------------------------------------------
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(introPanel, BorderLayout.CENTER);
        JPanel aux = new JPanel(new FlowLayout());
        aux.add(saveLogin);
        aux.add(cancel);
        mainPanel.add(aux, BorderLayout.SOUTH);

        // ---------------------------------------------------------------------
        // ---------------------------------------------------------------------
        // add main Panel
        add(mainPanel);

        setLocationRelativeTo(panel.getParent());
        pack();
        logger.trace("[OUT] ESGFLoginPanel");
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
                panel.setLogSuccess(false);
            } else {
                panel.setLogSuccess(true);
                try {
                    downloadManager.putToDownloadUnauthorizedFiles();
                } catch (IOException e) {
                    logger.warn("Put to download files that was"
                            + " unauthorized fails because info of files hasn't been"
                            + " found in system");
                }
            }
            dispose();
        }

    }

}
