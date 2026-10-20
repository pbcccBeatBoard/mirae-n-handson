# 전체 개발자 적용 규칙 후보 (pbccc)

`.claude/settings.json`(permissions · hooks)과 `hooks/` 의 규칙을 전체 개발자에게 거는 후보로 정리했다. 기존 파일은 고치지 않았다.

- 대상 스택: springBoot · python · javascript · sql · infra
- 스택 코드는 `spring` · `react` · `php` · `data-infra` 중에서만 골랐다. python · sql · infra 는 `data-infra` 로 묶었다(근거: `templates/CLAUDE.data-pipeline.md` 가 Python 서비스 · 배치 적재를, `templates/CLAUDE.iac.md` 가 Terraform 을 다룬다). javascript 는 `react`, springBoot 는 `spring` 이다. `php` 규칙은 이 저장소 설정에 따로 없다.
- 기준 시점: 작업 트리. `.claude/settings.json` 은 커밋되지 않은 수정 상태이고 `hooks/*.mjs` 는 git 에 추적되지 않는다.

## 규칙 표

| 규칙 | 종류(deny / ask / allow / Hook) | 적용 범위(공통 또는 스택 코드) | 막는 사고 또는 허용 이유 |
|---|---|---|---|
| `Bash(rm -rf *)` | deny | 공통 | 경로 하나가 틀리면 작업 폴더 전체가 사라진다. |
| `Bash(sudo *)` | deny | 공통 | 관리자 권한으로 시스템을 바꾸는 명령을 막는다. |
| `Bash(git push --force *)` · `Bash(git push -f *)` | deny | 공통 | 원격 이력을 덮어써 다른 사람의 커밋이 사라진다. |
| `Bash(git reset --hard *)` | deny | 공통 | 커밋하지 않은 작업이 복구 불가능하게 사라진다. |
| `Bash(*prod-db*)` · `Bash(*prod.db*)` | deny | 공통 | 운영 DB 호스트에 접속하는 명령을 막는다. 호스트 이름 패턴에 의존한다. |
| `Bash(curl *)` · `Bash(wget *)` | deny | 공통 | 내려받은 스크립트 실행과 외부 전송을 막는다. 정당한 curl 도 함께 막힌다. |
| `Read(./.env)` · `Read(./.env.*)` · `Read(./secrets/**)` | deny | 공통 | 읽은 시크릿이 대화 컨텍스트와 로그에 남는다. |
| `Read(**/*.pem)` · `Read(~/.ssh/**)` · `Read(~/.aws/**)` | deny | 공통 | 개인키와 클라우드 자격 증명이 컨텍스트에 노출된다. |
| `Edit(./.env)` · `Edit(./.env.*)` · `Edit(./secrets/**)` | deny | 공통 | 시크릿 파일을 AI가 덮어쓰는 것을 막는다. |
| `mcp__github__delete_file` · `mcp__github__merge_pull_request` | deny | 공통 | 원격 저장소의 파일 삭제와 PR 머지는 사람이 한다. |
| `Bash(git push *)` | ask | 공통 | 원격에 올리기 전에 사람이 확인한다(`--force` 는 위 deny 가 먼저 막는다). |
| `mcp__github__create_pull_request` · `mcp__github__push_files` | ask | 공통 | 원격에 쓰는 작업이라 확인 후 진행한다. |
| `mcp__dbhub` · `mcp__dbhub-mssql` | ask | 공통 | DB 접속 MCP 를 쓸 때마다 대상 DB 와 쿼리를 확인한다. |
| `Bash(npm install *)` · `Bash(npm i *)` · `Bash(npm ci *)` | ask | react | 의존성 추가 · 변경은 사람이 검토한다(악성 · 취약 패키지 유입 방지). |
| `Bash(pip install *)` | ask | data-infra | Python 패키지 설치를 사람이 검토한다. |
| `Bash(docker compose down *)` · `Bash(docker compose --profile *)` | ask | data-infra | 컨테이너를 내리거나 프로필을 올릴 때(영구 볼륨이 없어 데이터가 초기화됨) 확인한다. |
| `Bash(npm test *)` · `Bash(npm run test *)` · `Bash(npm run lint *)` · `Bash(npm run typecheck *)` · `Bash(npm run build *)` | allow | react | 읽기 · 검증 성격의 명령이라 매번 묻지 않는다. |
| `Bash(./gradlew test *)` · `Bash(./gradlew check *)` · `Bash(./gradlew build *)` | allow | spring | 테스트 · 검증 명령이라 매번 묻지 않는다. 테스트는 DB 없이(H2) 통과해야 한다. |
| `Bash(git status *)` · `Bash(git diff *)` · `Bash(git log *)` | allow | 공통 | 읽기 전용 git 조회다. |
| `Bash(git add *)` · `Bash(git commit *)` | allow | 공통 | 로컬 작업 단위 저장이라 허용한다. 시크릿은 아래 `secret-scan` Hook 이 막는다. |
| `WebFetch(domain:docs.spring.io)` · `WebFetch(domain:docs.gradle.org)` | allow | spring | 공식 문서 조회라 허용한다. |
| `WebFetch(domain:developer.mozilla.org)` | allow | react | 웹 표준 공식 문서 조회라 허용한다. |
| `mcp__github__get_file_contents` · `list_pull_requests` · `pull_request_read` · `list_issues` | allow | 공통 | GitHub 읽기 전용 조회다. |
| `mcp__item-bank__search_items` | allow | 공통 | 문항 검색 읽기 전용 MCP 다(이 저장소 도메인 전용). |
| `hooks/block-dangerous.mjs` — `rm -rf` 규칙 | Hook | 공통 | `/bin/rm -rf` 같은 경로 우회와 `-fr` · `--recursive --force` 변형까지 잡는다. |
| `hooks/block-dangerous.mjs` — `DROP TABLE` · `DROP DATABASE` 규칙 | Hook | 공통 | 시드로 되돌릴 수 없는 테이블 · DB 삭제를 막는다. |
| `hooks/block-dangerous.mjs` — `prod[-.]db` 규칙 | Hook | 공통 | deny 의 운영 DB 패턴을 한 번 더 막는다. |
| `hooks/secret-scan.mjs` — AWS Access Key ID · 하드코딩 password · PEM 개인키 | Hook | 공통 | `git commit` 직전 스테이징된 추가 줄에서 시크릿이 커밋 이력에 남는 것을 막는다. |
| `scripts/hook-node.sh` 로 두 Hook 실행(`PreToolUse` · `Bash`) | Hook | 공통 | node 를 찾지 못하면 명령을 막는다(fail-closed). Hook 이 조용히 꺼지는 것을 막는다. |

## 추가 제안 (내 스택에 필요한데 지금 없는 규칙)

| 규칙 | 종류 | 적용 범위 | 이유 |
|---|---|---|---|
| `TRUNCATE` 차단 (`block-dangerous.mjs` 에 패턴 추가) | Hook | 공통 | sql. `DROP` 만 막고 `TRUNCATE` 는 막지 않는다(시큐어코딩 체크리스트 2번 중 미구현 부분). |
| 조건 없는 `DELETE FROM` · `UPDATE` 감지 | Hook | 공통 | sql. WHERE 없는 쓰기가 전체 테이블을 지운다. 정규식 오탐이 있을 수 있어 ask 로 시작하는 편이 안전하다. |
| `terraform apply` · `terraform destroy` 에 `ask`, `-auto-approve` 에 `deny` | ask / deny | data-infra | infra. plan 과 apply 를 분리하고, 승인 없이 인프라를 바꾸거나 지우는 것을 막는다. |
| `kubectl delete *` · `aws * delete*` 계열 | ask | data-infra | infra. 클라우드 자원 삭제는 되돌리기 어렵다. 쓰는 도구에 맞게 패턴을 확정한다. |
| `pip3 install` · `python3 -m pip install` · `uv pip install` · `poetry add` | ask | data-infra | python. 현재 `pip install *` 패턴은 이 변형을 잡지 못한다. |
| `Bash(npx *)` · `yarn add` · `pnpm add` · `npm publish` | ask / deny | react | javascript. 원격 패키지를 바로 실행하거나 추가하고, 패키지를 외부에 게시하는 경로를 막는다. |
| `Bash(cat .env*)` · `grep … .env*` 등 Bash 로 시크릿 파일 읽기 | Hook | 공통 | `Read` deny 는 도구 호출만 막고 Bash `cat .env` 는 막지 않는다. |
| `chmod 777` 차단 | Hook | 공통 | 시큐어코딩 체크리스트 5번이 미구현이다. |
| `git clean -fd` · `git checkout .` · `git branch -D` | ask | 공통 | 추적되지 않는 파일과 미커밋 변경을 복구 없이 지운다. |
| `docker compose down -v` · `docker volume rm` · `docker system prune` | ask | data-infra | 컨테이너 데이터를 지운다. 현재 `down *` ask 는 있지만 볼륨 삭제 명령은 따로 없다. |
| `pytest *` · `ruff check *` · `mypy *` | allow | data-infra | python. 읽기 · 검증 명령이라 spring · react 의 test/lint 허용과 대칭이 된다. |
| `WebFetch(domain:docs.python.org)` · `registry.terraform.io` | allow | data-infra | python · infra 공식 문서 조회다. 지금은 spring · MDN 만 허용되어 있다. |

## 주의 (다른 스택에 그대로 걸면 일을 막을 수 있는 규칙)

- **`Bash(curl *)` · `Bash(wget *)` deny:** `templates/CLAUDE.data-pipeline.md` 의 헬스체크 명령(`curl … http://localhost:8000/health`)과 레거시 8081~8083 확인 같은 로컬 점검까지 막힌다. 로컬 주소만 허용하려면 `allow` 로 `curl http://localhost*` 예외를 두거나 deny 범위를 줄여야 한다.
- **`Read(./.env.*)` · `Edit(./.env.*)` deny:** `.env.example` · `.env.sample` 같은 템플릿 파일도 막는다. python · infra 는 이런 파일로 설정 규칙을 공유하는 경우가 많다.
- **`Read(**/*.pem)` deny:** infra 에서 다루는 공개 인증서(`.pem`)도 막는다.
- **`Bash(sudo *)` deny:** infra · 로컬 환경 구성에서 권한 상승이 필요한 작업이 막힌다. 필요하면 사람이 직접 실행한다.
- **`scripts/hook-node.sh` (fail-closed):** node 가 없는 python · infra 개발자의 환경에서는 Bash 명령이 모두 막힌다. 전체 적용 전에 node 설치를 전제로 할지 정해야 한다.
- **`Bash(npm ci *)` ask:** 이 저장소는 `npm ci` 로 설치하라고 하는데(`CLAUDE.md`) 매번 확인이 뜬다. react 쪽 마찰이 크다.
- **`Bash(docker compose --profile *)` ask:** spring · php · thymeleaf 개발자도 DB 를 올릴 때마다(`--profile modern up -d` 등) 확인이 뜬다. `data-infra` 로 분류했지만 실제로는 저장소 전체에 영향을 준다.
- **`Bash(git commit *)` allow:** `CLAUDE.md` 는 "커밋 전에 반드시 확인"이라고 하는데 allow 라서 프롬프트가 없다. 확인은 모델의 지시 이행에만 달려 있다. 강제하려면 ask 로 바꿔야 한다.
- **`secret-scan` 의 `password\s*=\s*['"]` 패턴:** 테스트 코드의 `String password = "test"` 나 Python `password = "x"` 같은 픽스처에도 걸려 spring · python 커밋이 막힐 수 있다. YAML 의 `password:` 는 걸리지 않는다. 또한 `git commit` 으로 시작하는 명령만 검사한다.
- **`prod[-.]db` 패턴 (Hook · deny 공통):** `nonprod-db` 처럼 이름 안에 `prod-db` 가 들어 있으면 비운영 DB 도 막는다. 다른 팀의 운영 호스트 이름 규칙과 맞지 않으면 운영 DB 를 못 막는다.
- **`DROP TABLE` Hook:** 명령 문자열에 `DROP TABLE` 이 있으면 `echo` 같은 무해한 명령도 막는다. sql · data 작업자가 로컬 개발 DB 의 임시 테이블을 정리하는 명령도 막힌다.
- **`WebFetch` allow 목록:** spring · MDN 도메인만 있어서 python · infra 문서 조회는 매번 확인이 뜬다(막지는 않는다).
