package com.dokpamine.domain.feed.support;

import com.dokpamine.domain.feed.dto.response.FeedItem;
import com.dokpamine.domain.feed.repository.FeedLikeReadRepository;
import com.dokpamine.domain.feed.repository.ReviewLikeCount;
import com.dokpamine.domain.record.entity.Review;
import com.dokpamine.domain.record.entity.UserBook;
import com.dokpamine.domain.record.repository.UserBookRepository;
import com.dokpamine.domain.user.entity.User;
import com.dokpamine.domain.user.repository.UserRepository;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class FeedItemMapper {
    private final UserBookRepository books;
    private final UserRepository users;
    private final FeedLikeReadRepository likes;

    public FeedItemMapper(
            UserBookRepository books, UserRepository users, FeedLikeReadRepository likes) {
        this.books = books;
        this.users = users;
        this.likes = likes;
    }

    public List<FeedItem> toItems(List<Review> reviews, UUID me) {
        if (reviews.isEmpty()) return List.of();
        Map<UUID, UserBook> publicBooks =
                books
                        .findAllById(reviews.stream().map(Review::getBookId).distinct().toList())
                        .stream()
                        .filter(book -> !book.getDeleted())
                        .collect(Collectors.toMap(UserBook::getId, Function.identity()));
        Map<UUID, User> authors =
                users
                        .findAllById(reviews.stream().map(Review::getUserId).distinct().toList())
                        .stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        List<UUID> ids = reviews.stream().map(Review::getId).toList();
        Map<UUID, Long> counts =
                likes.countByReviewIds(ids).stream()
                        .collect(
                                Collectors.toMap(
                                        ReviewLikeCount::getReviewId,
                                        ReviewLikeCount::getLikeCount));
        Set<UUID> liked = new HashSet<>(likes.findLikedReviewIds(me, ids));
        List<FeedItem> result = new ArrayList<>();
        for (Review review : reviews) {
            UserBook book = publicBooks.get(review.getBookId());
            User author = authors.get(review.getUserId());
            if (book == null || author == null) continue;
            result.add(
                    new FeedItem(
                            review.getId(),
                            author.getNickname(),
                            book.getTitle(),
                            book.getAuthor(),
                            book.getGenre(),
                            review.getOneLine(),
                            review.getBody(),
                            review.getPublishedAt(),
                            counts.getOrDefault(review.getId(), 0L),
                            liked.contains(review.getId()),
                            review.getUserId().equals(me)));
        }
        return result;
    }
}
