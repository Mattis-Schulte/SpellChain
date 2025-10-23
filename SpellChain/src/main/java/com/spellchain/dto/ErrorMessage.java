package com.spellchain.dto;

public record ErrorMessage(String type, String message) {
  public ErrorMessage(String message) {
    this("error", message);
  }
}