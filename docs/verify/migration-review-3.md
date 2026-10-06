# 검증 리뷰 3 — 문항 검색(search.php) 이관, 1차 리뷰 반영 후 재검증

- 검증 대상: `upstream/main...HEAD` (브랜치 `day1`, HEAD `b5a9b27`)
- 요청 범위: 레거시 엔드포인트 1개(`legacy/item-bank-php/search.php`)를 동작을 바꾸지 않고 modern 으로 이관.
- 이전 리뷰: `docs/verify/migration-review-2.md` (반려). 이번에는 그 뒤 추가된 커밋(`b5a9b27`)을 본다.
- 검증 일자: 2026-10-06
- 코드 수정 없음. 보고만 한다.

판정: **반려**

## 기준별 결과

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `cd modern/api && ./gradlew cleanTest test` 39 건 통과, 실패 0 · 오류 0 · 건너뜀 0. `cd characterization && npm test`(레거시 8081) 28 건 통과. `TARGET_BASE_URL=http://localhost:8080 npm test`(새 API) 27 건 통과 · 1 건 건너뜀(`tests/example-units.test.js:21`, `legacyOnly`, 단원 목록 `units.php`, 이번 변경 범위 밖). 문항 검색 14 케이스는 두 대상 모두 통과. |
| 치명 이슈 0건 | 충족 | 비밀값 리터럴 없음. SQL 문자열 연결 없음(Criteria 조건 + 파라미터 바인딩). 데이터 삭제 · 덮어쓰기 코드 없음. 이슈 1~3 은 치명 아님. |
| 요청 범위 이탈 없음 | **미충족** | 요청 문장으로 설명되지 않는 파일이 여전히 있다. 이슈 1 참조. |
| 컨벤션 준수 | 충족 | 2차 리뷰의 N+1 위반(`CLAUDE.md:60`)은 `b5a9b27` 에서 해소됐다. `ItemRepository.java:30-32` 에 `@EntityGraph(attributePaths = {"unit"})` 가 붙었고, `Item.java:61` 에 `@BatchSize(size = 20)` 가 붙었다. 컨트롤러 · 서비스 · 예외 · `ItemStatus` · `Clock` · 테스트 규칙 위반 없음. |

## 2차 리뷰 대비 변화

| 2차 이슈 | 상태 | 근거 |
|---|---|---|
| #1 N+1 (컨벤션, 경고) | **해소** | `b5a9b27`: `ItemRepository.java:30-32`(검색 `findAll` 에 `unit` fetch), `Item.java:61`(`tags` `@BatchSize`). 기존 테스트 전부 통과. 단, 쿼리 수를 확인하는 테스트는 없다(이슈 3). |
| #2 입력값 경고 누락 (경고) | 미해소 | `ItemSearchResult.java:9` 에 `warnings` 필드 없음. 변경 없음. |
| #3 범위 이탈 (경고) | 미해소 | 이번 범위 변경 없음. 아래 이슈 1 참조. |
| #4 LIKE 와일드카드 (제안) | 미해소 | 변경 없음. 제안 수준. |

## 실행 기록

- modern/api 가 바뀌었으므로 새 API 대상(8080)은 재기동 후 측정했다. 기존 JVM(PID 24548, 12:47 기동)은 `b5a9b27` 의 소스 수정(12:53:59)보다 먼저 떠 있었기 때문이다. 해당 JVM 을 종료하고 `./gradlew bootRun` 으로 다시 띄웠다(12:57 기동, 로그 `scratchpad/bootrun3.log`). 이 재기동은 리뷰 과정에서 한 작업이다.
- 테스트 결과는 위 기준 표와 같다.

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `CLAUDE.md:1-84`(파일 신규 추가), `.claude/skills/convention-check/SKILL.md`, `.claude/skills/document-module/SKILL.md`, `docs/item-bank/ARCHITECTURE.md`, `docs/item-bank/ERD.md`, `docs/item-bank/CROSS-CHECK.md`, `docs/verify/migration-review-1.md`, `docs/verify/migration-review-2.md` | 요청 문장은 "엔드포인트 1개 이관"인데, 브랜치에 1회차 작업(`ddaf9c3`, `0a9cac6`, `2994907`)과 검증 기록(`e82da53`)이 함께 들어 있다. 이 중 `docs/item-bank/BUSINESS-RULES.md` 만 테스트의 BR 번호로 연결되어 이관 근거로 설명된다. 나머지는 요청 문장으로 설명되지 않는다. 이 이슈 때문에 "요청 범위 이탈 없음" 이 미충족이다. | 이관 PR 에서 빼고 1회차 작업 · 검증 기록을 별도 PR 로 분리한다. |
| 2 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchResult.java:9`, `ItemService.java:113-117` | 레거시는 입력값 경고를 `$warnings` 배열로 만든다(`legacy/item-bank-php/search.php:48, 88, 92, 95, 113, 116, 142, 225, 238`). 이관본 응답 record 에는 `warnings` 가 없다. `characterization/lib/normalize.mjs` 의 JSON 정규화는 `items · count · message` 만 읽으므로 테스트로 잡히지 않는다. 동작 보존 기준에서 빠진 차이다. | 레거시가 이 경고를 실제로 노출하는지 먼저 확인한다(확인 필요 참조). 노출한다면 응답에 `warnings` 를 추가하고 정규화 대상에 넣는다. 스냅샷 · 정규화 변경은 `CLAUDE.md` 에 따라 먼저 사용자에게 묻는다. |
| 3 | 제안 | `modern/api/src/main/java/com/example/item/ItemRepository.java:30-32`, `Item.java:61` | N+1 해소를 검증하는 테스트가 없다. 지금 기준으로는 "쿼리 수가 페이지당 상수"라는 사실을 코드 리뷰로만 확인했다. `@BatchSize(size = 20)` 도 페이지 크기(`ItemService.java` 의 `SEARCH_PAGE_SIZE = 20`)와 값을 따로 맞춰야 하므로 나중에 어긋날 수 있다. | `ItemSearchServiceTest` 에 Hibernate 통계(쿼리 수) 검증을 추가한다. `@BatchSize` 값을 페이지 크기 상수와 연결하는 방법도 검토한다. |
| 4 | 제안 | `modern/api/src/main/java/com/example/item/ItemSpecifications.java:23-24` | 키워드의 `%` · `_` 를 이스케이프하지 않아 LIKE 와일드카드로 동작한다. 레거시도 같다(`legacy/item-bank-php/search.php:94-98`). SQL 인젝션은 파라미터 바인딩으로 막혀 있어 이관 차이는 아니다. | 동작 보존이 우선이면 그대로 둔다. 바꾸려면 레거시 결과 차이를 사용자와 먼저 정한다. |

## 확인 필요

- `ItemService.java:122` 의 `trimAndTruncate` 는 `String.substring` 으로 UTF-16 단위로 자른다. 서로게이트 쌍이 경계에 걸리면 깨진 문자열이 된다. 레거시 자르기 함수(`search.php:86-88`)가 문자 단위인지 이 리뷰에서 확인하지 않았다.
- 레거시가 `$warnings`(이슈 2)를 화면 · 응답에 실제로 노출하는지. 출력 템플릿은 이 리뷰에서 읽지 않았다.
- 쿼리 수 감소(N+1 해소)는 코드 읽기로만 확인했다. 실행 쿼리 로그는 이 리뷰에서 측정하지 않았다(이슈 3).
- 스냅샷 파일(`characterization/__snapshots__/item-bank.test.js.snap`)은 작업 트리에서 바뀌지 않았다(테스트 실행 후 `git status` 동일). 커밋 이력 안의 수정 여부는 추적하지 않았다.
