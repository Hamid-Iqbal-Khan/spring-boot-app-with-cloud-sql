package com.cloud.sql.spring_boot_app_with_cloud_sql.repo;

import com.cloud.sql.spring_boot_app_with_cloud_sql.entity.User;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class UserRowMapper implements RowMapper<User> {

  @Override
  public User mapRow(ResultSet rs, int rowNum) throws SQLException {
    LocalDate subscribeDate = toLocalDate(rs.getDate("subscribe_date"));
    LocalDate unsubscribeDate = toLocalDate(rs.getDate("unsubscribe_date"));
    return new User(
        rs.getInt("id"),
        rs.getString("name"),
        rs.getString("plan"),
        subscribeDate,
        unsubscribeDate);
  }

  private LocalDate toLocalDate(Date date) {
    return date != null ? date.toLocalDate() : null;
  }
}
