package com.example.item;

import java.util.List;

/**
 * {@code GET /api/items/search} 응답 본문. characterization 의 정규화 함수(JSON 경로)가
 * {@code items} 배열 · {@code count} · {@code message} 를 그대로 읽으므로 필드 이름을 맞춘다.
 */
public record ItemSearchResult(List<ItemSearchRow> items, long count, String message) {
}
