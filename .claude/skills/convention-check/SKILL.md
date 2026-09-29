---
name: convention-check
description: CLAUDE.md의 코딩 컨벤션과 금지 사항을 대상 파일에서 점검한다. "컨벤션 점검", "커밋 전 점검", "리뷰 전에 확인" 같은 요청 시 호출한다.
argument-hint: "[점검할 경로 (생략 시 변경된 파일 전체)]"
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash(git diff *)
  - Bash(git status *)
---

## 점검 대상 정하기

1. 인자로 경로가 주어지면 그 경로만 점검한다.
2. 인자가 없으면 `git status`와 `git diff`(staged/unstaged 모두)로 잡힌 변경 파일을 대상으로 한다. 아직 `git add` 하지 않은 새 파일(untracked)도 포함한다.
3. 대상 파일을 Read로 열어 실제 코드를 확인한다.

## 점검 항목 (예/아니오로만 판정)

1. **레이어 호출 순서** — 컨트롤러가 서비스만 호출하는가 (Repository·엔티티를 직접 호출/참조하지 않는가)
2. **응답 DTO** — 컨트롤러 응답이 엔티티가 아니라 record DTO(`XxxResponse.from(entity)`)인가
3. **예외 처리** — 예외를 catch 후 삼키지 않고 던지는가, `GlobalExceptionHandler` 외에 별도 예외 변환 로직을 두지 않았는가
4. **문항 상태 판정** — `item.status`를 문자열 하드코딩이 아니라 `ItemStatus`로 다루고, 외부 노출은 `A`만 허용하는가 (삭제를 별도 플래그로 판정하지 않는가)
5. **시각 처리** — 현재 시각이 필요할 때 `Clock` 빈을 주입받아 쓰는가 (`LocalDateTime.now()`/`Instant.now()` 직접 호출 금지)
6. **프런트 API 호출 경로** — `modern/web`에서 API 호출이 `src/api/client.ts`의 `getJson`을 거치는가 (직접 `fetch`/`axios` 사용 금지), 조회 훅이 `hooks/useApiQuery.ts` 기반인가
7. **테스트 규칙** — 테스트 클래스에 `@ActiveProfiles("test")`와 한국어 `@DisplayName`이 붙어 있는가, 엔티티 생성에 `ItemFixtures`를 쓰는가
8. **characterization 스냅샷** — 새 API 테스트 실패를 피하려고 스냅샷/테스트 코드 자체를 수정하지 않았는가

대상 파일과 무관한 항목(예: 프런트 파일에 1~5번)은 건너뛰고 표에도 올리지 않는다.

## 출력 형식 (반드시 이 순서로)

1. **판정 요약** — 대상 파일 목록과 항목별 위반 개수를 1~2문단으로.
2. **위반 목록 표** — 컬럼: `파일:줄번호 | 어긴 규칙 | 수정 방향`. 위반이 없으면 "위반 없음"만 적는다.

## 규칙

- 코드를 직접 고치지 않는다.
- 근거 라인을 댈 수 없는 지적은 하지 않고 "확인 필요"로 남긴다.
