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
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
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
    private static final String KEYSTORE_FILE = "keystore.ks";
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

            System.out.println("AQui");
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
                    esgDialog = new JDialog(parent, true);
                    esgDialog.setVisible(false);

                    esgDialog.setLayout(new BorderLayout());

                    // OpenId and password fields and options in panel
                    JPanel introPanel = createIntroPanel();

                    // main
                    mainPanel = new JPanel(new BorderLayout());
                    mainPanel.add(introPanel, BorderLayout.CENTER);
                    JPanel aux3 = new JPanel(new FlowLayout());
                    aux3.add(saveLogin);
                    mainPanel.add(aux3, BorderLayout.SOUTH);

                    JButton closeButton = new JButton("OK");
                    closeButton.addActionListener(new ActionListener() {

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            esgDialog.dispose();
                        }
                    });

                    esgDialog.add(closeButton, BorderLayout.SOUTH);
                    esgDialog.add(mainPanel, BorderLayout.CENTER);
                    esgDialog.pack();

                    logger.debug("Show esg login dialog");
                    esgDialog.setVisible(true);
                } else {
                    logger.debug("Generating HTTPSSLprovider from keystore file");
                    this.credentialProvider = new HTTPSSLProvider(
                            this.keystore, KEYSTORE_PASS, this.truststore,
                            TRUSTSTORE_PASS);
                    HTTPSession.setAnyCredentialsProvider(HTTPAuthScheme.SSL,
                            null, credentialProvider);
                }
            } else {
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

    private JPanel createIntroPanel() {
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
        List<String> nodes = getNodesFromFile(CONFIG_FILE);
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
                        login(completeOpenID, password);
                    } else {
                        login(openIdUser, password);
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
                    esgDialog.repaint();
                } else {
                    openUrlField.setColumns(15);
                    openUrlField.setText("");
                    esgDialog.repaint();
                }
            }
        });

        return introPanel;
    }

    /**
     * Private thread class that do login.
     */
    private void login(String openID, char[] pass) {

        boolean error = false;
        try {
            saveLogin.setEnabled(false);
            credentialsManager.initialize(openID, pass);
        } catch (IOException e) {
            error = true;
        }
        saveLogin.setEnabled(true);

        if (error) {
            infoSucces.setText("<html><FONT COLOR=\"red\"> OpenId or password "
                    + "aren't valid</FONT></html>");
            this.credentialProvider = null;
        } else {
            infoSucces
                    .setText("<html><FONT COLOR=\"blue\">Success</FONT></html>");
            this.credentialProvider = new HTTPSSLProvider(this.keystore,
                    KEYSTORE_PASS, this.truststore, TRUSTSTORE_PASS);
            HTTPSession.setAnyCredentialsProvider(HTTPAuthScheme.SSL, null,
                    credentialProvider);
        }

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
