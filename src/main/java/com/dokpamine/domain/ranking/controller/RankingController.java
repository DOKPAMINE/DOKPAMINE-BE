package com.dokpamine.domain.ranking.controller;

import com.dokpamine.domain.ranking.dto.response.RankingResponse;
import com.dokpamine.domain.ranking.service.RankingService;
import com.dokpamine.global.jwt.AuthenticatedUser;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/ranking")
public class RankingController {
    private final RankingService service;

    public RankingController(RankingService service) {
        this.service = service;
    }

    @GetMapping("/weekly")
    public RankingResponse weekly(
            Authentication auth, @RequestParam(required = false) LocalDate weekStart) {
        return service.weekly(AuthenticatedUser.id(auth), weekStart);
    }
}
