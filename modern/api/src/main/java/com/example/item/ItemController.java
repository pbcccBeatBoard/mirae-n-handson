package com.example.item;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 문항 API. 컨트롤러는 서비스만 호출하고, 예외는 {@code GlobalExceptionHandler} 가 처리한다.
 */
@RestController
@RequestMapping("/api")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /** {@code GET /api/items/{id}} — 문항 단건. */
    @GetMapping("/items/{id}")
    public ItemResponse getItem(@PathVariable Integer id) {
        return itemService.getItem(id);
    }

    /** {@code GET /api/units/{code}/items} — 단원의 공개 문항 목록. */
    @GetMapping("/units/{code}/items")
    public List<ItemResponse> listItemsByUnit(@PathVariable String code) {
        return itemService.listActiveItemsByUnit(code);
    }

    /**
     * {@code GET /api/items/search} — 문항 검색(legacy {@code search.php} 이관).
     * 파라미터는 모두 문자열로 받는다 — {@code level=-1} 같은 값도 400 대신 legacy 와 같이 0건으로 응답해야 하기 때문이다.
     */
    @GetMapping("/items/search")
    public ItemSearchResult searchItems(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String unit,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            @RequestParam(required = false) String page) {
        return itemService.search(q, unit, level, tag, sort, dir, page);
    }
}
