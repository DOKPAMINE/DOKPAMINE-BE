package com.dokpamine.domain.ranking.dto.response;

import java.util.UUID;

public record RankingEntry(int rank, UUID userId, String nickname, long seconds, boolean me) {}
