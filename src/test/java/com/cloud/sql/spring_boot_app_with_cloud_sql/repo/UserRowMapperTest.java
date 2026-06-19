package com.cloud.sql.spring_boot_app_with_cloud_sql.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cloud.sql.spring_boot_app_with_cloud_sql.entity.User;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserRowMapperTest {

  @Mock private ResultSet rs;

  private final UserRowMapper mapper = new UserRowMapper();

  @Test
  void mapRow_mapsAllFieldsCorrectly() throws SQLException {
    LocalDate subscribeDate = LocalDate.of(2022, Month.JANUARY, 1);
    LocalDate unsubscribeDate = LocalDate.of(2024, Month.JUNE, 15);

    when(rs.getInt("id")).thenReturn(1);
    when(rs.getString("name")).thenReturn("Alice");
    when(rs.getString("plan")).thenReturn("PREMIUM");
    when(rs.getObject("subscribe_date", LocalDate.class)).thenReturn(subscribeDate);
    when(rs.getObject("unsubscribe_date", LocalDate.class)).thenReturn(unsubscribeDate);

    User user = mapper.mapRow(rs, 1);

    assertThat(user.getId()).isEqualTo(1);
    assertThat(user.getName()).isEqualTo("Alice");
    assertThat(user.getPlan()).isEqualTo("PREMIUM");
    assertThat(user.getSubscribeDate()).isEqualTo(subscribeDate);
    assertThat(user.getUnsubscribeDate()).isEqualTo(unsubscribeDate);
  }

  @Test
  void mapRow_handlesNullDates() throws SQLException {
    when(rs.getInt("id")).thenReturn(2);
    when(rs.getString("name")).thenReturn("Bob");
    when(rs.getString("plan")).thenReturn("BASIC");
    when(rs.getObject("subscribe_date", LocalDate.class)).thenReturn(null);
    when(rs.getObject("unsubscribe_date", LocalDate.class)).thenReturn(null);

    User user = mapper.mapRow(rs, 1);

    assertThat(user.getSubscribeDate()).isNull();
    assertThat(user.getUnsubscribeDate()).isNull();
  }
}
