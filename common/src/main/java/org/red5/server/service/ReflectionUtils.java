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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.red5.annotations.DeclarePrivate;
import org.red5.io.utils.ConversionUtils;
import org.red5.server.api.IConnection;
import org.red5.server.api.service.IServiceCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a means for locating methods within service classes using reflection.
 *
 * @author mondain
 */
public class ReflectionUtils {

    private static final Logger log = LoggerFactory.getLogger(ReflectionUtils.class);

    private static final boolean isDebug = log.isDebugEnabled(), isTrace = log.isTraceEnabled();

    // used to prevent extra object creation when a method with a set of params is not found
    private static final Object[] NULL_RETURN = new Object[] { null, null };

    /**
     * System property that, when true, restores remote access to every public method declared by the framework application adapters.
     */
    public static final String EXPOSE_ADAPTER_METHODS_PROPERTY = "red5.service.expose_adapter_methods";

    private static final boolean exposeAdapterMethods = Boolean.getBoolean(EXPOSE_ADAPTER_METHODS_PROPERTY);

    // package holding the framework application adapters whose public methods are server-side API, not remote API
    private static final String ADAPTER_PACKAGE = "org.red5.server.adapter.";

    // adapter methods that clients such as Flash, OBS and FFmpeg call as part of the RTMP command set
    private static final Set<String> CLIENT_CALLABLE_ADAPTER_METHODS = Set.of("FCPublish", "FCUnpublish", "FCSubscribe", "getStreamLength", "checkBandwidth", "checkBandwidthUp", "measureBandwidth", "startTransmit", "stopTransmit", "setPeerInfo");

    /**
     * Returns whether a public method may be invoked by a remote peer. Methods declared by JDK classes, methods annotated with
     * {@link DeclarePrivate} and methods declared by the framework application adapters (other than the RTMP client command set) are
     * not remotely callable; methods declared by application classes are.
     *
     * @param method method to check
     * @return true if the method may be invoked remotely
     */
    public static boolean isRemotelyCallable(Method method) {
        if (method.isAnnotationPresent(DeclarePrivate.class)) {
            return false;
        }
        String declaringClass = method.getDeclaringClass().getName();
        if (declaringClass.startsWith("java.") || declaringClass.startsWith("javax.") || declaringClass.startsWith("jdk.")) {
            return false;
        }
        if (!exposeAdapterMethods && declaringClass.startsWith(ADAPTER_PACKAGE)) {
            return CLIENT_CALLABLE_ADAPTER_METHODS.contains(method.getName());
        }
        return true;
    }

    private static Set<Method> findNamedMethods(Object service, String methodName) {
        Set<Method> named = Arrays.stream(service.getClass().getMethods()).filter(m -> (m.getName().equals(methodName) && !m.getName().contains("$"))).collect(Collectors.toSet());
        if (named.removeIf(m -> !isRemotelyCallable(m))) {
            log.warn("Method {} on {} is not remotely callable", methodName, service.getClass().getName());
        }
        return named;
    }

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
     * @return Method/params pairs or null if not found
     * @param listArgs a {@link java.util.List} object
     */
    public static Object[] findMethod(Object service, String methodName, List<?> listArgs) {
        if (isDebug) {
            log.debug("Find method: {} in service: {} args: {}", methodName, service, listArgs);
        }
        // return value(s)
        Object[] methodResult = NULL_RETURN;
        final int argsSize = (listArgs != null ? listArgs.size() : 0);
        // get all the name matched methods once, then filter out the ones that contain a $
        final Set<Method> methods = findNamedMethods(service, methodName).stream().filter(m -> m.getParameterCount() == 1 || m.getParameterCount() == argsSize).collect(Collectors.toUnmodifiableSet());
        if (!methods.isEmpty()) {
            if (isDebug) {
                log.debug("Named method(s) {}: {} found in {}", methods.size(), methodName, service);
            }
            Object[] args = (listArgs != null ? listArgs.toArray() : new Object[0]);
            // convert the args to their class types
            final Class<?>[] callParams = ConversionUtils.convertParams(args);
            // search for method with matching parameters, trying those whose parameters accept the arguments as-is first
            for (Method method : orderByAssignability(methods, args, null)) {
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
        final Set<Method> methods = findNamedMethods(service, methodName).stream().filter(m -> m.getParameterCount() == 1 || m.getParameterCount() == callParams.length || m.getParameterCount() == (callParams.length + 1)).collect(Collectors.toUnmodifiableSet());
        if (methods.isEmpty()) {
            log.warn("Named method: {} not found in {}", methodName, service);
            call.setStatus(Call.STATUS_METHOD_NOT_FOUND);
            call.setException(new MethodNotFoundException(methodName, call.getArguments()));
        } else {
            if (isDebug) {
                log.debug("Named method(s) {}: {} found in {}", methods.size(), methodName, service);
            }
            // search for method with matching parameters, trying those whose parameters accept the arguments as-is first
            for (Method method : orderByAssignability(methods, args, conn)) {
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

    /**
     * Orders candidate methods so that those whose parameter types accept the call arguments without conversion come
     * first. Overloads such as publish(Boolean) and publish(String) are both reachable through argument conversion, so
     * without this ordering the selected overload depends on reflection order rather than on the argument types.
     *
     * @param methods candidate methods sharing a name
     * @param args call arguments, may be null
     * @param conn connection to consider as an optional leading parameter, may be null
     * @return the methods, directly assignable matches first
     */
    private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPERS = Map.of(boolean.class, Boolean.class, byte.class, Byte.class, char.class, Character.class, short.class, Short.class, int.class, Integer.class, long.class, Long.class, float.class, Float.class, double.class, Double.class);

    private static List<Method> orderByAssignability(Set<Method> methods, Object[] args, IConnection conn) {
        List<Method> direct = new ArrayList<>();
        List<Method> other = new ArrayList<>();
        Object[] callArgs = args != null ? args : new Object[0];
        for (Method method : methods) {
            Class<?>[] paramTypes = method.getParameterTypes();
            boolean matches = false;
            if (paramTypes.length == callArgs.length) {
                matches = isAssignable(paramTypes, callArgs, 0);
            } else if (conn != null && paramTypes.length == callArgs.length + 1 && paramTypes[0].isAssignableFrom(conn.getClass())) {
                matches = isAssignable(paramTypes, callArgs, 1);
            }
            (matches ? direct : other).add(method);
        }
        direct.addAll(other);
        return direct;
    }

    /**
     * Returns true when every argument can be passed to the parameter at the same index (plus offset) without conversion.
     * A null argument fits any non-primitive parameter.
     */
    private static boolean isAssignable(Class<?>[] paramTypes, Object[] args, int offset) {
        for (int i = 0; i < args.length; i++) {
            Class<?> param = paramTypes[i + offset];
            Object arg = args[i];
            if (arg == null) {
                if (param.isPrimitive()) {
                    return false;
                }
                continue;
            }
            Class<?> argType = arg.getClass();
            if (param.isPrimitive()) {
                param = PRIMITIVE_WRAPPERS.get(param);
            }
            if (!param.isAssignableFrom(argType)) {
                return false;
            }
        }
        return true;
    }

}
