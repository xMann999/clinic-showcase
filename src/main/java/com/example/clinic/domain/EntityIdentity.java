package com.example.clinic.domain;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.proxy.HibernateProxy;

/**
 * Resolves the persistent type used by entity equality and hashing.
 *
 * <p>Hibernate proxy subclasses have a different Java runtime class from the mapped entity. Reading
 * the proxy initializer's persistent class lets both representations use the same identity contract.
 * This package utility shares that operation without introducing an entity base class.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EntityIdentity {

    /**
     * Obtains the mapped class from a Hibernate proxy initializer or the runtime class otherwise.
     *
     * @param entity the nonnull entity or proxy
     * @return the class used by the identity contract
     */
    static Class<?> effectiveClass(Object entity) {
        if (entity instanceof HibernateProxy proxy) {
            return proxy.getHibernateLazyInitializer().getPersistentClass();
        }
        return entity.getClass();
    }
}
