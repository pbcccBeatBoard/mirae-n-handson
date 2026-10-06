# 검증 리뷰 2 — 문항 검색(search.php) 이관

- 검증 대상: `upstream/main...HEAD` (브랜치 `day1`)
- 요청 범위: 레거시 엔드포인트 1개(`legacy/item-bank-php/search.php`)를 동작을 바꾸지 않고 modern 으로 이관.
- 검증 일자: 2026-10-06
- 코드 수정 없음. 보고만 한다.

판정: **반려**

## 기준별 결과

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `cd modern/api && ./gradlew cleanTest test` 39 건 통과, 실패 0 · 오류 0 · 건너뜀 0. `cd characterization && npm test`(레거시) 28 건 통과. `TARGET_BASE_URL=http://localhost:8080 npm test`(새 API) 27 건 통과 · 1 건 건너뜀(`tests/example-units.test.js:21`, `it.skipIf(legacyOnly)`, 단원 목록 `units.php` 케이스로 이번 변경 범위 밖). 문항 검색 케이스 14 건은 두 대상 모두 통과. |
| 치명 이슈 0건 | 충족 | 비밀값 리터럴 없음. SQL 문자열 연결 없음(Criteria 조건 + 파라미터 바인딩). 데이터 삭제 · 덮어쓰기 코드 없음. 이슈 1~4 는 모두 치명 아님. |
| 요청 범위 이탈 없음 | **미충족** | 요청 문장으로 설명되지 않는 파일이 있다. 아래 이슈 3 참조. |
| 컨벤션 준수 | **미충족** | `CLAUDE.md:60` 의 "연관 엔티티가 필요한 조회는 `@EntityGraph` 나 fetch/집계 JPQL로 한 번에" 규칙을 어긴다. 아래 이슈 1 참조. |

### 기준별 점검 메모

- 테스트 충분성: 컨트롤러(`ItemControllerTest`, 검색 응답 모양 · 빈 결과 안내) · 서비스(`ItemSearchServiceTest`, H2 실제 SQL 로 BR 규칙 검증) · characterization(14 케이스, 스냅샷 14 개 `item-bank.test.js.snap`) 가 모두 있다. 기존 테스트를 지우거나 건너뛰게 바꾼 곳은 diff 에 없다.
- 스냅샷: 작업 트리에서 `characterization/__snapshots__/item-bank.test.js.snap` 은 변경되지 않았다(테스트 실행 후에도 `git status` 동일). 단, 이 파일은 브랜치에서 새로 추가된 파일이라 커밋 이력 안의 수정 여부는 이 리뷰에서 추적하지 않았다(확인 필요 참조).
- 새 API 대상 실행은 `localhost:8080` 의 기존 JVM(PID 24548, 2026-10-06 12:47 기동)에 대해 했다. 소스 최종 수정(2026-09-29 16:46)보다 나중에 떠 있어 현재 코드로 판단했다. 재기동해서 확인하지는 않았다.
- 테스트 Java 규칙(`@ActiveProfiles("test")`, 한국어 `@DisplayName`, `ItemFixtures` 사용)은 세 테스트 클래스 모두 지킨다.
- `Clock`, 예외 변환(`GlobalExceptionHandler`), 문항 상태(`ItemStatus.ACTIVE`) 규칙은 위반 없음.

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/ItemService.java:111-112`, `ItemSearchRow.java:15-17` | 검색은 `itemRepository.findAll(spec, pageable)` 로 조회하면서 `@EntityGraph` 를 붙이지 않는다. 이어서 `item.getUnit()`(LAZY, `Item.java:30-32`)과 `item.getTags()`(LAZY, `Item.java:54`)를 행마다 건드려 페이지당 최대 20 행만큼 추가 쿼리가 나간다(N+1). `CLAUDE.md:60` 규칙 위반. | 검색 쿼리에 연관 로딩을 붙인다. `tags` 는 컬렉션이라 페이지네이션과 fetch join 을 함께 쓰면 메모리 페이징 경고가 나므로, (a) id 페이지 조회 후 `findWithDetailsByIdIn` 으로 2차 조회하거나 (b) 다른 방식을 택한다. 쿼리 수를 확인하는 테스트를 같이 둔다. |
| 2 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchResult.java:9`, `ItemService.java:113-117` | 레거시는 입력값 경고를 `$warnings` 배열로 만든다(`legacy/item-bank-php/search.php:48, 88, 92, 95, 113, 116, 142, 225, 238`). 이관본 응답 record 에는 `warnings` 가 없다. `characterization/lib/normalize.mjs` 의 JSON 정규화는 `items · count · message` 만 읽으므로 테스트로 잡히지 않는다. 동작 보존 기준에서 빠진 차이다. | 레거시가 이 경고를 화면에 실제로 보이는지 먼저 확인한다(확인 필요 참조). 보인다면 응답에 `warnings` 를 추가하고 정규화 대상에 넣는다. 스냅샷 · 정규화 변경은 `CLAUDE.md` 에 따라 먼저 사용자에게 묻는다. |
| 3 | 경고 | `CLAUDE.md:1-84`(파일 신규 추가), `.claude/skills/convention-check/SKILL.md`, `.claude/skills/document-module/SKILL.md`, `docs/item-bank/ARCHITECTURE.md`, `docs/item-bank/ERD.md`, `docs/item-bank/CROSS-CHECK.md`, `docs/verify/migration-review-1.md` | 요청 문장은 "엔드포인트 1개 이관"인데, 브랜치에는 1회차 작업(`ddaf9c3`, `0a9cac6`, `2994907`)이 함께 들어 있다. 이 중 `docs/item-bank/BUSINESS-RULES.md` 만 테스트의 BR 번호로 연결되어 이관 근거로 설명된다. 나머지 파일은 요청 문장으로 설명되지 않는다. | 이관 PR 에서 빼고 1회차 작업을 별도 PR 로 분리한다. 분리 전에는 "요청 범위 이탈 없음" 기준이 충족되지 않는다. |
| 4 | 제안 | `modern/api/src/main/java/com/example/item/ItemSpecifications.java:23-24` | 키워드의 `%` · `_` 를 이스케이프하지 않아 LIKE 와일드카드로 동작한다. 레거시도 같다(`legacy/item-bank-php/search.php:94-98`, 경고 문구만 붙임). SQL 인젝션은 파라미터 바인딩으로 막혀 있어 이관 차이는 아니다. | 동작 보존이 우선이면 그대로 둔다. 이스케이프(`ESCAPE` 절)로 바꾸려면 레거시 결과 차이를 사용자와 먼저 정한다. |

## 확인 필요

- `ItemService.java:122` 의 `trimAndTruncate` 는 `String.substring` 으로 UTF-16 단위로 자른다. 서로게이트 쌍(이모지 등)이 경계에 걸리면 깨진 문자열이 된다. 레거시 자르기 함수(`search.php:86-88`)가 문자 단위인지 이 리뷰에서 확인하지 않았다.
- 레거시가 `$warnings`(이슈 2)를 화면 · 응답에 실제로 노출하는지. 출력 템플릿은 이 리뷰에서 읽지 않았다.
- 스냅샷 파일의 커밋 이력 수정 여부. 작업 트리는 깨끗하고 실행으로 바뀌지 않았지만, 이력은 추적하지 않았다.
- 새 API 대상 테스트를 현재 소스로 다시 기동해 돌린 결과인지 여부(기존 JVM 사용, 재기동하지 않음).

## 실행 기록 (요약)

| 대상 | 명령 | 결과 |
|---|---|---|
| modern/api | `./gradlew cleanTest test` | BUILD SUCCESSFUL, 39 통과 / 0 실패 / 0 건너뜀 |
| characterization (레거시 8081) | `npm test` | 28 통과 / 0 실패 |
| characterization (새 API 8080) | `TARGET_BASE_URL=http://localhost:8080 npm test` | 27 통과 / 0 실패 / 1 건너뜀(`units.php`, 범위 밖) |
