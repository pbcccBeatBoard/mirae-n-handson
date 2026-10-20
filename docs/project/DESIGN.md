# 실전 프로젝트 설계 — 백엔드 / (이름 미정)

> 잠정판. 완성 기준·범위 칸에 "백엔드"만 적혀 있어, 범위는 `specs/grading-report-api.md`의 핵심 기능(2.1)으로 **잠정** 잡았다. 확정 전에는 4번 질문 1~3에 답이 필요하다.

## 1. 목표
`specs/starters/java` 골격 위에 "과제 하나의 채점 리포트 조회" API 하나를, 스펙 수용 기준(5.1·5.3·5.5·5.6·5.8)을 통과하는 테스트와 함께 40분 안에 만든다.

## 2. 재료
| 재료 | 있음/없음 | 경로 |
|---|---|---|
| Skill: 변경분 검증(verify) | 있음 | `.claude/skills/verify/SKILL.md` |
| Skill: 컨벤션 점검 | 있음 | `.claude/skills/convention-check/SKILL.md` |
| Skill: 모듈 문서화 · RCA · 배치 점검 | 있음(이 트랙에서는 안 씀) | `.claude/skills/document-module/`, `rca/`, `batch-check/` |
| Sub-agent: reviewer, tester | 있음 | `.claude/agents/reviewer.md`, `.claude/agents/tester.md` |
| Sub-agent: iac-reviewer | 있음(백엔드 범위 밖) | `.claude/agents/iac-reviewer.md` |
| Hook: 위험 명령 차단, 시크릿 스캔 | 있음(Bash PreToolUse 연결됨) | `hooks/block-dangerous.mjs`, `hooks/secret-scan.mjs`, `.claude/settings.json` |
| 승인 체크리스트 | 있음 | `docs/approval-checklist.md` |
| 현행 API(참고 구현) | 있음 | `modern/api/src/main/java/com/example/{item,assignment,common}` |
| Java 시작 골격 (`GET /health`만) | 있음 | `specs/starters/java/` |
| 요구사항 스펙 | 있음 | `specs/grading-report-api.md`, `specs/item-tagging-review.md` |
| characterization (item-bank만) | 부분 | `characterization/tests/item-bank.test.js` (assignment · grade 테스트는 없음) |
| pipeline-samples | 있음(배치·Terraform, 백엔드 코드와 무관) | `pipeline-samples/` |
| 프로젝트 설계 문서 | 없음 → 새로 만들 것 | `docs/project/DESIGN.md` (이 파일) |
| 채점 리포트 API 코드 · 테스트 | 없음 → 새로 만들 것 | `specs/starters/java/src/**` 아래 (5번 참고) |
| 프로젝트용 CLAUDE.md | 없음 → 새로 만들 것(필요 시) | 미정(질문 5) |

## 3. 범위
### 한다
1. 과제별 채점 리포트 조회 API (제출·미제출·지각 건수, 평균·최고·최저, 학생별 현황) — 스펙 2.1·2.2
2. 담당 교사만 조회 가능 + 교사 ID 없는 요청 거절 — 스펙 2.3
3. 위 동작을 검증하는 단위·웹 테스트

### 하지 않는다
- 로그인·토큰 인증: 스펙 6번이 범위 밖으로 정했고 교사 ID를 요청에 싣는 것으로 대신한다.
- 채점 · 점수 수정: 채점은 API 밖에서 끝나 있다고 스펙이 전제한다.
- 화면 · CSV/PDF 내보내기: 별도 과제이며 40분 안에 들어가지 않는다.
- `modern/api` · `legacy/` 수정: 실습 대상이라 요청 없이 고치지 않는다는 저장소 규칙이 있다.
- 문항 태깅 검수 스펙: 한 차시에 스펙 하나만 소화할 수 있다.

## 4. 미해결 질문
1. 완성 기준·범위 칸의 원문: 붙여넣은 값이 "백엔드"뿐이다. 원래 표의 "완성 기준"과 "최소 범위로 줄인다면" 내용이 필요하다.
2. 대상 스펙: `grading-report-api.md`와 `item-tagging-review.md` 중 무엇인가? 잠정으로 앞의 것을 골랐다.
3. 이름: 헤더에 넣을 이름이 주어지지 않았다.
4. 스펙의 모호한 곳: 평균 점수 반올림 방식(5.2, "적절히"), 재제출 시 어느 제출을 점수로 볼지(3.4 최신/최고 미정), "빠르게"의 기준, 지각 제출의 시각 경계(마감 시각 정각 포함 여부), 오류 코드. 구현 전에 정해야 한다.
5. 작업 위치: `specs/starters/java`에 직접 만들지, 별도 디렉터리로 복사할지. 프로젝트용 CLAUDE.md가 필요한지.
6. 스펙의 데이터(학급·교사·학생·제출)를 어디서 가져오는가: 시작 골격의 `LocalSeedData.java`가 이를 포함하는지 아직 읽어 보지 않았다.
7. `modern/api`의 `ReportService`·`ReportController`가 이 스펙과 겹치는지 읽어 보지 않았다.

## 5. 단계
**단계 1. 스펙 확정과 골격 확인 (약 8분)**
- 할 일: 4번 질문 1·2·4에 답을 정해 이 문서에 반영하고, 골격이 빌드되는지 확인한다.
- 만들거나 고칠 파일: `docs/project/DESIGN.md`
- 확인 방법: `cd specs/starters/java && ./gradlew test` 통과, 서버를 띄우고 `curl -i localhost:8080/health` 가 200 · `{"status":"ok"}`.

**단계 2. 테스트 먼저, 그다음 리포트 조회 구현 (약 22분)**
- 할 일: 수용 기준 5.1·5.3·5.5·5.6·5.8을 실패하는 테스트로 먼저 쓰고, 컨트롤러→서비스→저장소 순으로 구현한다.
- 만들거나 고칠 파일: `specs/starters/java/src/main/java/com/example/specproject/` 아래 리포트 컨트롤러·서비스·DTO, `src/test/java/...` 아래 테스트
- 확인 방법: `cd specs/starters/java && ./gradlew test` 전부 통과. 교사 ID를 실은 `curl` 로 리포트 JSON에 제출 건수·평균이 보이는지 확인.

**단계 3. 검증 (약 10분)**
- 할 일: reviewer 서브에이전트로 읽기 전용 리뷰를 하고, 인젝션 · 권한 누락(5.6·5.7)을 점검해 지적 사항을 반영한다.
- 만들거나 고칠 파일: 리뷰 지적에 따른 단계 2의 파일
- 확인 방법: `/verify` 결과가 승인이고, `./gradlew test` 가 다시 통과한다.

## 6. 완성 기준 (Day 4-1에서 점검)
- [ ] `cd specs/starters/java && ./gradlew test` 가 종료 코드 0이다.
- [ ] 담당 교사 ID로 과제 `A1` 리포트를 요청하면 200이고, 응답 JSON에 제출 건수·미제출 건수·평균 점수가 있다.
- [ ] 제출이 0건인 과제를 요청하면 200이고 제출 건수가 0이다(오류가 나지 않는다).
- [ ] 담당하지 않는 학급 과제를 요청하면 403(또는 4번 질문 4에서 정한 코드)이고, 교사 ID가 없으면 거절된다.
- [ ] 존재하지 않는 과제 ID를 요청하면 404이다.
- [ ] 학생별 현황에 미제출 학생이 행으로 나오고, 학생 ID 오름차순이다.
