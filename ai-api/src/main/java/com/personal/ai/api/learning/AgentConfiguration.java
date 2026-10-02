package com.personal.ai.api.learning;

import com.personal.ai.agent.curriculum.CurriculumAgent;
import com.personal.ai.agent.evaluator.EvaluatorAgent;
import com.personal.ai.agent.pattern.PatternAnalyzerAgent;
import com.personal.ai.core.model.AiModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfiguration {

    @Bean
    public EvaluatorAgent evaluatorAgent(AiModel aiModel) {
        return new EvaluatorAgent(aiModel);
    }

    @Bean
    public CurriculumAgent curriculumAgent(AiModel aiModel) {
        return new CurriculumAgent(aiModel);
    }

    @Bean
    public PatternAnalyzerAgent patternAnalyzerAgent(AiModel aiModel) {
        return new PatternAnalyzerAgent(aiModel);
    }
}
