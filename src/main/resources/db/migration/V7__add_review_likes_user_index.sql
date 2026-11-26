-- 기본 키가 (review_id, user_id) 순서라 내가 좋아요한 감상을 찾을 때 쓸 인덱스를 따로 둔다(FR-09).
create index idx_review_likes_user_created on review_likes (user_id, created_at);
