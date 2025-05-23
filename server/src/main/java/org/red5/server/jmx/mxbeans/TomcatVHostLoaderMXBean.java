/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.jmx.mxbeans;

import javax.management.MXBean;
import jakarta.servlet.ServletException;

import org.apache.catalina.Host;
import org.apache.catalina.Valve;

/**
 * Simple mbean interface for Tomcat container virtual host loaders.
 *
 * @author The Red5 Project (red5@osflash.org)
 * @author Paul Gregoire (mondain@gmail.com)
 */
@MXBean
public interface TomcatVHostLoaderMXBean {

    /**
     * <p>startWebApplication.</p>
     *
     * @param applicationName a {@link java.lang.String} object
     * @return a boolean
     * @throws jakarta.servlet.ServletException if any.
     */
    public boolean startWebApplication(String applicationName) throws ServletException;

    /**
     * <p>getAutoDeploy.</p>
     *
     * @return a boolean
     */
    public boolean getAutoDeploy();

    /**
     * <p>setAutoDeploy.</p>
     *
     * @param autoDeploy a boolean
     */
    public void setAutoDeploy(boolean autoDeploy);

    /**
     * <p>getHost.</p>
     *
     * @return a {@link org.apache.catalina.Host} object
     */
    public Host getHost();

    /**
     * <p>getDomain.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getDomain();

    /**
     * <p>setDomain.</p>
     *
     * @param domain a {@link java.lang.String} object
     */
    public void setDomain(String domain);

    /**
     * <p>addAlias.</p>
     *
     * @param alias a {@link java.lang.String} object
     */
    public void addAlias(String alias);

    /**
     * <p>removeAlias.</p>
     *
     * @param alias a {@link java.lang.String} object
     */
    public void removeAlias(String alias);

    /**
     * <p>addContext.</p>
     *
     * @param path a {@link java.lang.String} object
     * @param docBase a {@link java.lang.String} object
     * @return a {@link org.apache.catalina.Context} object
     * @throws jakarta.servlet.ServletException if any.
     */
    public org.apache.catalina.Context addContext(String path, String docBase) throws ServletException;

    /**
     * <p>removeContext.</p>
     *
     * @param path a {@link java.lang.String} object
     */
    public void removeContext(String path);

    /**
     * <p>addValve.</p>
     *
     * @param valve a {@link org.apache.catalina.Valve} object
     */
    public void addValve(Valve valve);

    /**
     * <p>removeValve.</p>
     *
     * @param valveInfo a {@link java.lang.String} object
     */
    public void removeValve(String valveInfo);

    /**
     * <p>getLiveDeploy.</p>
     *
     * @return a boolean
     */
    public boolean getLiveDeploy();

    /**
     * <p>setLiveDeploy.</p>
     *
     * @param liveDeploy a boolean
     */
    public void setLiveDeploy(boolean liveDeploy);

    /**
     * <p>getName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName();

    /**
     * <p>setName.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void setName(String name);

    /**
     * <p>getStartChildren.</p>
     *
     * @return a boolean
     */
    public boolean getStartChildren();

    /**
     * <p>setStartChildren.</p>
     *
     * @param startChildren a boolean
     */
    public void setStartChildren(boolean startChildren);

    /**
     * <p>getUnpackWARs.</p>
     *
     * @return a boolean
     */
    public boolean getUnpackWARs();

    /**
     * <p>setUnpackWARs.</p>
     *
     * @param unpackWARs a boolean
     */
    public void setUnpackWARs(boolean unpackWARs);

    /**
     * <p>getWebappRoot.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getWebappRoot();

    /**
     * <p>setWebappRoot.</p>
     *
     * @param webappRoot a {@link java.lang.String} object
     */
    public void setWebappRoot(String webappRoot);

}
