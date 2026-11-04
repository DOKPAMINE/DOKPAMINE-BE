package com.dokpamine.domain.feed.controller;

import com.dokpamine.domain.feed.dto.response.FeedItem;
import com.dokpamine.domain.feed.dto.response.LikeResponse;
import com.dokpamine.domain.feed.service.FeedService;
import com.dokpamine.global.jwt.AuthenticatedUser;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/feed")
public class FeedController {
    private final FeedService service;

    public FeedController(FeedService service) {
        this.service = service;
    }

    @GetMapping
    public List<FeedItem> list(Authentication auth) {
        return service.list(AuthenticatedUser.id(auth));
    }

    @GetMapping("/{id}")
    public FeedItem detail(Authentication auth, @PathVariable UUID id) {
        return service.detail(AuthenticatedUser.id(auth), id);
    }

    @PostMapping("/{id}/like")
    public LikeResponse like(Authentication auth, @PathVariable UUID id) {
        return service.like(AuthenticatedUser.id(auth), id);
    }

    @DeleteMapping("/{id}/like")
    public LikeResponse unlike(Authentication auth, @PathVariable UUID id) {
        return service.unlike(AuthenticatedUser.id(auth), id);
    }
}
