create table users (
    id              uuid primary key,
    email           varchar(255) not null unique,
    password_hash   varchar(100) not null,
    nickname        varchar(30)  not null,
    ranking_opt_in  boolean      not null default false,
    created_at      timestamp(6) with time zone not null
);

create table user_books (
    id              uuid primary key,
    user_id         uuid         not null references users (id),
    isbn            varchar(13),
    title           varchar(200) not null,
    author          varchar(100) not null,
    publisher       varchar(100),
    total_pages     integer      not null,
    genre           varchar(30)  not null,
    cover_url       varchar(500),
    status          varchar(20)  not null,
    current_page    integer      not null default 0,
    completed_count integer      not null default 0,
    completed_at    timestamp(6) with time zone,
    deleted         boolean      not null default false,
    created_at      timestamp(6) with time zone not null,
    updated_at      timestamp(6) with time zone not null
);
create index idx_user_books_user on user_books (user_id);

create table reading_sessions (
    id              uuid primary key,
    user_id         uuid         not null references users (id),
    book_id         uuid         not null references user_books (id),
    started_at      timestamp(6) with time zone not null,
    ended_at        timestamp(6) with time zone not null,
    active_seconds  integer      not null,
    start_page      integer      not null,
    end_page        integer,
    source          varchar(10)  not null,
    deleted         boolean      not null default false,
    created_at      timestamp(6) with time zone not null
);
create index idx_sessions_user_started on reading_sessions (user_id, started_at);

create table reviews (
    id              uuid primary key,
    user_id         uuid         not null references users (id),
    book_id         uuid         not null references user_books (id),
    one_line        varchar(100),
    body            varchar(5000),
    visibility      varchar(10)  not null,
    published_at    timestamp(6) with time zone,
    deleted         boolean      not null default false,
    created_at      timestamp(6) with time zone not null,
    updated_at      timestamp(6) with time zone not null
);
create index idx_reviews_public on reviews (visibility, published_at);

create table quotes (
    id              uuid primary key,
    user_id         uuid          not null references users (id),
    book_id         uuid          not null references user_books (id),
    text            varchar(1000) not null,
    page            integer,
    memo            varchar(500),
    deleted         boolean       not null default false,
    created_at      timestamp(6) with time zone not null,
    updated_at      timestamp(6) with time zone not null
);

create table review_likes (
    review_id       uuid not null references reviews (id),
    user_id         uuid not null references users (id),
    created_at      timestamp(6) with time zone not null,
    primary key (review_id, user_id)
);
