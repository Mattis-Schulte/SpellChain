package com.spellchain.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinRoomRequest(@NotBlank String roomId) {}