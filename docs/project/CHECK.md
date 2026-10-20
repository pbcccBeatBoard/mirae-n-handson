# 완성 기준 점검 — 백엔드 / (이름 미정)

1. 트랙 완성 기준 ①: 충족 (`./gradlew test` 종료 코드 0)
   증거(실행한 명령과 결과 한 줄): `cd specs/starters/java && ./gradlew test --rerun-tasks -q` → 종료 코드 0. `curl -i localhost:8080/health` → 200, `{"status":"ok"}`. 단, 시작 골격 상태의 결과이고 리포트 API 테스트는 아직 없음.
2. 트랙 완성 기준 ②: 미충족 (리포트 API 기준 5개 모두 미구현 — 과제 A1 리포트 200, 제출 0건 200, 403/400, 404, 미제출 학생 행·ID 오름차순)
   증거(파일 경로 또는 PR 주소): 없음. DESIGN.md "5. 단계" 중 단계 1만 실행했고 단계 2(구현)·3(검증)은 하지 않음.
3. /verify 판정: 승인 (심사 대상 코드 없음)
   증거(판정 요약 한 줄, 치명 건수): `day4-start..HEAD` 변경이 제외 대상 DESIGN.md 하나뿐이라 reviewer · tester 를 호출하지 못한 형식적 승인, 치명 0건. 단계 2 이후 다시 돌려야 함.

범위를 줄였는가: 예 — 뺀 항목과 이유는 DESIGN.md "3. 범위 — 하지 않는다"에 있음
팀 저장소에 가져갈 파일: docs/project/DESIGN.md, docs/project/README.md (리포트 API 코드는 아직 없음)
