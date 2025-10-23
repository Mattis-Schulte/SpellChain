package com.spellchain.domain;

import java.util.List;

public record GameBroadcast(
    String roomId, Snapshot snapshot, Integer lastPlayer, String lastCh, List<String> messages) {
  public static GameBroadcast of(String roomId, Snapshot s) {
    return new GameBroadcast(roomId, s, null, null, null);
  }

  public GameBroadcast withLastMove(int player, String ch) {
    return new GameBroadcast(roomId, snapshot, player, ch, messages);
  }

  public GameBroadcast withMessages(List<String> msgs) {
    return new GameBroadcast(roomId, snapshot, lastPlayer, lastCh, msgs);
  }
}