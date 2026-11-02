package com.example.clinic.mapper;

import com.example.clinic.domain.User;
import com.example.clinic.dto.UserDto;
import org.mapstruct.Mapper;

/**
 * Maps account entities to credential-free response DTOs.
 *
 * <h2>Responsibility</h2>
 * <p>Maps between persistence/domain objects and command or response boundaries. The generated
 * implementation uses CentralMapperConfig and is supplied as a Spring bean.
 *
 * <h2>How it works</h2>
 * <p>Copies identifier, email and role into UserDto.
 *
 * <h2>Design choices</h2>
 * <p>The DTO has no password/hash component, so generated mapping has no credential target.
 *
 * <h2>Important details</h2>
 * <p>This protects this mapping path only. Returning a User entity directly would bypass the DTO
 * boundary and could expose its password getter.
 * Generated object mappings return null for a null source; generated updates ignore a null command.
 * Handwritten helpers retain their own
 * nonnull preconditions. A mapper neither starts a transaction nor makes an entity managed.
 */
@Mapper(config = CentralMapperConfig.class)
public interface UserMapper {
    /**
     * Maps an account to a public DTO containing no password hash.
     *
     * @param user the account, possibly null
     * @return the public account representation, or null for a null source
     */
    UserDto toDto(User user);
}
