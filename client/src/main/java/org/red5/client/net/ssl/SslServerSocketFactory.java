package org.red5.client.net.ssl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;
import javax.net.ServerSocketFactory;

/**
 * Simple Server Socket factory to create sockets with or without SSL enabled. If SSL enabled a "bogus" SSL Context is used (suitable for
 * test purposes)
 *
 * @author The Apache MINA Project (dev@mina.apache.org)
 */
public class SslServerSocketFactory extends javax.net.ServerSocketFactory {
    private static boolean sslEnabled = false;

    private static javax.net.ServerSocketFactory sslFactory = null;

    private static ServerSocketFactory factory = null;

    public SslServerSocketFactory() {
        super();
    }

    @Override
    public ServerSocket createServerSocket(int port) throws IOException {
        return new ServerSocket(port);
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog) throws IOException {
        return new ServerSocket(port, backlog);
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
        return new ServerSocket(port, backlog, ifAddress);
    }

    public static javax.net.ServerSocketFactory getServerSocketFactory() throws IOException {
        if (isSslEnabled()) {
            if (sslFactory == null) {
                try {
                    sslFactory = BogusSslContextFactory.getInstance(true).getServerSocketFactory();
                } catch (GeneralSecurityException e) {
                    IOException ioe = new IOException("could not create SSL socket");
                    ioe.initCause(e);
                    throw ioe;
                }
            }
            return sslFactory;
        } else {
            if (factory == null) {
                factory = new SslServerSocketFactory();
            }
            return factory;
        }
    }

    public static boolean isSslEnabled() {
        return sslEnabled;
    }

    public static void setSslEnabled(boolean newSslEnabled) {
        sslEnabled = newSslEnabled;
    }
}
