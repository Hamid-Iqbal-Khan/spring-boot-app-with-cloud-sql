package com.cloud.sql.spring_boot_app_with_cloud_sql.repo;

import com.cloud.sql.spring_boot_app_with_cloud_sql.entity.User;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class UserRowMapper implements RowMapper<User> {

  @Override
  public User mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new User(rs.getInt("id"), rs.getString("name"));
  }
}
