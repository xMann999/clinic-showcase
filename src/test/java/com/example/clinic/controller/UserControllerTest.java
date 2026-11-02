package com.example.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.clinic.command.PasswordChangeCommand;
import com.example.clinic.command.UserCreateCommand;
import com.example.clinic.domain.Role;
import com.example.clinic.dto.PageResponse;
import com.example.clinic.dto.UserDto;
import com.example.clinic.service.UserService;
import com.example.clinic.support.WebSliceTestConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(UserController.class)
@Import(WebSliceTestConfiguration.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void post_validUser_responseHasNoPasswordFields() throws Exception {
        when(userService.create(any(UserCreateCommand.class))).thenReturn(
            new UserDto(1L, "anna@example.test", Role.USER)
        );

        MvcResult result = mockMvc
            .perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"anna@example.test\",\"password\":\"Secret-123\",\"role\":\"USER\"}")
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Secret-123");
    }

    @Test
    void post_enumTypo_returns400WithFieldRole() throws Exception {
        mockMvc
            .perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"anna@example.test\",\"password\":\"Secret-123\",\"role\":\"ADMN\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.errors[0].field").value("role"));
        verifyNoInteractions(userService);
    }

    @Test
    void post_shortPassword_violationDoesNotEchoRejectedValue() throws Exception {
        MvcResult result = mockMvc
            .perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"anna@example.test\",\"password\":\"abc12\",\"role\":\"USER\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("password"))
            .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("abc12");
    }

    @Test
    void patchPassword_valid_returns204WithEmptyBody() throws Exception {
        mockMvc
            .perform(
                patch("/api/users/3/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"New-Secret-456\"}")
            )
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
        verify(userService).changePassword(eq(3L), any(PasswordChangeCommand.class));
    }

    @Test
    void list_defaultPaging_sortsByIdAndUsesDefaultSize() throws Exception {
        when(userService.list(any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc
            .perform(get("/api/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(0));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).list(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("id"));
    }

    @Test
    void list_sortByEmailDesc_appendsIdTieBreaker() throws Exception {
        when(userService.list(any())).thenReturn(new PageResponse<>(List.of(), 1, 5, 0, 0));

        mockMvc
            .perform(get("/api/users").param("page", "1").param("size", "5").param("sort", "email,desc"))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).list(pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("email"), Sort.Order.asc("id")));
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
    }
}
