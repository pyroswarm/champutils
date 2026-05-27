package com.champutils.util;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * Small compatibility layer for Cobblemon reflected events.
 *
 * Cobblemon/Fabric/Kotlin event observable subscribe signatures have changed across
 * versions. Calling subscribe with a raw Kotlin Function1 can throw
 * IllegalArgumentException: argument type mismatch if the selected subscribe method
 * expects another functional interface. This helper builds the correct callback type
 * for the reflected subscribe method instead of assuming Function1.
 */
public final class CobblemonEventReflection {
    private CobblemonEventReflection() {}

    public static boolean subscribe(Object observable, Consumer<Object> handler) {
        if (observable == null || handler == null) return false;

        for (Method method : observable.getClass().getMethods()) {
            if (!method.getName().equals("subscribe")) continue;

            try {
                method.setAccessible(true);

                if (method.getParameterCount() == 1) {
                    Class<?> callbackType = method.getParameterTypes()[0];
                    Object callback = callbackFor(callbackType, handler);
                    if (callback == null) continue;
                    method.invoke(observable, callback);
                    return true;
                }

                if (method.getParameterCount() == 2) {
                    Class<?> firstType = method.getParameterTypes()[0];
                    Class<?> callbackType = method.getParameterTypes()[1];
                    Object firstArg = defaultArgument(firstType);
                    Object callback = callbackFor(callbackType, handler);
                    if (firstArg == UNSUPPORTED || callback == null) continue;
                    method.invoke(observable, firstArg, callback);
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Try the next overload/shape.
            } catch (Throwable ignored) {
                // Keep trying instead of failing the whole listener registration.
            }
        }

        return false;
    }

    private static final Object UNSUPPORTED = new Object();

    private static Object defaultArgument(Class<?> type) {
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            if (constants == null || constants.length == 0) return UNSUPPORTED;
            for (Object constant : constants) {
                if (String.valueOf(constant).equalsIgnoreCase("NORMAL")) return constant;
            }
            return constants[0];
        }
        if (!type.isPrimitive()) return null;
        return UNSUPPORTED;
    }

    private static Object callbackFor(Class<?> parameterType, Consumer<Object> handler) {
        if (parameterType.isAssignableFrom(Function1.class)) {
            return new Function1<Object, Unit>() {
                @Override
                public Unit invoke(Object event) {
                    handler.accept(event);
                    return Unit.INSTANCE;
                }
            };
        }

        if (parameterType.isAssignableFrom(Consumer.class)) {
            return (Consumer<Object>) handler::accept;
        }

        if (parameterType.isInterface()) {
            InvocationHandler invocationHandler = (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> "ChampUtils reflected Cobblemon event callback";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == (args == null ? null : args[0]);
                        default -> null;
                    };
                }

                if (args != null && args.length >= 1) {
                    handler.accept(args[0]);
                }
                return defaultReturn(method.getReturnType());
            };
            return Proxy.newProxyInstance(parameterType.getClassLoader(), new Class<?>[] { parameterType }, invocationHandler);
        }

        return null;
    }

    private static Object defaultReturn(Class<?> returnType) {
        if (returnType == Void.TYPE || returnType == Unit.class) return Unit.INSTANCE;
        if (returnType == Boolean.TYPE) return false;
        if (returnType == Byte.TYPE) return (byte) 0;
        if (returnType == Short.TYPE) return (short) 0;
        if (returnType == Integer.TYPE) return 0;
        if (returnType == Long.TYPE) return 0L;
        if (returnType == Float.TYPE) return 0F;
        if (returnType == Double.TYPE) return 0D;
        if (returnType == Character.TYPE) return '\0';
        return null;
    }
}
