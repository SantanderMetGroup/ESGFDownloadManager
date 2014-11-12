/**
 *
 */
package es.unican.meteo.esgf.petition;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPrivateKeySpec;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.util.encoders.Base64;
import org.ietf.jgss.GSSCredential;
import org.w3c.dom.Document;

import ucar.nc2.util.net.HTTPSSLProvider;
import edu.uiuc.ncsa.MyProxy.MyProxyLogon;

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
    private static final String FEDERATION_TRUSTSTORE_URL = "https://raw.github.com/ESGF/esgf-dist/master/installer/certs/esg-truststore.ts";
    private static String ESGF_CA_CERTS_URL = "https://raw.githubusercontent.com/ESGF/esgf-dist/master/installer/certs/esg_trusted_certificates.tar";
    // private static final String FEDERATION_TRUSTSTORE_URL =
    // "https://rainbow.llnl.gov/dist/certs/esg-truststore.ts";

    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final int LIFE_TIME = 259200;
    private static final String RSA_PRIVATE_KEY_PEM_FOOTER = "-----END RSA PRIVATE KEY-----\n";
    private static final String RSA_PRIVATE_KEY_PEM_HEADER = "-----BEGIN RSA PRIVATE KEY-----\n";
    private static final String SSLCONTEXT = "TLS";
    private static final String TRUSTSTORE_FILE_NAME = "esg-truststore.ts";
    private static final String CERTIFICATE_PEM_FOOTER = "-----END CERTIFICATE-----\n";
    private static final String CERTIFICATE_PEM_HEADER = "-----BEGIN CERTIFICATE-----\n";
    private static final String CREDENTIALS_FILE_NAME_PEM = "credentials.pem";
    private static final String DEFAULT_ESG_FOLDER = ".esg";
    private static final String ESG_HOME_ENV_VAR = "ESG_HOME";
    private static final String KEYSTORE_FILE = "keystore.ks";
    private static final String TEMP_X509_CERTIFICATES = "temp_certs";

    /** User's folder for ESG credentials. */
    private static String esgHome;

    /** State of Credentials Manager (Inititialized or not). */
    private boolean initialized;

    /** Singleton instance. */
    private static CredentialsManager INSTANCE = null;

    /** Logger. */
    static private org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(CredentialsManager.class);

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

    /** X.509 user certificate. */
    private X509Certificate x509Certificate;

    /** Another X.509 certificates retrieved with myproxy server. */
    private List<X509Certificate> anotherCerts;

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

        // If .esg directory doesn't exist then create new
        File esgDirectory = new File(esgHome);
        if (!esgDirectory.exists()) {
            esgDirectory.mkdir();
            esgDirectory.setExecutable(true);
            esgDirectory.setReadable(true);
            esgDirectory.setWritable(true);
            logger.debug(".esg is created");
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

        anotherCerts = new LinkedList<X509Certificate>();
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
            // Get certificates of local file
            getX509CertificatesFromPem();

            // TODO new versions of ESGF not always have
            // CN name in ldapDN.getRdn(3); pos=3
            // find out how solution it
            // if (openID != null) {

            // check if the same user (if open id user is set)
            // String dn = cert.getSubjectX500Principal().getName();
            // LdapName ldapDN = new LdapName(dn);
            // Rdn rdn = ldapDN.getRdn(3);
            // String certOpenID = rdn.getValue().toString();
            //
            // if (!certOpenID.equals(openID.getUserName())) {
            // return false;
            // }
            // }
            if (x509Certificate == null) {
                return false;
            }

            // Checking vality of certificate. checValidity() throws
            // CertificateExpiredException if was expired or
            // CertificateNotYetValidException if not valid
            x509Certificate.checkValidity();

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
        }

        logger.debug("Generating key store (type JKS) for be used by netcdf HTTPSSLProvider");
        createKeyStoreFile();

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

            // ------------------------------------------------
            // KeyManagerFactory ------------------------------
            logger.debug("Generating key manager factory");

            logger.debug("Generating X509Certificate from Credential in pem format");
            x509Certificate = getX509UserFromPem();
            logger.debug("X509Certificate has been generated:\n {}",
                    x509Certificate);

            logger.debug("Generating PrivateKey from Credential in pem format");
            PrivateKey key = getPrivateKeyFromPem();
            logger.debug("PrivateKey has been generated:\n {}", key);

            // certificates [] <- user certificates and anothers
            Certificate[] certificates = new Certificate[anotherCerts.size() + 1];
            certificates[0] = x509Certificate;
            for (int i = 0; i < anotherCerts.size(); i++) {
                certificates[i + 1] = anotherCerts.get(i);
            }

            // key store must be type JCEKS (asymmetric key)
            KeyStore keystore = KeyStore.getInstance("JCEKS");
            keystore.load(null);
            // new keystore (PrivateKeys, certificates)
            keystore.setCertificateEntry("cert-alias", x509Certificate);
            keystore.setKeyEntry("key-alias", key, "changeit".toCharArray(),
                    certificates);
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

    private PrivateKey getPrivateKeyFromPem() throws Exception {
        PrivateKey key = null;
        String pem;
        try {
            pem = readFile(new File(esgHome + File.separator
                    + CREDENTIALS_FILE_NAME_PEM));

            byte[] bytes = getFragmentOfPEM(pem, RSA_PRIVATE_KEY_PEM_HEADER,
                    RSA_PRIVATE_KEY_PEM_FOOTER);

            String rsa = new String(bytes);
            String split[] = rsa.split("-----");
            rsa = split[2];

            ASN1Sequence primitive = (ASN1Sequence) ASN1Sequence
                    .fromByteArray(Base64.decode(rsa.getBytes()));

            Enumeration<?> e = primitive.getObjects();
            BigInteger v = ((DERInteger) e.nextElement()).getValue();

            int version = v.intValue();
            if (version != 0 && version != 1) {
                throw new IllegalArgumentException(
                        "wrong version for RSA private key");
            }

            /**
             * In fact only modulus and private exponent are in use.
             */
            BigInteger modulus = ((DERInteger) e.nextElement()).getValue();
            BigInteger publicExponent = ((DERInteger) e.nextElement())
                    .getValue();
            BigInteger privateExponent = ((DERInteger) e.nextElement())
                    .getValue();
            BigInteger prime1 = ((DERInteger) e.nextElement()).getValue();
            BigInteger prime2 = ((DERInteger) e.nextElement()).getValue();
            BigInteger exponent1 = ((DERInteger) e.nextElement()).getValue();
            BigInteger exponent2 = ((DERInteger) e.nextElement()).getValue();
            BigInteger coefficient = ((DERInteger) e.nextElement()).getValue();

            RSAPrivateKeySpec rsaPrivKeySpec = new RSAPrivateKeySpec(modulus,
                    privateExponent);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            key = kf.generatePrivate(rsaPrivKeySpec);

        } catch (Exception e) {
            logger.error("Error in getPrivateKeyFromPem(): {}", e);
            throw e;
        }

        return key;
    }

    private X509Certificate getX509UserFromPem() throws Exception {

        X509Certificate x509Certificate = null;
        String pem;
        try {
            pem = readFile(new File(esgHome + File.separator
                    + CREDENTIALS_FILE_NAME_PEM));

            // Credential.pem have RSA key and certificate in the same file
            // and must be splitted

            byte[] bytes = getFragmentOfPEM(pem, CERTIFICATE_PEM_HEADER,
                    CERTIFICATE_PEM_FOOTER);

            CertificateFactory certFactory = CertificateFactory
                    .getInstance("X.509");
            InputStream in = new ByteArrayInputStream(bytes);
            x509Certificate = (X509Certificate) certFactory
                    .generateCertificate(in);

        } catch (Exception e) {
            logger.error("Error in getX509FromPem(): {}", e);
            throw e;
        }

        return x509Certificate;
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
     * Read certificates of pem and returns array of certificates
     *
     * @param pem
     * @return array of {@link X509Certificate}
     * @throws CertificateException
     */
    public X509Certificate[] readX509CertificatesFromPem(String pem)
            throws CertificateException {

        CertificateFactory certFactory = CertificateFactory
                .getInstance("X.509");

        String[] tokens1 = pem.split(CERTIFICATE_PEM_HEADER);
        if (tokens1.length < 2) {
            throw new IllegalArgumentException(
                    "The PEM data does not contain the requested header");
        }

        int certNumber = tokens1.length - 1;

        X509Certificate[] certificates = new X509Certificate[certNumber];

        // first is the user cert
        String[] tokens2 = tokens1[1].split(CERTIFICATE_PEM_FOOTER);
        tokens2[0] = CERTIFICATE_PEM_HEADER + tokens2[0]
                + CERTIFICATE_PEM_FOOTER;
        InputStream in = new ByteArrayInputStream(tokens2[0].getBytes());
        certificates[0] = (X509Certificate) certFactory.generateCertificate(in);

        for (int i = 2; i < tokens1.length; i++) {
            tokens2 = tokens1[i].split(CERTIFICATE_PEM_FOOTER);
            tokens2[0] = CERTIFICATE_PEM_HEADER + tokens2[0]
                    + CERTIFICATE_PEM_FOOTER;
            in = new ByteArrayInputStream(tokens2[0].getBytes());
            certificates[i - 1] = (X509Certificate) certFactory
                    .generateCertificate(in);
        }

        return certificates;
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
            URL trustURL = new URL(FEDERATION_TRUSTSTORE_URL);
            keyStore.load(trustURL.openStream(),
                    KEYSTORE_PASSWORD.toCharArray());

            // Generate trust store factory
            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            logger.debug("Saving keystore of CA's");
            // If .esg directory doesn't exist then create new
            File esgDirectory = new File(esgHome);
            if (!esgDirectory.exists()) {
                esgDirectory.mkdir();
                esgDirectory.setExecutable(true);
                esgDirectory.setReadable(true);
                esgDirectory.setWritable(true);
                logger.debug(".esg is created");
            }
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
     * Read x509 certificates in file system (path: &lt;user home
     * folder&gt;/.[$ESG_HOME]/credentialas.pem) and return X509 user
     * certificate.
     *
     *
     * @return
     * @throws IOException
     *             if error happens reading X509 certificate in file system
     */
    private X509Certificate getX509CertificatesFromPem() throws IOException {
        logger.trace("[IN]  getX509CertificateFromFileSystem");

        String pem;
        try {
            pem = readFile(new File(esgHome + File.separator
                    + CREDENTIALS_FILE_NAME_PEM));

            X509Certificate[] certificates = readX509CertificatesFromPem(pem);

            this.x509Certificate = certificates[0];
            this.anotherCerts = new LinkedList<X509Certificate>();
            for (int i = 1; i < certificates.length; i++) {
                anotherCerts.add(certificates[i]);
            }
            logger.trace("[OUT] getX509CertificatesFromPem");
            return x509Certificate;
        } catch (Exception e) {
            logger.error("Error reading X509 certificates in file system: {}",
                    esgHome + File.separator + CREDENTIALS_FILE_NAME_PEM);
            throw new IOException(
                    "Error reading X509 certificates in file system " + esgHome
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
        try {
            retrieveCredentials();
        } catch (Exception e) {
            logger.error("Error renewing credentials: {}", e.getMessage());
        }
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

        // If already read (in retrieve createdSocketFactory)
        if (x509Certificate != null) {
            logger.trace("[OUT] getX509Certificate");
            return x509Certificate;
        } else {
            logger.trace("[OUT] getX509Certificate");
            return getX509CertificatesFromPem();
        }

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

        X509Certificate cert = getX509CertificatesFromPem();
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
     * @return MessageError String if connection failed or null is success
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
        } catch (Exception e) {
            logger.error("Error initializing credential manager:{} "
                    + e.getMessage());
            openID = null; // set a null openID
            initialized = false;
            throw new IOException(e.getMessage());
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
     * @throws Exception
     *
     * @throws IllegalStateException
     *             if user openID hasn't configured
     */
    private void retrieveCredentials() throws Exception {
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

            logger.debug("Getting ESGF CAs certificates...");
            try {
                getCASCertificates();
            } catch (ArchiveException e1) {
                throw new IOException(e1.getMessage(), e1.getCause());
            }

            // New myproxylogon
            MyProxyLogon mProxyLogon = new MyProxyLogon();

            mProxyLogon.setUsername(username);
            mProxyLogon.setPassphrase(String.valueOf(openID.getPassword()));
            mProxyLogon.setHost(host);
            mProxyLogon.setPort(port);
            mProxyLogon.setLifetime(LIFE_TIME);
            mProxyLogon.requestTrustRoots(true);

            logger.debug("New myProxylogon object with parameters: {}",
                    mProxyLogon);
            logger.debug("Get credentials of user with myProxy service");
            mProxyLogon.getCredentials();
            logger.debug("get credentials success!");

            // Getting X509Certificate from MyProxyLogon
            Collection<X509Certificate> x509Certificates = mProxyLogon
                    .getCertificates();

            logger.debug("number of certificates x509 retrieved:"
                    + x509Certificates.size());

            Iterator<X509Certificate> iter = x509Certificates.iterator();
            x509Certificate = iter.next();
            logger.debug("X509Certificate has been generated:\n {}",
                    x509Certificate);

            // Getting PrivateKey from MyProxyLogon
            PrivateKey key = mProxyLogon.getPrivateKey();
            logger.debug("PrivateKey has been generated:\n {}", key.toString());

            logger.debug("Generating credentials in pem format");
            FileOutputStream ous = new FileOutputStream(esgHome
                    + File.separator + CREDENTIALS_FILE_NAME_PEM);

            logger.debug("Writing x509 certificate in pem format...");
            ous.write(CERTIFICATE_PEM_HEADER.getBytes());
            writeBASE64(x509Certificate.getEncoded(), ous);
            ous.write(CERTIFICATE_PEM_FOOTER.getBytes());

            logger.debug("Transforming ASN.1 PKCS#8 private key to ASN1PKCS#1 format...");
            byte[] bytes = getPKCS1BytesFromPKCS8Bytes(key.getEncoded());

            logger.debug("Writing rsa private key in pem format...");
            ous.write(RSA_PRIVATE_KEY_PEM_HEADER.getBytes());
            writeBASE64(bytes, ous);
            ous.write(RSA_PRIVATE_KEY_PEM_FOOTER.getBytes());

            // Write another x509 certificates if exists
            anotherCerts = new LinkedList<X509Certificate>();
            for (int i = 1; i < x509Certificates.size(); i++) {
                X509Certificate cert = iter.next();
                logger.debug("certificate[{}]:{}", i, cert);
                logger.debug("Writing certificate number {} retrieved...", i);
                ous.write(CERTIFICATE_PEM_HEADER.getBytes());
                writeBASE64(cert.getEncoded(), ous);
                ous.write(CERTIFICATE_PEM_FOOTER.getBytes());

                anotherCerts.add(cert);
            }

            ous.close();

            logger.debug("PEM has been generated in " + esgHome
                    + File.separator + CREDENTIALS_FILE_NAME_PEM);

            logger.debug("Generating key store for netcdf HTTPSSLProvider");
            createKeyStoreFile(x509Certificate, key);

        } catch (GeneralSecurityException e) {
            logger.error("Error in retrieve credentials:{}", e.getMessage());
            throw e;
        } catch (Exception e) {
            throw e;
        }

        logger.trace("[OUT] retrieveCredentials");
    }

    private void getCASCertificates() throws IOException, ArchiveException {
        URL url = new URL(ESGF_CA_CERTS_URL);
        URLConnection connection = url.openConnection();
        InputStream is = connection.getInputStream();
        writeCAsCertificates(is, esgHome + File.separator + "certificates");
    }

    private void writeCAsCertificates(InputStream in, String caDirectoryPath)
            throws IOException, ArchiveException {
        // read tar from ESGF URL
        String tempPath = System.getProperty("java.io.tmpdir") + File.separator
                + "esg-certificates.tar";
        File tarFile = new File(tempPath);
        OutputStream ous = new FileOutputStream(tarFile);
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            ous.write(buf, 0, len);
        }
        ous.close();
        in.close();

        // untar certificates
        String dir = System.getProperty("java.io.tmpdir") + File.separator;
        File tempCertDir = new File(dir);
        List<File> certs = unTar(tarFile, tempCertDir);

        // Copy untar certs in $ESG_HOME/certificates
        File caDirectory = new File(caDirectoryPath);
        if (!caDirectory.exists()) {
            caDirectory.mkdir();
        }

        for (File cert : certs) {
            if (!cert.isDirectory()) {
                File outputFile = new File(caDirectory, cert.getName());
                final OutputStream outputFileStream = new FileOutputStream(
                        outputFile);
                IOUtils.copy(new FileInputStream(cert), new FileOutputStream(
                        outputFile));
                outputFileStream.close();
            }
        }
    }

    /**
     * Untar an input file into an output file. The output file is created in
     * the output folder, having the same name as the input file, minus the
     * '.tar' extension.
     *
     * @param inputFile
     *            the input .tar file
     * @param outputDir
     *            the output directory file.
     * @throws IOException
     * @throws FileNotFoundException
     *
     * @return The {@link List} of {@link File}s with the untared content.
     * @throws ArchiveException
     */
    private static List<File> unTar(final File inputFile, final File outputDir)
            throws FileNotFoundException, IOException, ArchiveException {

        logger.debug(String.format("Untaring %s to dir %s.",
                inputFile.getAbsolutePath(), outputDir.getAbsolutePath()));

        final List<File> untaredFiles = new LinkedList<File>();
        final InputStream is = new FileInputStream(inputFile);
        final TarArchiveInputStream debInputStream = (TarArchiveInputStream) new ArchiveStreamFactory()
                .createArchiveInputStream("tar", is);
        TarArchiveEntry entry = null;
        while ((entry = (TarArchiveEntry) debInputStream.getNextEntry()) != null) {
            final File outputFile = new File(outputDir, entry.getName());
            if (entry.isDirectory()) {
                logger.debug(String.format(
                        "Attempting to write output directory %s.",
                        outputFile.getAbsolutePath()));
                if (!outputFile.exists()) {
                    logger.info(String.format(
                            "Attempting to create output directory %s.",
                            outputFile.getAbsolutePath()));
                    if (!outputFile.mkdirs()) {
                        throw new IllegalStateException(String.format(
                                "Couldn't create directory %s.",
                                outputFile.getAbsolutePath()));
                    }
                }
            } else {
                logger.debug(String.format("Creating output file %s.",
                        outputFile.getAbsolutePath()));
                final OutputStream outputFileStream = new FileOutputStream(
                        outputFile);
                IOUtils.copy(debInputStream, outputFileStream);
                outputFileStream.close();
            }
            untaredFiles.add(outputFile);
        }
        debInputStream.close();

        return untaredFiles;
    }

    /**
     * Generate key store (type JKS) for be used by netcdf
     * {@link HTTPSSLProvider}
     *
     */
    private void createKeyStoreFile(X509Certificate x509Certificate,
            PrivateKey key) {
        // must be type JKS
        KeyStore keystore;
        try {
            // certificates [] <- user certificates and anothers
            Certificate[] certificates = new Certificate[anotherCerts.size() + 1];
            certificates[0] = x509Certificate;
            for (int i = 0; i < anotherCerts.size(); i++) {
                certificates[i + 1] = anotherCerts.get(i);
            }

            keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(null);
            // new keystore (PrivateKeys, certificates)
            keystore.setCertificateEntry("cert-alias", x509Certificate);
            keystore.setKeyEntry("key-alias", key, "changeit".toCharArray(),
                    certificates);
            logger.debug("Generated key store of private key and X509Certificate.");
            // save credentials in keystore file
            keystore.store(new BufferedOutputStream(new FileOutputStream(
                    new File(esgHome + File.separator + KEYSTORE_FILE))),
                    KEYSTORE_PASSWORD.toCharArray());

        } catch (Exception e) {
            logger.warn("key store for netcdf isn't generated: {}",
                    e.getStackTrace());
        }
    }

    /**
     * Generate key store (type JKS) for be used by netcdf
     * {@link HTTPSSLProvider}
     *
     */
    private void createKeyStoreFile() {
        // must be type JKS
        KeyStore keystore;
        try {
            logger.debug("Generating X509Certificate from Credential in pem format");
            x509Certificate = getX509UserFromPem();
            logger.debug("X509Certificate has been generated:\n {}",
                    x509Certificate);

            logger.debug("Generating PrivateKey from Credential in pem format");
            PrivateKey key = getPrivateKeyFromPem();

            logger.debug("PrivateKey has been generated:\n {}", key);

            // certificates [] <- user certificates and anothers
            Certificate[] certificates = new Certificate[anotherCerts.size() + 1];
            certificates[0] = x509Certificate;
            for (int i = 0; i < anotherCerts.size(); i++) {
                certificates[i + 1] = anotherCerts.get(i);
            }

            keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(null);
            // new keystore (PrivateKeys, certificates)
            keystore.setCertificateEntry("cert-alias", x509Certificate);
            keystore.setKeyEntry("key-alias", key, "changeit".toCharArray(),
                    certificates);
            logger.debug("Generated key store of private key and X509Certificate.");
            // save credentials in keystore file
            keystore.store(new BufferedOutputStream(new FileOutputStream(
                    new File(esgHome + File.separator + KEYSTORE_FILE))),
                    KEYSTORE_PASSWORD.toCharArray());

        } catch (Exception e) {
            logger.warn("key store for netcdf isn't generated: {}", e);
        }

    }

    /**
     * Convert PKCS#8 format into PKCS#1 format.
     *
     * @param bytes
     *            bytes of PKCS#8 private key
     * @return byte array of private key in format PKCS#1
     */
    private static byte[] getPKCS1BytesFromPKCS8Bytes(byte[] bytes) {
        /*
         * DER format: http://en.wikipedia.org/wiki/Distinguished_Encoding_Rules
         * PKCS#8: http://tools.ietf.org/html/rfc5208
         */
        byte[] pkcs1Bytes = null;
        int bIndex = 0;

        // Start with PrivateKeyInfo::=SEQUENCE
        // 0x30 Sequence
        if (bytes[bIndex] != 48) {
            logger.error("Not a PKCS#8 private key");
            throw new IllegalArgumentException("Not a PKCS#8 private key");
        }

        // next byte contain the number of bytes
        // of SEQUENCE element (length field)
        ++bIndex;

        // Get number of bytes of element
        int sizeOfContent = getSizeOfContent(bytes, bIndex);
        int sizeOfLengthField = getSizeOfLengthField(bytes, bIndex);

        logger.debug("PrivateKeyInfo(SEQUENCE): Number of bytes:"
                + sizeOfContent
                + "PrivateKeyInfo(SEQUENCE): Number of bytes of length field:"
                + sizeOfLengthField);

        // version::=INTEGER
        // shift index to version element
        bIndex += sizeOfLengthField;

        // 0x02 Integer
        if (bytes[bIndex] != 2) {
            logger.error("Not a PKCS#8 private key");
            throw new IllegalArgumentException("Not a PKCS#8 private key");
        }
        ++bIndex;

        // Get number of bytes of element
        sizeOfContent = getSizeOfContent(bytes, bIndex);
        sizeOfLengthField = getSizeOfLengthField(bytes, bIndex);

        logger.debug("Version(INTEGER): Number of bytes:" + sizeOfContent
                + "Version(INTEGER): Number of bytes of length field:"
                + sizeOfLengthField);

        // PrivateKeyAlgorithm::= PrivateKeyAlgorithmIdentifier
        // shift index to PrivateKeyAlgorithm element
        bIndex = bIndex + sizeOfLengthField + sizeOfContent;

        // ? PrivateKeyAlgorithmIdentifier
        // if (bytes[bIndex] != ?) {
        // throw new IllegalArgumentException("Not a PKCS#8 private key");
        // }

        ++bIndex;

        // Get number of bytes of element
        sizeOfContent = getSizeOfContent(bytes, bIndex);
        sizeOfLengthField = getSizeOfLengthField(bytes, bIndex);
        logger.debug("PrivateKeyAlgorithm(PrivateKeyAlgorithmIdentifier): Number of bytes:"
                + sizeOfContent
                + "PrivateKeyAlgorithm(PrivateKeyAlgorithmIdentifier): "
                + "Number of bytes of length field:" + sizeOfLengthField);

        // PrivateKey::= OCTET STRING
        // shift index to PrivateKey element
        bIndex = bIndex + sizeOfLengthField + sizeOfContent;

        // 0x04 OCTET STRING
        if (bytes[bIndex] != 4) {
            throw new IllegalArgumentException("Not a PKCS#8 private key");
        }
        ++bIndex;

        // Get number of bytes of element
        sizeOfContent = getSizeOfContent(bytes, bIndex);
        sizeOfLengthField = getSizeOfLengthField(bytes, bIndex);

        logger.debug("PrivateKey(OCTET STRING: Number of bytes:"
                + sizeOfContent
                + "PrivateKey(OCTET STRING): Number of bytes of length field:"
                + sizeOfLengthField);

        return Arrays.copyOfRange(bytes, bIndex + sizeOfLengthField, bIndex
                + sizeOfLengthField + sizeOfContent);
    }

    private static int getSizeOfLengthField(byte[] bytes, int bIndex) {
        byte aux = bytes[bIndex];

        if ((aux & 0x80) == 0) { // applies mask
            return 1; // short form
        }
        return ((aux & 0x7F) + 1); // long form
    }

    private static int getSizeOfContent(byte[] bytes, int bIndex) {
        byte aux = bytes[bIndex];

        if ((aux & 0x80) == 0) { // applies mask
            // short form
            return aux;
        }

        /*
         * long form: if first bit begins with 1 then the rest of bits are the
         * number of bytes that contain the number of bytes of element 375 is
         * 101110111 then in 2 bytes: 00000001 01110111 that is the number of
         * bytes that contain the number of bytes ex: 375 is 101110111 then in 2
         * bytes: 00000001 01110111 .
         */
        byte numOfBytes = (byte) (aux & 0x7F);

        if (numOfBytes * 8 > 32) {
            throw new IllegalArgumentException("ASN.1 field too long");
        }

        int contentLength = 0;

        // find out the number of bits in the bytes
        for (int i = 0; i < numOfBytes; ++i) {
            contentLength = (contentLength << 8) + bytes[(bIndex + 1 + i)];
        }

        return contentLength;
    }

    /**
     * Write bytes encoded in base 64 into output stream
     *
     * @param bytes
     *            to encoded
     * @param out
     *            output stream of bytes
     * @throws IOException
     *             if an I/O error occurs.
     */
    private void writeBASE64(byte[] bytes, OutputStream out) throws IOException {
        logger.debug("Encoding in base64...");
        byte[] arrayOfByte = Base64.encode(bytes);
        for (int i = 0; i < arrayOfByte.length; i += 64) {
            if (arrayOfByte.length - i > 64) {
                out.write(arrayOfByte, i, 64);
            } else {
                out.write(arrayOfByte, i, arrayOfByte.length - i);
            }
            out.write("\n".getBytes());
        }
    }
}
