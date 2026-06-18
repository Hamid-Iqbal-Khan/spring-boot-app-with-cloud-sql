package com.cloud.sql.spring_boot_app_with_cloud_sql.service;

import com.cloud.sql.spring_boot_app_with_cloud_sql.entity.User;
import com.cloud.sql.spring_boot_app_with_cloud_sql.repo.UserDao;
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
    return userDao.update(existing);
  }

  public void delete(Integer id) {
    userDao.deleteById(id);
  }
}
