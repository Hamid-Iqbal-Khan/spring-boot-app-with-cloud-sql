package com.cloud.sql.spring_boot_app_with_cloud_sql.entity;

public class User {

  private Integer id;
  private String name;

  public User() {}

  public User(Integer id, String name) {
    this.id = id;
    this.name = name;
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

  @Override
  public String toString() {
    return "User{id=" + id + ", name='" + name + "'}";
  }
}
