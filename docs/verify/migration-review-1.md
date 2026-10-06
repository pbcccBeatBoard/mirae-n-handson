# 검증 보고서 — 레거시 엔드포인트 1개 이관 (item-bank search.php → modern)

검증 대상: `upstream/main...HEAD` (커밋 `0a9cac6` 포함, 17개 파일, +2552 / -1)
요청 범위: **레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.**
검증 방식: 리뷰 세션. 코드는 수정하지 않았다. 판정 기준은 `templates/approval-checklist.md` 의 네 기준.

## 판정: 반려

이관 본체(`search.php` → `GET /api/items/search`)는 테스트 기준으로 동작이 맞다. 다만 두 가지가 머지를 막는다.

1. **요청 범위 이탈.** 같은 커밋에 이관과 무관한 문서·skill·CLAUDE.md 가 섞여 있다.
2. **동작 차이.** 레거시가 보여 주는 입력 경고 문구를 새 API 가 주지 않는다. 요청의 "동작을 바꾸지 않고"에 어긋나고, 스냅샷 테스트도 이를 잡지 못한다.

또 CLAUDE.md 의 "연관 엔티티는 `@EntityGraph`/fetch 로 한 번에" 규칙을 어기는 N+1 구조가 있다(컨벤션 미충족).

## 네 기준 판정

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | **충족** | `modern/api` `./gradlew test` 39건 통과 · 실패 0 · skip 0. `characterization` 레거시 대상 `npm test` 28건 통과. 새 API 대상 `TARGET_BASE_URL=http://localhost:8080 npm test` 27건 통과 · 실패 0 · skip 1(`example-units.test.js` 의 `legacyOnly` 케이스, 이관 전부터 설계된 skip). item-bank 14케이스는 양쪽 모두 14/14. 테스트 충분성은 이슈 #4 로 따로 뺀다. |
| 치명 이슈 0건 | **충족** | 치명 이슈 없음. SQL 은 Specification 으로 바인딩되고(문자열 연결 없음), 비밀값 리터럴 없음, 데이터 삭제 · 덮어쓰기 코드 없음. |
| 요청 범위 이탈 없음 | **미충족** | 이관과 무관한 파일이 섞여 있다(이슈 #3). 또 "동작을 바꾸지 않고"에 어긋나는 차이가 있다(이슈 #1). |
| 컨벤션 준수 | **미충족** | CLAUDE.md "연관 엔티티가 필요한 조회는 `@EntityGraph` 나 fetch/집계 JPQL로 한 번에" 위반(이슈 #2). `convention-check` 결과는 위반 0건, 확인 필요 2건(아래 확인 필요 참고). |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/ItemService.java:76-118`, 레거시 `legacy/item-bank-php/search.php:87-347, 510-512` | 레거시는 입력 경고 15여 종을 `<ul id="warnings">` 에 그린다. 예: 미등록 단원 `등록되지 않은 단원 코드입니다: Z9-9`, 정렬 `알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.`, 난이도 `난이도는 1~5 사이여야 합니다.`. 실측: 레거시 `?unit=Z9-9` 는 위 문구를 내고, 새 API `/api/items/search?unit=Z9-9` 는 `{"items":[],"count":0,"message":"검색 결과가 없습니다"}` 만 준다. `?sort=bogus` 도 새 API 는 경고 없이 기본 정렬 결과를 준다. 요청 "동작을 바꾸지 않고"에 어긋난다. | `ItemSearchResult` 에 `warnings` 목록을 추가하고, 서비스에서 레거시 문구를 같은 조건으로 만든다. `characterization/lib/normalize.mjs:175` 는 `#message` 만 읽으므로 `#warnings` 도 정규화하려면 스냅샷이 바뀐다. CLAUDE.md 규칙상 스냅샷 변경은 먼저 사람에게 묻는다. |
| 2 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchRow.java:15,17`; 엔티티 `modern/api/src/main/java/com/example/item/Item.java:30,54` | `Item.unit` 과 `Item.tags` 는 `FetchType.LAZY` 다. `ItemSearchRow.from` 이 행마다 `getUnit()` · `getTags()` 를 호출하고, `search` 는 `findAll(spec, pageable)` 로 `@EntityGraph`/fetch 없이 가져온다. 한 페이지 최대 20행이라 행마다 추가 쿼리가 나간다. CLAUDE.md "연관 엔티티가 필요한 조회는 `@EntityGraph` 나 fetch/집계 JPQL로 한 번에" 규칙 위반이다. **실측하지 않았다**(엔티티 매핑 기준 추정). | 리포지토리에 `findAll(Specification, Pageable)` 을 `@EntityGraph(attributePaths = {"unit"})` 로 덮어쓴다. `tags` 는 컬렉션 fetch 가 `Pageable` 과 충돌하므로(메모리 페이징 경고) `@BatchSize` 나 2단계 조회(ID 페이지 → 연관 조회)로 푼다. 수정 후 SQL 로그로 쿼리 수를 확인한다. |
| 3 | 경고 | 커밋 `0a9cac6` 의 `CLAUDE.md`(+84), `.claude/skills/convention-check/SKILL.md`(+40), `.claude/skills/document-module/SKILL.md`(+71), `docs/item-bank/ARCHITECTURE.md`(+97), `docs/item-bank/BUSINESS-RULES.md`(+446), `docs/item-bank/CROSS-CHECK.md`(+216), `docs/item-bank/ERD.md`(+90) | 요청 문장은 엔드포인트 1개 이관 하나다. 위 파일은 그 문장으로 설명되지 않는다(1회차 문서·skill 정리). 체크리스트 "변경 파일 목록의 모든 파일이 요청 문장으로 설명된다", "요청과 무관한 변경이 지나치게 많지 않다"에 해당한다. | 이관 커밋과 문서·skill 커밋을 나눈다. 1회차 문서를 같은 작업으로 묶는 게 의도라면, 요청 문장을 그렇게 고쳐서 다시 검증한다. |
| 4 | 경고 | `characterization/tests/item-bank.test.js` (케이스 14개); `modern/api/src/main/java/com/example/item/ItemService.java:138-166` (`resolveSort`) | 정렬 옵션(`id`·`title`·`unit`·`level`·`created`·`dir`)은 characterization 에 한 건도 없다. 2페이지 이상의 결과도 없다. `ItemSearchServiceTest` 는 기본 정렬 한 건만 검증한다. `resolveSort` 의 `title`·`unit`·`id`·`created` 분기와 방향 처리가 테스트로 잠기지 않는다. 이슈 #1 의 경고 문구도 테스트 대상이 아니다. | 정렬 · 페이지 · 경고 케이스를 characterization 에 추가한다. 새 스냅샷이 생기므로 먼저 사람에게 묻는다(CLAUDE.md "스냅샷을 바꿔야 한다고 판단되면 먼저 멈추고 묻는다"). |
| 5 | 제안 | `modern/api/src/test/java/com/example/item/ItemControllerTest.java:122-130` | 테스트 이름은 "서비스에 null 로 위임한다"인데 `any()` 를 쓰고 상태 코드만 검증한다. 위임된 인자가 null 인지 확인하지 않는다. | 이름을 맞추거나, `ArgumentCaptor` 나 `isNull()` 로 인자를 검증한다. 승인에 영향 없음. |
| 6 | 제안 | `modern/api/src/main/java/com/example/item/ItemSpecifications.java:21` | 키워드의 `%` · `_` 를 이스케이프하지 않는다. 레거시도 같은 동작이며 search.php:95 가 "와일드카드로 처리됩니다" 경고를 낸다. 파라미터 바인딩이라 SQL 인젝션은 아니다. | 동작 보존이 우선이므로 이관 중에는 고치지 않는다. 의심 동작 목록에 올리고, 고칠지는 사람이 정한다. |

## 확인 필요

- **8080 서버 빌드 버전.** 실행 중인 modern 서버가 `HEAD` 빌드인지 확인하지 못했다. 다만 curl 응답은 현재 코드의 동작과 맞았다(`level=-1` 0건, `sort=bogus` 기본 정렬).
- **예외 삼킴 여부(convention-check 항목 3).** `ItemService.java:132` · `:172` 가 `NumberFormatException` 을 잡아 `0` · 기본 페이지로 바꾼다. 레거시 `(int)` 캐스팅과 페이지 보정을 흉내 낸 의도된 변환으로 보이지만, 규칙 문구상 사람이 판단해야 한다.
- **스냅샷 출처.** `characterization/__snapshots__/item-bank.test.js.snap` 이 `npm run baseline -- item-bank` 로 찍힌 것인지 수작업인지는 커밋 이력만으로 판정할 수 없다.
- **N+1 실측.** 이슈 #2 는 매핑 기준 추정이다. SQL 로그로 확인하기 전까지는 "발생 가능성"으로만 본다.

## 실행 기록

- `cd modern/api && ./gradlew test` → BUILD SUCCESSFUL, 39 tests, 0 failures, 0 errors (ItemSearchServiceTest 11 · ItemControllerTest 8 포함)
- `cd characterization && npm test` → 3 files, 28 passed
- `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` → 2 files passed, 1 skipped; 28 중 27 passed, 1 skipped (`example-units.test.js` legacyOnly)
- `cd characterization && npx vitest run tests/item-bank.test.js` (레거시 · 새 API 각각) → 14/14 · 14/14
- 테스트 후 `git status` 변경 없음(스냅샷 미변경)
- `convention-check` Skill → 위반 0건, 확인 필요 2건

## 다음 단계 (사람이 결정)

1. 이슈 #3 대로 커밋을 나눈다(이관 vs 문서·skill).
2. 이슈 #1 · #4 는 스냅샷 변경이 필요하므로, 방향을 정해 주면 그에 맞춰 이관 코드를 고친다.
3. 이슈 #2 는 수정 후 SQL 로그로 쿼리 수를 확인하고 재검증한다.
4. 수정 후 이 보고서를 다시 생성해 판정을 갱신한다.
