package com.spellchain.dto;

import jakarta.validation.constraints.NotBlank;

public record StartRoomRequest(@NotBlank String roomId) {}