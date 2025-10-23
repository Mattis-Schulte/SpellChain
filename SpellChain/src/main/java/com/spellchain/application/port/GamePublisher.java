package com.spellchain.application.port;

import com.spellchain.domain.GameBroadcast;

public interface GamePublisher {
  void publish(GameBroadcast b);
}