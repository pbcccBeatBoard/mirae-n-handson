package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * ItemService.search() — legacy search.php(BR-01~BR-12, BR-22~BR-29) 규칙을 실제 SQL(H2, MariaDB 모드)로 검증한다.
 * ItemService 는 리포지토리 빈만 필요하므로 @DataJpaTest 트랜잭션 안에서 직접 생성해 쓴다.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ItemSearchServiceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UnitRepository unitRepository;

    private ItemService itemService;

    @BeforeEach
    void seed() {
        itemService = new ItemService(itemRepository, unitRepository);

        Unit m51 = entityManager.persist(ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5));
        Unit m52 = entityManager.persist(ItemFixtures.unit(2, "M5-2", "분수의 곱셈", 5));
        Tag calc = entityManager.persist(ItemFixtures.tag(1, "계산"));
        Tag word = entityManager.persist(ItemFixtures.tag(2, "문장제"));
        Tag longTag = entityManager.persist(ItemFixtures.tag(3, "가".repeat(50)));

        entityManager.persist(ItemFixtures.item(null, m51, "분모가 같은 분수의 덧셈", 1, ItemStatus.ACTIVE, calc));
        entityManager.persist(ItemFixtures.item(null, m51, "분수 문장제", 4, ItemStatus.ACTIVE, calc, word));
        entityManager.persist(ItemFixtures.item(null, m51, "검수 중 문항", 3, ItemStatus.REVIEWING));
        entityManager.persist(ItemFixtures.item(null, m51, "삭제된 문항", 5, ItemStatus.DELETED));
        entityManager.persist(ItemFixtures.item(null, m51, "난이도 5 분수", 5, ItemStatus.ACTIVE, word));
        entityManager.persist(ItemFixtures.item(null, m52, "소수 곱셈", 1, ItemStatus.ACTIVE));
        entityManager.persist(ItemFixtures.item(null, m52, "긴 태그 문항", 2, ItemStatus.ACTIVE, longTag));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("level 빈 값: 공개 문항만, 난이도 5는 제외된다 — 주석·코드 불일치 그대로(BR-07)")
    void blankLevelExcludesLevelFiveAndNonActive() {
        ItemSearchResult result = itemService.search(null, null, "", null, null, null, null);

        assertThat(result.count()).isEqualTo(4);
        assertThat(result.message()).isNull();
        assertThat(result.items()).extracting(ItemSearchRow::title)
            .containsExactly("분수 문장제", "긴 태그 문항", "분모가 같은 분수의 덧셈", "소수 곱셈");
    }

    @Test
    @DisplayName("level=5 명시: 빈 값과 달리 난이도 5 문항이 조회된다(BR-08)")
    void explicitLevelFiveIsIncluded() {
        ItemSearchResult result = itemService.search(null, null, "5", null, null, null, null);

        assertThat(result.count()).isEqualTo(1);
        assertThat(result.items()).extracting(ItemSearchRow::title).containsExactly("난이도 5 분수");
    }

    @Test
    @DisplayName("level 범위 밖 값은 정수로 캐스팅해 그대로 비교한다 — 대개 0건(BR-09)")
    void outOfRangeLevelCastsToInt() {
        ItemSearchResult result = itemService.search(null, null, "-1", null, null, null, null);

        assertThat(result.count()).isZero();
        assertThat(result.message()).isEqualTo("검색 결과가 없습니다");
    }

    @Test
    @DisplayName("단원 코드로 필터하면 정확히 일치하는 단원의 문항만 남는다(BR-06)")
    void unitCodeFiltersExactly() {
        ItemSearchResult result = itemService.search(null, "M5-2", null, null, null, null, null);

        assertThat(result.count()).isEqualTo(2);
        assertThat(result.items()).extracting(ItemSearchRow::unit).containsOnly("M5-2");
    }

    @Test
    @DisplayName("등록되지 않은 단원 코드는 오류가 아니라 0건이다(BR-04, BR-05)")
    void unknownUnitCodeReturnsEmptyNotError() {
        ItemSearchResult result = itemService.search(null, "Z9-9", null, null, null, null, null);

        assertThat(result.count()).isZero();
        assertThat(result.items()).isEmpty();
        assertThat(result.message()).isEqualTo("검색 결과가 없습니다");
    }

    @Test
    @DisplayName("태그 이름이 정확히 일치하는 문항만 조회한다(BR-11)")
    void tagNameFiltersExactly() {
        ItemSearchResult result = itemService.search(null, null, "5", "문장제", null, null, null);

        assertThat(result.items()).extracting(ItemSearchRow::title).containsExactly("난이도 5 분수");
    }

    @Test
    @DisplayName("태그로 필터한 뒤에도 행의 tags 는 필터 대상 외 태그를 포함한 전체 태그다(BR-11)")
    void tagFilterKeepsAllTagsOnRow() {
        ItemSearchResult result = itemService.search(null, null, null, "계산", null, null, null);

        assertThat(result.items()).extracting(ItemSearchRow::title)
            .containsExactly("분수 문장제", "분모가 같은 분수의 덧셈");
        ItemSearchRow multiTag = result.items().get(0);
        assertThat(multiTag.tags()).containsExactlyInAnyOrder("계산", "문장제");
        assertThat(result.items().get(1).tags()).containsExactly("계산");
    }

    @Test
    @DisplayName("level 이 소수(4.9)면 정수부 4로 비교한다(BR-09)")
    void decimalLevelCastsToIntegerPart() {
        ItemSearchResult result = itemService.search(null, null, "4.9", null, null, null, null);

        assertThat(result.items()).extracting(ItemSearchRow::title).containsExactly("분수 문장제");
    }

    @Test
    @DisplayName("level 이 '4abc' 면 앞쪽 숫자 4로 비교한다(BR-09)")
    void levelWithTrailingGarbageUsesLeadingDigits() {
        ItemSearchResult result = itemService.search(null, null, "4abc", null, null, null, null);

        assertThat(result.items()).extracting(ItemSearchRow::title).containsExactly("분수 문장제");
    }

    @Test
    @DisplayName("level 이 지수 표기 '1e3' 이면 PHP 7.4 는 1000으로 캐스팅하므로 0건이다 — 불일치 의심(BR-09)")
    void exponentLevelCastsLikePhp74() {
        ItemSearchResult result = itemService.search(null, null, "1e3", null, null, null, null);

        assertThat(result.count()).isZero();
        assertThat(result.items()).isEmpty();
    }

    @Test
    @DisplayName("level 이 int 범위를 넘는 아주 큰 수면 0건이고 오류가 아니다(BR-09)")
    void hugeLevelReturnsEmpty() {
        ItemSearchResult overInt = itemService.search(null, null, "99999999999", null, null, null, null);
        ItemSearchResult overLong = itemService.search(null, null, "99999999999999999999999", null, null, null, null);

        assertThat(overInt.count()).isZero();
        assertThat(overLong.count()).isZero();
        assertThat(overLong.message()).isEqualTo("검색 결과가 없습니다");
    }

    @Test
    @DisplayName("level 이 숫자가 아니면 0으로 캐스팅되어 0건이다(BR-09)")
    void nonNumericLevelCastsToZero() {
        ItemSearchResult result = itemService.search(null, null, "abc", null, null, null, null);

        assertThat(result.count()).isZero();
    }

    private List<String> titles(String sort, String dir) {
        return itemService.search(null, null, "", null, sort, dir, null).items().stream()
            .map(ItemSearchRow::title).toList();
    }

    @Test
    @DisplayName("sort=unit: 방향 생략·asc 는 단원 오름차순, desc 는 내림차순이고 난이도 내림차순·id 오름차순이 뒤따른다(BR-22)")
    void sortByUnit() {
        assertThat(titles("unit", null))
            .containsExactly("분수 문장제", "분모가 같은 분수의 덧셈", "긴 태그 문항", "소수 곱셈");
        assertThat(titles("unit", "asc")).isEqualTo(titles("unit", null));
        assertThat(titles("unit", "desc"))
            .containsExactly("긴 태그 문항", "소수 곱셈", "분수 문장제", "분모가 같은 분수의 덧셈");
    }

    @Test
    @DisplayName("sort=title: 제목 오름차순이 기본, desc 면 내림차순")
    void sortByTitle() {
        assertThat(titles("title", null))
            .containsExactly("긴 태그 문항", "분모가 같은 분수의 덧셈", "분수 문장제", "소수 곱셈");
        assertThat(titles("title", "desc"))
            .containsExactly("소수 곱셈", "분수 문장제", "분모가 같은 분수의 덧셈", "긴 태그 문항");
    }

    @Test
    @DisplayName("sort=created: 생성 시각이 같으면 방향과 무관하게 id 오름차순")
    void sortByCreated() {
        List<String> byId = titles("id", "asc");
        assertThat(titles("created", null)).isEqualTo(byId);
        assertThat(titles("created", "asc")).isEqualTo(byId);
        assertThat(titles("created", "desc")).isEqualTo(byId);
    }

    @Test
    @DisplayName("sort=level: 기본은 내림차순, asc 만 오름차순이며 잘못된 dir 은 무시된다")
    void sortByLevel() {
        assertThat(titles("level", null))
            .containsExactly("분수 문장제", "긴 태그 문항", "분모가 같은 분수의 덧셈", "소수 곱셈");
        assertThat(titles("level", "asc"))
            .containsExactly("분모가 같은 분수의 덧셈", "소수 곱셈", "긴 태그 문항", "분수 문장제");
        assertThat(titles("level", "sideways")).isEqualTo(titles("level", null));
    }

    @Test
    @DisplayName("sort=id: 기본 오름차순, desc 면 내림차순, 잘못된 dir 은 오름차순(BR-24)")
    void sortById() {
        assertThat(titles("id", "desc"))
            .containsExactly("긴 태그 문항", "소수 곱셈", "분수 문장제", "분모가 같은 분수의 덧셈");
        assertThat(titles("id", "bogus")).isEqualTo(titles("id", "asc"));
    }

    @Test
    @DisplayName("sort · dir 은 앞뒤 공백을 자르고 대소문자를 구분하지 않는다")
    void sortAndDirAreTrimmedAndCaseInsensitive() {
        assertThat(titles("  TiTle ", " DESC ")).isEqualTo(titles("title", "desc"));
        assertThat(titles(" UNIT", "Desc")).isEqualTo(titles("unit", "desc"));
        assertThat(titles(" Level ", " ASC")).isEqualTo(titles("level", "asc"));
    }

    @Test
    @DisplayName("알 수 없는 sort 는 dir 과 무관하게 기본 정렬(난이도 내림차순, id 오름차순)이다(BR-23)")
    void unknownSortIgnoresDir() {
        List<String> defaultOrder = titles(null, null);
        assertThat(titles("nope", "asc")).isEqualTo(defaultOrder);
        assertThat(titles("title;drop table item", "desc")).isEqualTo(defaultOrder);
        assertThat(titles("", "desc")).isEqualTo(defaultOrder);
    }

    @Test
    @DisplayName("태그 이름이 50자를 넘으면 앞 50자까지만 비교한다(BR-10)")
    void tagNameLongerThan50CharsIsTruncated() {
        String overLong = "가".repeat(50) + "나머지는 잘려야 한다";

        ItemSearchResult result = itemService.search(null, null, null, overLong, null, null, null);

        assertThat(result.items()).extracting(ItemSearchRow::title).containsExactly("긴 태그 문항");
    }

    @Test
    @DisplayName("키워드는 제목 또는 지문에 부분 일치한다(BR-02)")
    void keywordMatchesTitleOrStem() {
        ItemSearchResult result = itemService.search("문장제", null, null, null, null, null, null);

        assertThat(result.items()).extracting(ItemSearchRow::title).containsExactly("분수 문장제");
    }

    @Test
    @DisplayName("페이지가 999를 넘으면 999로 보정된다 — 결과가 없어도 count·message 는 그대로다(BR-27)")
    void pageBeyondMaxIsClampedButCountUnaffected() {
        ItemSearchResult result = itemService.search(null, null, "", null, null, null, "1000");

        assertThat(result.count()).isEqualTo(4);
        assertThat(result.items()).isEmpty();
        assertThat(result.message()).isNull();
    }

    @Test
    @DisplayName("페이지 값이 숫자가 아니면 1페이지로 취급한다(BR-28)")
    void nonNumericPageFallsBackToPageOne() {
        ItemSearchResult withInvalidPage = itemService.search(null, null, "", null, null, null, "abc");
        ItemSearchResult withPageOne = itemService.search(null, null, "", null, null, null, "1");

        assertThat(withInvalidPage.items()).extracting(ItemSearchRow::title)
            .containsExactlyElementsOf(withPageOne.items().stream().map(ItemSearchRow::title).toList());
    }

    @Test
    @DisplayName("정렬 기준을 모르면 기본 정렬(난이도 내림차순, id 오름차순)로 대체한다(BR-22, BR-23)")
    void unknownSortFallsBackToDefaultOrder() {
        ItemSearchResult result = itemService.search(null, null, "", null, "no-such-sort", null, null);

        assertThat(result.items()).extracting(ItemSearchRow::title)
            .containsExactly("분수 문장제", "긴 태그 문항", "분모가 같은 분수의 덧셈", "소수 곱셈");
    }
}
