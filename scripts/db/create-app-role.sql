-- 앱 실행용 최소 권한 DB 계정을 만든다. 스키마 변경(Flyway)은 소유자 계정으로, 앱은 이 계정으로 접속한다.
--
-- 소유자(마이그레이션) 계정으로 한 번 실행:
--   psql "<소유자 접속 문자열>" -v app_password='<새 비밀번호>' -f scripts/db/create-app-role.sql
-- 비밀번호는 파일에 쓰지 않고 위처럼 변수로 넘긴다(셸 기록에 남는 게 싫으면 psql 안에서 \set app_password ...).
--
-- 앱은 DELETE를 하지 않으므로 SELECT/INSERT/UPDATE만 준다. DDL(CREATE/ALTER/DROP)은 줄 수 없다.
-- ALTER DEFAULT PRIVILEGES는 "이 스크립트를 실행한 계정"이 앞으로 만드는 테이블에 적용되므로,
-- Flyway를 돌리는 소유자 계정으로 실행해야 이후 마이그레이션(V7…)의 새 테이블에도 권한이 자동으로 붙는다.

CREATE ROLE personal_ai_app LOGIN PASSWORD :'app_password';

CREATE SCHEMA IF NOT EXISTS personal_ai;
GRANT USAGE ON SCHEMA personal_ai TO personal_ai_app;

GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA personal_ai TO personal_ai_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA personal_ai TO personal_ai_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA personal_ai GRANT SELECT, INSERT, UPDATE ON TABLES TO personal_ai_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA personal_ai GRANT USAGE, SELECT ON SEQUENCES TO personal_ai_app;
