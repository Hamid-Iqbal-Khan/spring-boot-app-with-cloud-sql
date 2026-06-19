package com.cloud.sql.spring_boot_app_with_cloud_sql.service;

import com.cloud.sql.spring_boot_app_with_cloud_sql.dto.RebateResponse;
import com.cloud.sql.spring_boot_app_with_cloud_sql.entity.SubscriptionType;
import com.cloud.sql.spring_boot_app_with_cloud_sql.entity.User;
import com.cloud.sql.spring_boot_app_with_cloud_sql.repo.UserDao;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserService {

  private final UserDao userDao;

  public UserService(UserDao userDao) {
    this.userDao = userDao;
  }

  public User create(User user) {
    return userDao.insert(user);
  }

  public List<User> findAll() {
    return userDao.findAll();
  }

  public User findById(Integer id) {
    return userDao.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
  }

  public User update(Integer id, User user) {
    User existing = findById(id);
    existing.setName(user.getName());
    existing.setPlan(user.getPlan());
    existing.setSubscribeDate(user.getSubscribeDate());
    existing.setUnsubscribeDate(user.getUnsubscribeDate());
    return userDao.update(existing);
  }

  public void delete(Integer id) {
    userDao.deleteById(id);
  }

  public RebateResponse calculateRebate(Integer id) {
    User user = findById(id);
    double rebate = 0.0;
    List<String> reasons = new ArrayList<>();

    if (user.getSubscribeDate() != null) {
      long years =
          ChronoUnit.YEARS.between(user.getSubscribeDate(), LocalDate.now(Clock.systemUTC()));
      if (years >= 1) {
        rebate += 10.0;
        reasons.add("10% loyalty rebate (subscribed for over 1 year)");
      }
    }

    if (SubscriptionType.PREMIUM.name().equals(user.getPlan())) {
      rebate += 20.0;
      reasons.add("20% Premium plan rebate");
    }

    String message =
        reasons.isEmpty()
            ? "No rebate applicable for this account."
            : String.join(" + ", reasons) + ". Total: " + (int) rebate + "% off renewal.";

    return new RebateResponse(
        user.getId(), user.getName(), user.getPlan(), user.getSubscribeDate(), rebate, message);
  }
}
