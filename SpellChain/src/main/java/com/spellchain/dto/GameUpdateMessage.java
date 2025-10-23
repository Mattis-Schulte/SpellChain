package com.spellchain.dto;

import java.util.List;
import java.util.Map;

public record GameUpdateMessage(
    String type,
    Integer player,
    String ch,
    List<String> messages,
    Integer currentPlayer,
    String sequence,
    Map<Integer, Integer> scores,
    Integer roundCount,
    Boolean started,
    Integer playerCount,
    Integer joinedCount,
    Integer hostPlayer) {

  public static GameUpdateMessage base(Boolean started, Integer cap, Integer joined, Integer host) {
    return new GameUpdateMessage(
        "game_update",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        started,
        cap,
        joined,
        host);
  }

  public GameUpdateMessage withMessages(List<String> msgs) {
    return new GameUpdateMessage(
        type,
        player,
        ch,
        msgs,
        currentPlayer,
        sequence,
        scores,
        roundCount,
        started,
        playerCount,
        joinedCount,
        hostPlayer);
  }

  public GameUpdateMessage withSnapshot(
      int cur, String seq, Map<Integer, Integer> sc, int round) {
    return new GameUpdateMessage(
        type, player, ch, messages, cur, seq, sc, round, started, playerCount, joinedCount,
        hostPlayer);
  }

  public GameUpdateMessage withLastMove(int lastPlayer, String lastCh) {
    return new GameUpdateMessage(
        type,
        lastPlayer,
        lastCh,
        messages,
        currentPlayer,
        sequence,
        scores,
        roundCount,
        started,
        playerCount,
        joinedCount,
        hostPlayer);
  }
}