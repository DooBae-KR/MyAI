package com.personal.ai.agent.curriculum;

import com.personal.ai.agent.evaluator.AreaScore;
import com.personal.ai.core.learning.Level;

import java.util.List;

/** 커리큘럼 생성 입력. 진단 채점 결과를 그대로 요약해 넘긴다. */
public record CurriculumRequest(String subject, String goal, Level targetLevel, Level currentLevel,
                                List<String> prerequisites, List<AreaScore> areaScores,
                                List<String> strengths, List<String> weaknesses) {
}
