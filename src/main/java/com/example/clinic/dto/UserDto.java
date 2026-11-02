package com.example.clinic.dto;

import com.example.clinic.domain.Role;

/**
 * Carries account identifier, normalized email and role for API output.
 *
 * <p>Excludes plaintext passwords and password hashes. These immutable scalar components define the DTO
 * boundary; returning an entity directly would bypass that boundary.
 *
 * @param id the resource identifier
 * @param email the normalized account email
 * @param role the account role
 */
public record UserDto(Long id, String email, Role role) {}
