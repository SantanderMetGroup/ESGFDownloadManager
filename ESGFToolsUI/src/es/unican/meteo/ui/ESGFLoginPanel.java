package es.unican.meteo.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import ucar.util.prefs.PreferencesExt;
import es.unican.meteo.esgf.download.DownloadManager;
import es.unican.meteo.esgf.petition.CredentialsManager;

public class ESGFLoginPanel extends JPanel {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /**
     * Logger
     */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(ESGFLoginPanel.class);
    /** Preferences of configuration. */
    private PreferencesExt prefs;

    /** Download Manager. Manage download of datasets */
    private DownloadManager downloadManager;
    /** Credential Manager. Manage user credentials. */
    private CredentialsManager credentialsManager;

    /** Main Panel */
    private JPanel mainPanel;

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
    public ESGFLoginPanel(PreferencesExt prefs,
            DownloadManager downloadManager,
            CredentialsManager credentialsManager) {
        super();
        logger.trace("[IN]  ESGFMetadataHESGFLoginPanel");

        // Request manager an download manager are shared in all ESGF tabs
        this.downloadManager = downloadManager;
        this.credentialsManager = credentialsManager;

        this.prefs = prefs;
        this.setLayout(new FlowLayout());

        // ---------------------------------------------------------------------
        // OpenId, pass
        // ---------------------------------------------------------------------
        // success info
        infoSucces = new JLabel();
        // remain time info
        infoRemainTime = new JLabel();

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

        introPanel.add(infoRemainTime);
        introPanel.add(new JLabel("         "));// empty label
        introPanel.add(aux1);
        introPanel.add(aux2);
        introPanel.add(infoSucces);

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
                    updateUI();
                } else {
                    openUrlField.setColumns(15);
                    openUrlField.setText("");
                    updateUI();
                }
            }
        });

        // ---------------------------------------------------------------------
        // Main panel
        // ---------------------------------------------------------------------

        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(introPanel, BorderLayout.CENTER);
        JPanel aux3 = new JPanel(new FlowLayout());
        aux3.add(saveLogin);
        mainPanel.add(aux3, BorderLayout.SOUTH);

        // ---------------------------------------------------------------------
        // ---------------------------------------------------------------------
        // add main Panel
        add(mainPanel);

        update();
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
                    .setText("<HTML><BR><FONT COLOR=\"red\"> Not logged.</FONT></HTML>");
        }
        logger.trace("[OUT] update");
    }

    void save() {
        // save configuration
        // prefs.putInt("splitPos", split.getDividerLocation());
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
            } catch (IOException e) {
                error = true;
            }
            saveLogin.setEnabled(true);

            if (error) {
                infoSucces
                        .setText("<html><FONT COLOR=\"red\"> OpenId or password "
                                + "aren't valid</FONT></html>");
            } else {
                infoSucces
                        .setText("<html><FONT COLOR=\"blue\">Success</FONT></html>");
                try {
                    downloadManager.putToDownloadUnauthorizedFiles();
                } catch (IOException e) {
                    logger.warn("Put to download files that was"
                            + " unauthorized fails because info of files hasn't been"
                            + " found in system");
                }
            }
        }

    }

}
