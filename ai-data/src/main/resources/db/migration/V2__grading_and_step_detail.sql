-- 문제별 답변을 구분하고, 채점 결과/Step 상세를 JSONB로 보관한다.
ALTER TABLE learning_answer     ADD COLUMN question_id INTEGER;
ALTER TABLE learning_assessment ADD COLUMN result JSONB;
ALTER TABLE learning_step       ADD COLUMN detail JSONB;
