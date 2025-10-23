package com.spellchain.application.port;

public interface Dictionary {
  boolean isWord(String w);

  boolean hasPrefix(String p);

  String definition(String w);
}