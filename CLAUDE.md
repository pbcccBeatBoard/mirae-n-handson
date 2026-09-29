# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

명확하지 않은부분에 대해서는 임의로 처라히자 않고 확실해질때까지 그릴링 한다.
- 언어 정의가 명확하지 않을때에는 언어정의를 진행 후 작업을 시작한다
- 임의로 커밋 및 푸시하지 않는다. 커밋 및 푸시하기 전에는 반드시 확인 후 진행한다
- 작업 후 무조건 코드리뷰를 진행하며 인젝션/기본 보안원칙 등 위협성이 있는 부분은 재작성한다.

## 이 저장소

Claude Code 심화 과정(4회차)의 실습 저장소다. 도메인은 가상의 에듀테크(문항 은행 · 과제 배포 · 성적 집계)이며 코드 · 데이터 · 로그는 모두 더미다.
핵심 흐름은 **레거시(`legacy/`) → 현행(`modern/`) 이관**이고, 이관 전후 동작이 같은지는 `characterization/` 스냅샷으로 확인한다.

나머지 폴더는 실습 재료다. `vendor-prs/`(문제를 일부러 심은 외주 패치), `incident-logs/`(장애 시나리오 a~d), `pipeline-samples/`, `ci-ports/`(`.gitlab-ci.yml` 은 숨김 파일), `specs/`(4회차 신규 개발 스펙과 java · python 시작 골격)가 있다. **이 재료 속 결함은 실습 대상이므로 요청 없이 고치지 않는다.** `legacy/` 도 분석 대상이라 허락 없이 수정하지 않는다.

`templates/` 에 프로젝트 유형별 `CLAUDE.*.md` 템플릿과 검증루프 · 승인 · 시큐어코딩 체크리스트가 있다. `modern/api` 규칙의 상세는 `templates/CLAUDE.spring.md` 를 따른다.

## 명령

```bash
# 인프라 — down · build · logs 도 반드시 프로필을 붙인다
docker compose --profile modern up -d        # 현행 API 용 MariaDB 만 (3306)
docker compose --profile php up -d           # 문항 은행 레거시 8081 (+ MariaDB)
docker compose --profile thymeleaf up -d     # 과제 배포 레거시 8082 (+ MariaDB)
docker compose --profile mssql up -d         # 성적 집계 레거시 8083 + MS-SQL 1433 (Apple Silicon 에선 느리거나 안 뜸)
docker compose --profile php down && docker compose --profile php up -d   # 시드 상태로 초기화(영구 볼륨 없음)
bash scripts/check-env.sh                    # 환경 셀프 점검

# modern/api — Spring Boot 3.3 · Java 21
cd modern/api && ./gradlew test                                   # DB 없이 통과해야 함(H2)
cd modern/api && ./gradlew test --tests 'com.example.item.UnitServiceTest'   # 테스트 클래스 하나
cd modern/api && ./gradlew bootRun                                # 8080, modern 프로필 DB 필요

# modern/web — React 18 · TypeScript · Vite
cd modern/web && npm ci && npm run dev                            # 5173
cd modern/web && npm run lint && npm run typecheck && npm test
cd modern/web && npx vitest run src/components/ItemTable.test.tsx  # 테스트 파일 하나

# characterization — 동작 보존 스냅샷 테스트
cd characterization && npm ci
cd characterization && npm run baseline -- <item-bank|assignment|grade>   # 스냅샷 새로 찍기
cd characterization && npm test                                   # 레거시 대상
cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test   # 새 API 대상
```

의존성은 `npm install` 이 아니라 `npm ci` 로 설치한다(lock 파일이 바뀌지 않게).

## 아키텍처

### 모듈과 DB
- `php` · `thymeleaf` · `modern` 프로필은 **MariaDB 서비스 하나(`itembank` DB)를 공유**한다. 스키마 · 시드는 `db/mariadb/init/`, 성적 집계용 MS-SQL은 `db/mssql/init/`(프로시저는 `legacy/grade-mssql/sql/` 에서도 읽을 수 있다).
- 실습에서 DB를 직접 볼 때는 읽기 전용 계정(`readonly` / `readonly-pass`, MS-SQL은 `Readonly-pass1`)을 쓴다. 앱의 쓰기 계정은 `docker-compose.yml` · `application.yml` 안에서만 쓴다.
- 도메인 용어: 문항 `item` · 단원 `unit`(코드 `M5-1` = 학년-순번) · 난이도 `level`(1~5) · 태그 `tag` · 학급 `class` · 과제 `assignment` · 배포 `distribution` · 제출 `submission` · 학생 식별자 `STU-<숫자>`.

### modern/api
- 패키지는 도메인 단위(`item`, `assignment`)로 나누고 `Controller → Service → Repository` 순으로만 호출한다. 컨트롤러는 서비스만 호출하고, 응답은 record DTO(`XxxResponse.from(entity)`)다. 엔티티를 컨트롤러 밖으로 내보내지 않는다.
- 예외는 삼키지 않고 던진다. HTTP 변환은 `common/GlobalExceptionHandler` 한 곳에서 한다. `NotFoundException` → 404, 타입 불일치 · 검증 실패 · `IllegalArgumentException` → 400, `IllegalStateException` → 409, 그 밖 → 500. 응답 모양은 `ErrorResponse`(`status`, `message`, `path` …).
- 문항 상태는 `item.status CHAR(1)`: `A` 공개 · `R` 검수 중 · `D` 삭제(`ItemStatus`). **외부에 노출하는 건 `A` 만**이고, 삭제는 플래그가 아니라 상태 코드로 판단한다.
- OSIV가 꺼져 있다(`open-in-view: false`). 연관 엔티티가 필요한 조회는 `@EntityGraph` 나 fetch/집계 JPQL로 한 번에 가져온다. 서비스는 `@Transactional(readOnly = true)` 가 기본이다.
- 현재 시각은 `Clock` 빈(`common/ClockConfig`)을 주입받아 쓴다. 테스트에서 시각을 고정하기 위해서다.
- 커넥션 풀은 일부러 작고(최대 5) 대기 시간이 짧다(3초). 트랜잭션 안에서 외부 호출이나 긴 루프를 돌리지 않는다. `application.yml` 의 DB · 풀 설정은 바꾸지 않는다.
- CORS는 `config/WebConfig` 에서 `http://localhost:5173` 의 `GET /api/**` 만 허용한다.
- 테스트 구성: 컨트롤러는 `@WebMvcTest` + `@MockBean` 서비스, 서비스는 Mockito, 쿼리는 `@DataJpaTest` 로 H2(MariaDB 모드)에 실제 SQL을 보낸다. 모든 테스트에 `@ActiveProfiles("test")` 와 한국어 `@DisplayName` 을 붙이고, 엔티티는 `ItemFixtures` 로 만든다.

### modern/web
- 모든 API 호출은 `src/api/client.ts` 의 `getJson` 을 거친다. 기본 주소는 `http://localhost:8080` 이고, `VITE_API_BASE=""` 로 두면 상대 경로가 되어 Vite 프록시(`/api` → 8080)를 탄다.
- 조회 훅(`useUnits`, `useItem` …)은 모두 `hooks/useApiQuery.ts` 위에 만든다. 이 훅은 `key` 가 바뀌면 다시 조회하고 이전 요청은 `AbortSignal` 로 취소하며, 상태를 `QueryState` 판별 유니언으로 돌려준다. 테스트는 Vitest + jsdom + Testing Library를 쓰고 fetch는 `src/test/mockFetch.ts` 로 목 처리한다.

### characterization
- HTML(레거시)이든 JSON(새 API)이든 응답을 `{status, rows, count, message}` 로 정규화(`lib/normalize.mjs`)해서 같은 스냅샷과 비교한다. 그래서 이관한 API는 필드 이름 · 순서 · 건수가 레거시와 맞아야 한다.
- 테스트는 `fetchNormalized(모듈명, 레거시 경로, 파라미터)` 만 쓰고 주소를 직접 적지 않는다(기본 주소는 `lib/target.mjs` 한 곳). 이관으로 경로가 바뀌면 `PATH_ALIASES` 에 적는다.
- 스냅샷은 커밋하는 "기준"이다. **새 API 대상 테스트가 실패하면 이관 코드를 고친다. 테스트나 스냅샷을 고쳐서 통과시키지 않는다.**

## 완료 기준

- 이관 · 리팩토링 작업은 `characterization` 의 `npm test` 가 전부 통과하기 전에는 완료로 보고하지 않는다.
- 테스트가 실패하면 실패한 케이스와 그 차이를 그대로 보고한다. 뭉뚱그려 "거의 됐다"고 말하지 않는다.
- 테스트를 통과시키려고 `characterization/` 의 테스트 코드나 스냅샷 파일을 고치지 않는다. 스냅샷을 바꿔야 한다고 판단되면 먼저 멈추고 묻는다.
- 레거시 동작이 버그로 보여도 이관 중에는 고치지 않는다. 대신 "의심 동작" 목록으로 따로 보고한다.

## Hook

Hook 스크립트는 `node` 를 직접 부르지 않고 `bash "$CLAUDE_PROJECT_DIR"/scripts/hook-node.sh hooks/<스크립트>.mjs` 로 실행한다. 이 실행기는 node를 찾지 못하면 명령을 막는다(fail-closed).
