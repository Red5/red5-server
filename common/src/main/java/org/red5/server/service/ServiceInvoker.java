/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IServiceCall;
import org.red5.server.api.service.IServiceInvoker;
import org.red5.server.exception.ClientDetailsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes remote calls, invoking services, resolves service handlers
 *
 * @author The Red5 Project
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 */
public class ServiceInvoker implements IServiceInvoker {

    private static final Logger log = LoggerFactory.getLogger(ServiceInvoker.class);

    /**
     * Service name
     */
    public static final String SERVICE_NAME = "serviceInvoker";

    /**
     * Service resolvers set
     */
    private Set<IServiceResolver> serviceResolvers = new HashSet<IServiceResolver>();

    /**
     * Setter for service resolvers.
     *
     * @param resolvers
     *            Service resolvers
     */
    public void setServiceResolvers(Set<IServiceResolver> resolvers) {
        serviceResolvers = resolvers;
    }

    /**
     * Lookup a handler for the passed service name in the given scope.
     *
     * @param scope
     *            Scope
     * @param serviceName
     *            Service name
     * @return Service handler
     */
    private Object getServiceHandler(IScope scope, String serviceName) {
        // get application scope handler first
        Object service = scope.getHandler();
        if (serviceName == null || serviceName.equals("")) {
            // no service requested, return application scope handler
            log.trace("No service requested, return application scope handler: {}", service);
            return service;
        }
        // search service resolver that knows about service name
        for (IServiceResolver resolver : serviceResolvers) {
            service = resolver.resolveService(scope, serviceName);
            if (service != null) {
                return service;
            }
        }
        // requested service does not exist
        return null;
    }

    /** {@inheritDoc} */
    public boolean invoke(IServiceCall call, IScope scope) {
        String serviceName = call.getServiceName();
        log.trace("Service name {}", serviceName);
        Object service = getServiceHandler(scope, serviceName);
        if (service == null) {
            // Exception must be thrown if service was not found
            call.setException(new ServiceNotFoundException(serviceName));
            // Set call status
            call.setStatus(Call.STATUS_SERVICE_NOT_FOUND);
            log.warn("Service not found: {}", serviceName);
            return false;
        } else {
            log.debug("Service found: {}", serviceName);
        }
        // Invoke if everything is ok
        return invoke(call, service);
    }

    /** {@inheritDoc} */
    public boolean invoke(IServiceCall call, Object service) {
        boolean invoked = false;
        IConnection conn = Red5.getConnectionLocal();
        String methodName = call.getServiceMethodName();
        log.debug("Service: {} name: {} method: {}", service, call.getServiceName(), methodName);
        // pull off the prefixes since java doesnt allow this on a method name
        if (methodName.charAt(0) == '@') {
            log.debug("Method name contained an illegal prefix, it will be removed: {}", methodName);
            methodName = methodName.substring(1);
        }
        // look up the method with provided matching arguments
        Object[] methodResult = ReflectionUtils.findMethod(conn, call, service, methodName);
        // get the method
        Method method = (methodResult != null ? (Method) methodResult[0] : null);
        if (method == null || call.getException() != null) {
            log.warn("Method not found: {}", methodName);
        } else {
            log.debug("Method found: {}", methodName);
            // get the parameters; the value at index 1 can be null, but the methodResult array will never be null
            Object[] params = (Object[]) methodResult[1];
            try {
                /* XXX(paul) legacy flash logic for restricting access to methods
                if (method.isAnnotationPresent(DeclarePrivate.class)) {
                    // Method may not be called by clients.
                    log.debug("Method {} is declared private.", method);
                    throw new NotAllowedException("Access denied, method is private");
                }
                final DeclareProtected annotation = method.getAnnotation(DeclareProtected.class);
                if (annotation != null && !conn.getClient().hasPermission(conn, annotation.permission())) {
                    // client doesn't have required permission
                    log.debug("Client {} doesn't have required permission {} to call {}", new Object[] { conn.getClient(), annotation.permission(), method });
                    throw new NotAllowedException("Access denied, method is protected");
                }
                */
                Object result = null;
                log.debug("Invoking method: {}", method.toString());
                if (method.getReturnType().equals(Void.TYPE)) {
                    log.debug("result: void");
                    method.invoke(service, params);
                    call.setStatus(Call.STATUS_SUCCESS_VOID);
                } else {
                    result = method.invoke(service, params);
                    log.debug("result: {}", result);
                    call.setStatus(result == null ? Call.STATUS_SUCCESS_NULL : Call.STATUS_SUCCESS_RESULT);
                }
                if (call instanceof IPendingServiceCall) {
                    ((IPendingServiceCall) call).setResult(result);
                }
                invoked = true;
            } catch (NotAllowedException | IllegalAccessException e) {
                call.setException(e);
                call.setStatus(Call.STATUS_ACCESS_DENIED);
                log.error("Error executing call: {}", call, e);
            } catch (InvocationTargetException invocationEx) {
                call.setException(invocationEx);
                call.setStatus(Call.STATUS_INVOCATION_EXCEPTION);
                if (!(invocationEx.getCause() instanceof ClientDetailsException)) {
                    // only log if not handled by client
                    log.error("Error executing call: {}", call, invocationEx);
                }
            } catch (Exception ex) {
                call.setException(ex);
                call.setStatus(Call.STATUS_GENERAL_EXCEPTION);
                log.error("Error executing call: {}", call, ex);
            }
        }
        return invoked;
    }

}
