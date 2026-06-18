package com.cloud.sql.spring_boot_app_with_cloud_sql.controller;

import com.cloud.sql.spring_boot_app_with_cloud_sql.dto.RebateResponse;
import com.cloud.sql.spring_boot_app_with_cloud_sql.entity.User;
import com.cloud.sql.spring_boot_app_with_cloud_sql.service.UserService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserService service;

  public UserController(UserService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public User create(@RequestBody User user) {
    return service.create(user);
  }

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public List<User> findAll() {
    return service.findAll();
  }

  @GetMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  public User findById(@PathVariable Integer id) {
    return service.findById(id);
  }

  @PutMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  public User update(@PathVariable Integer id, @RequestBody User user) {
    return service.update(id, user);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  public String delete(@PathVariable Integer id) {
    service.delete(id);
    return "User deleted successfully";
  }

  @GetMapping("/{id}/rebate")
  @ResponseStatus(HttpStatus.OK)
  public RebateResponse getRebate(@PathVariable Integer id) {
    return service.calculateRebate(id);
  }
}
