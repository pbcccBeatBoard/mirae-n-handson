# 검증 리뷰 5 — 문항 검색(search.php) 이관, 비밀값 수정 · test 설정 반영 후 재검증

- 검증 대상: `upstream/main...HEAD -- modern characterization` + 커밋되지 않은 워킹트리 변경
  - 워킹트리 변경: `GradingServerClient.java`, `GradingServerClientTest.java`, `src/test/resources/application-test.yml`
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 이전 리뷰: `docs/verify/migration-review-4.md` (반려)
- 검증 일자: 2026-10-06
- 수행: reviewer Sub-agent(체크리스트 대조) · tester Sub-agent(테스트 실행) 보고를 받아 메인 세션이 판정.
- 이번 리뷰 중 tester 가 수정한 파일은 테스트 리소스 1개(`application-test.yml`)뿐이다. 소스와 `src/main` 설정, `characterization` 의 테스트 · 스냅샷은 수정하지 않았다. 커밋 · 푸시 없음.

판정: **반려**

## 기준별 결과

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | **미충족** (현재 코드 기준 실행 미완료) | `cd modern/api && ./gradlew test` 54 건 중 54 건 통과(4차의 `ItemBankApplicationTests` 실패 해소). `cd characterization && npm test`(레거시) 28 건 통과. `TARGET_BASE_URL=http://localhost:8080 npm test`(새 API) 27 건 통과 · 1 건 건너뜀 · 실패 0 이지만, **8080 서버가 현재 워킹트리 코드가 아니어서** 이 결과는 현재 코드의 검증이 아니다. CLAUDE.md 완료 기준(새 API 대상 전부 통과)은 서버를 재기동한 뒤 재실행해야 충족된다. |
| 치명 이슈 0건 | **미충족** | 이슈 1 (커밋된 비밀번호 리터럴). 워킹트리에서는 제거됐으나 커밋되지 않았고 히스토리에 남는다. |
| 요청 범위 이탈 없음 | **미충족** | 이슈 3 (`GradingServerClient` · 테스트 · `grading.*` 설정 키). |
| 컨벤션 준수 | 충족 | Controller → Service → Repository, record DTO, `@EntityGraph` · `@BatchSize` 로 OSIV off 대응, `A` 상태만 노출, 삼키는 예외 없음, 임시 출력 없음. 테스트 규칙(`@ActiveProfiles("test")`, 한국어 `@DisplayName`, `ItemFixtures`) 준수. |

두 보고가 엇갈린 지점은 없다. reviewer 는 테스트 기준을 "확인 필요"로, tester 는 서버 버전 문제를 별도로 보고했고 둘은 서로 모순되지 않는다.

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 | 상태 |
|---|---|---|---|---|---|
| 1 | 치명 | `modern/api/src/main/java/com/example/assignment/GradingServerClient.java:28-29` (HEAD 기준) | 커밋 `661dbd9` 에 `username` · `password` 문자열 리터럴(값은 문서에 옮기지 않는다) 리터럴이 있다. 워킹트리는 `@Value("${grading.username}")`, `@Value("${grading.password}")` 주입으로 바꿨다. | 워킹트리 수정본을 반영하고(커밋은 사용자 확인 후), 노출된 비밀번호는 채점 서버 쪽에서 교체한다. 히스토리 정리 여부는 사람이 결정한다. | 코드는 해소, 반영 · 교체 전까지 미해소 |
| 2 | 경고 | `GradingServerClient.java:42-43`, `modern/api/src/main/resources/application.yml`(grading 키 없음) | 기본값 없는 `@Value` 라서 운영과 `bootRun` 은 환경변수(`GRADING_USERNAME`, `GRADING_PASSWORD`) 없이는 기동에 실패한다. test 프로필은 `application-test.yml` 에 더미 값이 추가돼 해소됐다(54/54 통과). | 운영 주입 방법을 문서화한다. `application.yml` 의 DB · 풀 설정은 건드리지 않는다. | 테스트 쪽 해소, 운영 쪽 남음 |
| 3 | 경고 | `GradingServerClient.java` 전체, `GradingServerClientTest.java` 전체, `application-test.yml` 의 grading 블록 | 문항 검색 이관 요청으로 설명되지 않는 외부 호출 클라이언트와 설정 키다. `661dbd9` 에서 들어왔다. 이번 수정으로 범위 밖 변경이 오히려 늘었다. | 별도 요청 · 별도 PR 로 분리하거나, 사람이 범위에 포함하기로 명시한다. | 남음(사람 결정) |
| 4 | 경고 | `GradingServerClient.java:68-84`(워킹트리), HEAD 56행 | HEAD 의 문자열 연결 JSON 은 `studentId` 에 `"` · `\` 가 있으면 필드 주입이 가능했다. 워킹트리는 `STU-\d+` 검증과 Jackson 직렬화로 고쳤고, 잘못된 studentId 10종 · null 거부 테스트가 통과한다. | 이슈 1 과 함께 커밋한다. | 코드는 해소, 미커밋 |
| 5 | 경고 | `characterization/__snapshots__/item-bank.test.js.snap`, `characterization/tests/item-bank.test.js` | 유효한 태그 필터 케이스(예: `tag=문장제`)가 없다. 태그 케이스는 60자 태그(0건) 하나뿐이라, 태그 필터 결과의 `tags` 가 레거시와 같은지 스냅샷으로 검증되지 않는다. | 스냅샷 추가는 CLAUDE.md 상 **먼저 묻는다**. 사람이 결정한다. | 남음 |
| 6 | 경고 | `modern/api/src/test/java/com/example/item/ItemSearchServiceTest.java` 의 `tagNameFiltersExactly` | `title` 만 검증하고 `tags` 내용은 단언하지 않는다. 필터용 join 이 결과의 `tags` 를 줄이는 회귀를 못 잡는다. | 필터 후 행의 `tags` 가 전체 태그(예: `["계산","문장제"]`)인지 단언을 추가한다(tester 담당). | 남음(테스트 누락) |
| 7 | 제안 | `modern/api/src/main/java/com/example/item/ItemService.java:139-140` | `toLowerCase()` 가 기본 로케일을 따른다. PHP `strtolower` 는 ASCII 만 바꾼다. 영향은 극히 작다. | `toLowerCase(Locale.ROOT)`. | 제안 |

## 확인 필요

- **8080 서버가 오래된 코드다.** tester 확인: pid 34022, 12:57:28 시작. 이후 `GradingServerClient.java` 등 `src/main` 파일이 바뀌었다. `GET /api/units` 는 200, `GET /api/items?q=a` 는 404(검색 엔드포인트 경로인지는 확인하지 못함). 서버를 재기동하고 `TARGET_BASE_URL=http://localhost:8080 npm test` 를 다시 돌려야 한다. 재기동은 사람이 판단한다.
  - 참고: 재기동 시 `grading.username`, `grading.password` 환경변수가 없으면 이슈 2 때문에 서버가 뜨지 않는다.
- 태그 필터의 `tags` 결과 일치: 코드상으로는 레거시와 같을 것으로 보인다(`ItemSpecifications.java:43-45` 의 `root.join("tags")` 는 필터 전용이고, 응답의 `tags` 는 `Item.java:60-61` 의 `@BatchSize` 로 따로 로딩하며 `@OrderBy("id ASC")` 가 레거시 뷰의 `GROUP_CONCAT(... ORDER BY t.id)` 와 같다). 스냅샷으로는 확인되지 않는다(이슈 5).
- `loosePhpIntCast`(`ItemService.java:125-135`): 정규식 `^\s*[+-]?\d+` 로 정수 부분만 읽는다. PHP 의 `(int)"1e3"`(=1000), `(int)"1.9"`, int64 범위 초과 포화 동작과 다를 수 있다. 스냅샷에는 `-1` 만 있어 검증되지 않는다. 레거시 PHP 실제 값과 비교가 필요하다.
- `ItemSearchServiceTest` 에서 `dir` · `sort` 조합(unit, title, created)과 `level` 이 소수 · `3abc` 일 때 검증이 없다(tester 몫).

## 이전 리뷰(4차)의 줄번호 정정

`migration-review-4.md` 이슈 5 의 `ItemService.java:315-316, 355-357` 과 의심 동작의 `ItemService.java:232, 276` 은 존재하지 않는다(`ItemService.java` 는 184 행). 직접 확인한 실제 위치는 다음과 같다.
- `NumberFormatException` 처리: `ItemService.java:132`(`loosePhpIntCast`, 125-135행), `:172`(페이지 해석)
- BR-07 적용: `ItemService.java:98` 근처의 `levelEquals(loosePhpIntCast(levelRaw))` 호출과 상수 정의. reviewer 는 `:31`(상수), `:93`(적용)이라고 보고했으나 `:93` 은 직접 대조하지 못했다. 위치 확인이 필요하다.

## 테스트 상세 (tester 보고)

| 명령 | 결과 |
|---|---|
| `cd modern/api && ./gradlew test` | 54 건 중 54 통과, 실패 0, 건너뜀 0 |
| `cd characterization && npm test` (레거시) | 3 개 파일, 28 건 통과 |
| `TARGET_BASE_URL=http://localhost:8080 npm test` (새 API) | 27 건 통과, 1 건 건너뜀, 실패 0 — **오래된 서버 대상** |

- 건너뜀 1 건: `tests/example-units.test.js:21` 의 단원 목록(`units.php`). `it.skipIf(legacyOnly)` 로 `TARGET_BASE_URL` 이 있으면 건너뛰는 기존 조건이며 이번 변경 범위 밖이다.
- 수정한 테스트 파일: `modern/api/src/test/resources/application-test.yml` 에 `grading.base-url`, `grading.username`, `grading.password` 더미 값 추가. 새 테스트 파일은 추가하지 않았다(`GradingServerClientTest` 가 이미 새 로직을 다룬다).
- 검증 방식 확인: `characterization/lib/target.mjs:19-22` 의 `PATH_ALIASES` 에 `/search.php → /api/items/search` 가 있다(이전 "확인 필요" 해소).

## 의심 동작 (레거시 동작이라 이관에서 고치지 않음)

- `level` 이 빈 값이면 `level < 5` 로 조회해 난이도 5 문항이 제외된다. 주석과 코드가 불일치한다(`legacy/item-bank-php/search.php:213-216`, BR-07). 스냅샷이 이를 고정한다.
- `level` 이 1~5 형식이 아니면 `(int)` 캐스트로 비교한다(`search.php:223-230`, BR-09). `3abc` 는 3 으로 조회된다.
- 키워드의 `%` · `_` 를 이스케이프하지 않는다(`search.php:98`). modern 도 `ItemSpecifications.java:19-21` 에서 동일하다.
- 존재하지 않는 `unit` · `tag` 를 검증하지 않고 0건으로 응답한다(BR-05, BR-12). `page` 가 범위를 넘으면 `count` 는 그대로이고 `message` 는 null, `items` 만 빈 배열이다(`search.php:657`).
- 태그의 `%` · `_` 는 경고만 내고 정확 일치로 처리한다(`search.php:237-239`). modern 은 경고를 응답에 담지 않으나 정규화 결과에 경고가 들어가지 않아 스냅샷에는 영향이 없다.

## 4차 대비 변화

- 해소: `ItemBankApplicationTests` 실패(테스트 쪽 `application-test.yml`), 비밀값 리터럴 · JSON 주입(코드, 워킹트리 한정).
- 남음: 워킹트리 미커밋, 비밀번호 교체, 범위 이탈, 운영 설정 주입 문서화, 태그 필터 검증 공백(이슈 5 · 6), 새 API 대상 테스트 재실행.

## 완료까지 남은 일

1. 사용자 확인 후 워킹트리 수정(이슈 1 · 4)을 커밋하고, 채점 서버의 비밀번호를 교체한다.
2. 이슈 3 처리 방식(분리 · 범위 포함)을 사람이 결정한다. 분리하면 이슈 2 · 4 도 함께 정리된다.
3. 8080 서버를 현재 코드로 재기동하고 새 API 대상 `npm test` 를 다시 실행한다.
4. 이슈 5(스냅샷 추가)는 먼저 사람에게 묻고, 이슈 6 의 `tags` 단언은 tester 가 추가한다.
