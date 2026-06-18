package com.cloud.sql.spring_boot_app_with_cloud_sql.dto;

import java.time.LocalDate;

public class RebateResponse {

  private final Integer userId;
  private final String name;
  private final String plan;
  private final LocalDate subscribeDate;
  private final double rebatePercentage;
  private final String message;

  public RebateResponse(
      Integer userId,
      String name,
      String plan,
      LocalDate subscribeDate,
      double rebatePercentage,
      String message) {
    this.userId = userId;
    this.name = name;
    this.plan = plan;
    this.subscribeDate = subscribeDate;
    this.rebatePercentage = rebatePercentage;
    this.message = message;
  }

  public Integer getUserId() {
    return userId;
  }

  public String getName() {
    return name;
  }

  public String getPlan() {
    return plan;
  }

  public LocalDate getSubscribeDate() {
    return subscribeDate;
  }

  public double getRebatePercentage() {
    return rebatePercentage;
  }

  public String getMessage() {
    return message;
  }
}
