/**
 * 
 */
package es.unican.meteo.esgf.petition;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Map;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.bouncycastle.openssl.PEMReader;
import org.globus.myproxy.GetParams;
import org.globus.myproxy.MyProxy;
import org.globus.util.Util;
import org.gridforum.jgss.ExtendedGSSCredential;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.w3c.dom.Document;

import ucar.nc2.util.net.HTTPSSLProvider;
import es.unican.meteo.esgf.search.SearchManager;

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

    // Constants.
    private static final String FEDERATION_TRUSTSTORE_URL = "https://rainbow.llnl.gov/dist/certs/esg-truststore.ts";

    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final int LIFE_TIME = 259200;
    private static final String RSA_PRIVATE_KEY_PEM_FOOTER = "-----END RSA PRIVATE KEY-----";
    private static final String RSA_PRIVATE_KEY_PEM_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String SSLCONTEXT = "TLS";
    private static final String TRUSTSTORE_FILE_NAME = "esg-truststore.ts";
    private static final String CERTIFICATE_PEM_FOOTER = "-----END CERTIFICATE-----";
    private static final String CERTIFICATE_PEM_HEADER = "-----BEGIN CERTIFICATE-----";
    private static final String CREDENTIALS_FILE_NAME_PEM = "credentials.pem";
    private static final String DEFAULT_ESG_FOLDER = ".esg";
    private static final String ESG_HOME_ENV_VAR = "ESG_HOME";
    private static final String KEYSTORE_FILE = "keystore.ks";

    /**
     * If this directory exists, not create the CA necessary, and thats why the
     * directory must not exits or must contains the CAs necessary. If that
     * isn't checked fails with this Exception:
     * <p>
     * 
     * <pre>
     * MyProxy get failed. Caused by Authentication failed. 
     * Caused by Failure unspecified at GSS-API level. 
     * Caused by COM.claymoresystems.ptls.SSLThrewAlertException: Unknown CA
     * </pre>
     * 
     * </p>
     */
    private static final String TEMP_X509_CERTIFICATES = "tempCert";

    /** User's folder for ESG credentials. */
    private static String esgHome;

    /** State of Credentials Manager (Inititialized or not). */
    private boolean initialized;

    /** Singleton instance. */
    private static CredentialsManager INSTANCE = null;

    /** Logger. */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(SearchManager.class);

    /**
     * Create a thread-safe singleton.
     */
    private static void createInstance() {
        logger.trace("[IN]  createInstance");

        logger.debug("Checking if exist an instance of CredentialManager");
        // creating a thread-safe singleton
        if (INSTANCE == null) {

            // Only the synchronized block is accessed when the instance hasn't
            // been created.
            synchronized (CredentialsManager.class) {
                // Inside the block it must check again that the instance has
                // not been created.
                if (INSTANCE == null) {
                    logger.debug("Creating new instance of CredentialManager");
                    INSTANCE = new CredentialsManager();
                }
            }
        }
        logger.trace("[OUT] createInstance");
    }

    /**
     * Get singleton instance of {@link CredentialsManager}. This instance is
     * the only that exists.
     * 
     * @return the unique instance of {@link CredentialsManager}.
     */
    public static CredentialsManager getInstance() {
        logger.trace("[IN]  getInstance");
        createInstance();
        logger.trace("[OUT] getInstance");
        return INSTANCE;
    }

    /** OpenID account. */
    private PasswordAuthentication openID;

    /** Socket factory that uses the client ESG certificate. */
    private SSLSocketFactory socketFactory;

    /** X.509 certificate. */
    private X509Certificate x509Certificate;

    /**
     * Constructor. Creates the credentials manager. If the user has a ESG_HOME
     * environment variable set, then it is used as the folder to store the ESG
     * credentials; otherwise, the default folder (&lt;user home
     * folder&gt;/.esg) is used.
     */
    private CredentialsManager() {
        logger.trace("[IN]  CredentialsManager");
        // use ESG_HOME environmental variable if exists
        Map<String, String> env = System.getenv();
        if (env.containsKey(ESG_HOME_ENV_VAR)) {
            this.esgHome = env.get(ESG_HOME_ENV_VAR);
        } else { // use default directory if not
            String homePath = System.getProperty("user.home");
            this.esgHome = homePath + File.separator + DEFAULT_ESG_FOLDER;
        }

        // if .esg directory doesn't exist then create new
        File esgDirectory = new File(esgHome);
        if (!esgDirectory.exists()) {
            esgDirectory.mkdir();
            // drwxrwxr-x
            Util.setFilePermissions(esgDirectory.getPath(), 775);
        }

        openID = null;

        // -----------------------------------------------------------------
        // System options---------------------------------------------------
        // Add java security provider
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        // Predetermine temp directory from x509 crtificates
        System.setProperty("X509_CERT_DIR", esgHome + File.separator
                + TEMP_X509_CERTIFICATES);
        // for console debugging
        // System.setProperty("javax.net.debug", "ssl");
        // ------------------------------------------------------------------
        logger.trace("[OUT] CredentialsManager");
    }

    /**
     * Check if certificates in local system files are valid.
     * <ul>
     * <li>Check if exist files of credentials</li>
     * <li>Check if the credentials corresponds with current openId of
     * CredentialManager if it is set</li>
     * <li>Check vality of credentials</li>
     * <li>Create keystore file in format JKS if not exists</li>
     * </ul>
     * 
     * @return true if are valid and otherwise false
     */
    private boolean areValidCertificates() {
        logger.trace("[IN]  areValidCertificates");

        // Checking if exists credential files
        File trustFile = new File(esgHome + File.separator
                + TRUSTSTORE_FILE_NAME);
        File credentialsFile = new File(esgHome + File.separator
                + CREDENTIALS_FILE_NAME_PEM);
        if (!trustFile.exists() || !credentialsFile.exists()) {
            logger.trace("[OUT] areValidCertificates");
            return false;
        }

        try {
            // Get certificate of local file
            X509Certificate cert = getX509CertificateFromFileSystem();

            if (openID != null) {
                // check if the same user (if open id user is set)
                String dn = cert.getSubjectX500Principal().getName();
                LdapName ldapDN = new LdapName(dn);
                Rdn rdn = ldapDN.getRdn(3);
                String certOpenID = rdn.getValue().toString();

                if (!certOpenID.equals(openID.getUserName())) {
                    return false;
                }
            }

            // Checking vality of certificate. checValidity() throws
            // CertificateExpiredException if was expired or
            // CertificateNotYetValidException if not valid
            cert.checkValidity();

            // Any error will return a boolean=false
        } catch (IOException e) {
            // some error reading credential file
            logger.trace("[OUT] areValidCertificates");
            return false;
        } catch (CertificateExpiredException e) {
            logger.trace("[OUT] areValidCertificates");
            return false;
        } catch (CertificateNotYetValidException e) {
            logger.trace("[OUT] areValidCertificates");
            return false;
        } catch (InvalidNameException e) {
            logger.trace("[OUT] areValidCertificates");
            return false;
        }

        File keystoreFile = new File(esgHome + File.separator + KEYSTORE_FILE);
        if (!keystoreFile.exists()) {
            logger.debug("Generating key store (type JKS) for be used by netcdf HTTPSSLProvider");
            createKeyStoreFile(null);
        }

        // if not return false previously then true
        logger.trace("[OUT] areValidCertificates");
        return true;
    }

    /**
     * Initializes the SSL socket factory.
     * 
     * @throws IOException
     *             if happens some IO error
     */
    private void createSocketFactory() throws IOException {
        logger.trace("[IN]  createSocketFactory");

        try {

            logger.debug("Checking if user certificate is valid");
            // If esg user certificate isn't valid
            if (!areValidCertificates()) {
                retrieveCredentials();
            }

            logger.debug("Credential has been retrieved");
            // }

            // TrustManagerFactory-------------------------------
            logger.debug("Generating trust manager factory");
            // Load keystore file
            InputStream trustCertInput = new FileInputStream(new File(esgHome
                    + File.separator + TRUSTSTORE_FILE_NAME));
            // keystore of trust certificated: esg-truststore
            KeyStore ksTrustCert = KeyStore.getInstance(KeyStore
                    .getDefaultType());// JKS
            // pass: changeit. Apparently it is the predetermine key. Not sure
            // load keystore from input stream
            ksTrustCert.load(trustCertInput, KEYSTORE_PASSWORD.toCharArray());
            trustCertInput.close();
            logger.debug("Generated key store of trust certificates. Key store size:"
                    + ksTrustCert.size());

            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ksTrustCert);
            logger.debug("Trust manager factory was generated");
            // -------------------------------

            // KeyManagerFactory ------------------------------
            logger.debug("Generating key manager factory");

            logger.debug("Generating X509Certificate from Credential in pem format");
            // read credential file
            String pem = readFile(new File(esgHome + File.separator
                    + CREDENTIALS_FILE_NAME_PEM));
            // Generate X509 certificate with PEMReader (org.bouncycastle)
            // Credential.pem have RSA key and certificate in the same file
            // and must be splitted
            PEMReader reader = new PEMReader(new InputStreamReader(
                    new ByteArrayInputStream(getFragmentOfPEM(pem,
                            CERTIFICATE_PEM_HEADER, CERTIFICATE_PEM_FOOTER))));
            x509Certificate = (X509Certificate) reader.readObject();
            logger.debug("X509Certificate has been generated:\n {}",
                    x509Certificate);

            logger.debug("Generating PrivateKey from Credential in pem format");
            // Generate PrivateKey also with PEMReader.
            // Used the another part of pem
            reader = new PEMReader(new InputStreamReader(
                    new ByteArrayInputStream(getFragmentOfPEM(pem,
                            RSA_PRIVATE_KEY_PEM_HEADER,
                            RSA_PRIVATE_KEY_PEM_FOOTER))));
            // PEMReader read a KeyPair class and then get the Private key
            KeyPair keyPair = (KeyPair) reader.readObject();
            PrivateKey key = keyPair.getPrivate();

            logger.debug("PrivateKey has been generated:\n {}", key);

            // key store must be type JCEKS (asymmetric key)
            KeyStore keystore = KeyStore.getInstance("JCEKS");
            keystore.load(null);
            // new keystore (PrivateKeys, certificates)
            keystore.setCertificateEntry("cert-alias", x509Certificate);
            keystore.setKeyEntry("key-alias", key, "changeit".toCharArray(),
                    new Certificate[] { x509Certificate });
            logger.debug("Generated key store of private key and X509Certificate.");

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keystore, "changeit".toCharArray());
            logger.debug("Key manager factory was generated");
            // -------------------------------

            // SSL Context with client certificates and CA's
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            SSLSocketFactory sslSocketFactory = context.getSocketFactory();

            // set atributte
            socketFactory = sslSocketFactory;
        } catch (IOException e) {
            logger.error("IOException in createSocketFactory(): {}",
                    e.getStackTrace());
            throw new IOException();
        } catch (KeyStoreException e) {
            // must not happens
            logger.error(
                    "Unexpected KeyStoreException in createSocketFactory(): {}",
                    e.getStackTrace());
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            // must not happens
            logger.error(
                    "Unexpected NoSuchAlgorithmException in createSocketFactory(): {}",
                    e.getStackTrace());
            e.printStackTrace();
        } catch (CertificateException e) {
            // must not happens
            logger.error(
                    "Unexpected CertificateException in createSocketFactory(): {}",
                    e.getStackTrace());
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            // must not happens
            logger.error(
                    "Unexpected UnrecoverableKeyException in createSocketFactory(): {}",
                    e.getStackTrace());
            e.printStackTrace();
        } catch (KeyManagementException e) {
            // must not happens
            logger.error(
                    "Unexpected KeyManagementException in createSocketFactory(): {}",
                    e.getStackTrace());
            e.printStackTrace();
        } catch (Exception e) {
            logger.error("Unexpected exception in createSocketFactory(): {}",
                    e.getStackTrace());
            e.printStackTrace();
        }

        logger.trace("[OUT] createSocketFactory");
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
        logger.trace("[IN]  getAuthenticatedConnection");

        if (!hasInitiated()) {
            logger.error("IllegalStateException. Credential Manager hasn't been initiated");
            throw new IllegalStateException(
                    "Credential Manager hasn't been iniciated");
        }

        HttpURLConnection authenticatedConnection = null;
        try {

            logger.debug("Open unauthenticated connection to {}", url);
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

            logger.debug("Redirection obtained (aunthentication endpoint){}.",
                    urlLocation);

            logger.debug("Connecting to authentication endpoint...");
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
                logger.debug("Successful connection. Authentication cookie:{}",
                        cookie);
            } else {
                logger.warn("Failed connection of autentication. Authentication cookie is null");
            }

            logger.debug(
                    "Creating new authenticated connection for file download: {}",
                    url);

            // If Header "Location" of authentication https connection not
            // redirections to url (must not happens)
            if (httpsUrlConnection.getHeaderField("Location") == null) {
                httpsUrlConnection.disconnect();
                // authenticated connection
                authenticatedConnection = (HttpURLConnection) url
                        .openConnection();
            } else {
                URL autenticatedURL = new URL(
                        httpsUrlConnection.getHeaderField("Location"));
                httpsUrlConnection.disconnect();
                // open authenticated connection
                authenticatedConnection = (HttpURLConnection) autenticatedURL
                        .openConnection();
            }
            // put authentication cookie
            authenticatedConnection.setRequestProperty("Cookie", cookie);

        } catch (IOException e) {
            logger.error(
                    "IOException trying to obtains authenticated connection of {}\n. {}",
                    url, e.getStackTrace());
            throw new IOException();
        } catch (Exception e) {
            logger.error(
                    "Unnespected exception trying to obtains authenticated connection of {}. {}",
                    url, e.getStackTrace());
            e.printStackTrace();
        }

        logger.trace("[OUT] getAuthenticatedConnection");
        return authenticatedConnection;
    }

    /**
     * Get fragment of PEM
     * 
     * @param pem
     *            PEM formatted data String
     * @param header
     *            DER data header
     * @param footer
     *            DER data footer
     * @return
     * @throws IllegalArgumentException
     *             if the PEM String does not contain the requested data
     */
    private byte[] getFragmentOfPEM(String pem, String header, String footer) {
        logger.trace("[IN]  getFragmentOfPEM");
        String[] tokens1 = pem.split(header);
        if (tokens1.length < 2) {
            throw new IllegalArgumentException(
                    "The PEM data does not contain the requested header");
        }
        String[] tokens2 = tokens1[1].split(footer);
        tokens2[0] = header + tokens2[0] + footer;

        logger.trace("[OUT] getFragmentOfPEM");

        return tokens2[0].getBytes();
    }

    /**
     * Get secure connection {@link HttpURLConnection} to openIdUrl with
     * permissions (truststore CA) and get truststore CA from ESGF and store in
     * file system (path: &lt;user home
     * folder&gt;/.[$ESG_HOME]/TRUSTSTORE_FILE_NAME)
     * 
     * @param openIdURLStr
     *            openID url
     * @return
     * @throws Exception
     *             any error
     */
    private HttpsURLConnection getOpenIdConnectionAndTrustStoreOfCA(
            String openIdURLStr) throws Exception {

        logger.trace("[IN]  getOpenIdURLConnection");
        SSLContext context = null;
        HttpsURLConnection openIdConnection = null;
        try {

            logger.debug("Creating new httpsUrlConnection to access openId info");
            // New HttpsURLConnection
            URL secureUrl = new URL(openIdURLStr);
            URLConnection sslConnection = secureUrl.openConnection();
            openIdConnection = (HttpsURLConnection) sslConnection;

            logger.debug("Getting keystore of CA from: {}",
                    FEDERATION_TRUSTSTORE_URL);
            // Generate key store of trust CA. Load CA from ESGF URL
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            URL keyStoreURL = new URL(FEDERATION_TRUSTSTORE_URL);
            keyStore.load(keyStoreURL.openStream(),
                    KEYSTORE_PASSWORD.toCharArray());

            // Generate trust store factory
            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            logger.debug("Saving keystore of CA's");
            // Save truststore in system file
            keyStore.store(
                    new BufferedOutputStream(new FileOutputStream(new File(
                            esgHome + File.separator + TRUSTSTORE_FILE_NAME))),
                    KEYSTORE_PASSWORD.toCharArray());

            // SSL context with client certificates
            context = SSLContext.getInstance(SSLCONTEXT);
            context.init(null, tmf.getTrustManagers(), null);

            // Set ssl socket factory
            openIdConnection.setSSLSocketFactory(context.getSocketFactory());
            logger.debug("Secure openIdConnection (HttpsURLConnection) are generated");
        } catch (Exception e) {
            logger.error("Error getting open id url connection: {}", e);
            throw new Exception("Error getting open id url connection.");
        }

        logger.trace("[OUT] getOpenIdURLConnection");
        return openIdConnection;
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

        logger.trace("[IN]  getSocketFactory");

        // doble check thread-safe
        if (socketFactory == null) {
            synchronized (this) {
                if (socketFactory == null) {
                    logger.debug("Socket factory isn't created. Creating new socket factory");
                    createSocketFactory();
                }
            }
        } else {
            // doble check thread-safe
            if (!areValidCertificates()) {
                synchronized (this) {
                    if (!areValidCertificates()) {

                        // renew certificates only if openID is setted
                        if (openID != null) {
                            logger.debug("Certificate isn't valid. Creating new socket factory");
                            createSocketFactory();
                        }
                    }
                }
            }
        }

        logger.trace("[OUT] getSocketFactory");
        return socketFactory;
    }

    /**
     * Read credentials in file system (path: &lt;user home
     * folder&gt;/.[$ESG_HOME]/credentialas.pem) and return X509Certificate.
     * 
     * <p>
     * If previously was read then it returned of memory
     * </p>
     * 
     * @return
     * @throws IOException
     *             if error happens reading X509 certificate in file system
     */
    private X509Certificate getX509CertificateFromFileSystem()
            throws IOException {
        logger.trace("[IN]  getX509CertificateFromFileSystem");

        // If already read (in retrieve createdSocketFactory)
        if (x509Certificate != null) {
            logger.trace("[OUT] getX509CertificateFromFileSystem");
            return x509Certificate;
        }

        // read credential file
        String pem;
        try {
            pem = readFile(new File(esgHome + File.separator
                    + CREDENTIALS_FILE_NAME_PEM));

            // Generate X509 certificate with PEMReader (org.bouncycastle)
            // Credential.pem have RSA key and certificate in the same file
            // and must be splitted
            PEMReader reader = new PEMReader(new InputStreamReader(
                    new ByteArrayInputStream(getFragmentOfPEM(pem,
                            CERTIFICATE_PEM_HEADER, CERTIFICATE_PEM_FOOTER))));

            X509Certificate cert = (X509Certificate) reader.readObject();
            reader.close();
            logger.trace("[OUT] getX509CertificateFromFileSystem");
            return cert;
        } catch (IOException e) {
            logger.error("Error reading X509 certificate in file system: {}",
                    esgHome + File.separator + CREDENTIALS_FILE_NAME_PEM);
            throw new IOException(
                    "Error reading X509 certificate in file system " + esgHome
                            + File.separator + CREDENTIALS_FILE_NAME_PEM);
        }

    }

    /**
     * Check if CredentialManager has been initiated.
     * 
     * @return true if is configured and otherwise false.
     */
    public synchronized boolean hasInitiated() {
        logger.trace("[IN]  hasInitiated");
        logger.trace("[OUT] hasInitiated");
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
        logger.trace("[IN]  getX509Certificate");
        if (!hasInitiated()) {
            logger.error("IllegalStateException. Credential Manager hasn't been initiated");
            throw new IllegalStateException(
                    "Credential Manager hasn't been iniciated");
        }

        if (openID == null) {
            logger.error("IllegalStateException. User openID hasn't configured");
            throw new IllegalStateException("User openID hasn't configured");
        }

        socketFactory = null;
        retrieveCredentials();
        logger.trace("[OUT] getX509Certificate");

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
    public X509Certificate getX509Certificate() throws IOException {
        logger.trace("[IN]  getX509Certificate");
        if (!hasInitiated()) {
            logger.error("IllegalStateException. Credential Manager hasn't been initiated");
            throw new IllegalStateException(
                    "Credential Manager hasn't been iniciated");
        }
        logger.trace("[OUT] getX509Certificate");
        return getX509CertificateFromFileSystem();

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
        logger.trace("[IN]  getRemainTimeOfCredentialsInMillis");
        if (!hasInitiated()) {
            logger.error("IllegalStateException. Credential Manager hasn't been initiated");
            throw new IllegalStateException(
                    "Credential Manager hasn't been iniciated");
        }

        X509Certificate cert = getX509CertificateFromFileSystem();
        Date expireDate = cert.getNotAfter();
        Date currentDate = new Date();

        // Calculate difference in milliseconds
        // getdate() returns the number of milliseconds since
        // January 1, 1970
        long diffTime = expireDate.getTime() - currentDate.getTime();
        logger.trace("[OUT] getRemainTimeOfCredentialsInMillis");
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
     * @throws IOException
     *             if some error happens getting credentials
     */
    public synchronized void initialize(String openIDURL, char[] password)
            throws IOException {
        logger.trace("[IN]  initialize");

        x509Certificate = null;
        socketFactory = null;
        try {
            openID = new PasswordAuthentication(openIDURL, password);
            retrieveCredentials();
            initialized = true;
        } catch (IOException e) {
            logger.error("Error initializing credential manager");
            openID = null; // set a null openID
            initialized = false;
            throw new IOException();
        }
        logger.trace("[OUT] initialize");
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
     * @throws IllegalStateException
     *             if previously has been initiated
     */
    public synchronized void initialize() throws IOException,
            CertificateException {
        logger.trace("[IN]  initialize");

        if (hasInitiated()) {
            logger.error("Credential Manager already has been initiated");
            throw new IllegalStateException(
                    "Credential Manager already has been initiated");
        }

        if (areValidCertificates()) {
            initialized = true;
            openID = null;
        } else {
            throw new CertificateException("User certificates aren't valid");
        }
        logger.trace("[OUT] initialize");
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

        logger.trace("[IN]  readFile");

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

        logger.trace("[OUT] readFile");
        return sb.toString();
    }

    /**
     * Get credentials from ESGF IdP node and write it in file system (path:
     * &lt;user home folder&gt;/.[$ESG_HOME]).
     * 
     * @throws IOException
     *             if some error happens retrieving credentials from ESGF
     * @throws IllegalStateException
     *             if user openID hasn't configured
     */
    private void retrieveCredentials() throws IOException {
        logger.trace("[IN]  retrieveCredentials");

        if (openID == null) {
            logger.error("IllegalStateException. User openID hasn't configured");
            throw new IllegalStateException("User openID hasn't configured");
        }

        GSSCredential credential = null;
        OutputStream out = null;

        logger.debug("Getting connection with OpenID");
        String openIdURLStr = openID.getUserName();
        String username = (openIdURLStr
                .substring(openIdURLStr.lastIndexOf("/") + 1));

        try {
            logger.debug("Establishing connection with OpenID and getting CA's trustStore");
            HttpURLConnection openIdConnection = getOpenIdConnectionAndTrustStoreOfCA(openIdURLStr);
            openIdConnection.connect();

            // read openId XML document
            InputStream localInputStream = openIdConnection.getInputStream();
            Document localDocument = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(localInputStream);

            // Get myproxy-service info
            DOMSource domSource = new DOMSource(localDocument);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.transform(domSource, result);
            logger.debug("OpenID XML: \n" + writer.toString());

            logger.debug("Getting my proxy service from OpenId XML");
            // Get myproxy-service section in xml
            XPath localXPath = XPathFactory.newInstance().newXPath();
            XPathExpression localXPathExpression = localXPath
                    .compile("//*[Type='urn:esg:security:myproxy-service']/URI");
            String str = (String) localXPathExpression.evaluate(localDocument,
                    XPathConstants.STRING);
            String[] arrayOfString = str.split(":");
            String host = (arrayOfString[1].substring(2));
            int port = (Integer.parseInt(arrayOfString[2]));

            // Set new proxy
            GetParams params = new GetParams();
            params.setUserName(username);
            params.setPassphrase(String.valueOf(openID.getPassword()));
            params.setWantTrustroots(false);
            params.setLifetime(LIFE_TIME);

            MyProxy myProxy = new MyProxy(host, port);
            myProxy.bootstrapTrust();

            logger.debug("New myProxy object with parameters: {}, {}", params,
                    "host:" + host + ", port:" + port);

            logger.debug("Get credentials of user with myProxy service");
            credential = myProxy.get(null, params);

        } catch (Exception e) {
            logger.error("Error in retrive credentials:{}", e.getMessage());
            throw new IOException();
        }

        // save credentials.pem in file
        try {
            logger.debug("Save credentials in file {}", esgHome
                    + File.separator + CREDENTIALS_FILE_NAME_PEM);

            // write credentials file
            File f = new File(esgHome + File.separator
                    + CREDENTIALS_FILE_NAME_PEM);
            out = new FileOutputStream(f.getPath());
            Util.setFilePermissions(f.getPath(), 600);
            byte[] data = ((ExtendedGSSCredential) credential)
                    .export(ExtendedGSSCredential.IMPEXP_OPAQUE);
            out.write(data);
        } catch (Exception e) {
            logger.error("Error in retrive credentials:{}", e);
            throw new IOException();
        } finally {
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        logger.debug("Generating key store for netcdf HTTPSSLProvider");
        createKeyStoreFile(credential);

        logger.trace("[OUT] retrieveCredentials");
    }

    /**
     * Generate key store (type JKS) for be used by netcdf
     * {@link HTTPSSLProvider}
     * 
     * @param credential
     *            if is null, try read certificates.pem file in ESG_ENV.
     *            Otherwise use credential instance to get certificates
     */
    private void createKeyStoreFile(GSSCredential credential) {
        // must be type JKS
        KeyStore keystore;
        try {
            logger.debug("Generating X509Certificate from Credential in pem format");
            // read credential file
            String pem = readFile(new File(esgHome + File.separator
                    + CREDENTIALS_FILE_NAME_PEM));

            // Generate X509 certificate with PEMReader (org.bouncycastle)
            PEMReader reader;
            try {
                if (credential != null) {
                    reader = new PEMReader(
                            new InputStreamReader(
                                    new ByteArrayInputStream(
                                            ((ExtendedGSSCredential) credential)
                                                    .export(ExtendedGSSCredential.IMPEXP_OPAQUE))));
                } else {
                    pem = readFile(new File(esgHome + File.separator
                            + CREDENTIALS_FILE_NAME_PEM));

                    // Generate X509 certificate with PEMReader
                    // (org.bouncycastle)
                    // Credential.pem have RSA key and certificate in the same
                    // file and must be splitted
                    reader = new PEMReader(new InputStreamReader(
                            new ByteArrayInputStream(getFragmentOfPEM(pem,
                                    CERTIFICATE_PEM_HEADER,
                                    CERTIFICATE_PEM_FOOTER))));
                }

                x509Certificate = (X509Certificate) reader.readObject();
                logger.debug("X509Certificate has been generated:\n {}",
                        x509Certificate);

                logger.debug("Generating PrivateKey from Credential in pem format");
                // Generate PrivateKey also with PEMReader.
                // Used the another part of pem
                reader = new PEMReader(new InputStreamReader(
                        new ByteArrayInputStream(getFragmentOfPEM(pem,
                                RSA_PRIVATE_KEY_PEM_HEADER,
                                RSA_PRIVATE_KEY_PEM_FOOTER))));
                // PEMReader read a KeyPair class and then get the Private key
                KeyPair keyPair = (KeyPair) reader.readObject();
                PrivateKey key = keyPair.getPrivate();

                logger.debug("PrivateKey has been generated:\n {}", key);
                keystore = KeyStore.getInstance(KeyStore.getDefaultType());

                keystore.load(null);
                // new keystore (PrivateKeys, certificates)
                keystore.setCertificateEntry("cert-alias", x509Certificate);
                keystore.setKeyEntry("key-alias", key,
                        "changeit".toCharArray(),
                        new Certificate[] { x509Certificate });
                logger.debug("Generated key store of private key and X509Certificate.");
                // save credentials in keystore file
                keystore.store(new BufferedOutputStream(new FileOutputStream(
                        new File(esgHome + File.separator + KEYSTORE_FILE))),
                        KEYSTORE_PASSWORD.toCharArray());
            } catch (GSSException e) {
                logger.error("Error in retrive credentials:{}", e);

            }
        } catch (Exception e) {
            logger.warn("key store for netcdf isn't generated: {}",
                    e.getStackTrace());
        }
    }
}
