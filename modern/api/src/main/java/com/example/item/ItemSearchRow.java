package com.example.item;

import java.util.List;

/**
 * 문항 검색 결과 한 행. characterization 의 정규화 결과({@code {status, rows[], count, message}})가
 * 읽는 필드({@code id, title, unit, level, tags})만 담는다 — {@code stem} · {@code status} 는 넣지 않는다.
 */
public record ItemSearchRow(Integer id, String title, String unit, Integer level, List<String> tags) {

    static ItemSearchRow from(Item item) {
        return new ItemSearchRow(
            item.getId(),
            item.getTitle(),
            item.getUnit().getCode(),
            item.getLevel(),
            item.getTags().stream().map(Tag::getName).toList());
    }
}
