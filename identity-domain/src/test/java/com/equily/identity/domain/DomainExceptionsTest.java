package com.equily.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.equily.identity.domain.exception.InvalidCredentialsException;
import com.equily.identity.domain.exception.UserAlreadyExistsException;
import com.equily.identity.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;

class DomainExceptionsTest {

  @Test
  void UserAlreadyExistsException_message_contains_email() {
    assertThat(new UserAlreadyExistsException("test@test.com").getMessage())
        .contains("test@test.com");
  }

  @Test
  void InvalidCredentialsException_has_non_blank_message() {
    assertThat(new InvalidCredentialsException().getMessage()).isNotBlank();
  }

  @Test
  void InvalidCredentialsException_string_constructor_uses_provided_message() {
    assertThat(new InvalidCredentialsException("Invalid or expired refresh token").getMessage())
        .isEqualTo("Invalid or expired refresh token");
  }

  @Test
  void UserNotFoundException_message_contains_identifier() {
    assertThat(new UserNotFoundException("test@test.com").getMessage()).contains("test@test.com");
  }

  @Test
  void HouseholdMemberRole_has_three_values() {
    assertThat(HouseholdMemberRole.values()).hasSize(3);
    assertThat(HouseholdMemberRole.valueOf("OWNER")).isEqualTo(HouseholdMemberRole.OWNER);
    assertThat(HouseholdMemberRole.valueOf("MEMBER")).isEqualTo(HouseholdMemberRole.MEMBER);
    assertThat(HouseholdMemberRole.valueOf("VIEWER")).isEqualTo(HouseholdMemberRole.VIEWER);
  }
}
