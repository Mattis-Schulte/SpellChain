package com.spellchain.domain;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class GameRoom {
  private final String id;
  private final int capacity;
  private final Map<Integer, Player> players = new ConcurrentHashMap<>();
  private final Map<Integer, Integer> scores = new ConcurrentHashMap<>();
  private final Map<Integer, Set<String>> words = new ConcurrentHashMap<>();
  private final ReentrantLock lock = new ReentrantLock();

  private String sequence = "";
  private int round = 1;
  private int current = 1;
  private boolean started = false;

  public GameRoom(String id, int capacity) {
    this.id = id;
    this.capacity = capacity;
  }

  public String id() {
    return id;
  }

  public int capacity() {
    return capacity;
  }

  public Map<Integer, Player> players() {
    return players;
  }

  public Map<Integer, Integer> scores() {
    return scores;
  }

  public Map<Integer, Set<String>> words() {
    return words;
  }

  public ReentrantLock lock() {
    return lock;
  }

  public String sequence() {
    return sequence;
  }

  public void sequence(String s) {
    sequence = s;
  }

  public int round() {
    return round;
  }

  public void round(int r) {
    round = r;
  }

  public int current() {
    return current;
  }

  public void current(int p) {
    current = p;
  }

  public boolean started() {
    return started;
  }

  public void started(boolean s) {
    started = s;
  }

  public void addPlayer(int n, Player p) {
    players.put(n, p);
    scores.putIfAbsent(n, 0);
    words.putIfAbsent(n, Collections.synchronizedSet(new HashSet<>()));
  }

  public void removePlayer(int n) {
    players.remove(n);
    scores.remove(n);
    words.remove(n);
  }

  public void removeAll() {
    players.clear();
    scores.clear();
    words.clear();
    sequence = "";
    round = 1;
    current = 1;
    started = false;
  }
}