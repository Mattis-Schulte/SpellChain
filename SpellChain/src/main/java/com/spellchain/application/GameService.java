package com.spellchain.application;

import com.spellchain.application.port.Dictionary;
import com.spellchain.application.port.GamePublisher;
import com.spellchain.domain.GameBroadcast;
import com.spellchain.domain.GameRoom;
import com.spellchain.domain.Player;
import com.spellchain.domain.Snapshot;
import com.spellchain.dto.RoomCreatedMessage;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Core game orchestration service.
 *
 * <p>Responsibilities:
 * - Create/join rooms and manage membership
 * - Start games and maintain turn order
 * - Process moves, scoring, and round flow
 * - Publish real-time updates to clients
 *
 * <p>State is in-memory using concurrent maps. Per-room operations are guarded by a
 * {@link java.util.concurrent.locks.ReentrantLock} within {@link GameRoom}. Public methods
 * validate input, lock as needed, update state, take a snapshot, then publish via
 * {@link GamePublisher} after releasing the lock.
 */
@Service
public class GameService {
  private static final String ROOM_ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final SecureRandom rnd = new SecureRandom();

  /** All active rooms keyed by room code (upper-case). */
  private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
  /** Session-to-room reverse index to quickly find a session's current room. */
  private final Map<String, String> sessionRoom = new ConcurrentHashMap<>();

  private final Dictionary dict;
  private final GamePublisher publisher;
  private final int minPlayers;
  private final int maxPlayers;
  private final String allowedPunctuation;

  public GameService(
      Dictionary dict,
      GamePublisher publisher,
      @Value("${spellchain.min-players:2}") int minPlayers,
      @Value("${spellchain.max-players:4}") int maxPlayers,
      @Value("${spellchain.allowed-punctuation:-'/ .}") String allowedPunctuation) {
    this.dict = dict;
    this.publisher = publisher;
    this.minPlayers = minPlayers;
    this.maxPlayers = maxPlayers;
    this.allowedPunctuation = allowedPunctuation;
  }

  /**
   * Create a new room and register the calling session as host (#1).
   *
   * @param sid WebSocket session id of the creator (host)
   * @return message for the caller containing room metadata and assigned player number
   */
  public RoomCreatedMessage createRoom(String sid) {
    String id = newRoomId();
    GameRoom room = new GameRoom(id, maxPlayers);
    rooms.put(id, room);

    room.addPlayer(1, new Player(1, sid));
    sessionRoom.put(sid, id);

    GameBroadcast b;
    room.lock().lock();
    try {
      b =
          GameBroadcast.of(id, snap(room))
              .withMessages(
                  List.of(
                      "Room created. Share code: "
                          + id
                          + ". Waiting to start (auto-start at "
                          + maxPlayers
                          + ")."));
    } finally {
      room.lock().unlock();
    }
    publisher.publish(b);
    log.info("Created room {} host #1", id);
    return new RoomCreatedMessage(id, 1, room.capacity(), room.started());
  }

  /**
   * Join an existing room.
   *
   * <p>Behavior:
   * - If the session is already in a different room, it leaves that room first.
   * - If already in this room, returns the current assignment (idempotent).
   * - Otherwise assigns the lowest free player number in [1..capacity].
   * - Auto-starts when the room fills up.
   *
   * @param sid WebSocket session id
   * @param id room code (case-insensitive)
   * @return message for the caller containing room metadata and assigned player number
   * @throws NoSuchElementException if the room does not exist
   * @throws IllegalStateException if the game already started or the room is full
   */
  public RoomCreatedMessage joinRoom(String sid, String id) {
    id = norm(id);
    GameRoom room = getRoom(id);

    // Leave previous room if different (prevents a session from occupying multiple rooms)
    String prev = sessionRoom.get(sid);
    if (prev != null && !prev.equals(id)) {
      removePlayerBySession(sid);
    }

    // If already mapped to this room and still present → idempotent reply
    if (id.equals(sessionRoom.get(sid))) {
      room.lock().lock();
      try {
        int existing = playerNum(room, sid);
        if (existing > 0) {
          return new RoomCreatedMessage(id, existing, room.capacity(), room.started());
        }
        // Stale mapping: clean and proceed to join fresh
        sessionRoom.remove(sid);
      } finally {
        room.lock().unlock();
      }
    }

    GameBroadcast toPublish;
    RoomCreatedMessage reply;

    room.lock().lock();
    try {
      if (room.started()) {
        throw new IllegalStateException("Game already started");
      }
      if (room.players().size() >= room.capacity()) {
        throw new IllegalStateException("Room full");
      }

      int num = firstFreeNumber(room);
      room.addPlayer(num, new Player(num, sid));
      sessionRoom.put(sid, id);

      List<String> msgs = new ArrayList<>();
      msgs.add("Player " + num + " joined (" + room.players().size() + "/" + room.capacity() + ").");

      if (!room.started() && room.players().size() == room.capacity()) {
        startInternal(room);
        msgs.add("Auto-started at " + room.capacity() + " players.");
      }

      toPublish = GameBroadcast.of(id, snap(room)).withMessages(msgs);
      reply = new RoomCreatedMessage(id, num, room.capacity(), room.started());
      log.info("Session {} joined room {} as #{}", sid, id, num);
    } finally {
      room.lock().unlock();
    }

    publisher.publish(toPublish);
    return reply;
  }

  /**
   * Start a game manually. Only the host (player #1) may start a game.
   *
   * @param sid WebSocket session id of the caller
   * @param id room code
   * @throws NoSuchElementException if the room does not exist
   * @throws IllegalStateException if caller is not in the room, not host, or not enough players
   */
  public void startGame(String sid, String id) {
    id = norm(id);
    GameRoom room = getRoom(id);
    int num = playerNum(room, sid);
    if (num == 0) {
      throw new IllegalStateException("Player not in room");
    }
    if (num != 1) {
      throw new IllegalStateException("Only the host can start the game");
    }

    GameBroadcast toPublish = null;
    room.lock().lock();
    try {
      int joined = room.players().size();
      if (joined < minPlayers) {
        throw new IllegalStateException("Need at least " + minPlayers + " players to start");
      }
      if (!room.started()) {
        startInternal(room);
        toPublish = GameBroadcast.of(id, snap(room)).withMessages(List.of("Game started by host."));
      }
    } finally {
      room.lock().unlock();
    }
    if (toPublish != null) {
      publisher.publish(toPublish);
    }
  }

  /**
   * Process a player's single-character input.
   *
   * <p>Validates turn order and input, appends to the sequence, checks for completed words and
   * prefixes, awards points, advances the turn, and publishes an update. If the new sequence is
   * not a valid prefix, the round ends and the sequence resets.
   *
   * @param sid WebSocket session id of the caller
   * @param roomId room code
   * @param ch user-provided character string (must be exactly one Unicode code point)
   * @throws NoSuchElementException if the room does not exist
   * @throws IllegalStateException if not in the room, game not started, not enough players, or not caller's turn
   * @throws IllegalArgumentException if the input is not exactly one character or not allowed
   */
  public void addCharacter(String sid, String roomId, String ch) {
    if (ch == null || ch.isEmpty() || ch.codePointCount(0, ch.length()) != 1) {
      throw new IllegalArgumentException("Enter exactly one character");
    }

    GameRoom room = getRoom(roomId);
    int num = playerNum(room, sid);
    if (num == 0) {
      throw new IllegalStateException("Player not in room");
    }

    GameBroadcast toPublish;
    room.lock().lock();
    try {
      if (!room.started()) {
        throw new IllegalStateException("Game has not started.");
      }
      if (room.players().size() < minPlayers) {
        throw new IllegalStateException("Need at least " + minPlayers + " players.");
      }
      if (num != room.current()) {
        throw new IllegalStateException("Not your turn.");
      }

      // Unicode-safe lower-casing by code point
      int cp = ch.codePointAt(0);
      int lower = Character.toLowerCase(cp);
      String chStr = new String(Character.toChars(lower));

      boolean isLetter = Character.isLetter(lower);
      boolean isAllowedPunct = chStr.length() == 1 && allowedPunctuation.indexOf(chStr.charAt(0)) >= 0;
      if (!(isLetter || isAllowedPunct)) {
        throw new IllegalArgumentException("Invalid character: enter a single letter or one of " + allowedPunctuation);
      }

      String cand = room.sequence() + chStr;
      List<String> messages = new ArrayList<>();

      if (dict.isWord(cand)) {
        boolean used = room.words().values().stream().anyMatch(s -> s.contains(cand));
        if (!used) {
          int pts = Math.max((cand.length() + 1) / 2, 1);
          room.scores().merge(num, pts, Integer::sum);
          room.words().get(num).add(cand);
          String def = dict.definition(cand);
          messages.add(
              String.format(
                  "*** Player %d completed \"%s\"! (%d Point%s) *** Definition: %s",
                  num, cand, pts, (pts != 1 ? "s" : ""), def));
        } else {
          messages.add("\"" + cand + "\" has already been used. No points this round.");
        }
      }

      if (!dict.hasPrefix(cand)) {
        messages.add("\"" + cand + "\" is not a valid prefix. Round over. Sequence reset.");
        room.sequence("");
        room.round(room.round() + 1);
      } else {
        room.sequence(cand);
      }

      room.current(nextActive(room));
      toPublish =
          GameBroadcast.of(room.id(), snap(room))
              .withLastMove(num, chStr)
              .withMessages(messages);
    } finally {
      room.lock().unlock();
    }
    publisher.publish(toPublish);
    log.info("Room {} p#{} '{}' processed", roomId, num, ch);
  }

  /**
   * Remove a player by WebSocket session id.
   *
   * <p>If the game has not started:
   * - If the host (#1) leaves, the room closes.
   * - Otherwise the player is removed; if no players remain, the room is cleaned up.
   *
   * <p>If the game has started, the game ends and the room is cleaned up.
   *
   * @param sid WebSocket session id to remove
   */
  public void removePlayerBySession(String sid) {
    String roomId = sessionRoom.remove(sid);
    if (roomId == null) return;

    GameRoom room = rooms.get(roomId);
    if (room == null) return;

    GameBroadcast b;
    room.lock().lock();
    try {
      int num = playerNum(room, sid);
      if (!room.started()) {
        if (num == 1) {
          b = GameBroadcast.of(roomId, snap(room)).withMessages(List.of("Host left. Room closed."));
          cleanupRoom(roomId, room);
        } else {
          if (num > 0) room.removePlayer(num);
          b = GameBroadcast.of(roomId, snap(room)).withMessages(List.of("Player " + num + " left."));
          if (room.players().isEmpty()) cleanupRoom(roomId, room);
        }
      } else {
        b =
            new GameBroadcast(
                roomId,
                new Snapshot(
                    false,
                    room.capacity(),
                    Math.max(0, room.players().size() - 1),
                    1,
                    null,
                    room.sequence(),
                    new HashMap<>(room.scores()),
                    room.round()),
                null,
                null,
                List.of("Player " + num + " left. Game ended."));
        cleanupRoom(roomId, room);
      }
    } finally {
      room.lock().unlock();
    }
    publisher.publish(b);
  }

  // Helpers

  /** Normalize a room id (trim and upper-case). */
  private String norm(String id) {
    return id == null ? null : id.trim().toUpperCase(Locale.ROOT);
  }

  /** Retrieve a room by id or throw. */
  private GameRoom getRoom(String id) {
    GameRoom r = rooms.get(norm(id));
    if (r == null) throw new NoSuchElementException("Room not found");
    return r;
  }

  /**
   * Find the player number for the session.
   *
   * @return player number in the room, or 0 if the session is not present
   */
  private int playerNum(GameRoom room, String sid) {
    return room.players().entrySet().stream()
        .filter(e -> sid.equals(e.getValue().sessionId()))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(0);
  }

  /** Generate a new unique 6-character room id. */
  private String newRoomId() {
    while (true) {
      StringBuilder sb = new StringBuilder(6);
      for (int j = 0; j < 6; j++) {
        sb.append(ROOM_ID_CHARS.charAt(rnd.nextInt(ROOM_ID_CHARS.length())));
      }
      String id = sb.toString();
      if (!rooms.containsKey(id)) return id;
    }
  }

  /** Mark room as started and set current player to the lowest-numbered active player. */
  private void startInternal(GameRoom room) {
    room.started(true);
    int first = room.players().keySet().stream().min(Integer::compareTo).orElse(1);
    room.current(first);
  }

  /** Return the next active player after the current player (wrap-around). */
  private int nextActive(GameRoom room) {
    List<Integer> active = new ArrayList<>(room.players().keySet());
    Collections.sort(active);
    if (active.isEmpty()) return 1;
    int cur = room.current();
    int idx = active.indexOf(cur);
    return (idx < 0) ? active.get(0) : active.get((idx + 1) % active.size());
  }

  /** Create an immutable snapshot of the room's current visible state. */
  private Snapshot snap(GameRoom room) {
    return new Snapshot(
        room.started(),
        room.capacity(),
        room.players().size(),
        1,
        room.current(),
        room.sequence(),
        new HashMap<>(room.scores()),
        room.round());
  }

  /** Remove all players, clear reverse mappings, and drop the room from the registry. */
  private void cleanupRoom(String roomId, GameRoom room) {
    room.players().values().forEach(p -> sessionRoom.remove(p.sessionId()));
    room.removeAll();
    rooms.remove(roomId);
    log.info("Room {} removed.", roomId);
  }

  /**
   * Choose the lowest free player number in [1..capacity].
   *
   * @throws IllegalStateException if the room is already full
   */
  private int firstFreeNumber(GameRoom room) {
    for (int i = 1; i <= room.capacity(); i++) {
      if (!room.players().containsKey(i)) return i;
    }
    throw new IllegalStateException("Room full");
  }
}