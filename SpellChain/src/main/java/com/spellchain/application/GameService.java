package com.spellchain.application;

import com.spellchain.application.port.Dictionary;
import com.spellchain.application.port.GamePublisher;
import com.spellchain.domain.GameRoom;
import com.spellchain.domain.GameBroadcast;
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

@Service
public class GameService {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final SecureRandom rnd = new SecureRandom();

  private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
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

    public RoomCreatedMessage joinRoom(String sid, String id) {
    id = norm(id);
    GameRoom room = getRoom(id);

    GameBroadcast toPublish = null;
    RoomCreatedMessage reply;

    room.lock().lock();
    try {
        if (room.started()) {
        throw new IllegalStateException("Game already started");
        }
        if (room.players().size() >= room.capacity()) {
        throw new IllegalStateException("Room full");
        }

        int num = room.players().size() + 1;
        room.addPlayer(num, new Player(num, sid));
        sessionRoom.put(sid, id);

        List<String> msgs = new ArrayList<>();
        msgs.add("Player " + num + " joined (" + room.players().size() + "/" + room.capacity() + ").");

        if (!room.started() && room.players().size() == room.capacity()) {
        startInternal(room);
        msgs.add("Auto-started at " + room.capacity() + " players.");
        }

        toPublish = GameBroadcast.of(id, snap(room)).withMessages(msgs);
        log.info("Session {} joined room {} as #{}", sid, id, num);

        reply = new RoomCreatedMessage(id, num, room.capacity(), room.started());
    } finally {
        room.lock().unlock();
    }

    if (toPublish != null) {
        publisher.publish(toPublish);
    }
    return reply;
    }

  public void startGame(String sid, String id) {
    id = norm(id);
    GameRoom room = getRoom(id);
    int num = playerNum(room, sid);
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
      if (joined > maxPlayers) {
        throw new IllegalStateException("Game auto-starts at " + maxPlayers);
      }
      if (!room.started()) {
        startInternal(room);
        toPublish =
            GameBroadcast.of(id, snap(room)).withMessages(List.of("Game started by host."));
      }
    } finally {
      room.lock().unlock();
    }
    if (toPublish != null) {
      publisher.publish(toPublish);
    }
  }

  public void addCharacter(String sid, String roomId, String ch) {
    if (ch == null) {
      throw new IllegalArgumentException("Invalid character");
    }
    ch =
        ch.codePoints()
            .limit(1)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    if (ch.isEmpty() || ch.codePointCount(0, ch.length()) != 1) {
      throw new IllegalArgumentException("Enter exactly one character");
    }

    GameRoom room = getRoom(roomId);
    int num = playerNum(room, sid);

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

      int cp = ch.codePointAt(0);
      int lower = Character.toLowerCase(cp);
      char c = (char) lower;

      boolean isLetter = Character.isLetter(lower);
      boolean isAllowedPunct = allowedPunctuation.indexOf(c) >= 0;
      if (!(isLetter || isAllowedPunct)) {
        throw new IllegalArgumentException(
            "Invalid character: enter a single letter or one of " + allowedPunctuation);
      }

      String cand = room.sequence() + c;
      List<String> messages = new ArrayList<>();

      if (dict.isWord(cand)) {
        boolean used = room.words().values().stream().anyMatch(s -> s.contains(cand));
        if (!used) {
          int pts = Math.max((cand.length() + 1) / 2, 1);
          room.scores().merge(num, pts, Integer::sum);
          room
              .words()
              .computeIfAbsent(num, k -> Collections.synchronizedSet(new java.util.HashSet<>()))
              .add(cand);
          String def = dict.definition(cand);
          messages.add(
              String.format(
                  "*** Player %d completed \"%s\"! (%d Point%s) *** Definition: %s",
                  num,
                  cand,
                  pts,
                  pts != 1 ? "s" : "",
                  def));
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
              .withLastMove(num, String.valueOf(c))
              .withMessages(messages);
    } finally {
      room.lock().unlock();
    }
    publisher.publish(toPublish);
    log.info("Room {} p#{} '{}' processed", roomId, num, ch);
  }

  public void removePlayerBySession(String sid) {
    String roomId = sessionRoom.remove(sid);
    if (roomId == null) {
      return;
    }
    GameRoom room = rooms.get(roomId);
    if (room == null) {
      return;
    }

    GameBroadcast b;
    room.lock().lock();
    try {
      int num = playerNumOrZero(room, sid);
      if (!room.started()) {
        if (num == 1) {
          b = GameBroadcast.of(roomId, snap(room)).withMessages(List.of("Host left. Room closed."));
          cleanupRoom(roomId, room);
        } else {
          if (num > 0) {
            room.removePlayer(num);
          }
          b = GameBroadcast.of(roomId, snap(room)).withMessages(List.of("Player " + num + " left."));
          if (room.players().isEmpty()) {
            cleanupRoom(roomId, room);
          }
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

  private String norm(String id) {
    return id == null ? null : id.trim().toUpperCase(Locale.ROOT);
  }

  private GameRoom getRoom(String id) {
    GameRoom r = rooms.get(norm(id));
    if (r == null) {
      throw new NoSuchElementException("Room not found");
    }
    return r;
  }

  private int playerNum(GameRoom room, String sid) {
    return room.players().entrySet().stream()
        .filter(e -> sid.equals(e.getValue().sessionId()))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Player not in room"));
  }

  private int playerNumOrZero(GameRoom room, String sid) {
    return room.players().entrySet().stream()
        .filter(e -> sid.equals(e.getValue().sessionId()))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(0);
  }

  private String newRoomId() {
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    while (true) {
      StringBuilder sb = new StringBuilder(6);
      for (int j = 0; j < 6; j++) {
        sb.append(chars.charAt(rnd.nextInt(chars.length())));
      }
      String id = sb.toString();
      if (!rooms.containsKey(id)) {
        return id;
      }
    }
  }

  private void startInternal(GameRoom room) {
    room.started(true);
    int first = room.players().keySet().stream().min(Integer::compareTo).orElse(1);
    room.current(first);
  }

  private int nextActive(GameRoom room) {
    List<Integer> active = new ArrayList<>(room.players().keySet());
    Collections.sort(active);
    if (active.isEmpty()) {
      return 1;
    }
    int cur = room.current();
    int idx = active.indexOf(cur);
    return (idx < 0) ? active.get(0) : active.get((idx + 1) % active.size());
  }

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

  private void cleanupRoom(String roomId, GameRoom room) {
    room.players().values().forEach(p -> sessionRoom.remove(p.sessionId()));
    room.removeAll();
    rooms.remove(roomId);
    log.info("Room {} removed.", roomId);
  }
}