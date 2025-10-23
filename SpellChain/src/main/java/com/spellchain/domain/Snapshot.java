package com.spellchain.domain;

import java.util.Map;

public record Snapshot(
    boolean started,
    int capacity,
    int joined,
    int host,
    Integer current,
    String sequence,
    Map<Integer, Integer> scores,
    int round
) {}
