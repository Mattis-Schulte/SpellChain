package com.spellchain.interfaces.ws;

import com.spellchain.application.GameService;
import com.spellchain.dto.AddCharacterRequest;
import com.spellchain.dto.ErrorMessage;
import com.spellchain.dto.JoinRoomRequest;
import com.spellchain.dto.RoomCreatedMessage;
import com.spellchain.dto.StartRoomRequest;
import jakarta.validation.Valid;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Validated
@Controller
public class GameWsController {
  private final GameService game;

  public GameWsController(GameService game) {
    this.game = game;
  }

  @MessageMapping("/createRoom")
  @SendToUser("/queue/reply")
  public RoomCreatedMessage create(@Header("simpSessionId") String sid) {
    return game.createRoom(sid);
  }

  @MessageMapping("/joinRoom")
  @SendToUser("/queue/reply")
  public RoomCreatedMessage join(@Valid JoinRoomRequest req, @Header("simpSessionId") String sid) {
    return game.joinRoom(sid, req.roomId());
  }

  @MessageMapping("/start")
  public void start(@Valid StartRoomRequest req, @Header("simpSessionId") String sid) {
    game.startGame(sid, req.roomId());
  }

  @MessageMapping("/addCharacter")
  public void add(@Valid AddCharacterRequest req, @Header("simpSessionId") String sid) {
    game.addCharacter(sid, req.roomId(), req.ch());
  }

  @MessageMapping("/exit")
  public void exit(@Header("simpSessionId") String sid) {
    game.removePlayerBySession(sid);
  }

  @MessageExceptionHandler(Exception.class)
  @SendToUser("/queue/reply")
  public ErrorMessage onError(Exception e) {
    return new ErrorMessage(e.getMessage());
  }

  @EventListener
  public void onDisconnect(SessionDisconnectEvent e) {
    game.removePlayerBySession(e.getSessionId());
  }
}