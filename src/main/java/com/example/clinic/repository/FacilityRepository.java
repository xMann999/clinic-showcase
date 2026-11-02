package com.example.clinic.repository;

import com.example.clinic.domain.Facility;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists facilities using inherited repository operations.
 *
 * <h2>Responsibility</h2>
 * <p>Declares persistence operations implemented by Spring Data repository proxies; inherited
 * JpaRepository methods provide standard entity lookup, saving, paging and deletion.
 *
 * <h2>How it works</h2>
 * <p>No custom query is declared; callers use the inherited entity and paging operations.
 *
 * <h2>Design choices</h2>
 * <p>The interface stays limited to the persistence needs of the facility service.
 *
 * <h2>Important details</h2>
 * <p>Relationship deletion rules belong to the service and database, not this interface.
 * Empty queries return empty collections/pages or Optional values as declared. Database constraints
 * remain necessary; existence queries alone cannot make a subsequent write atomic.
 */
public interface FacilityRepository extends JpaRepository<Facility, Long> {}
