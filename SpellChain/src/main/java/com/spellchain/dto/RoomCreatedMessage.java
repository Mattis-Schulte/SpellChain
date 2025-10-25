package com.spellchain.dto;

public record RoomCreatedMessage(String type, String roomId, int playerNumber, boolean started) {
  public RoomCreatedMessage(String roomId, int playerNumber, boolean started) {
    this("room_created", roomId, playerNumber, started);
  }
}