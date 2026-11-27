package org.fxt.freexmltoolkit.service;

import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Trusts the root certificates of the operating system next to the JDK's own list: the Windows certificate
 * store ({@code Windows-ROOT}) and the macOS keychain ({@code KeychainStore-ROOT}). A company proxy that inspects
 * HTTPS signs with a company root the IT department installs there - Java alone does not know it and every
 * HTTPS connection fails with "PKIX path building failed". Setting {@code ssl.systemTrust} (on by default),
 * installed JVM-wide (default {@link SSLContext} and {@link HttpsURLConnection} socket factory) by
 * {@link ConnectionServiceImpl#applyGlobalSettings()}. A certificate is accepted when the JDK's list or an
 * operating-system store accepts it; nothing is accepted that neither would.
 */
public final class SystemTrust {

    private static final Logger logger = LogManager.getLogger(SystemTrust.class);

    /** The setting; {@code true} unless set to {@code false}. */
    public static final String KEY = "ssl.systemTrust";

    private static SSLContext original;
    private static SSLSocketFactory originalFactory;
    private static SSLContext installed;

    private SystemTrust() {
    }

    /** @return whether the operating system's roots are trusted now (installed and a store was found) */
    public static synchronized boolean isInstalled() {
        return installed != null;
    }

    /** @return whether the setting is on in {@code properties} (default on) */
    public static boolean enabled(java.util.Properties properties) {
        return !"false".equalsIgnoreCase(properties.getProperty(KEY, "true").trim());
    }

    /**
     * Installs (or removes) the combined trust JVM-wide. Connections opened later use it; a client that already
     * built its own TLS context keeps it until it is created again.
     */
    public static synchronized void install(boolean enabled) {
        try {
            if (original == null) {
                original = SSLContext.getDefault();
                originalFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
            }
            if (!enabled) {
                if (installed != null) {
                    SSLContext.setDefault(original);
                    HttpsURLConnection.setDefaultSSLSocketFactory(originalFactory);
                    installed = null;
                }
                return;
            }
            if (installed != null) {
                return;
            }
            List<KeyStore> stores = systemStores(osName());
            if (stores.isEmpty()) {
                return; // Linux: the JDK's list (or the distribution's) is all there is
            }
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{combined(stores)}, null);
            SSLContext.setDefault(context);
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
            installed = context;
            logger.info("Trusting the operating system's root certificates too ({} store(s))", stores.size());
        } catch (GeneralSecurityException | RuntimeException e) {
            // never worse than before: the JDK's own list stays in place
            logger.warn("Could not add the operating system's root certificates: {}", e.getMessage());
        }
    }

    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }

    /** @return the loadable root stores of the operating system named {@code os} (lower case) */
    static List<KeyStore> systemStores(String os) {
        List<String> types = os.startsWith("windows") ? List.of("Windows-ROOT")
                : os.startsWith("mac") ? List.of("KeychainStore-ROOT", "KeychainStore") : List.of();
        List<KeyStore> stores = new ArrayList<>();
        for (String type : types) {
            try {
                KeyStore store = KeyStore.getInstance(type);
                store.load(null, null);
                stores.add(store);
            } catch (Exception e) {
                logger.debug("Root store {} not available: {}", type, e.getMessage());
            }
        }
        return stores;
    }

    /** @return a trust manager accepting what the JDK's list or one of {@code extra} accepts */
    static X509ExtendedTrustManager combined(List<KeyStore> extra) throws GeneralSecurityException {
        List<X509ExtendedTrustManager> managers = new ArrayList<>();
        managers.add(trustManager(null)); // the JDK's cacerts
        for (KeyStore store : extra) {
            managers.add(trustManager(store));
        }
        return new Combined(managers);
    }

    private static X509ExtendedTrustManager trustManager(KeyStore store) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509ExtendedTrustManager x509) {
                return x509;
            }
        }
        throw new GeneralSecurityException("no X509 trust manager");
    }

    /** Accepts a chain when one of its managers does (in order); else the first manager's rejection. */
    static final class Combined extends X509ExtendedTrustManager {
        private final List<X509ExtendedTrustManager> managers;

        Combined(List<X509ExtendedTrustManager> managers) {
            this.managers = List.copyOf(managers);
        }

        private interface Check {
            void run(X509ExtendedTrustManager manager) throws CertificateException;
        }

        private void any(Check check) throws CertificateException {
            CertificateException first = null;
            for (X509ExtendedTrustManager manager : managers) {
                try {
                    check.run(manager);
                    return;
                } catch (CertificateException e) {
                    if (first == null) {
                        first = e;
                    }
                }
            }
            throw first != null ? first : new CertificateException("no trust manager");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            any(m -> m.checkClientTrusted(chain, authType));
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            any(m -> m.checkServerTrusted(chain, authType));
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            any(m -> m.checkClientTrusted(chain, authType, socket));
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            any(m -> m.checkServerTrusted(chain, authType, socket));
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            any(m -> m.checkClientTrusted(chain, authType, engine));
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            any(m -> m.checkServerTrusted(chain, authType, engine));
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return managers.stream().flatMap(m -> Arrays.stream(m.getAcceptedIssuers()))
                    .toArray(X509Certificate[]::new);
        }
    }
}
