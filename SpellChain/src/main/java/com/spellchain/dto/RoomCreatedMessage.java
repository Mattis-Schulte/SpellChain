package com.spellchain.dto;

public record RoomCreatedMessage(String type, String roomId, int playerNumber, int playerCount, boolean started) {
  public RoomCreatedMessage(String roomId, int playerNumber, int playerCount, boolean started) {
    this("room_created", roomId, playerNumber, playerCount, started);
  }
}