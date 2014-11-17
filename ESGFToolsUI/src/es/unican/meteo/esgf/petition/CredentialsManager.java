/**
 *
 */
package es.unican.meteo.esgf.petition;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import es.unican.meteo.esgf.common.ESGFCredentials;
import es.unican.meteo.esgf.myproxyclient.CredentialsProvider;
import es.unican.meteo.esgf.myproxyclient.CredentialsProvider.Lib;
import es.unican.meteo.esgf.util.PemUtil;
import es.unican.meteo.esgf.util.StoreUtil;

/**
 * <p>
 * Obtains from the user's openID an authenticated connection (
 * {@link HttpURLConnection})for access to some ESGF url.
 * </p>
 *
 * <p>
 * Singleton class. Synchronized.
 * </p>
 *
 * @author Karem Terry
 * @author Manuel Vega
 */
public class CredentialsManager {

    private static final String FEDERATION_TRUSTSTORE_URL = "https://raw.github.com/ESGF/esgf-dist/master/installer/certs/esg-truststore.ts";

    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final String DEFAULT_ESG_FOLDER = ".esg";
    private static final String ESG_HOME_ENV_VAR = "ESG_HOME";
    private static final String KEYSTORE_JKS_FILE = "keystore_jks.ks";
    private static final String KEYSTORE_JCEKS_FILE = "keystore_jceks.ks";
    private static final String CREDENTIALS_FILE_PEM = "credentials.pem";
    private static final String TRUSTSTORE_FILE_NAME = "esg-truststore.ts";
    private static final String SSLCONTEXT = "TLS";

    /** Logger. */
    static private org.slf4j.Logger LOG = org.slf4j.LoggerFactory
            .getLogger(CredentialsManager.class);

    /** User's folder for ESG credentials. */
    private String esgHome;

    /** State of Credentials Manager (Inititialized or not). */
    private boolean initialized;

    /**
     * Parameters to configure MyProxy for retrieving credentials from a MyProxy
     * server.
     */
    private PasswordAuthentication openID;

    /** Socket factory that uses the client ESG certificate. */
    private SSLSocketFactory socketFactory;

    /** X.509 user certificate. */
    private ESGFCredentials esgfCredentials;

    /** Allows retrieve user credentials from ESGF. */
    private CredentialsProvider credentialsProvider;

    /** Singleton instance. */
    private static CredentialsManager INSTANCE = null;

    /**
     * Create a thread-safe singleton.
     */
    private static void createInstance() {
        LOG.trace("[IN]  createInstance");

        LOG.debug("Checking if exist an instance of CredentialManager");
        // creating a thread-safe singleton
        if (INSTANCE == null) {

            // Only the synchronized block is accessed when the instance hasn't
            // been created.
            synchronized (CredentialsManager.class) {
                // Inside the block it must check again that the instance has
                // not been created.
                if (INSTANCE == null) {
                    LOG.debug("Creating new instance of CredentialManager");
                    INSTANCE = new CredentialsManager();
                }
            }
        }
        LOG.trace("[OUT] createInstance");
    }

    /**
     * Get singleton instance of {@link CredentialsManager}. This instance is
     * the only that exists.
     *
     * @return the unique instance of {@link CredentialsManager}.
     */
    public static CredentialsManager getInstance() {
        LOG.trace("[IN]  getInstance");
        createInstance();
        LOG.trace("[OUT] getInstance");
        return INSTANCE;
    }

    /**
     * Constructor. Creates the credentials manager. If the user has a ESG_HOME
     * environment variable set, then it is used as the folder to store the ESG
     * credentials; otherwise, the default folder (&lt;user home
     * folder&gt;/.esg) is used.
     */
    private CredentialsManager() {
        LOG.trace("[IN]  CredentialsManager");
        // use ESG_HOME environmental variable if exists
        Map<String, String> env = System.getenv();
        if (env.containsKey(ESG_HOME_ENV_VAR)) {
            this.esgHome = env.get(ESG_HOME_ENV_VAR);
        } else { // use default directory if not
            String homePath = System.getProperty("user.home");
            this.esgHome = homePath + File.separator + DEFAULT_ESG_FOLDER;
        }

        openID = null;

        // -----------------------------------------------------------------
        // System options---------------------------------------------------
        System.clearProperty("X509_CERT_DIR");
        Security.removeProvider("BC");
        System.setProperty("X509_CERT_DIR", esgHome + File.separator
                + "certificates");
        // System.setProperty("javax.net.debug", "ssl"); // for console
        // debugging
        // ------------------------------------------------------------------
        // ------------------------------------------------------------------

        // Init credentials provier with this default options
        credentialsProvider = CredentialsProvider.getInstance();

        LOG.trace("[OUT] CredentialsManager");
    }

    /**
     * Check if pem certificate in local system file is valid and generate the
     * necessary files.
     * <ul>
     * <li>Check if exist trustore file</li>
     * <li>Check if exist files of credentials</li>
     * <li>Check vality of credentials</li>
     * <li>Generate or regenerate keystore files in format JKS and JCEKS from
     * pem</li>
     * </ul>
     *
     * @return true if are valid and all files was generated successfully and
     *         otherwise false
     */
    private boolean checkLocalCertificateValidate() {
        LOG.trace("[IN]  isLocalCertificateValid");

        // Checking if exists credentials files
        File trustFile = new File(esgHome + File.separator
                + TRUSTSTORE_FILE_NAME);
        File credentialsFile = new File(esgHome + File.separator
                + CREDENTIALS_FILE_PEM);
        if (!credentialsFile.exists()) {
            LOG.trace("[OUT] checkLocalCertificateValidate");
            return false;
        } else if (!trustFile.exists()) {
            try {
                retrieveESGFTrustStore();
            } catch (Exception e) {
                LOG.error(
                        "Trustore insn't in file system and can't be retrieved from {}. \n {}",
                        FEDERATION_TRUSTSTORE_URL, e.getMessage());
                LOG.trace("[OUT] checkLocalCertificateValidate");
                return false;
            }
        }

        try {
            // Get certificates of local file
            String pem = readFile(new File(esgHome + File.separator
                    + CREDENTIALS_FILE_PEM));

            X509Certificate x509certificates[] = PemUtil
                    .getX509Certificates(pem);
            PrivateKey privatekey = PemUtil.getPrivateKey(pem);

            if (x509certificates == null || x509certificates.length < 1
                    || privatekey == null) {
                return false;
            }

            // get user certificate
            X509Certificate userCert = x509certificates[0];

            // Checking vality of certificate. checValidity() throws
            // CertificateExpiredException if was expired or
            // CertificateNotYetValidException if not valid
            userCert.checkValidity();

            // get server certificates
            Collection<X509Certificate> x509ServerCertificates = new LinkedList<X509Certificate>();
            for (int i = 1; i < x509certificates.length; i++) {
                x509ServerCertificates.add(x509certificates[i]);
            }

            esgfCredentials = new ESGFCredentials(userCert, privatekey,
                    x509ServerCertificates);

            LOG.debug("Generating key store (type JCEKS) for be used by java.net");
            KeyStore keystore = StoreUtil.generateJCEKSKeystore(userCert,
                    privatekey, x509ServerCertificates, KEYSTORE_PASSWORD);
            // save keystore file
            keystore.store(new BufferedOutputStream(new FileOutputStream(
                    new File(esgHome + File.separator + KEYSTORE_JCEKS_FILE))),
                    KEYSTORE_PASSWORD.toCharArray());

            LOG.debug("Generating key store (type JKS) for be used by netcdf HTTPSSLProvider");
            keystore = StoreUtil.generateJKSKeystore(userCert, privatekey,
                    x509ServerCertificates, KEYSTORE_PASSWORD);

            // save credentials in keystore file (for netcdf toolsUI)
            keystore.store(new BufferedOutputStream(new FileOutputStream(
                    new File(esgHome + File.separator + KEYSTORE_JKS_FILE))),
                    KEYSTORE_PASSWORD.toCharArray());

            // Any error will return a boolean=false
        } catch (IOException e) {
            // some error reading credential file
            LOG.trace("[OUT] checkLocalCertificateValidate");
            return false;
        } catch (CertificateExpiredException e) {
            LOG.trace("[OUT] checkLocalCertificateValidate");
            return false;
        } catch (CertificateNotYetValidException e) {
            LOG.trace("[OUT] checkLocalCertificateValidate");
            return false;
        } catch (CertificateException e) {
            LOG.error("Error reading certificates in file system {}"
                    + e.getMessage());
            LOG.trace("[OUT] checkLocalCertificateValidate");
            return false;
        } catch (GeneralSecurityException e) {
            LOG.error("Error reading certificates in file system {}"
                    + e.getMessage());
            LOG.trace("[OUT] checkLocalCertificateValidate");
            return false;
        }

        // if not return false previously then true
        LOG.trace("[OUT] checkLocalCertificateValidate");
        return true;
    }

    /**
     * Initializes the SSL socket factory.
     *
     * @throws IOException
     *             if happens some IO error
     */
    private void createSocketFactory() throws IOException {
        LOG.trace("[IN]  createSocketFactory");

        try {

            LOG.debug("Checking if esgf credentials are valid");
            // If esg user certificate isn't valid
            if (!hasValidESGFCertificates()) {
                retrieveCredentials();
            }
            LOG.debug("Credential has been retrieved");

            // if JKS or trustore cant be in file system but exists pem
            // then generate them from pem file
            File truststore = new File(esgHome + File.separator
                    + TRUSTSTORE_FILE_NAME);
            File jcekstore = new File(esgHome + File.separator
                    + KEYSTORE_JCEKS_FILE);

            if (!truststore.exists()) {
                retrieveESGFTrustStore();
            }

            if (!jcekstore.exists()) {
                KeyStore keystore = StoreUtil.generateJCEKSKeystore(
                        esgfCredentials.getX509userCertificate(),
                        esgfCredentials.getPrivateKey(),
                        esgfCredentials.getX509ServerCertificates(),
                        KEYSTORE_PASSWORD);
                // save keystore file
                keystore.store(new BufferedOutputStream(
                        new FileOutputStream(new File(esgHome + File.separator
                                + KEYSTORE_JCEKS_FILE))), KEYSTORE_PASSWORD
                        .toCharArray());
            }

            // }

            // TrustManagerFactory-------------------------------
            LOG.debug("Generating trust manager factory");
            // Load keystore of trust certificated: esg-truststore.ts
            KeyStore ksTruststore = KeyStore.getInstance(KeyStore
                    .getDefaultType());
            ksTruststore.load(new FileInputStream(truststore),
                    KEYSTORE_PASSWORD.toCharArray());
            LOG.debug("Generated key store of trust certificates. Key store size:"
                    + ksTruststore.size());

            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ksTruststore);
            LOG.debug("Trust manager factory was generated");

            // KeyManagerFactory ------------------------------
            LOG.debug("Generating key manager factory");
            // key store must be type JCEKS (asymmetric key)
            KeyStore keystore = KeyStore.getInstance("JCEKS");
            keystore.load(new FileInputStream(jcekstore),
                    KEYSTORE_PASSWORD.toCharArray());
            LOG.debug("Generated key store of private key and X509Certificate. Key store size:"
                    + keystore.size());

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keystore, "changeit".toCharArray());
            LOG.debug("Key manager factory was generated");

            // SSL Context with client certificates and CA's
            SSLContext context = SSLContext.getInstance(SSLCONTEXT);
            context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            SSLSocketFactory sslSocketFactory = context.getSocketFactory();

            // Avoid handshake failure in JVM < 7
            String version = System.getProperty("java.version");
            // checking for 1.6 prefix should work in version naming convention
            if (version.startsWith("1.6.") || version.startsWith("1.5.")) {
                configureProtocolsToAvoidHandshakeException();
            }

            // set atributte
            socketFactory = sslSocketFactory;
        } catch (IOException e) {
            LOG.error("IOException in createSocketFactory(): {}", e);
            throw new IOException();
        } catch (KeyStoreException e) {
            // must not happens
            LOG.error(
                    "Unexpected KeyStoreException in createSocketFactory(): {}",
                    e);
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            // must not happens
            LOG.error(
                    "Unexpected NoSuchAlgorithmException in createSocketFactory(): {}",
                    e);
            e.printStackTrace();
        } catch (CertificateException e) {
            // must not happens
            LOG.error(
                    "Unexpected CertificateException in createSocketFactory(): {}",
                    e);
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            // must not happens
            LOG.error(
                    "Unexpected UnrecoverableKeyException in createSocketFactory(): {}",
                    e);
            e.printStackTrace();
        } catch (KeyManagementException e) {
            // must not happens
            LOG.error(
                    "Unexpected KeyManagementException in createSocketFactory(): {}",
                    e);
            e.printStackTrace();
        } catch (Exception e) {
            LOG.error("Unexpected exception in createSocketFactory(): {}", e);
            e.printStackTrace();
        }

        LOG.trace("[OUT] createSocketFactory");
    }

    private boolean hasValidESGFCertificates() {
        if (esgfCredentials == null) {
            return false;
        }

        // get user certificate
        X509Certificate userCert = esgfCredentials.getX509userCertificate();

        // Checking vality of certificate. checValidity() throws
        // CertificateExpiredException if was expired or
        // CertificateNotYetValidException if not valid
        try {
            userCert.checkValidity();
            return true;
        } catch (CertificateExpiredException e) {
            return false;
        } catch (CertificateNotYetValidException e) {
            // TODO Auto-generated catch block
            return false;
        }
    }

    /**
     * This private method must be done because
     * HttpsUrlConnection.setSSLSocketFactory(SSLSocketFactory) that is used in
     * getAuthenticatedConnection(URL) don't throw the SSLHandshakeException and
     * this exception can't be treated in these cases (JVM < 7).
     *
     */
    private void configureProtocolsToAvoidHandshakeException() {
        System.setProperty("https.protocols", "TLSv1");
    }

    /**
     * Get authenticated {@link HttpURLConnection} for the given URL. The
     * returned connection is already opened.
     *
     * @param url
     *            ESGF file endpoint
     * @return opened authenticated URL connection
     * @throws IOException
     *             if happens some error trying to obtains authenticated
     *             connection
     * @throws IllegalStateException
     *             if CredentialManager hasn't been initiated
     */
    public HttpURLConnection getAuthenticatedConnection(URL url)
            throws IOException {
        LOG.trace("[IN]  getAuthenticatedConnection");

        if (!hasInitiated()) {
            LOG.error("IllegalStateException. Credential Manager hasn't been initiated");
            throw new IllegalStateException(
                    "Credential Manager hasn't been iniciated");
        }

        HttpURLConnection authenticatedConnection = null;
        try {

            LOG.debug("Open unauthenticated connection to {}", url);
            // open unauthenticated connection
            HttpURLConnection connection = (HttpURLConnection) url
                    .openConnection();
            connection.connect();
            // obtain redirection to authentication endpoint
            String urlLocation = connection.getHeaderField("Location");
            connection.disconnect();

            // Source not available
            if (urlLocation == null) {
                throw new IOException(
                        "Source not available. Url fails with response code"
                                + connection.getResponseCode());
            }

            LOG.debug("Redirection obtained (aunthentication endpoint){}.",
                    urlLocation);

            LOG.debug("Connecting to authentication endpoint...");
            // connect authentication endpoint
            URL authenticationURL = new URL(urlLocation);
            URLConnection authenticationConnection = authenticationURL
                    .openConnection();
            HttpsURLConnection httpsUrlConnection = (HttpsURLConnection) authenticationConnection;

            // Control hot reinit credentials
            synchronized (this) {
                // if reinit credentials manager and fails
                if (!hasInitiated()) {
                    return authenticatedConnection = (HttpURLConnection) url
                            .openConnection();
                } else {
                    // set socket factory with client certificate
                    httpsUrlConnection.setSSLSocketFactory(getSocketFactory());
                }
            }

            // get authentication cookie
            String cookie = httpsUrlConnection.getHeaderField("Set-Cookie");

            if (cookie != null) {
                LOG.debug("Successful connection. Authentication cookie:{}",
                        cookie);
            } else {
                LOG.warn("Failed connection of autentication. Authentication cookie is null");
            }

            LOG.debug(
                    "Creating new authenticated connection for file download: {}",
                    url);

            // If Header "Location" of authentication https connection not
            // redirections to url (must not happens)
            if (httpsUrlConnection.getHeaderField("Location") == null) {
                URL authenticatedURL = new URL(url.toString());
                httpsUrlConnection.disconnect();
                // authenticated connection
                authenticatedConnection = (HttpURLConnection) authenticatedURL
                        .openConnection();
            } else {
                URL authenticatedURL = new URL(
                        httpsUrlConnection.getHeaderField("Location"));
                httpsUrlConnection.disconnect();
                // open authenticated connection
                authenticatedConnection = (HttpURLConnection) authenticatedURL
                        .openConnection();
            }
            // put authentication cookie
            authenticatedConnection.setRequestProperty("Cookie", cookie);

        } catch (IOException e) {
            LOG.error(
                    "IOException trying to obtains authenticated connection of {}\n. {}",
                    url, e.getStackTrace());
            throw new IOException();
        } catch (Exception e) {
            LOG.error(
                    "Unnespected exception trying to obtains authenticated connection of {}. {}",
                    url, e.getStackTrace());
            e.printStackTrace();
        }

        LOG.trace("[OUT] getAuthenticatedConnection");
        return authenticatedConnection;
    }

    /**
     * Get the SSL socket factory that uses the user's ESG credentials.
     *
     * @return socket factory that uses the user's ESG credentials
     *
     *
     * @throws IllegalStateException
     *             if aren't user open id data
     * @throws IOException
     *             if some error happens creating socket factory
     */
    private SSLSocketFactory getSocketFactory() throws IllegalStateException,
            IOException {

        LOG.trace("[IN]  getSocketFactory");

        // doble check thread-safe
        if (socketFactory == null) {
            synchronized (this) {
                if (socketFactory == null) {
                    LOG.debug("Socket factory isn't created. Creating new socket factory");
                    createSocketFactory();
                }
            }
        } else {
            // doble check thread-safe
            if (!hasValidESGFCertificates()) {
                synchronized (this) {
                    if (!hasValidESGFCertificates()) {

                        // renew certificates only if openID is setted
                        if (openID != null) {
                            LOG.debug("Certificate isn't valid. Creating new socket factory");
                            createSocketFactory();
                        }
                    }
                }
            }
        }

        LOG.trace("[OUT] getSocketFactory");
        return socketFactory;
    }

    /**
     * Check if CredentialManager has been initiated.
     *
     * @return true if is configured and otherwise false.
     */
    public synchronized boolean hasInitiated() {
        LOG.trace("[IN]  hasInitiated");
        LOG.trace("[OUT] hasInitiated");
        return initialized;
    }

    /**
     * Force to renew credentials an save them in local file system (path:
     * &lt;user home folder&gt;/.[$ESG_HOME]/credentials.pem). Download
     * credentials an save them in local files.
     *
     * @throws IOException
     *             if error happens reading X509 certificate in file system
     * @throws IllegalStateException
     *             if CredentialManager hasn't been initiated or openID user
     *             hasn't configured
     */
    public synchronized void renewFileCredentials() throws IOException {
        LOG.trace("[IN]  getX509Certificate");
        if (!hasInitiated()) {
            LOG.error("IllegalStateException. Credential Manager hasn't been initiated");
            throw new IllegalStateException(
                    "Credential Manager hasn't been iniciated");
        }

        if (openID == null) {
            LOG.error("IllegalStateException. User openID hasn't configured");
            throw new IllegalStateException("User openID hasn't configured");
        }

        socketFactory = null;
        try {
            retrieveCredentials();
        } catch (Exception e) {
            LOG.error("Error renewing credentials: {}", e.getMessage());
        }
        LOG.trace("[OUT] getX509Certificate");

    }

    /**
     * Get X509Certificate credentials in file system (path: &lt;user home
     * folder&gt;/.[$ESG_HOME]/credentials.pem) and return X509Certificate.
     *
     * <p>
     * If previously was read then it returned of memory
     * </p>
     *
     * @return X509Certificate credentials
     * @throws IOException
     *             if error happens reading X509 certificate in file system
     * @throws IllegalStateException
     *             if CredentialManager hasn't been initiated
     */
    public X509Certificate getX509Certificate() {
        LOG.trace("[IN]  getX509Certificate");
        if (!hasInitiated()) {
            LOG.error("IllegalStateException. Credential Manager hasn't been initiated");
            throw new IllegalStateException(
                    "Credential Manager hasn't been iniciated");
        }

        LOG.trace("[OUT] getX509Certificate");
        return esgfCredentials.getX509userCertificate();

    }

    /**
     * Get remaining time of certificate in milliseconds, after this time it
     * will be invalid.
     *
     * @return remaining time of certificate in milliseconds
     * @throws IOException
     *             if error happens reading X509 certificate in file system
     * @throws IllegalStateException
     *             if CredentialManager hasn't been initiated
     */
    public synchronized long getRemainTimeOfCredentialsInMillis()
            throws IOException {
        LOG.trace("[IN]  getRemainTimeOfCredentialsInMillis");
        if (!hasInitiated()) {
            LOG.error("IllegalStateException. Credential Manager hasn't been initiated");
            throw new IllegalStateException(
                    "Credential Manager hasn't been iniciated");
        }

        X509Certificate cert = esgfCredentials.getX509userCertificate();
        Date expireDate = cert.getNotAfter();
        Date currentDate = new Date();

        // Calculate difference in milliseconds
        // getdate() returns the number of milliseconds since
        // January 1, 1970
        long diffTime = expireDate.getTime() - currentDate.getTime();
        LOG.trace("[OUT] getRemainTimeOfCredentialsInMillis");
        return diffTime;
    }

    /**
     * Initialize credential manager with an openID. Synchronized. If previously
     * has been initiated then reset all state of credential manager and
     * reinitialize it.
     *
     * @param openIDURL
     *            OpenID-enabled URL that can be used to log into OpenID-enabled
     *            websites
     * @param password
     *            OpenID password
     * @return MessageError String if connection failed or null is success
     * @throws IOException
     *             if some error happens getting credentials
     */
    public synchronized void initialize(String openIDURL, char[] password)
            throws IOException {
        LOG.trace("[IN]  initialize");

        esgfCredentials = null;
        socketFactory = null;
        try {
            openID = new PasswordAuthentication(openIDURL, password);
            retrieveCredentials();
            initialized = true;
        } catch (Exception e) {
            LOG.error("Error initializing credential manager:{} "
                    + e.getMessage());
            openID = null; // set a null openID
            initialized = false;
            throw new IOException(e.getMessage());
        }
        LOG.trace("[OUT] initialize");
    }

    /**
     * Initialize credential manager from local system files. (path: &lt;user
     * home folder&gt;/.[$ESG_HOME]). Synchronized.
     *
     *
     * @throws CertificateException
     *             if system local files certificates aren't valid
     * @throws IOException
     *             if error happens reading file
     */
    public synchronized void initialize() throws IOException,
            CertificateException {
        LOG.trace("[IN]  initialize");

        if (checkLocalCertificateValidate()) {
            initialized = true;
            openID = null;
        } else {
            throw new CertificateException("User certificates aren't valid");
        }
        LOG.trace("[OUT] initialize");
    }

    /**
     * Reads the contents of a file as a string.
     *
     * @param credentialsFile
     *            file to be read
     * @return contents of the file
     * @throws IOException
     *             if the file could not be read
     */
    private String readFile(File credentialsFile) throws IOException {

        LOG.trace("[IN]  readFile");

        // read file
        BufferedReader reader = new BufferedReader(new FileReader(
                credentialsFile));
        StringBuffer sb = new StringBuffer();
        String line = reader.readLine();
        while (line != null) {
            sb.append(line);
            sb.append("\n");
            line = reader.readLine();
        }
        reader.close();

        LOG.trace("[OUT] readFile");
        return sb.toString();
    }

    /**
     * Get credentials from ESGF IdP node and write it in file system (path:
     * &lt;user home folder&gt;/.[$ESG_HOME]).
     *
     * @throws Exception
     *
     * @throws IllegalStateException
     *             if user openID hasn't configured
     */
    private void retrieveCredentials() throws Exception {
        LOG.trace("[IN]  retrieveCredentials");

        if (openID == null) {
            LOG.error("IllegalStateException. User openID hasn't configured");
            throw new IllegalStateException("User openID hasn't configured");
        }

        // configure credentials provider
        credentialsProvider.setMyProxyLib(Lib.MYPROXYLOGON);
        credentialsProvider.setWriteTrustRootsCerts(true);
        credentialsProvider.setWriteTruststore(true);
        credentialsProvider.setCredentialsDirectory(esgHome);

        // configure OpenID
        credentialsProvider.setOpenID(openID.getUserName(),
                openID.getPassword());

        // configure output files
        credentialsProvider.setWritePem(true);
        credentialsProvider.setWriteJCEKSKeystore(true); // for javax.net
        credentialsProvider.setWriteJKSKeystore(true); // for httpclient

        LOG.debug("Get credentials of user with myProxy service");
        esgfCredentials = credentialsProvider.retrieveCredentials();
        LOG.debug("get credentials success!");

        LOG.trace("[OUT] retrieveCredentials");
    }

    /**
     * Retrieve ESGF truststore
     *
     * @return
     * @throws GeneralSecurityException
     * @throws IOException
     */
    private KeyStore retrieveESGFTrustStore() throws GeneralSecurityException,
    IOException {
        URL trustURL = new URL(FEDERATION_TRUSTSTORE_URL);
        KeyStore truststore = StoreUtil.loadJKSTrustStore(
                trustURL.openStream(), KEYSTORE_PASSWORD);
        // Save truststore in system file
        truststore.store(new BufferedOutputStream(new FileOutputStream(
                new File(esgHome + File.separator + TRUSTSTORE_FILE_NAME))),
                KEYSTORE_PASSWORD.toCharArray());

        return truststore;
    }

}
