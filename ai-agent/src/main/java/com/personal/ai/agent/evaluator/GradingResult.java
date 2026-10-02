package com.personal.ai.agent.evaluator;

import com.personal.ai.core.learning.Level;

import java.util.List;

/** correctness, areaScores, level은 LLM이 아니라 코드가 문제별 점수에서 계산한 값이다. */
public record GradingResult(String promptVersion, int correctness, Criteria criteria,
                            List<AreaScore> areaScores, Level level,
                            List<String> strengths, List<String> weaknesses,
                            List<QuestionResult> questionResults) {
}
