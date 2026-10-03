package com.personal.ai.api.stock;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 스케줄을 기다리지 않고 지금 한 번 분석해 디스코드로 보낸다. */
@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockRecommendationService service;

    public StockController(StockRecommendationService service) {
        this.service = service;
    }

    @PostMapping("/recommend")
    public List<Recommendation> recommend() {
        try {
            return service.run();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "외부 호출 실패: " + e.getMessage());
        }
    }
}
