스펙(`specs/grading-report-api.md`)을 받아 "테스트 먼저 → 구현 → reviewer·verify 검증" 순서로 과제별 채점 리포트 조회 API를 만들도록 짠 3단계 파이프라인이다(설계: `DESIGN.md`).
실행: `cd specs/starters/java && ./gradlew test` 로 골격을 확인하고(`./gradlew bootRun` 후 `curl -i localhost:8080/health` 가 200), 변경분은 `/verify <범위>` Skill 로 검증한다.
한계: 단계 1(골격 확인)까지만 실행해 리포트 API 기준은 모두 미충족(`CHECK.md`)이며, 현업에서는 교사 ID를 요청에 싣는 임시 방식을 로그인·토큰 인증으로, 비어 있는 local 프로필 시드(`LocalSeedData`)를 실제 학급·제출 데이터 연동으로 바꿔야 한다.
