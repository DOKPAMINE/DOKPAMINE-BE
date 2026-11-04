package com.dokpamine.domain.ranking.dto.response;

import java.time.LocalDate;
import java.util.List;

public record RankingResponse(
        LocalDate weekStart,
        boolean participating,
        List<RankingEntry> top,
        List<RankingEntry> around) {}
