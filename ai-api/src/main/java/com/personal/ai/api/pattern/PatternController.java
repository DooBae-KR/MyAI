package com.personal.ai.api.pattern;

import com.personal.ai.api.pattern.PatternDtos.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/learning/patterns")
public class PatternController {

    private final PatternService patterns;

    public PatternController(PatternService patterns) {
        this.patterns = patterns;
    }

    /** 아직 분석하지 않은 답변(최대 30개)에서 패턴 가설을 찾는다. 새 답변이 없으면 아무것도 하지 않는다. */
    @PostMapping("/analyze")
    public AnalysisResponse analyze() {
        return patterns.analyze();
    }

    @GetMapping
    public List<PatternView> list() {
        return patterns.list();
    }

    /** 패턴 기각({"status":"DISMISSED"}) 또는 되살리기({"status":"HYPOTHESIS"}). */
    @PatchMapping("/{id}")
    public PatternView update(@PathVariable Long id, @RequestBody UpdateRequest request) {
        return patterns.update(id, request);
    }
}
