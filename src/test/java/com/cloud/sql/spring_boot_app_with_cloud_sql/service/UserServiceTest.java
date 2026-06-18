package com.cloud.sql.spring_boot_app_with_cloud_sql.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloud.sql.spring_boot_app_with_cloud_sql.dto.RebateResponse;
import com.cloud.sql.spring_boot_app_with_cloud_sql.entity.User;
import com.cloud.sql.spring_boot_app_with_cloud_sql.repo.UserDao;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserDao userDao;

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(userDao);
  }

  @Test
  void create_savesAndReturnsUser() {
    User input = new User(null, "Alice");
    when(userDao.insert(input)).thenReturn(new User(1, "Alice"));

    User result = userService.create(input);

    assertThat(result.getId()).isEqualTo(1);
    assertThat(result.getName()).isEqualTo("Alice");
    verify(userDao).insert(input);
  }

  @Test
  void findAll_returnsAllUsers() {
    when(userDao.findAll()).thenReturn(List.of(new User(1, "Alice"), new User(2, "Bob")));

    assertThat(userService.findAll()).hasSize(2);
  }

  @Test
  void findById_returnsUser_whenExists() {
    when(userDao.findById(1)).thenReturn(Optional.of(new User(1, "Alice")));

    assertThat(userService.findById(1).getName()).isEqualTo("Alice");
  }

  @Test
  void findById_throwsException_whenNotFound() {
    when(userDao.findById(99)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.findById(99))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("User not found");
  }

  @Test
  void update_updatesAllFieldsAndSaves() {
    User existing = new User(1, "Alice", "BASIC", LocalDate.now().minusMonths(3), null);
    when(userDao.findById(1)).thenReturn(Optional.of(existing));
    when(userDao.update(existing))
        .thenReturn(new User(1, "Alice", "PREMIUM", LocalDate.now().minusMonths(3), null));

    User result =
        userService.update(
            1, new User(null, "Alice", "PREMIUM", LocalDate.now().minusMonths(3), null));

    assertThat(result.getPlan()).isEqualTo("PREMIUM");
    verify(userDao).update(existing);
  }

  @Test
  void delete_callsDeleteById() {
    userService.delete(1);

    verify(userDao).deleteById(1);
  }

  // --- Rebate tests ---

  @Test
  void calculateRebate_noRebate_whenBasicAndLessThanOneYear() {
    User user = new User(1, "Alice", "BASIC", LocalDate.now().minusMonths(6), null);
    when(userDao.findById(1)).thenReturn(Optional.of(user));

    RebateResponse response = userService.calculateRebate(1);

    assertThat(response.getRebatePercentage()).isEqualTo(0.0);
    assertThat(response.getMessage()).contains("No rebate applicable");
  }

  @Test
  void calculateRebate_10percent_whenBasicAndMoreThanOneYear() {
    User user = new User(1, "Alice", "BASIC", LocalDate.now().minusYears(2), null);
    when(userDao.findById(1)).thenReturn(Optional.of(user));

    RebateResponse response = userService.calculateRebate(1);

    assertThat(response.getRebatePercentage()).isEqualTo(10.0);
    assertThat(response.getMessage()).contains("10% loyalty rebate");
  }

  @Test
  void calculateRebate_20percent_whenPremiumAndLessThanOneYear() {
    User user = new User(1, "Alice", "PREMIUM", LocalDate.now().minusMonths(3), null);
    when(userDao.findById(1)).thenReturn(Optional.of(user));

    RebateResponse response = userService.calculateRebate(1);

    assertThat(response.getRebatePercentage()).isEqualTo(20.0);
    assertThat(response.getMessage()).contains("20% Premium plan rebate");
  }

  @Test
  void calculateRebate_30percent_whenPremiumAndMoreThanOneYear() {
    User user = new User(1, "Alice", "PREMIUM", LocalDate.now().minusYears(2), null);
    when(userDao.findById(1)).thenReturn(Optional.of(user));

    RebateResponse response = userService.calculateRebate(1);

    assertThat(response.getRebatePercentage()).isEqualTo(30.0);
    assertThat(response.getMessage()).contains("10% loyalty rebate");
    assertThat(response.getMessage()).contains("20% Premium plan rebate");
    assertThat(response.getMessage()).contains("30%");
  }

  @Test
  void calculateRebate_noRebate_whenNoSubscriptionDate() {
    User user = new User(1, "Alice", "BASIC", null, null);
    when(userDao.findById(1)).thenReturn(Optional.of(user));

    RebateResponse response = userService.calculateRebate(1);

    assertThat(response.getRebatePercentage()).isEqualTo(0.0);
  }
}
