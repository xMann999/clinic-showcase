package com.example.clinic.repository;

import com.example.clinic.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists accounts and queries normalized emails.
 *
 * <h2>Responsibility</h2>
 * <p>Declares persistence operations implemented by Spring Data repository proxies; inherited
 * JpaRepository methods provide standard entity lookup, saving, paging and deletion.
 *
 * <h2>How it works</h2>
 * <p>Spring Data derives predicates from method names and registers the repository implementation.
 *
 * <h2>Design choices</h2>
 * <p>Optional expresses missing email lookup without returning a null account.
 *
 * <h2>Important details</h2>
 * <p>Email normalization belongs to callers; this repository does not perform it.
 * Empty queries return empty collections/pages or Optional values as declared. Database constraints
 * remain necessary; existence queries alone cannot make a subsequent write atomic.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * Looks up an exact stored email without normalizing it.
     *
     * @param email the normalized email to match
     * @return the account, or empty if no match exists
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks exact stored-email reuse without reserving the value.
     *
     * @param email the normalized email to match
     * @return whether a matching account exists
     */
    boolean existsByEmail(String email);
}
