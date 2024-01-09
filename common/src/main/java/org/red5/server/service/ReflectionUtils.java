/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.service;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    // used to prevent extra object creation when a method with a set of params is not found
    private static final Object[] NULL_RETURN = new Object[] { null, null };

    // Note for .26 update is to ensure other service methods don't fail when a method is not found
    // See https://github.com/Red5/red5-server/commit/d4096a4d7b35b2b92905154a9e18edea04268fb4

    /**
     * Returns (method, params) for the given service or method name if found on a service or scope handler. There is
     * no connection argument for these calls.
     *
     * SharedObjectScope uses this method to find methods on the handler.
     *
     * @param service service to search for the method, if given
     * @param methodName method name to find
     * @param args arguments
     * @return Method/params pairs or null if not found
     */
    public static Object[] findMethod(Object service, String methodName, List<?> listArgs) {
        if (isDebug) {
            log.debug("Find method: {} in service: {} args: {}", methodName, service, listArgs);
        }
        // return value(s)
        Object[] methodResult = NULL_RETURN;
        // get all the name matched methods once, then filter out the ones that contain a $
        final Set<Method> methods = Arrays.stream(service.getClass().getMethods()).filter(m -> (m.getName().equals(methodName) && !m.getName().contains("$"))).filter(m -> m.getParameterCount() == 1 || m.getParameterCount() == listArgs.size()).collect(Collectors.toUnmodifiableSet());
        if (!methods.isEmpty()) {
            if (isDebug) {
                log.debug("Named method(s) {}: {} found in {}", methods.size(), methodName, service);
            }
            Object[] args = listArgs.toArray();
            // convert the args to their class types
            final Class<?>[] callParams = ConversionUtils.convertParams(args);
            // search for method with matching parameters
            for (Method method : methods) {
                // track method parameters count
                int paramCount = method.getParameterCount();
                if (isTrace) {
                    log.trace("Method {} count - parameters: {} args: {}", methodName, paramCount, callParams.length);
                }
                // if there are no args nor parameters
                if ((listArgs == null || listArgs.isEmpty()) && paramCount == 0) {
                    if (isTrace) {
                        log.trace("Method {} matched - zero-length", methodName);
                    }
                    // fastest way to handle zero parameter methods
                    methodResult = new Object[] { method, listArgs };
                    break;
                }
                // get the methods parameter types
                Class<?>[] paramTypes = method.getParameterTypes();
                // search for method with List as the first and only parameter
                if (paramTypes[0].isAssignableFrom(List.class)) {
                    if (isTrace) {
                        log.trace("Method {} matched - parameter 0 is a list", methodName);
                    }
                    methodResult = new Object[] { method, listArgs };
                    break;
                }
                // search for method matching parameters without a forced connection parameter
                if (paramCount == callParams.length) {
                    // attempt to convert the args to match the method
                    try {
                        Object[] convertedArgs = ConversionUtils.convertParams(args, paramTypes);
                        if (isTrace) {
                            log.trace("Found method {} {} - parameters: {}", methodName, method, paramTypes);
                        }
                        methodResult = new Object[] { method, convertedArgs };
                        break;
                    } catch (Exception e) {
                        log.warn("Method {} not found in {} with parameters {}", methodName, service, Arrays.asList(paramTypes), e);
                    }
                }
            }
            if (isTrace) {
                log.trace("Method name: {} result: {}", methodName, methodResult[0]);
            }
        }
        return methodResult;
    }

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
        // return value(s)
        Object[] methodResult = NULL_RETURN;
        // clear any previous exception from the call as it may be reused
        if (call.getException() != null) {
            log.debug("Clearing status and exception from call: {}", call);
            call.setStatus(Call.STATUS_PENDING);
            call.setException(null);
        }
        // get the arguments
        final Object[] args = call.getArguments();
        // convert the args to their class types
        Class<?>[] callParams = ConversionUtils.convertParams(args);
        // XXX(paul) someday this will be deprecated as its an extremely legacy feature to have a method with a
        // connection as the first parameter
        // build an array with the incoming args and the current connection as the first element
        final Object[] argsWithConnection;
        if (args != null) {
            argsWithConnection = new Object[args.length + 1];
            argsWithConnection[0] = conn;
            for (int i = 0; i < args.length; i++) {
                if (isDebug) {
                    log.debug("Arg: {} type: {} => {}", i, (args[i] != null ? args[i].getClass().getName() : null), args[i]);
                }
                argsWithConnection[i + 1] = args[i];
            }
        } else {
            argsWithConnection = conn != null ? new Object[] { conn } : new Object[0];
        }
        // get all the name matched methods once, then filter out the ones that contain a $
        final Set<Method> methods = Arrays.stream(service.getClass().getMethods()).filter(m -> (m.getName().equals(methodName) && !m.getName().contains("$"))).filter(m -> m.getParameterCount() == 1 || m.getParameterCount() == callParams.length || m.getParameterCount() == (callParams.length + 1)).collect(Collectors.toUnmodifiableSet());
        if (methods.isEmpty()) {
            log.warn("Named method: {} not found in {}", methodName, service);
            call.setStatus(Call.STATUS_METHOD_NOT_FOUND);
            call.setException(new MethodNotFoundException(methodName, call.getArguments()));
        } else {
            if (isDebug) {
                log.debug("Named method(s) {}: {} found in {}", methods.size(), methodName, service);
            }
            // search for method with matching parameters
            for (Method method : methods) {
                // track method parameters count
                int paramCount = method.getParameterCount();
                if (isTrace) {
                    log.trace("Method {} count - parameters: {} args: {}", methodName, paramCount, callParams.length);
                }
                // if there are no args nor parameters
                if ((args == null || args.length == 0) && paramCount == 0) {
                    if (isTrace) {
                        log.trace("Method {} matched - zero-length", methodName);
                    }
                    // fastest way to handle zero parameter methods
                    methodResult = new Object[] { method, args };
                    break;
                }
                // get the methods parameter types
                Class<?>[] paramTypes = method.getParameterTypes();
                // search for method with Object[] as the first and only parameter
                if (paramCount == 1 && paramTypes[0].isArray()) {
                    if (isTrace) {
                        log.trace("Method {} matched - parameter 0 is an array", methodName);
                    }
                    methodResult = new Object[] { method, args };
                    break;
                }
                // search for method matching parameters without a forced connection parameter
                if (paramCount == callParams.length && !paramTypes[0].isAssignableFrom(IConnection.class)) {
                    // attempt to convert the args to match the method
                    try {
                        Object[] convertedArgs = ConversionUtils.convertParams(args, paramTypes);
                        if (isTrace) {
                            log.trace("Found method {} {} - parameters: {}", methodName, method, paramTypes);
                        }
                        methodResult = new Object[] { method, convertedArgs };
                        break;
                    } catch (Exception e) {
                        log.warn("Method {} not found in {} with parameters {}", methodName, service, Arrays.asList(paramTypes), e);
                    }
                }
                // lastly try with connection at position 0 in parameters
                if (conn != null && paramCount == (callParams.length + 1) && paramTypes[0].isAssignableFrom(IConnection.class)) {
                    // attempt to convert the args to match the method
                    try {
                        Object[] convertedArgs = ConversionUtils.convertParams(argsWithConnection, paramTypes);
                        if (isTrace) {
                            log.trace("Found method {} {} - parameters: {}", methodName, method, paramTypes);
                        }
                        methodResult = new Object[] { method, convertedArgs };
                        break;
                    } catch (Exception e) {
                        log.warn("Method {} not found in {} with parameters {}", methodName, service, Arrays.asList(paramTypes), e);
                    }
                }
            }
            if (isTrace) {
                log.trace("Method name: {} result: {}", methodName, methodResult[0]);
            }
            if (methodResult[0] == null) {
                log.warn("Method {} not found in {} with parameters {}", methodName, service, Arrays.asList(callParams));
                call.setStatus(Call.STATUS_METHOD_NOT_FOUND);
                call.setException(new MethodNotFoundException(methodName, args));
            }
        }
        return methodResult;
    }

}
