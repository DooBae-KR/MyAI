package com.personal.ai.core.learning;

/** OBSERVED: 패턴이 관찰된 근거(신뢰도에 반영). IMPROVED: 개선 훈련 이후 패턴과 반대로(훈련 제안대로) 쓴 개선 신호(신뢰도에는 반영하지 않음). */
public enum EvidenceKind { OBSERVED, IMPROVED }
