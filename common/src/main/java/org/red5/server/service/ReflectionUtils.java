/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.red5.io.utils.ConversionUtils;
import org.red5.server.api.IConnection;
import org.red5.server.api.service.IServiceCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a means for locating methods within service classes using reflection.
 */
public class ReflectionUtils {

    private static final Logger log = LoggerFactory.getLogger(ReflectionUtils.class);

    private static final boolean isDebug = log.isDebugEnabled(), isTrace = log.isTraceEnabled();

    //used to prevent extra object creation when a method with a set of params is not found
    private static final Object[] NULL_RETURN = new Object[] { null, null };

    /**
     * Returns (method, params) for the given service or method name if found on a service or scope handler.
     *
     * @param conn current connection
     * @param call service call interested in the method
     * @param service service to search for the method, if given
     * @param methodName method name to find
     * @return Method/params pairs or null if not found
     */
    public static Object[] findMethod(IConnection conn, IServiceCall call, Object service, String methodName) {
        if (isDebug) {
            log.debug("Find method: {} in service: {} for call: {} and connection: {}", methodName, service, call, conn);
        }
        // build an array with the incoming args and the current connection as the first element
        Object[] args = call.getArguments();
        Object[] argsWithConnection;
        if (args != null) {
            argsWithConnection = new Object[args.length + 1];
            argsWithConnection[0] = conn;
            for (int i = 0; i < args.length; i++) {
                if (isDebug) {
                    log.debug("{} => {}", i, args[i]);
                    if (isTrace && args[i] != null) {
                        log.trace("Arg type: {}", args[i].getClass().getName());
                    }
                }
                argsWithConnection[i + 1] = args[i];
            }
        } else {
            argsWithConnection = new Object[] { conn };
        }
        // First, search for method without the connection as first "forced" parameter
        Object[] methodResult = ReflectionUtils.findMethodWithExactParameters(service, methodName, args);
        if (methodResult[0] == null) {
            // Second, search for method with the connection as first parameter
            methodResult = ReflectionUtils.findMethodWithExactParameters(service, methodName, argsWithConnection);
            if (methodResult[0] == null) {
                // Third, search for method without the connection as first parameter in a list argument
                methodResult = ReflectionUtils.findMethodWithListParameters(service, methodName, args);
                if (methodResult[0] == null) {
                    // Fourth, search for method with the connection as first parameter in a list argument
                    methodResult = ReflectionUtils.findMethodWithListParameters(service, methodName, argsWithConnection);
                    if (methodResult[0] == null) {
                        log.warn("Method {} not found in {} with parameters {}", methodName, service, Arrays.asList(args));
                        call.setStatus(Call.STATUS_METHOD_NOT_FOUND);
                        call.setException(new MethodNotFoundException(methodName, args));
                    }
                }
            }
        }
        return methodResult;
    }

    /**
     * Returns (method, params) for the given service or (null, null) if no method was found.
     *
     * @param service
     *            Service
     * @param methodName
     *            Method name
     * @param args
     *            Arguments
     * @return Method/params pairs
     */
    public static Object[] findMethodWithExactParameters(Object service, String methodName, List<?> args) {
        Object[] arguments = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            arguments[i] = args.get(i);
        }
        return findMethodWithExactParameters(service, methodName, arguments);
    }

    /**
     * Returns (method, params) for the given service or (null, null) if not method was found. XXX use ranking for method matching rather
     * than exact type matching plus type conversion.
     *
     * @param service
     *            Service
     * @param methodName
     *            Method name
     * @param args
     *            Arguments
     * @return Method/params pairs
     */
    public static Object[] findMethodWithExactParameters(Object service, String methodName, Object[] args) {
        int numParams = (args == null) ? 0 : args.length;
        if (isTrace) {
            log.trace("Args / parameters count: {}", numParams);
        }
        // convert the args first
        Object[] params = ConversionUtils.convertParams(args);
        if (isDebug) {
            for (Object clazz : params) {
                log.debug("Parameter: {}", clazz);
            }
        }
        // try to skip the listing of all the methods by checking for exactly what we want first
        try {
            Method method = service.getClass().getMethod(methodName, (Class<?>[]) params);
            if (isDebug) {
                log.debug("Exact method found, skipping list: {}", methodName);
            }
            return new Object[] { method, args };
        } catch (NoSuchMethodException nsme) {
            log.debug("Method not found using exact parameter types", nsme);
        }
        List<Method> methods = ConversionUtils.findMethodsByNameAndNumParams(service, methodName, numParams);
        if (isTrace) {
            log.trace("Found {} methods", methods.size());
        }
        if (!methods.isEmpty()) {
            for (Method m : methods) {
                boolean valid = true;
                log.debug("Method: {}", m);
                if (args != null) {
                    // check args against method parameter types
                    Class<?>[] paramTypes = m.getParameterTypes();
                    // search for method with exact parameters
                    for (int j = 0; j < args.length; j++) {
                        if ((args[j] == null && paramTypes[j].isPrimitive()) || (args[j] != null && !args[j].getClass().equals(paramTypes[j]))) {
                            valid = false;
                            break;
                        }
                    }
                }
                if (valid) {
                    return new Object[] { m, args };
                }
            }
        }
        return NULL_RETURN;
    }

    /**
     * Returns (method, params) for the given service or (null, null) if no method was found.
     *
     * @param service
     *            Service
     * @param methodName
     *            Method name
     * @param args
     *            Arguments
     * @return Method/params pairs
     */
    public static Object[] findMethodWithListParameters(Object service, String methodName, List<?> args) {
        Object[] arguments = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            arguments[i] = args.get(i);
        }
        return findMethodWithListParameters(service, methodName, arguments);
    }

    /**
     * Returns (method, params) for the given service or (null, null) if not method was found.
     *
     * @param service
     *            Service
     * @param methodName
     *            Method name
     * @param args
     *            Arguments
     * @return Method/params pairs
     */
    public static Object[] findMethodWithListParameters(Object service, String methodName, Object[] args) {
        Method method = null;
        try {
            //try to skip the listing of all the methods by checking for exactly what
            //we want first
            method = service.getClass().getMethod(methodName, ConversionUtils.convertParams(args));
            log.debug("Exact method found (skipping list): {}", methodName);
            return new Object[] { method, args };
        } catch (NoSuchMethodException nsme) {
            log.debug("Method not found using exact parameter types", nsme);
        }
        List<Method> methods = ConversionUtils.findMethodsByNameAndNumParams(service, methodName, 1);
        if (isTrace) {
            log.trace("Found {} methods", methods.size());
        }
        if (!methods.isEmpty()) {
            log.debug("Multiple methods found with same name and parameter count; parameter conversion will be attempted in order");
            ArrayList<Object> argsList = new ArrayList<Object>();
            if (args != null) {
                for (Object element : args) {
                    argsList.add(element);
                }
            }
            args = new Object[] { argsList };
            Object[] params = null;
            for (int i = 0; i < methods.size(); i++) {
                try {
                    method = methods.get(i);
                    params = ConversionUtils.convertParams(args, method.getParameterTypes());
                    if (argsList.size() > 0 && (argsList.get(0) instanceof IConnection) && (!(params[0] instanceof IConnection))) {
                        // Don't convert first IConnection parameter
                        continue;
                    }
                    return new Object[] { method, params };
                } catch (Exception ex) {
                    log.debug("Parameter conversion failed", ex);
                }
            }
        }
        return NULL_RETURN;
    }

}
