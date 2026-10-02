-- 실행 중에 바꾸는 앱 설정(예: 사용할 LLM). 비밀 값(API 키 등)은 여기에 저장하지 않는다.
CREATE TABLE app_setting (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(1000) NOT NULL,
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);
