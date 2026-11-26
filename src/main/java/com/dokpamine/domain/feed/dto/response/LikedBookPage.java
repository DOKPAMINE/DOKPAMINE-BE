package com.dokpamine.domain.feed.dto.response;

import java.util.List;

/** 내가 좋아요한 책 묶음 한 페이지. 마지막 페이지면 nextCursor가 null이다. */
public record LikedBookPage(List<LikedBookGroup> items, String nextCursor) {}
