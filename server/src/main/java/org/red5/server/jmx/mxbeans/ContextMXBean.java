/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.jmx.mxbeans;

import java.io.IOException;

import javax.management.MXBean;

import org.red5.server.api.IClientRegistry;
import org.red5.server.api.IMappingStrategy;
import org.red5.server.api.persistence.IPersistenceStore;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.IScopeHandler;
import org.red5.server.api.service.IServiceInvoker;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

/**
 * <p>
 * This is basic context implementation used by Red5.
 * </p>
 *
 * @author mondain
 */
@MXBean
public interface ContextMXBean {

    /**
     * <p>getGlobalScope.</p>
     *
     * @return a {@link org.red5.server.api.scope.IScope} object
     */
    public IScope getGlobalScope();

    /**
     * <p>resolveScope.</p>
     *
     * @param path a {@link java.lang.String} object
     * @return a {@link org.red5.server.api.scope.IScope} object
     */
    public IScope resolveScope(String path);

    /**
     * <p>resolveScope.</p>
     *
     * @param root a {@link org.red5.server.api.scope.IScope} object
     * @param path a {@link java.lang.String} object
     * @return a {@link org.red5.server.api.scope.IScope} object
     */
    public IScope resolveScope(IScope root, String path);

    /**
     * <p>getPersistanceStore.</p>
     *
     * @return a {@link org.red5.server.api.persistence.IPersistenceStore} object
     */
    public IPersistenceStore getPersistanceStore();

    /**
     * <p>getApplicationContext.</p>
     *
     * @return a {@link org.springframework.context.ApplicationContext} object
     */
    public ApplicationContext getApplicationContext();

    /**
     * <p>setContextPath.</p>
     *
     * @param contextPath a {@link java.lang.String} object
     */
    public void setContextPath(String contextPath);

    /**
     * <p>getClientRegistry.</p>
     *
     * @return a {@link org.red5.server.api.IClientRegistry} object
     */
    public IClientRegistry getClientRegistry();

    /**
     * <p>getScope.</p>
     *
     * @return a {@link org.red5.server.api.scope.IScope} object
     */
    public IScope getScope();

    /**
     * <p>getServiceInvoker.</p>
     *
     * @return a {@link org.red5.server.api.service.IServiceInvoker} object
     */
    public IServiceInvoker getServiceInvoker();

    /**
     * <p>lookupService.</p>
     *
     * @param serviceName a {@link java.lang.String} object
     * @return a {@link java.lang.Object} object
     */
    public Object lookupService(String serviceName);

    /**
     * <p>lookupScopeHandler.</p>
     *
     * @param contextPath a {@link java.lang.String} object
     * @return a {@link org.red5.server.api.scope.IScopeHandler} object
     */
    public IScopeHandler lookupScopeHandler(String contextPath);

    /**
     * <p>getMappingStrategy.</p>
     *
     * @return a {@link org.red5.server.api.IMappingStrategy} object
     */
    public IMappingStrategy getMappingStrategy();

    /**
     * <p>getResources.</p>
     *
     * @param pattern a {@link java.lang.String} object
     * @return an array of {@link org.springframework.core.io.Resource} objects
     * @throws java.io.IOException if any.
     */
    public Resource[] getResources(String pattern) throws IOException;

    /**
     * <p>getResource.</p>
     *
     * @param path a {@link java.lang.String} object
     * @return a {@link org.springframework.core.io.Resource} object
     */
    public Resource getResource(String path);

    /**
     * <p>resolveScope.</p>
     *
     * @param host a {@link java.lang.String} object
     * @param path a {@link java.lang.String} object
     * @return a {@link org.red5.server.api.scope.IScope} object
     */
    public IScope resolveScope(String host, String path);

    /**
     * <p>getBean.</p>
     *
     * @param beanId a {@link java.lang.String} object
     * @return a {@link java.lang.Object} object
     */
    public Object getBean(String beanId);

    /**
     * <p>getCoreService.</p>
     *
     * @param beanId a {@link java.lang.String} object
     * @return a {@link java.lang.Object} object
     */
    public Object getCoreService(String beanId);

}
