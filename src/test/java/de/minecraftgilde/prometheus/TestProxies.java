package de.minecraftgilde.prometheus;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Small interface proxy helper for public Paper API unit tests. */
public final class TestProxies {

    private TestProxies() {}

    @SuppressWarnings("unchecked")
    public static <T> T proxy(Class<T> type, Map<String, ?> values) {
        Map<String, Object> methods = new HashMap<>(values);
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] { type },
            (instance, method, arguments) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> "TestProxy(" + type.getSimpleName() + ")";
                        case "hashCode" -> System.identityHashCode(instance);
                        case "equals" -> instance == arguments[0];
                        default -> throw new UnsupportedOperationException(method.toString());
                    };
                }
                if (!methods.containsKey(method.getName())) {
                    throw new UnsupportedOperationException(
                        "No test value for " + type.getSimpleName() + "#" + method.getName()
                    );
                }
                Object value = methods.get(method.getName());
                if (value instanceof Function<?, ?> function) {
                    return ((Function<Object[], ?>) function).apply(
                        arguments == null ? new Object[0] : arguments
                    );
                }
                return value;
            }
        );
    }
}
