package com.cloud.sql.spring_boot_app_with_cloud_sql.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloud.sql.spring_boot_app_with_cloud_sql.entity.User;
import com.cloud.sql.spring_boot_app_with_cloud_sql.repo.UserDao;
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
  void update_updatesNameAndSaves() {
    User existing = new User(1, "Alice");
    when(userDao.findById(1)).thenReturn(Optional.of(existing));
    when(userDao.update(existing)).thenReturn(new User(1, "Bob"));

    User result = userService.update(1, new User(null, "Bob"));

    assertThat(result.getName()).isEqualTo("Bob");
    verify(userDao).update(existing);
  }

  @Test
  void delete_callsDeleteById() {
    userService.delete(1);

    verify(userDao).deleteById(1);
  }
}
