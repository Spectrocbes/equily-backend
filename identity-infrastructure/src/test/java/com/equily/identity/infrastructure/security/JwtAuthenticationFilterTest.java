package com.equily.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.equily.identity.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock private JwtService jwtService;
  @InjectMocks private JwtAuthenticationFilter filter;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private MockFilterChain chain;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    chain = new MockFilterChain();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void passes_through_when_no_authorization_header() throws Exception {
    filter.doFilterInternal(request, response, chain);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void passes_through_when_not_bearer_prefix() throws Exception {
    request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
    filter.doFilterInternal(request, response, chain);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void sets_authentication_for_valid_token() throws Exception {
    UserId userId = UserId.generate();
    request.addHeader("Authorization", "Bearer valid.jwt.token");
    when(jwtService.isTokenValid("valid.jwt.token")).thenReturn(true);
    when(jwtService.extractUserId("valid.jwt.token")).thenReturn(userId);

    filter.doFilterInternal(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isEqualTo(userId);
  }

  @Test
  void does_not_set_authentication_for_invalid_token() throws Exception {
    request.addHeader("Authorization", "Bearer invalid.token");
    when(jwtService.isTokenValid("invalid.token")).thenReturn(false);

    filter.doFilterInternal(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
