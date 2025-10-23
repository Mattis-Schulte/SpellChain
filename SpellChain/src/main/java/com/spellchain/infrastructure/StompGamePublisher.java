package com.spellchain.infrastructure;

import com.spellchain.application.port.GamePublisher;
import com.spellchain.domain.GameBroadcast;
import com.spellchain.domain.Snapshot;
import com.spellchain.dto.GameUpdateMessage;
import java.util.HashMap;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class StompGamePublisher implements GamePublisher {
  private final SimpMessagingTemplate ws;

  public StompGamePublisher(SimpMessagingTemplate ws) {
    this.ws = ws;
  }

  @Override
  public void publish(GameBroadcast b) {
    Snapshot s = b.snapshot();
    GameUpdateMessage m =
        GameUpdateMessage.base(s.started(), s.capacity(), s.joined(), s.host())
            .withSnapshot(
                s.current() == null ? 0 : s.current(),
                s.sequence() == null ? "" : s.sequence(),
                new HashMap<>(s.scores()),
                s.round());
    if (b.lastPlayer() != null || b.lastCh() != null) {
      m = m.withLastMove(b.lastPlayer() == null ? 0 : b.lastPlayer(), b.lastCh());
    }
    if (b.messages() != null && !b.messages().isEmpty()) {
      m = m.withMessages(b.messages());
    }
    ws.convertAndSend("/topic/rooms/" + b.roomId(), m);
  }
}