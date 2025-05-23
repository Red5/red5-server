/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.util;

/**
 * Simple JEE server detector, based on an idea created by Brian Wing Shun Chan.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class ServerDetector {

    private static String type;

    private static boolean geronimo;

    private static boolean glassfish;

    private static boolean jboss;

    private static boolean jetty;

    private static boolean jonas;

    private static boolean resin;

    private static boolean tomcat;

    private static boolean weblogic;

    private static boolean websphere;

    /** Constant <code>GERONIMO_CLASS="/org/apache/geronimo/system/main/Daemon"{trunked}</code> */
    public static final String GERONIMO_CLASS = "/org/apache/geronimo/system/main/Daemon.class";

    /** Constant <code>GERONIMO_ID="geronimo"</code> */
    public static final String GERONIMO_ID = "geronimo";

    /** Constant <code>GLASSFISH_ID="glassfish"</code> */
    public static final String GLASSFISH_ID = "glassfish";

    /** Constant <code>GLASSFISH_SYSTEM_PROPERTY="com.sun.aas.instanceRoot"</code> */
    public static final String GLASSFISH_SYSTEM_PROPERTY = "com.sun.aas.instanceRoot";

    /** Constant <code>JBOSS_CLASS="/org/jboss/Main.class"</code> */
    public static final String JBOSS_CLASS = "/org/jboss/Main.class";

    /** Constant <code>JBOSS_ID="jboss"</code> */
    public static final String JBOSS_ID = "jboss";

    /** Constant <code>JETTY_CLASS="/org/mortbay/jetty/Server.class"</code> */
    public static final String JETTY_CLASS = "/org/mortbay/jetty/Server.class";

    /** Constant <code>JETTY_ID="jetty"</code> */
    public static final String JETTY_ID = "jetty";

    /** Constant <code>JONAS_CLASS="/org/objectweb/jonas/server/Server.clas"{trunked}</code> */
    public static final String JONAS_CLASS = "/org/objectweb/jonas/server/Server.class";

    /** Constant <code>JONAS_ID="jonas"</code> */
    public static final String JONAS_ID = "jonas";

    /** Constant <code>RESIN_CLASS="/com/caucho/server/resin/Resin.class"</code> */
    public static final String RESIN_CLASS = "/com/caucho/server/resin/Resin.class";

    /** Constant <code>RESIN_ID="resin"</code> */
    public static final String RESIN_ID = "resin";

    /** Constant <code>TOMCAT_BOOTSTRAP_CLASS="/org/apache/catalina/startup/Bootstrap."{trunked}</code> */
    public static final String TOMCAT_BOOTSTRAP_CLASS = "/org/apache/catalina/startup/Bootstrap.class";

    /** Constant <code>TOMCAT_EMBEDDED_CLASS="/org/apache/catalina/startup/Embedded.c"{trunked}</code> */
    public static final String TOMCAT_EMBEDDED_CLASS = "/org/apache/catalina/startup/Embedded.class";

    /** Constant <code>TOMCAT_ID="tomcat"</code> */
    public static final String TOMCAT_ID = "tomcat";

    /** Constant <code>WEBLOGIC_CLASS="/weblogic/Server.class"</code> */
    public static final String WEBLOGIC_CLASS = "/weblogic/Server.class";

    /** Constant <code>WEBLOGIC_ID="weblogic"</code> */
    public static final String WEBLOGIC_ID = "weblogic";

    /** Constant <code>WEBSPHERE_CLASS="/com/ibm/websphere/product/VersionInfo."{trunked}</code> */
    public static final String WEBSPHERE_CLASS = "/com/ibm/websphere/product/VersionInfo.class";

    /** Constant <code>WEBSPHERE_ID="websphere"</code> */
    public static final String WEBSPHERE_ID = "websphere";

    static {
        // do the check only once per classloader / execution
        type = getServerType();
    }

    private static String getServerType() {
        if (type == null) {

            String tmp = null;

            if (isGeronimo()) {
                tmp = GERONIMO_ID;
            } else if (isGlassfish()) {
                tmp = GLASSFISH_ID;
            } else if (isJBoss()) {
                tmp = JBOSS_ID;
            } else if (isJOnAS()) {
                tmp = JONAS_ID;
            } else if (isResin()) {
                tmp = RESIN_ID;
            } else if (isWebLogic()) {
                tmp = WEBLOGIC_ID;
            } else if (isWebSphere()) {
                tmp = WEBSPHERE_ID;
            }
            //check for tomcat or jetty - standalone or embedded
            if (isTomcat()) {
                if (tmp == null) {
                    tmp = TOMCAT_ID;
                } else {
                    tmp += "-" + TOMCAT_ID;
                }
            } else if (isJetty()) {
                if (tmp == null) {
                    tmp = JETTY_ID;
                } else {
                    tmp += "-" + JETTY_ID;
                }
            }

            if (tmp == null) {
                throw new RuntimeException("Server is not supported");
            }

            return tmp;
        } else {
            return type;
        }

    }

    /**
     * <p>isGeronimo.</p>
     *
     * @return a boolean
     */
    public static boolean isGeronimo() {
        if (!geronimo) {
            geronimo = detect(GERONIMO_CLASS);
        }
        return geronimo;
    }

    /**
     * <p>isGlassfish.</p>
     *
     * @return a boolean
     */
    public static boolean isGlassfish() {
        if (!glassfish) {
            String value = System.getProperty(GLASSFISH_SYSTEM_PROPERTY);
            if (value != null) {
                glassfish = true;
            }
        }
        return glassfish;
    }

    /**
     * <p>isJBoss.</p>
     *
     * @return a boolean
     */
    public static boolean isJBoss() {
        if (!jboss) {
            jboss = detect(JBOSS_CLASS);
        }
        return jboss;
    }

    /**
     * <p>isJetty.</p>
     *
     * @return a boolean
     */
    public static boolean isJetty() {
        if (!jetty) {
            jetty = detect(JETTY_CLASS);
        }
        return jetty;
    }

    /**
     * <p>isJOnAS.</p>
     *
     * @return a boolean
     */
    public static boolean isJOnAS() {
        if (!jonas) {
            jonas = detect(JONAS_CLASS);
        }
        return jonas;
    }

    /**
     * <p>isResin.</p>
     *
     * @return a boolean
     */
    public static boolean isResin() {
        if (!resin) {
            resin = detect(RESIN_CLASS);
        }
        return resin;
    }

    /**
     * <p>isTomcat.</p>
     *
     * @return a boolean
     */
    public static boolean isTomcat() {
        if (!tomcat) {
            tomcat = detect(TOMCAT_BOOTSTRAP_CLASS);
            //check embedded
            if (!tomcat) {
                tomcat = detect(TOMCAT_EMBEDDED_CLASS);
            }
        }
        return tomcat;
    }

    /**
     * <p>isWebLogic.</p>
     *
     * @return a boolean
     */
    public static boolean isWebLogic() {
        if (!weblogic) {
            weblogic = detect(WEBLOGIC_CLASS);
        }
        return weblogic;
    }

    /**
     * <p>isWebSphere.</p>
     *
     * @return a boolean
     */
    public static boolean isWebSphere() {
        if (!websphere) {
            websphere = detect(WEBSPHERE_CLASS);
        }
        return websphere;
    }

    private static boolean detect(String className) {
        try {
            ClassLoader.getSystemClassLoader().loadClass(className);
            return true;
        } catch (ClassNotFoundException cnfe) {
            try {
                //try the current classloader
                Thread.currentThread().getContextClassLoader().loadClass(className);
                return true;
            } catch (ClassNotFoundException cnfe2) {
                return false;
            }
        }
    }

}
