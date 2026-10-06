package com.example.item;

import org.springframework.data.jpa.domain.Specification;

/**
 * 문항 검색 조건 조합. {@code legacy/item-bank-php/search.php} 의 {@code buildSearchQuery()} WHERE 절을
 * 그대로 옮긴다 — 근거 줄번호는 각 메서드 주석 참고.
 */
final class ItemSpecifications {

    private ItemSpecifications() {
    }

    /** 공개 문항만. {@code v_item_public} 뷰와 같은 조건이다. 근거: search.php:521-522, 01-schema.sql:70 */
    static Specification<Item> active() {
        return (root, query, cb) -> cb.equal(root.get("status"), ItemStatus.ACTIVE);
    }

    /** 제목 또는 지문에 키워드가 부분 일치(LIKE). 근거: search.php:97-98 (BR-02) */
    static Specification<Item> keywordLike(String keyword) {
        String like = "%" + keyword + "%";
        return (root, query, cb) -> cb.or(
            cb.like(root.get("title"), like),
            cb.like(root.get("stem"), like));
    }

    /** 단원 코드 정확 일치(부분 일치 아님). 근거: search.php:118-120 (BR-06) */
    static Specification<Item> unitCodeEquals(String unitCode) {
        return (root, query, cb) -> cb.equal(root.get("unit").get("code"), unitCode);
    }

    /** 난이도 정확 일치. 근거: search.php:217-222 (BR-08), :223-230 (BR-09) */
    static Specification<Item> levelEquals(int level) {
        return (root, query, cb) -> cb.equal(root.get("level"), level);
    }

    /** 난이도 &lt; upperBoundExclusive. level 파라미터가 빈 값일 때 쓴다. 근거: search.php:213-216 (BR-07) */
    static Specification<Item> levelLessThan(int upperBoundExclusive) {
        return (root, query, cb) -> cb.lt(root.get("level"), upperBoundExclusive);
    }

    /** 연결된 태그 중 이름이 정확히 일치하는 것이 있어야 한다. 근거: search.php:244-248 (BR-11) */
    static Specification<Item> tagNameEquals(String tagName) {
        return (root, query, cb) -> cb.equal(root.join("tags").get("name"), tagName);
    }
}
