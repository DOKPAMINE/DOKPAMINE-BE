package com.dokpamine.domain.feed.repository;

import java.util.*;

public interface BookTitleCandidate {
    UUID getId();

    String getTitle();

    String getAuthor();

    String getIsbn();
}
