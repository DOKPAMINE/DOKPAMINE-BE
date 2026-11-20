-- FR-01: 장르를 열두 개에서 아홉 개로 묶는다. 시/에세이는 시와 에세이를 구분할 수 없어 더 흔한 에세이로 옮긴다.
update user_books
set genre = case genre
        when '소설' then '소설/시'
        when 'SF' then '소설/시'
        when '시/에세이' then '에세이'
        when '인문' then '인문/사회'
        when '역사' then '인문/사회'
        when '사회/정치' then '인문/사회'
        when '과학' then '과학/IT'
        else genre
    end
where genre in ('소설', 'SF', '시/에세이', '인문', '역사', '사회/정치', '과학');
