package com.exchange.matching;

import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;

/**
 * Remplit les champs @Autowired d'un bean dans un test unitaire, comme le
 * ferait Spring: chaque dependance va dans le seul champ @Autowired dont le
 * type lui correspond.
 *
 *   TradingService service = TestInjection.inject(new TradingService(), engine, orderRepository, ...);
 *
 * Avec l'injection par champ, les services n'ont plus de constructeur a
 * arguments; ce helper evite de repeter ReflectionTestUtils.setField(...)
 * pour chaque champ, et echoue clairement si une dependance ne correspond
 * a aucun champ ou a plusieurs.
 */
public final class TestInjection {

    private TestInjection() {
    }

    public static <T> T inject(T target, Object... dependencies) {
        for (Object dependency : dependencies) {
            Field match = null;
            for (Class<?> type = target.getClass(); type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Autowired.class) && field.getType().isInstance(dependency)) {
                        if (match != null) {
                            throw new IllegalArgumentException("Dependance ambigue pour " + target.getClass().getSimpleName()
                                + ": " + match.getName() + " et " + field.getName());
                        }
                        match = field;
                    }
                }
            }
            if (match == null) {
                throw new IllegalArgumentException("Aucun champ @Autowired de " + target.getClass().getSimpleName()
                    + " n'accepte " + dependency.getClass().getName());
            }
            try {
                match.setAccessible(true);
                match.set(target, dependency);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return target;
    }
}
