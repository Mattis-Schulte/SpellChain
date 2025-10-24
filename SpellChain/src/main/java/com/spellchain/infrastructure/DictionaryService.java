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

/**
 * Implementation of a simple dictionary backed by an in-memory trie.
 *
 * <p>This service loads a JSON dictionary file on construction (after Spring initializes the bean).
 * Loaded entries are normalized to lower-case (Locale.ROOT) and inserted into a trie for fast
 * prefix and exact-word queries. Definitions are formatted from the senses and truncated to a
 * configured maximum length.
 */
@Service
public class DictionaryService implements Dictionary {
  /**
   * JSON resource file containing the dictionary data. Provided via Spring property
   * {@code spellchain.dictionary-path}.
   */
  private final Resource file;
  /** Root node of the trie storing words and definitions. */
  private final TrieNode root = new TrieNode();

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int DEF_MAX = 500;
  private static final Locale LOCALE = Locale.ROOT;
  private static final String NO_DEF = "No definition available.";

  /**
   * Internal trie node representation.
   *
   * <p>Children are stored in a map keyed by character. If {@code def} is non-null the node
   * represents a complete word and {@code def} contains its formatted definition.
   */
  private static final class TrieNode {
    Map<Character, TrieNode> kids;
    String def;
  }

  /**
   * Representation of a dictionary sense (a gloss and optional tags).
   *
   * <p>Exposed as a record to make it easy to map JSON objects into this structure via Jackson.
   *
   * @param gloss human readable definition text for this sense
   * @param tags optional tags associated with the sense (e.g., usage notes)
   */
  public record Sense(String gloss, List<String> tags) {}

  /**
   * Create a {@code DictionaryService} backed by the given JSON resource.
   *
   * @param file Spring {@link Resource} pointing to the dictionary JSON file (must not be null)
   */
  public DictionaryService(@Value("${spellchain.dictionary-path}") Resource file) {
    this.file = Objects.requireNonNull(file);
  }

  /**
   * Load the dictionary file into the trie.
   *
   * <p>This method is invoked by the container after construction ({@link PostConstruct}). It
   * parses the JSON resource, normalizes words, cleans sense data, formats definitions and inserts
   * them into the trie.
   *
   * @throws Exception if the file cannot be read or parsed
   */
  @PostConstruct
  public void load() throws Exception {
    if (!file.exists()) throw new IllegalStateException("Dictionary not found: " + file);

    try (InputStream in = file.getInputStream(); var p = MAPPER.createParser(in)) {
      if (p.nextToken() != JsonToken.START_OBJECT) {
        throw new IllegalStateException("Expected root JSON object");
      }
      while (p.nextToken() != JsonToken.END_OBJECT) {
        String rawWord = p.getCurrentName();
        p.nextToken();

        Map<String, List<Sense>> posMap =
            MAPPER.readValue(p, new TypeReference<Map<String, List<Sense>>>() {});

        if (rawWord == null || posMap == null) continue;

        String word = normalize(rawWord);
        Map<String, List<Sense>> cleaned = cleanPosMap(posMap);

        if (!cleaned.isEmpty()) {
          String def = truncate(format(cleaned), DEF_MAX);
          insertWord(word, def);
        }
      }
    }
  }

  /**
   * Check whether the provided string is a known dictionary word.
   *
   * @param w string to check (may be null)
   * @return true if {@code w} corresponds to an inserted word (case-insensitive), false otherwise
   */
  @Override
  public boolean isWord(String w) {
    if (w == null) return false;
    TrieNode n = node(normalize(w));
    return n != null && n.def != null;
  }

  /**
   * Check whether the provided string is a prefix of any word in the dictionary.
   *
   * @param p prefix to check (may be null)
   * @return true if there exists at least one word in the dictionary that begins with {@code p}
   */
  @Override
  public boolean hasPrefix(String p) {
    if (p == null) return false;
    TrieNode n = node(normalize(p));
    return n != null && n.kids != null && !n.kids.isEmpty();
  }

  /**
   * Retrieve the formatted definition for the given word.
   *
   * @param w word to look up (may be null)
   * @return formatted definition if the word exists, otherwise a {@code "No definition available."}
   *     placeholder
   */
  @Override
  public String definition(String w) {
    if (w == null) return NO_DEF;
    TrieNode n = node(normalize(w));
    return (n == null || n.def == null) ? NO_DEF : n.def;
  }

  /**
   * Traverse the trie to the node corresponding to the given normalized string.
   *
   * @param s normalized string (must already be lower-cased)
   * @return the trie node for {@code s}, or {@code null} if no such node exists
   */
  private TrieNode node(String s) {
    TrieNode n = root;
    for (int i = 0; i < s.length() && n != null; i++) {
      n = (n.kids == null) ? null : n.kids.get(s.charAt(i));
    }
    return n;
  }

  /**
   * Insert a word and its definition into the trie.
   *
   * <p>If intermediate nodes are missing they will be created.
   *
   * @param word normalized word to insert (must already be lower-cased)
   * @param def formatted definition to associate with the word
   */
  private void insertWord(String word, String def) {
    TrieNode n = root;
    for (char c : word.toCharArray()) {
      if (n.kids == null) n.kids = new HashMap<>(2);
      n = n.kids.computeIfAbsent(c, k -> new TrieNode());
    }
    n.def = def;
  }

  /**
   * Normalize a word for storage and lookup.
   *
   * <p>Current behavior lower-cases using {@link Locale#ROOT}.
   *
   * @param s input string
   * @return normalized string (never null if input was non-null)
   */
  private static String normalize(String s) {
    return s.toLowerCase(LOCALE);
  }

  /**
   * Clean raw JSON-parsed part-of-speech map into a safe structure.
   *
   * <p>Removes nulls, replaces null glosses/tags with safe defaults, and drops empty glosses.
   *
   * @param posMap raw map parsed from JSON (pos -> list of senses)
   * @return cleaned map containing only positions with at least one valid sense
   */
  private static Map<String, List<Sense>> cleanPosMap(Map<String, List<Sense>> posMap) {
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
    return cleaned;
  }

  /**
   * Format a cleaned pos->senses map into a readable single-line definition string.
   *
   * <p>Parts-of-speech are sorted (TreeMap). Each sense is numbered within its POS and tags are
   * appended in square brackets if present. Different parts of speech are separated by " | " and
   * senses within a POS are separated by " ; ".
   *
   * @param byPos cleaned map of parts-of-speech to senses
   * @return formatted definition string or the {@link #NO_DEF} placeholder if no senses exist
   */
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
    return sb.length() == 0 ? NO_DEF : sb.toString();
  }

  /**
   * Truncate a string to the given maximum length, appending an ellipsis character if truncated.
   *
   * @param s input string
   * @param max maximum allowed length (including the ellipsis if truncation occurs)
   * @return the original string if it fits within {@code max}, otherwise a truncated string
   *     ending with a Unicode ellipsis
   */
  private static String truncate(String s, int max) {
    if (s == null || s.length() <= max) return s;
    return s.substring(0, Math.max(0, max - 1)) + "…";
  }
}