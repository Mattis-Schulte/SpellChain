package com.spellchain.infrastructure;

import com.spellchain.application.port.Dictionary;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class DictionaryService implements Dictionary {
  private final Resource file;
  private final TrieNode root = new TrieNode();
  private static final Pattern SEP = Pattern.compile("\\s{2,}");

  public DictionaryService(@Value("${spellchain.dictionary-path}") Resource file) {
    this.file = file;
  }

  @PostConstruct
  public void load() throws Exception {
    if (!file.exists()) {
      throw new IllegalStateException("Dictionary not found: " + file);
    }
    try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
      String line;
      while ((line = br.readLine()) != null) {
        line = line.replace("\u007F ", "");
        String[] parts = SEP.split(line.trim(), 2);
        if (parts.length < 2) {
          continue;
        }
        String word = parts[0].toLowerCase().replaceAll("\\d+$", "");
        insert(word, parts[1].trim());
      }
    }
  }

  private static class TrieNode {
    final Map<Character, TrieNode> kids = new ConcurrentHashMap<>();
    volatile boolean word;
    volatile String def;
  }

  private void insert(String w, String d) {
    TrieNode n = root;
    for (char c : w.toCharArray()) {
      n = n.kids.computeIfAbsent(c, k -> new TrieNode());
    }
    synchronized (n) {
      n.def = n.word ? n.def + " OR " + d : d;
      n.word = true;
    }
  }

  @Override
  public boolean isWord(String w) {
    if (w == null) {
      return false;
    }
    w = w.toLowerCase(Locale.ROOT);
    TrieNode n = node(w);
    return n != null && n.word;
  }

  @Override
  public boolean hasPrefix(String p) {
    if (p == null) {
      return false;
    }
    p = p.toLowerCase(Locale.ROOT);
    TrieNode n = node(p);
    return n != null && !n.kids.isEmpty();
  }

  @Override
  public String definition(String w) {
    if (w == null) {
      return "No definition available.";
    }
    w = w.toLowerCase(Locale.ROOT);
    TrieNode n = node(w);
    return (n != null && n.word) ? n.def : "No definition available.";
  }

  private TrieNode node(String s) {
    if (s == null) {
      return null;
    }
    TrieNode n = root;
    for (char c : s.toCharArray()) {
      n = n.kids.get(c);
      if (n == null) {
        return null;
      }
    }
    return n;
  }
}