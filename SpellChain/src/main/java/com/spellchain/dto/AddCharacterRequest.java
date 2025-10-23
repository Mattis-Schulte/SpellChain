package com.spellchain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record AddCharacterRequest(@NotBlank String roomId, @NotEmpty String ch) {}