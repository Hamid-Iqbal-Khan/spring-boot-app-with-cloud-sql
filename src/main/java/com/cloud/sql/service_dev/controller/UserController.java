package com.cloud.sql.service_dev.controller;

import com.cloud.sql.service_dev.entity.User;
import com.cloud.sql.service_dev.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping
    public User create(@RequestBody User user) {
        return service.create(user);
    }

    @GetMapping
    public List<User> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public User findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public User update(@PathVariable Integer id,
                       @RequestBody User user) {
        return service.update(id, user);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Integer id) {
        service.delete(id);
        return "User deleted successfully";
    }
}