package com.cloud.sql.spring_boot_app_with_cloud_sql.repo;

import com.cloud.sql.spring_boot_app_with_cloud_sql.entity.User;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserDao {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final UserRowMapper rowMapper;

  public UserDao(NamedParameterJdbcTemplate jdbcTemplate, UserRowMapper rowMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.rowMapper = rowMapper;
  }

  public User insert(User user) {
    String sql = "INSERT INTO users (name) VALUES (:name)";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("name", user.getName());
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(sql, params, keyHolder, new String[] {"id"});
    user.setId(keyHolder.getKey().intValue());
    return user;
  }

  public User update(User user) {
    String sql = "UPDATE users SET name = :name WHERE id = :id";
    jdbcTemplate.update(sql, Map.of("name", user.getName(), "id", user.getId()));
    return user;
  }

  public List<User> findAll() {
    return jdbcTemplate.query("SELECT id, name FROM users", rowMapper);
  }

  public Optional<User> findById(Integer id) {
    List<User> results =
        jdbcTemplate.query(
            "SELECT id, name FROM users WHERE id = :id", Map.of("id", id), rowMapper);
    return results.stream().findFirst();
  }

  public void deleteById(Integer id) {
    jdbcTemplate.update("DELETE FROM users WHERE id = :id", Map.of("id", id));
  }
}
