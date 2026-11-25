-- 기존 계정의 로그인 값과 사용자 ID를 그대로 유지한다.
alter table users rename column email to username;
