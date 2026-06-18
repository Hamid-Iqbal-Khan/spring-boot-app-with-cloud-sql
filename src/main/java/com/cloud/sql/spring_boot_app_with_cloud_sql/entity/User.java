package com.cloud.sql.spring_boot_app_with_cloud_sql.entity;

import java.time.LocalDate;

public class User {

  private Integer id;
  private String name;
  private String plan;
  private LocalDate subscribeDate;
  private LocalDate unsubscribeDate;

  public User() {}

  public User(Integer id, String name) {
    this.id = id;
    this.name = name;
  }

  public User(
      Integer id, String name, String plan, LocalDate subscribeDate, LocalDate unsubscribeDate) {
    this.id = id;
    this.name = name;
    this.plan = plan;
    this.subscribeDate = subscribeDate;
    this.unsubscribeDate = unsubscribeDate;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPlan() {
    return plan;
  }

  public void setPlan(String plan) {
    this.plan = plan;
  }

  public LocalDate getSubscribeDate() {
    return subscribeDate;
  }

  public void setSubscribeDate(LocalDate subscribeDate) {
    this.subscribeDate = subscribeDate;
  }

  public LocalDate getUnsubscribeDate() {
    return unsubscribeDate;
  }

  public void setUnsubscribeDate(LocalDate unsubscribeDate) {
    this.unsubscribeDate = unsubscribeDate;
  }

  @Override
  public String toString() {
    return "User{id="
        + id
        + ", name='"
        + name
        + "', plan='"
        + plan
        + "', subscribeDate="
        + subscribeDate
        + ", unsubscribeDate="
        + unsubscribeDate
        + "}";
  }
}
