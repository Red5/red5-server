package org.red5.client.net.ssl;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;

/**
 * Bogus trust manager factory. Creates BogusX509TrustManager
 *
 * @author The Apache MINA Project (dev@mina.apache.org)
 */
class BogusTrustManagerFactory extends TrustManagerFactorySpi {

    static final X509TrustManager X509 = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    static final TrustManager[] X509_MANAGERS = new TrustManager[] { X509 };

    public BogusTrustManagerFactory() {
    }

    @Override
    protected TrustManager[] engineGetTrustManagers() {
        return X509_MANAGERS;
    }

    @Override
    protected void engineInit(KeyStore keystore) throws KeyStoreException {
        // noop
    }

    @Override
    protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws InvalidAlgorithmParameterException {
        // noop
    }

}