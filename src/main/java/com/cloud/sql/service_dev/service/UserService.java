package com.cloud.sql.service_dev.service;

import com.cloud.sql.service_dev.entity.User;
import com.cloud.sql.service_dev.repo.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public User create(User user) {
        return repository.save(user);
    }

    public List<User> findAll() {
        return repository.findAll();
    }

    public User findById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User update(Integer id, User user) {

        User existingUser = findById(id);

        existingUser.setName(user.getName());

        return repository.save(existingUser);
    }

    public void delete(Integer id) {
        repository.deleteById(id);
    }
}