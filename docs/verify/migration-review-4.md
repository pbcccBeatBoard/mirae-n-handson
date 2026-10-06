# 검증 리뷰 4 — 문항 검색(search.php) 이관, 비밀값 수정 반영 후 재검증

- 검증 대상: `upstream/main...HEAD -- modern characterization` + 커밋되지 않은 워킹트리 변경
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 이전 리뷰: `docs/verify/migration-review-3.md`
- 검증 일자: 2026-10-06
- 수행: reviewer Sub-agent(체크리스트 대조) · tester Sub-agent(테스트 실행) 보고를 받아 메인 세션이 판정.
- 이 리뷰 중 tester 가 수정한 파일은 테스트 파일 1개(`GradingServerClientTest.java`)뿐이다. 소스는 수정하지 않았다.

판정: **반려**

## 기준별 결과

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | **미충족** | `cd modern/api && ./gradlew test` 54 건 중 53 건 통과, 1 건 실패(`ItemBankApplicationTests` — 컨텍스트 기동, 원인은 이슈 2). `cd characterization && npm test`(레거시) 28 건 통과. `TARGET_BASE_URL=http://localhost:8080 npm test`(새 API) 27 건 통과 · 1 건 건너뜀 · 실패 0. |
| 치명 이슈 0건 | **미충족** | 이슈 1 (커밋된 비밀번호 리터럴) |
| 요청 범위 이탈 없음 | **미충족** | 이슈 3 (`GradingServerClient` 와 그 테스트는 문항 검색 이관과 무관) |
| 컨벤션 준수 | 충족 | 이관 코드는 Controller → Service → Repository, record DTO, 예외 처리, `A` 상태 노출, OSIV 대응, 테스트 규칙을 지켰다. 위반 항목 없음. |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 치명 | `modern/api/src/main/java/com/example/assignment/GradingServerClient.java:28-29` (HEAD 기준) | `username` · `password` 문자열 리터럴(값은 문서에 옮기지 않는다) 가 커밋 `661dbd9` 에 들어 있다. 워킹트리는 설정 주입(41-43행)으로 바꿨지만 커밋되지 않았다. 이대로면 비밀값이 히스토리에 남는다. | 워킹트리 수정본을 반영하고, 노출된 비밀번호는 채점 서버 쪽에서 교체(rotate)한다. 히스토리 정리 여부는 사람이 판단한다. |
| 2 | 경고 | `GradingServerClient.java:42-43`, `modern/api/src/main/resources/application.yml`, `src/test/resources` | `@Value("${grading.username}")`, `@Value("${grading.password}")` 에 기본값이 없고 어떤 설정 파일에도 `grading.*` 가 없다. tester 가 `ItemBankApplicationTests` 에서 `Could not resolve placeholder 'grading.username'` 로 실패하는 것을 확인했다. `bootRun` 도 같은 이유로 실패한다. | 운영은 환경변수로 주입한다. test 프로필(또는 `ItemBankApplicationTests` 의 `@TestPropertySource`)에 더미 값을 둔다. `application.yml` 의 DB · 풀 설정은 건드리지 않는다. |
| 3 | 경고 | `GradingServerClient.java` 전체, `GradingServerClientTest.java` 전체 | 요청 문장으로 설명되지 않는 외부 호출 클라이언트와 설정 키(`grading.*`)다. `661dbd9`(vendor pr-2 적용)에서 들어왔다. | 이관 브랜치에서 분리하고 별도 요청 · 별도 PR 로 다룬다. |
| 4 | 경고 | `GradingServerClient.java` 의 `buildRequest` (HEAD 56행) | HEAD 의 JSON 본문은 문자열 연결이라 `studentId` 에 `"` 나 `\` 가 들어가면 필드 주입이 가능했다. 워킹트리는 Jackson 직렬화와 `STU-<숫자>` 검증으로 고쳤고, tester 가 잘못된 studentId 10종 · null 거부 테스트를 추가해 통과를 확인했다. | 워킹트리 수정본을 커밋하면 해소된다(이슈 1 과 함께). |
| 5 | 제안 | `modern/api/src/main/java/com/example/item/ItemService.java:315-316, 355-357` | `NumberFormatException` 을 잡아 값을 보정한다. 삼키는 것이 아니라 PHP `(int)` 캐스트 동작을 옮긴 것이다. | 조치 불필요. 정수 범위를 넘는 `level` 케이스를 characterization 에 추가할지 검토할 수 있다(스냅샷 변경이므로 먼저 묻는다). |

(reviewer 보고의 이슈 1 줄번호 34-35 는 실제와 달라 HEAD 를 직접 확인한 28-29 로 고쳐 적었다.)

## 테스트 상세 (tester 보고)

- 수정한 테스트 파일: `modern/api/src/test/java/com/example/assignment/GradingServerClientTest.java`
  - 3-인자 생성자에 맞추고 `@ActiveProfiles("test")` · 한국어 `@DisplayName` 을 붙였다.
  - 요청 구성(POST · URI · 헤더 · Basic 인증), JSON 본문 구조, 잘못된 studentId 10종 거부, null studentId · null submissionId 거부, 유효 studentId 의 JSON 왕복을 검증한다.
- 문항 검색 쪽은 `ItemSearchServiceTest` · `ItemControllerTest` 가 이미 있어 새로 추가하지 않았다.
- `characterization` 의 테스트 코드와 스냅샷은 수정하지 않았다.
- 건너뜀 1 건: `characterization/tests/example-units.test.js:21` (`legacyOnly`, 단원 목록 `units.php`). 새 API 대상에서만 건너뛰는 기존 조건이며 이번 변경 범위 밖이다. 문항 검색 14 케이스는 새 API 대상에서 건너뛰지 않고 실행됐다.

## 확인 필요

- 8080 서버를 tester 가 기동하지 않았는데 접속됐다. 이미 떠 있던 서버가 **현재 워킹트리 코드**로 뜬 것인지 확인하지 못했다. 위 새 API 대상 27 건 통과의 신뢰도는 이 확인에 달려 있다. 이슈 2 때문에 현재 워킹트리 코드로는 `bootRun` 이 실패하므로, 떠 있는 서버는 수정 전 코드일 가능성이 높다.
- `characterization/lib/target.mjs` 의 `PATH_ALIASES` 에 `/search.php → /api/items/search` 가 있는지는 이번 diff 에 없어 reviewer 가 보지 못했다.
- 태그 필터(`ItemSpecifications.java:44` 의 `join("tags")`)가 결과 행의 `tags` 를 레거시처럼 전체 태그로 돌려주는지는 스냅샷과 대조가 필요하다. 새 API 대상 테스트 통과가 신뢰되면 해소된다.
- reviewer 가 `ItemSearchServiceTest` 를 읽지 않았다.
- 두 보고 사이에 엇갈린 판정은 없다. 테스트 기준은 reviewer 가 잠정 미충족으로 예상했고 tester 실행 결과가 같은 결론을 확인했다.

## 의심 동작 (레거시 동작이라 이관에서 고치지 않음)

- `level` 이 빈 값이면 `level < 5` 로 조회해 난이도 5 문항이 제외된다. 주석과 코드가 불일치한다(`search.php:213-216`, BR-07, `ItemService.java:232, 276`). 스냅샷이 이를 고정한다.
- `level` 형식이 1~5 가 아니면 PHP `(int)` 캐스트로 비교한다. `3abc` 는 3 으로 조회된다(BR-09).
- 키워드의 `%` · `_` 와일드카드를 이스케이프하지 않는다(레거시 `search.php:98` 도 동일).
- 존재하지 않는 `unit` · `tag` 를 검증하지 않고 0건으로 응답한다(BR-05, BR-12).

## 완료까지 남은 일

1. 이슈 2 해소(설정 · 테스트 프로퍼티) 후 `./gradlew test` 전체 통과 확인.
2. 이슈 1 · 4 의 워킹트리 수정 반영과 비밀번호 교체. 커밋은 사용자 확인 후에만 한다.
3. 이슈 3 처리 방식 결정(분리 · 삭제).
4. 현재 워킹트리 코드로 8080 서버를 새로 띄운 뒤 `TARGET_BASE_URL=http://localhost:8080 npm test` 재실행.
