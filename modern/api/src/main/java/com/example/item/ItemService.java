package com.example.item;

import com.example.common.NotFoundException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문항 조회 서비스. 엔티티는 트랜잭션 안에서 DTO로 바꿔 돌려준다(OSIV 꺼져 있음).
 */
@Service
@Transactional(readOnly = true)
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);

    // 문항 검색(search) 상수 · 정규식 — legacy/item-bank-php/search.php 의 buildSearchQuery() 를 그대로 옮긴다.
    private static final int SEARCH_PAGE_SIZE = 20; // BR-26, search.php:349,523
    private static final int SEARCH_MAX_PAGE = 999; // BR-27, search.php:345-348
    private static final int SEARCH_MAX_KEYWORD_LENGTH = 100; // BR-01, search.php:86
    private static final int SEARCH_MAX_TAG_LENGTH = 50; // BR-10, search.php:240
    private static final int SEARCH_LEVEL_EXCLUSIVE_UPPER_BOUND = 5; // BR-07, search.php:213-216 (주석·코드 불일치, 그대로 이관)
    private static final Pattern SEARCH_LEVEL_FORMAT = Pattern.compile("^[1-5]$"); // BR-08, search.php:217
    private static final Pattern SEARCH_PAGE_FORMAT = Pattern.compile("^[0-9]+$"); // BR-28, search.php:337
    // PHP 의 (int) 문자열 캐스팅 흉내(BR-09, search.php:223-230): 앞쪽 부호·숫자만 읽고, 없으면 0.
    private static final Pattern SEARCH_LOOSE_INT = Pattern.compile("^\\s*[+-]?\\d+");
    private static final String SEARCH_NO_RESULT_MESSAGE = "검색 결과가 없습니다"; // search.php:657-659

    private final ItemRepository itemRepository;
    private final UnitRepository unitRepository;

    public ItemService(ItemRepository itemRepository, UnitRepository unitRepository) {
        this.itemRepository = itemRepository;
        this.unitRepository = unitRepository;
    }

    /** 문항 단건. 상태와 무관하게 id로 찾는다(관리 화면용). */
    public ItemResponse getItem(Integer id) {
        Item item = itemRepository.findWithDetailsById(id)
            .orElseThrow(() -> new NotFoundException("문항이 없습니다: id=" + id));
        return ItemResponse.from(item);
    }

    /** 단원의 공개({@code status='A'}) 문항 목록. 단원 코드가 없으면 404. */
    public List<ItemResponse> listActiveItemsByUnit(String unitCode) {
        Unit unit = unitRepository.findByCode(unitCode)
            .orElseThrow(() -> new NotFoundException("단원이 없습니다: code=" + unitCode));
        List<ItemResponse> items = itemRepository.findByUnitCodeAndStatus(unit.getCode(), ItemStatus.ACTIVE).stream()
            .map(ItemResponse::from)
            .toList();
        log.debug("unit {} has {} active items", unit.getCode(), items.size());
        return items;
    }

    /**
     * 문항 검색. {@code legacy/item-bank-php/search.php} 의 {@code buildSearchQuery()} · {@code runSearchQuery()}
     * (search.php:42-624) 를 그대로 옮긴 것이다. 존재하지 않는 단원 · 범위 밖 난이도도 오류가 아니라 0건으로 응답한다.
     *
     * @param q     키워드 (제목 · 지문)
     * @param unit  단원 코드
     * @param level 난이도 (1~5, 빈 값 허용)
     * @param tag   태그 이름
     * @param sort  정렬 기준 (id | title | unit | level | created)
     * @param dir   asc | desc
     * @param page  페이지 번호 (1부터)
     */
    public ItemSearchResult search(String q, String unit, String level, String tag, String sort, String dir,
            String page) {
        Specification<Item> spec = Specification.where(ItemSpecifications.active());

        String keyword = trimAndTruncate(q, SEARCH_MAX_KEYWORD_LENGTH);
        if (!keyword.isEmpty()) {
            spec = spec.and(ItemSpecifications.keywordLike(keyword)); // BR-01, BR-02
        }

        String unitCode = unit == null ? "" : unit.trim();
        if (!unitCode.isEmpty()) {
            // 형식이 다르거나 등록되지 않은 코드여도 검증하지 않고 그대로 조회한다 — 결과만 0건이 된다. (BR-04, BR-05)
            spec = spec.and(ItemSpecifications.unitCodeEquals(unitCode)); // BR-06
        }

        String levelRaw = level == null ? "" : level.trim();
        if (levelRaw.isEmpty()) {
            spec = spec.and(ItemSpecifications.levelLessThan(SEARCH_LEVEL_EXCLUSIVE_UPPER_BOUND)); // BR-07
        } else if (SEARCH_LEVEL_FORMAT.matcher(levelRaw).matches()) {
            spec = spec.and(ItemSpecifications.levelEquals(Integer.parseInt(levelRaw))); // BR-08
        } else {
            // 1~5 형식이 아니면 정수로 캐스팅한 값을 그대로 비교한다(대개 0건). (BR-09)
            spec = spec.and(ItemSpecifications.levelEquals(loosePhpIntCast(levelRaw)));
        }

        String tagName = trimAndTruncate(tag, SEARCH_MAX_TAG_LENGTH);
        if (!tagName.isEmpty()) {
            // 등록되지 않은 태그여도 그대로 조회한다 — 결과만 0건이 된다. (BR-12)
            spec = spec.and(ItemSpecifications.tagNameEquals(tagName)); // BR-11
        }

        Sort sortOrder = resolveSort(sort, dir); // BR-22, BR-23, BR-24, BR-25
        int pageNumber = resolvePage(page); // BR-27, BR-28
        Pageable pageable = PageRequest.of(pageNumber - 1, SEARCH_PAGE_SIZE, sortOrder); // BR-26

        Page<Item> result = itemRepository.findAll(spec, pageable);
        List<ItemSearchRow> rows = result.getContent().stream().map(ItemSearchRow::from).toList();
        long count = result.getTotalElements();
        String message = count == 0 ? SEARCH_NO_RESULT_MESSAGE : null;

        log.debug("item search: {} rows of {} (page {})", rows.size(), count, pageNumber);
        return new ItemSearchResult(rows, count, message);
    }

    private static String trimAndTruncate(String value, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private static int loosePhpIntCast(String value) {
        Matcher matcher = SEARCH_LOOSE_INT.matcher(value);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 정렬 기준 · 방향 → JPA {@link Sort}. search.php:266-331 의 switch 문을 그대로 옮긴다. */
    private static Sort resolveSort(String sort, String dir) {
        String sortKey = sort == null ? "" : sort.trim().toLowerCase();
        String direction = dir == null ? "" : dir.trim().toLowerCase();
        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            direction = ""; // BR-24
        }
        boolean desc = "desc".equals(direction);
        boolean ascExplicit = "asc".equals(direction);

        return switch (sortKey) {
            case "id" -> Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, "id");
            case "title" -> desc
                ? Sort.by(Sort.Order.desc("title"), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id"));
            case "unit" -> desc
                ? Sort.by(Sort.Order.desc("unit.code"), Sort.Order.desc("level"), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.asc("unit.code"), Sort.Order.desc("level"), Sort.Order.asc("id"));
            case "level" -> ascExplicit
                ? Sort.by(Sort.Order.asc("level"), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id"));
            case "created" -> ascExplicit
                ? Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
            default -> Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id")); // BR-22, BR-23(빈 값 · 알 수 없는 값)
        };
    }

    /** 페이지 번호 파싱 · 보정. search.php:336-349 를 그대로 옮긴다. */
    private static int resolvePage(String pageRaw) {
        String raw = pageRaw == null ? "1" : pageRaw;
        int pageNumber = 1;
        if (SEARCH_PAGE_FORMAT.matcher(raw).matches()) {
            try {
                pageNumber = Integer.parseInt(raw); // BR-28
            } catch (NumberFormatException e) {
                pageNumber = SEARCH_MAX_PAGE + 1; // 자릿수가 너무 많아 오버플로면 상한 보정에 맡긴다
            }
        }
        if (pageNumber < 1) {
            pageNumber = 1;
        }
        if (pageNumber > SEARCH_MAX_PAGE) {
            pageNumber = SEARCH_MAX_PAGE; // BR-27
        }
        return pageNumber;
    }
}
