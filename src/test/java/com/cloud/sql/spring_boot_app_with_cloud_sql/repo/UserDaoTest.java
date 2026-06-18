package com.cloud.sql.spring_boot_app_with_cloud_sql.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloud.sql.spring_boot_app_with_cloud_sql.entity.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@JdbcTest
@Import({UserDao.class, UserRowMapper.class})
@TestPropertySource(properties = {"spring.flyway.enabled=false"})
@Sql(
    statements = {
      "CREATE TABLE IF NOT EXISTS users (id SERIAL, name VARCHAR(255) NOT NULL, CONSTRAINT users_pk PRIMARY KEY (id))"
    })
class UserDaoTest {

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  private UserDao userDao;

  @BeforeEach
  void setUp() {
    userDao = new UserDao(jdbcTemplate, new UserRowMapper());
  }

  @Test
  void insert_persistsAndReturnsUserWithId() {
    User saved = userDao.insert(new User(null, "Alice"));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getName()).isEqualTo("Alice");
  }

  @Test
  void findAll_returnsAllInsertedUsers() {
    userDao.insert(new User(null, "Alice"));
    userDao.insert(new User(null, "Bob"));

    List<User> users = userDao.findAll();

    assertThat(users).hasSize(2);
  }

  @Test
  void findById_returnsUser_whenExists() {
    User saved = userDao.insert(new User(null, "Alice"));

    assertThat(userDao.findById(saved.getId())).isPresent();
  }

  @Test
  void findById_returnsEmpty_whenNotFound() {
    assertThat(userDao.findById(999)).isEmpty();
  }

  @Test
  void update_changesName() {
    User saved = userDao.insert(new User(null, "Alice"));
    saved.setName("Bob");

    User updated = userDao.update(saved);

    assertThat(updated.getName()).isEqualTo("Bob");
    assertThat(userDao.findById(saved.getId()).get().getName()).isEqualTo("Bob");
  }

  @Test
  void deleteById_removesUser() {
    User saved = userDao.insert(new User(null, "Alice"));
    userDao.deleteById(saved.getId());

    assertThat(userDao.findById(saved.getId())).isEmpty();
  }
}
