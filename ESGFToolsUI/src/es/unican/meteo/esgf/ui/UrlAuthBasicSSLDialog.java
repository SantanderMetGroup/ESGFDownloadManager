/*
 * Copyright 1998-2009 University Corporation for Atmospheric Research/Unidata
 *
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations and
 * understandings on the user. The user is granted the right, without any fee or
 * cost, to use, copy, modify, alter, enhance and distribute this software, and
 * any derivative works thereof, and its supporting documentation for any
 * purpose whatsoever, provided that this entire notice appears in all copies of
 * the software, derivative works and supporting documentation. Further, UCAR
 * requests that the user credit UCAR/Unidata in any publications that result
 * from the use of this software or in any product that includes this software.
 * The names UCAR and/or Unidata, however, may not be used in any advertising or
 * publicity to endorse or promote any products or commercial entity unless
 * specific written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with any
 * support, consulting, training or assistance of any kind with regard to the
 * use, operation and performance of this software nor to provide the user with
 * any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL
 * DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
 * PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS
 * ACTION, ARISING OUT OF OR IN CONNECTION WITH THE ACCESS, USE OR PERFORMANCE
 * OF THIS SOFTWARE.
 */
package es.unican.meteo.esgf.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScheme;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.auth.CredentialsNotAvailableException;
import org.apache.commons.httpclient.auth.CredentialsProvider;
import org.apache.commons.httpclient.auth.RFC2617Scheme;

import ucar.nc2.ui.widget.IndependentDialog;
import ucar.nc2.util.net.HTTPAuthScheme;
import ucar.nc2.util.net.HTTPSSLProvider;
import ucar.nc2.util.net.HTTPSession;
import ucar.util.prefs.ui.Field;
import ucar.util.prefs.ui.PrefPanel;
import es.unican.meteo.esgf.petition.CredentialsManager;

/**
 * This can be used both for java.net authentication:
 * java.net.Authenticator.setDefault(new
 * thredds.ui.UrlAuthenticatorDialog(frame));
 *
 * or for org.apache.commons.httpclient authentication:
 * httpclient.getParams().setParameter( CredentialsProvider.PROVIDER, new
 * UrlAuthenticatorDialog( null));
 *
 * @author John Caron, Karem terry
 */
public class UrlAuthBasicSSLDialog extends Authenticator implements
CredentialsProvider {

    private final IndependentDialog dialog;
    private UsernamePasswordCredentials pwa = null;
    private final Field.Text serverF, realmF, userF;
    private final Field.Password passwF;
    private final boolean debug = false;

    private static final String CONFIG_FILE = "config.txt";
    private static final String KEYSTORE_FILE = "keystore_jks.ks";
    private static final String TRUSTSTORE_FILE = "esg-truststore.ts";
    private static final String KEYSTORE_PASS = "changeit";
    private static final String TRUSTSTORE_PASS = "changeit";
    private static final String ESG_HOME_ENV_VAR = "ESG_HOME";
    private static final String DEFAULT_ESG_FOLDER = ".esg";

    private String keystore;
    private String truststore;

    /** Logger. */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(UrlAuthBasicSSLDialog.class);

    /** Credential Manager. Manage user credentials. */
    private CredentialsManager credentialsManager;

    /** Dialog. */
    private JDialog esgDialog;

    /** Main Panel */
    private JPanel mainPanel;

    /** Save button. */
    private JButton saveLogin;

    private JLabel infoSucces;

    private JLabel infoRemainTime;

    private final AuthScope anyscope = new AuthScope(AuthScope.ANY);

    private JFrame parent;
    private HTTPSSLProvider credentialProvider;

    /**
     * constructor
     *
     * @param parent
     *            JFrame
     */
    public UrlAuthBasicSSLDialog(javax.swing.JFrame parent) {

        this.parent = parent;
        PrefPanel pp = new PrefPanel("UrlAuthenticatorDialog", null);
        serverF = pp.addTextField("server", "Server",
                "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww");
        realmF = pp.addTextField("realm", "Realm", "");
        serverF.setEditable(false);
        realmF.setEditable(false);

        userF = pp.addTextField("user", "User", "");
        passwF = pp.addPasswordField("password", "Password", "");
        pp.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pwa = new UsernamePasswordCredentials(userF.getText(),
                        new String(passwF.getPassword()));
                dialog.setVisible(false);
            }
        });
        // button to dismiss
        JButton cancel = new JButton("Cancel");
        pp.addButton(cancel);
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                pwa = null;
                dialog.setVisible(false);
            }
        });
        pp.finish();

        dialog = new IndependentDialog(parent, true, "HTTP Authentication", pp);
        dialog.setLocationRelativeTo(parent);
        dialog.setLocation(100, 100);
    }

    public void clear() {
    }

    /*
     * fix:public void setCredentials(AuthScope scope, Credentials cred) {
     * provider.setCredentials(scope,cred); }
     *
     * public Credentials getCredentials(AuthScope scope) { return
     * provider.getCredentials(scope); }
     */

    // java.net calls this: g
    @Override
    protected PasswordAuthentication getPasswordAuthentication() {

        if (debug) {
            System.out.println("site= " + getRequestingSite());
            System.out.println("port= " + getRequestingPort());
            System.out.println("protocol= " + getRequestingProtocol());
            System.out.println("prompt= " + getRequestingPrompt());
            System.out.println("scheme= " + getRequestingScheme());
        }

        serverF.setText(getRequestingHost() + ":" + getRequestingPort());
        realmF.setText(getRequestingPrompt());
        dialog.setVisible(true);

        if (debug && pwa != null) {
            System.out.println("user= (" + pwa.getUserName() + ")");
            System.out.println("password= (" + new String(pwa.getPassword())
            + ")");
        }

        return new PasswordAuthentication(pwa.getUserName(), pwa.getPassword()
                .toCharArray());
    }

    // http client calls this:
    @Override
    public Credentials getCredentials(AuthScheme scheme, String host, int port,
            boolean proxy) throws CredentialsNotAvailableException {

        logger.trace("[IN]  getCredentials");
        boolean SSL = false;
        if (scheme == null) {
            throw new CredentialsNotAvailableException(
                    "Null authentication scheme: ");
        }

        logger.debug("Checking AuthScheme...");
        if (!(scheme instanceof RFC2617Scheme)) {
            // if (scheme.getSchemeName().equals("schemeSSL")) {
            SSL = true;
        }

        if (SSL == false) {
            logger.debug("AuthScheme basic or digest");

            if (debug) {
                System.out.println(host + ":" + port
                        + " requires authentication with the realm '"
                        + scheme.getRealm() + "'");
            }

            serverF.setText(host + ":" + port);
            realmF.setText(scheme.getRealm());

            dialog.setVisible(true);
            if (pwa == null) {
                throw new CredentialsNotAvailableException();
            }

            if (debug) {
                System.out.println("user= (" + pwa.getUserName() + ")");
                System.out.println("password= ("
                        + new String(pwa.getPassword()) + ")");
            }

            logger.trace("[OUT] getCredentials");
            return new UsernamePasswordCredentials(pwa.getUserName(),
                    new String(pwa.getPassword()));
        } else {// SSL

            logger.debug("Auth Scheme is SSL");

            // use ESG_HOME environmental variable if exists
            Map<String, String> env = System.getenv();

            logger.debug("Generating esg paths...");
            if (env.containsKey(ESG_HOME_ENV_VAR)) {
                this.keystore = env.get(ESG_HOME_ENV_VAR) + File.separator
                        + KEYSTORE_FILE;
                this.truststore = env.get(ESG_HOME_ENV_VAR) + File.separator
                        + TRUSTSTORE_FILE;
            } else { // use default directory if not
                String homePath = System.getProperty("user.home");
                this.keystore = homePath + File.separator + DEFAULT_ESG_FOLDER
                        + File.separator + KEYSTORE_FILE;
                this.truststore = homePath + File.separator
                        + DEFAULT_ESG_FOLDER + File.separator + TRUSTSTORE_FILE;
            }

            this.credentialsManager = CredentialsManager.getInstance();
            if (!credentialsManager.hasInitiated()) {
                boolean init;
                // try initialize credential manager
                // with system files
                try {
                    credentialsManager.initialize();
                    init = true;
                } catch (Exception e1) {
                    // Initialization of credentials manager was failed
                    init = false;
                }

                // If credentials manager can't initialize
                // then show login dialog
                if (!init) {
                    createESGDialog();

                    logger.debug("Show esg login dialog");
                    esgDialog.setVisible(true);
                } else { // if credential manager is successfully initialized
                    logger.debug("Generating HTTPSSLprovider from keystore file");
                    this.credentialProvider = new HTTPSSLProvider(
                            this.keystore, KEYSTORE_PASS, this.truststore,
                            TRUSTSTORE_PASS);
                    HTTPSession.setAnyCredentialsProvider(HTTPAuthScheme.SSL,
                            null, credentialProvider);
                }
            } else {// if credentials manager is already initialized
                logger.debug("Generating HTTPSSLprovider from keystore file");
                this.credentialProvider = new HTTPSSLProvider(this.keystore,
                        KEYSTORE_PASS, this.truststore, TRUSTSTORE_PASS);
                HTTPSession.setAnyCredentialsProvider(HTTPAuthScheme.SSL, null,
                        credentialProvider);
            }

            logger.trace("[OUT] getCredentials");

            // if fails authentication
            if (credentialProvider == null) {
                return (Credentials) this;
            }

            return credentialProvider; // return null credentials

        }
        /*
         * if (null != httpSession) { AuthScope authScope = new AuthScope( host,
         * port, scheme.getRealm()); httpSession.setDefaultCredentials(
         * authScope, cred); }
         */

        // return cred;
    }

    private void createESGDialog() {
        esgDialog = new JDialog(parent, true);
        esgDialog.setVisible(false);

        esgDialog.setLayout(new BorderLayout());

        // Components
        infoSucces = new JLabel(" ");
        infoRemainTime = new JLabel(" ");
        saveLogin = new JButton("  Login  ");
        JPanel introPanel = createIntroPanel(); // OpenId and password fields

        // Ok
        JButton exitButton = new JButton("  Ok  ");
        exitButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent arg0) {
                // releases this dialog, close this dialog
                esgDialog.dispose();
            }
        });

        // -----------------
        // Build main panel
        // -----------------

        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(introPanel, BorderLayout.CENTER);
        JPanel aux = new JPanel(new GridLayout(3, 1));
        JPanel aux2 = new JPanel(new FlowLayout());
        aux2.add(saveLogin);
        aux.add(aux2);
        aux.add(infoSucces);
        aux.add(infoRemainTime);
        mainPanel.add(aux, BorderLayout.SOUTH);
        // end main panel-------------------------------------------

        // -----------------
        // Build Dialog
        // -----------------
        esgDialog.setLayout(new BorderLayout());
        esgDialog.add(mainPanel, BorderLayout.CENTER);
        esgDialog.add(exitButton, BorderLayout.SOUTH);

        update();

        int x = ((int) parent.getLocation().getX() + parent.getWidth()) / 2;
        int y = ((int) parent.getLocation().getY() + parent.getHeight()) / 2;

        esgDialog.setLocation(x, y);
        esgDialog.pack();
    }

    private JPanel createIntroPanel() {
        final JPanel introPanel = new JPanel(new GridBagLayout());

        // ----------
        // Components
        // ----------

        // providers
        JLabel providerLabel = new JLabel("Id Provider:");
        List<String> nodes = getNodesFromFile(CONFIG_FILE);
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

                    esgDialog.validate();
                    esgDialog.repaint();
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

                    esgDialog.validate();
                    esgDialog.repaint();
                }
            }
        });

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
                        login(completeOpenID, password);
                    } else {
                        login(user, password);
                    }

                }

            }
        });

        return introPanel;
    }

    private void update() {
        logger.trace("[IN]  update");
        if (credentialsManager.hasInitiated()) {
            long millis;
            try {
                millis = credentialsManager
                        .getRemainTimeOfCredentialsInMillis();

                double hours = millis / (1000.0 * 60.0 * 60.0);

                Date expireDate = credentialsManager.getX509Certificate()
                        .getNotAfter();
                infoRemainTime.setText("<HTML>Remaining time of credentials: "
                        + String.format("%.2f", hours)
                        + " hours (Expire date: " + expireDate + ")</HTML>");

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
    private void login(String openID, char[] pass) {

        boolean error = false;
        String message = "";
        try {
            saveLogin.setEnabled(false);
            credentialsManager.initialize(openID, pass);
        } catch (IOException e) {
            error = true;
            message = e.getMessage();
        }
        saveLogin.setEnabled(true);

        if (error) {
            infoSucces.setText("<html><FONT COLOR=\"red\"> Error: " + message
                    + "</FONT></html>");
            this.credentialProvider = null;
        } else {
            infoSucces
            .setText("<html><FONT COLOR=\"blue\">Success</FONT></html>");
            this.credentialProvider = new HTTPSSLProvider(this.keystore,
                    KEYSTORE_PASS, this.truststore, TRUSTSTORE_PASS);
            HTTPSession.setAnyCredentialsProvider(HTTPAuthScheme.SSL, null,
                    credentialProvider);
        }

        update();

    }

    /**
     * Private method that read nodes from configuration file
     *
     * @param nodes
     */
    private List<String> getNodesFromFile(String fileName) {
        File file = null;
        BufferedReader br = null;
        FileReader fr = null;

        List<String> nodeList = new LinkedList<String>();

        try {
            // Open file and create a BufferedReader for read
            file = new File(fileName);
            fr = new java.io.FileReader(file);
            br = new BufferedReader(fr);

            // read file
            String line;
            while ((line = br.readLine()) != null) {

                // tag nodes:
                if (line.substring(0, line.indexOf(":")).equalsIgnoreCase(
                        "nodes")) {
                    String nodesStr = line.substring(line.indexOf(":") + 1);
                    String[] nodes = nodesStr.split(",");
                    for (String node : nodes) {
                        nodeList.add(node.trim());
                    }

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // In finally close file for be sure that file is closed if is
            // thrown an Exception
            try {
                if (null != fr) {
                    fr.close();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }

        return nodeList;
    }

    /*
     * private HttpSession httpSession = null; public void
     * setHttpSession(HttpSession httpSession) { this.httpSession = httpSession;
     * }
     */
}
