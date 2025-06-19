package org.red5.resource;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Utility class to resolve server root
 */
public class Red5Root {

    private static final String PropertyRed5Root = "red5.root";

    private static final String VarRed5Home = "RED5_HOME";

    private static final String VarUnixPWD = "PWD";

    private static final String PropertyUserDir = "user.dir";

    private static String homeDirectory;

    private static ReentrantLock lookupLock = new ReentrantLock();

    /**
     * Get Root directory of server.
     * @return String path or throws exception.
     */
    public static String get() throws UnknownError {
        if (homeDirectory == null) {
            lookupLock.lock();//After acquiring the lock, ensure the condition directing this thread to the lock is still true.
            try {
                if (homeDirectory == null) {
                    homeDirectory = resolve();
                }
            } finally {
                lookupLock.unlock();
            }
        }
        if (homeDirectory == null) {
            throw new UnknownError("Server root path cannot be resolved from system/env properties or code location.");
        }
        return homeDirectory;
    }

    /**
     * Returns grandparent directory of this jar, assumed to be in lib folder of 'root/lib/red5-io-version.jar'
     * <p>
     * Uses:
     * <pre>{@code
     *   sysvar : 'red5.root
     *   envar  : 'RED5_HOME'
     *   envar  : 'PWD'
     *   sysvar : 'user.dir'}
     * </pre>
     *   As a last resort , it uses {@code Class::getProtectionDomain::getCodeSource::getLocation}
     *
     * @return server root or null.
     */
    private static String resolve() {
        /*
         We could also look up:
         sysvars: 'red5.config_root'='X:\red5\server\conf'
         sysvars: 'red5.plugins_root'='X:\red5\server/plugins'
         sysvars: 'red5.webapp.root'='X:\red5\server/webapps'
         */
        String path = null;
        try {
            path = System.getProperty(PropertyRed5Root);//Exported system property set by application launch script with value of server's directory. //red5.config_root
            if (path == null) {
                path = System.getenv(VarRed5Home);//Exported system property set by application launch script with value of server's directory.
                if (path == null) {
                    path = System.getenv(VarUnixPWD);//unix
                    if (path == null) {
                        path = System.getProperty(PropertyUserDir);//
                        if (path == null) {
                            //Last resort. find this jar location of lib folder, and resolve root directory.
                            path = Optional.of(Red5Root.class).map(Class::getProtectionDomain).map(ProtectionDomain::getCodeSource).map(CodeSource::getLocation).map(location -> {
                                try {
                                    return Paths.get(location.toURI());
                                } catch (Exception e) {
                                    // Wrap URI-specific issues
                                    throw new RuntimeException("Failed to convert URL to URI", e);
                                }
                            }).map(Path::getParent).map(Path::getParent).map(Path::toString).orElse(null);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            //Catch everything possible
        }
        return path;
    }
}
