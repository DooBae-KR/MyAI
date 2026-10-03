package com.personal.ai.api.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService service;
    private final InsightsService insights;

    public StatsController(StatsService service, InsightsService insights) {
        this.service = service;
        this.insights = insights;
    }

    @GetMapping
    public StatsResponse stats() {
        return service.stats();
    }

    /** 설계서 21장의 나머지 통계(평균 점수, 알고리즘별 일치율, 언어·분야별 학습량, 영역 변화, 패턴 변화). */
    @GetMapping("/insights")
    public InsightsResponse insights() {
        return insights.insights();
    }
}
