package com.spellchain.interfaces.rest;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigController {
  private final int minPlayers;
  private final int maxPlayers;
  private final String allowedPunctuation;

  public ConfigController(
      @Value("${spellchain.min-players:2}") int minPlayers,
      @Value("${spellchain.max-players:4}") int maxPlayers,
      @Value("${spellchain.allowed-punctuation:-'/ .}") String allowedPunctuation) {
    this.minPlayers = minPlayers;
    this.maxPlayers = maxPlayers;
    this.allowedPunctuation = allowedPunctuation;
  }

  @GetMapping("/config")
  public Map<String, Object> config() {
    return Map.of(
        "minPlayers", minPlayers,
        "maxPlayers", maxPlayers,
        "allowedPunctuation", allowedPunctuation,
        "protocolVersion", 1);
  }
}