-- FR-08: 라운지 박스는 독후감을 처음 쓴 시각으로 정렬한다. 수정·공개 전환 시각과 구분하려고 따로 저장한다.
alter table reviews add column body_written_at timestamp(6) with time zone;

-- 기존 독후감은 쓴 시각을 알 수 없어 감상을 만든 시각으로 채운다.
update reviews set body_written_at = created_at where body is not null;

create index idx_reviews_body_written on reviews (visibility, body_written_at);
