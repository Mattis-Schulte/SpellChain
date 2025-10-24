package com.spellchain.infrastructure;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spellchain.application.port.Dictionary;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class DictionaryService implements Dictionary {
  private final Resource file;
  private final TrieNode root = new TrieNode();
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int DEF_MAX = 500;

  public DictionaryService(@Value("${spellchain.dictionary-path}") Resource file) {
    this.file = Objects.requireNonNull(file);
  }

  private static final class TrieNode {
    Map<Character, TrieNode> kids;
    String def;
  }

  public record Sense(String gloss, List<String> tags) {}

  @PostConstruct
  public void load() throws Exception {
    if (!file.exists()) throw new IllegalStateException("Dictionary not found: " + file);

    try (InputStream in = file.getInputStream(); var p = MAPPER.createParser(in)) {
      if (p.nextToken() != JsonToken.START_OBJECT) {
        throw new IllegalStateException("Expected root JSON object");
      }
      while (p.nextToken() != JsonToken.END_OBJECT) {
        String w = p.getCurrentName();
        p.nextToken();

        Map<String, List<Sense>> posMap =
            MAPPER.readValue(p, new TypeReference<Map<String, List<Sense>>>() {});

        if (w == null || posMap == null) continue;
        String word = w.toLowerCase(Locale.ROOT);

        Map<String, List<Sense>> cleaned = new HashMap<>();
        posMap.forEach((pos, senses) -> {
          if (pos == null) return;
          List<Sense> list = (senses == null ? List.<Sense>of() : senses).stream()
              .filter(Objects::nonNull)
              .map(s -> new Sense(
                  s.gloss() == null ? "" : s.gloss(),
                  s.tags() == null ? List.of() : List.copyOf(s.tags())))
              .filter(s -> !s.gloss().isBlank())
              .toList();
          if (!list.isEmpty()) cleaned.put(pos, list);
        });

        if (!cleaned.isEmpty()) {
          String def = truncate(format(cleaned), DEF_MAX);
          TrieNode n = root;
          for (char c : word.toCharArray()) {
            if (n.kids == null) n.kids = new HashMap<>(2);
            n = n.kids.computeIfAbsent(c, k -> new TrieNode());
          }
          n.def = def;
        }
      }
    }
  }

  @Override
  public boolean isWord(String w) {
    if (w == null) return false;
    TrieNode n = node(w.toLowerCase(Locale.ROOT));
    return n != null && n.def != null;
  }

  @Override
  public boolean hasPrefix(String p) {
    if (p == null) return false;
    TrieNode n = node(p.toLowerCase(Locale.ROOT));
    return n != null && n.kids != null && !n.kids.isEmpty();
  }

  @Override
  public String definition(String w) {
    if (w == null) return "No definition available.";
    TrieNode n = node(w.toLowerCase(Locale.ROOT));
    return (n == null || n.def == null) ? "No definition available." : n.def;
  }

  private TrieNode node(String s) {
    TrieNode n = root;
    for (int i = 0; i < s.length() && n != null; i++) {
      n = (n.kids == null) ? null : n.kids.get(s.charAt(i));
    }
    return n;
  }

  private static String format(Map<String, List<Sense>> byPos) {
    StringBuilder sb = new StringBuilder();
    boolean firstPos = true;
    for (Map.Entry<String, List<Sense>> e : new TreeMap<>(byPos).entrySet()) {
      List<Sense> senses = e.getValue();
      if (senses == null || senses.isEmpty()) continue;

      if (!firstPos) sb.append(" | ");
      firstPos = false;
      sb.append(e.getKey()).append(": ");

      for (int i = 0; i < senses.size(); i++) {
        Sense s = senses.get(i);
        if (i > 0) sb.append(" ; ");
        sb.append(i + 1).append(". ").append(s.gloss());
        if (!s.tags().isEmpty()) {
          sb.append(" [").append(String.join(", ", s.tags())).append("]");
        }
      }
    }
    return sb.length() == 0 ? "No definition available." : sb.toString();
  }

  private static String truncate(String s, int max) {
    if (s == null || s.length() <= max) return s;
    return s.substring(0, Math.max(0, max - 1)) + "…";
  }
}