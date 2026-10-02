package com.btc.userservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.userservice.config.JwtConfig;
import com.btc.userservice.config.SecurityConfig;
import com.btc.userservice.entity.User;
import com.btc.userservice.repository.UserRepository;
import com.btc.userservice.security.TestJwt;
import com.btc.userservice.service.impl.UserServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Role rules enforced end to end: verified JWT -> controller -> service, with only the repository mocked. */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtConfig.class, UserServiceImpl.class})
class UserAuthorizationWebTests {

    private static final String SELF_UPDATE_WITH_ROLE = """
            {"name":"Emp","email":"emp@example.com","phone":"1","role":"ADMIN"}""";

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void employeeIsForbiddenFromUserManagement() throws Exception {
        String employee = TestJwt.bearer(2, "EMPLOYEE");

        mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(post("/users").header(HttpHeaders.AUTHORIZATION, employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"email\":\"x@example.com\",\"phone\":\"1\",\"password\":\"long-enough\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/users/3").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/users/3").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(status().isForbidden());
        verify(userRepository, never()).delete(any());
    }

    @Test
    void adminCanListUsers() throws Exception {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(User.builder().id(2L).name("Emp").role("EMPLOYEE").build())));

        mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(1, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("EMPLOYEE"));
    }

    @Test
    void roleInSelfUpdateBodyIsIgnored() throws Exception {
        when(userRepository.findById(2L)).thenReturn(Optional.of(
                User.builder().id(2L).name("Emp").email("emp@example.com").phone("1").role("EMPLOYEE").build()));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/users/2").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(2, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SELF_UPDATE_WITH_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    void forgedRoleHeaderDoesNotGrantAdmin() throws Exception {
        mockMvc.perform(get("/users")
                        .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(2, "EMPLOYEE"))
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "1"))
                .andExpect(status().isForbidden());
    }
}
