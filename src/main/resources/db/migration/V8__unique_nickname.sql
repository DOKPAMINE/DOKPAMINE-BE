-- FR-10: 피드와 랭킹에서 사용자를 구분할 수 있도록 닉네임을 고유하게 만든다.
-- 기존 중복은 가장 먼저 가입한 사용자만 원래 닉네임을 유지한다.
-- 나머지는 앞 6자에 '#'과 사용자 ID 앞 8자를 붙여 닉네임 최대 길이(15자) 안에서 고유하게 바꾼다.
update users u
set nickname = left(u.nickname, 6) || '#' || left(replace(cast(u.id as varchar), '-', ''), 8)
where exists (
    select 1
    from users o
    where o.nickname = u.nickname
      and (o.created_at < u.created_at or (o.created_at = u.created_at and o.id < u.id))
);

alter table users add constraint uk_users_nickname unique (nickname);
