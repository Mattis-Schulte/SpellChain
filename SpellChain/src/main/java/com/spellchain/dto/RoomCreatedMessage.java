package com.spellchain.dto;

public record RoomCreatedMessage(String type, String roomId, int playerNumber, int playerCount) {
  public RoomCreatedMessage(String roomId, int playerNumber, int playerCount) {
    this("room_created", roomId, playerNumber, playerCount);
  }
}