package com.cloud.sql.spring_boot_app_with_cloud_sql.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloud.sql.spring_boot_app_with_cloud_sql.dto.RebateResponse;
import com.cloud.sql.spring_boot_app_with_cloud_sql.entity.User;
import com.cloud.sql.spring_boot_app_with_cloud_sql.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
class UserControllerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;

  @Test
  void create_returns201_withCreatedUser() throws Exception {
    when(userService.create(any(User.class))).thenReturn(new User(1, "Alice"));

    mockMvc
        .perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new User(null, "Alice"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.name").value("Alice"));
  }

  @Test
  void findAll_returns200_withUserList() throws Exception {
    when(userService.findAll()).thenReturn(List.of(new User(1, "Alice"), new User(2, "Bob")));

    mockMvc
        .perform(get("/api/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void findById_returns200_whenFound() throws Exception {
    when(userService.findById(1)).thenReturn(new User(1, "Alice"));

    mockMvc
        .perform(get("/api/users/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Alice"));
  }

  @Test
  void update_returns200_withUpdatedUser() throws Exception {
    when(userService.update(eq(1), any(User.class))).thenReturn(new User(1, "Bob"));

    mockMvc
        .perform(
            put("/api/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new User(null, "Bob"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Bob"));
  }

  @Test
  void delete_returns200() throws Exception {
    doNothing().when(userService).delete(1);

    mockMvc.perform(delete("/api/users/1")).andExpect(status().isOk());
  }

  @Test
  void getRebate_returns200_withRebateDetails() throws Exception {
    RebateResponse response =
        new RebateResponse(
            1,
            "Alice",
            "PREMIUM",
            LocalDate.of(2022, Month.JANUARY, 1),
            30.0,
            "10% loyalty rebate (subscribed for over 1 year) + 20% Premium plan rebate. Total: 30% off renewal.");
    when(userService.calculateRebate(1)).thenReturn(response);

    mockMvc
        .perform(get("/api/users/1/rebate"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(1))
        .andExpect(jsonPath("$.plan").value("PREMIUM"))
        .andExpect(jsonPath("$.rebatePercentage").value(30.0))
        .andExpect(jsonPath("$.message").value(response.getMessage()));
  }
}
