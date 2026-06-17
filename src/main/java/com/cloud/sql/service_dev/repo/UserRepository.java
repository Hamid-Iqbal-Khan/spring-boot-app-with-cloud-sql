package com.cloud.sql.service_dev.repo;
import com.cloud.sql.service_dev.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {
}
