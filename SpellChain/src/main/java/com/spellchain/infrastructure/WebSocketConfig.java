package com.spellchain.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Value("${spellchain.allowed-origins:*}")
  private String allowedOrigins;

  @Override
  public void configureMessageBroker(MessageBrokerRegistry r) {
    r.enableSimpleBroker("/topic", "/queue");
    r.setApplicationDestinationPrefixes("/app");
    r.setUserDestinationPrefix("/user");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry r) {
    String[] origins = allowedOrigins.split("\\s*,\\s*");
    r.addEndpoint("/ws")
      .setAllowedOriginPatterns(origins)
      .withSockJS();
  }
}