# 장애 A (커넥션 풀) 원인 분석

대상 로그: `incident-logs/a-connection-pool/` · 모든 시각의 기준은 **KST(UTC+9)**.
이 문서는 "수집 범위"만 다룬다. 원인 추측은 하지 않는다.

## 1. 수집 범위

### 1-1. 파일별 기록 기간 · 시각 형식 · 타임존

| 파일 | 줄 수 | 기록 기간 (KST) | 시각 형식 | 타임존 | KST 변환 |
|---|---:|---|---|---|---|
| `app.log` | 5,371 (타임스탬프 줄 1,927 + 스택트레이스 등 3,444) | 09-16 06:43:56 ~ 15:29:51 | `2026-09-16T06:43:56.753+09:00` (ISO-8601, 밀리초) | 오프셋 명시 `+09:00` | 변환 불필요 |
| `nginx-access.log` | 21,086 | 09-16 06:43:56 ~ 15:29:59 | `[16/Sep/2026:06:43:56 +0900]` | 오프셋 명시 `+0900` | 변환 불필요 |
| `nginx-error.log` | 168 | 09-16 09:12:03 ~ 14:51:47 | `2026/09/16 09:12:03` | **표기 없음** → KST로 판단 | 변환 불필요(근거는 아래) |
| `mariadb-slow.log` | 21 (슬로우 쿼리 3건) | 09-16 09:14:09 ~ 14:37:52 | 헤더 `# Time: 260916  0:14:09` (YYMMDD H:MM:SS) | **UTC** | **+9시간** |

### 1-2. KST 변환 규칙

- `app.log`, `nginx-access.log`: 그대로 사용한다.
- `nginx-error.log`: 타임존 표기가 없다. `14:51:47` 의 upstream timeout(`GET /api/classes/3/report`, client 10.20.3.17)이 `nginx-access.log` 의 같은 초 `[16/Sep/2026:14:51:47 +0900]` 504 응답과 일치하므로 KST로 본다. (간접 근거이므로 확정 전 서버 `nginx` 컨테이너의 TZ 설정으로 재확인 필요)
- `mariadb-slow.log`: **UTC → KST는 +9시간**. 각 항목의 `SET timestamp=<epoch>` 으로 확인했다.
  - 헤더 `# Time` 은 쿼리 **종료** 시각이고, `SET timestamp` 는 **시작** 시각이다(`Query_time` 만큼 차이).
  - 예: `# Time: 260916 5:37:52` + `SET timestamp=1789537062`(= 05:37:42 UTC) + `Query_time: 9.81`.

| 슬로우 쿼리 | 시작 (KST) | 종료 (KST) | Query_time | Lock_time | Rows_examined |
|---|---|---|---:|---:|---:|
| 1 | 09:14:07 | 09:14:09 | 2.32s | 0.00s | 41,822 |
| 2 | 11:02:40 | 11:02:42 | 2.04s | 0.91s | 5 |
| 3 | 14:37:42 | 14:37:52 | 9.81s | 0.00s | 1,284,310 |

### 1-3. 이상 징후 건수 (분 단위) 와 구간 후보

"평소" 기준은 06:43 ~ 14:36 구간(약 8시간)이다.

**평소 수준 (14:37 이전)**
- `app.log`: `state conflict` WARN 20건(업무상 409, 시간대 전체에 흩어져 있음)과 404 INFO 외에 WARN/ERROR **0건**
- `nginx-access.log`: 5xx **0건** (400 · 404 · 409 는 평소에도 있음)
- `nginx-error.log`: `[error]` **0건**, `[warn]`/`[info]` 5건(버퍼링 · 긴 헤더 · keepalive 종료, 타임아웃 아님)
- 분당 요청 수: 시간당 약 1,700 ~ 2,700건 (분당 약 30 ~ 45건)

**이상 구간 (분 단위, 14시대)** — `app` = `state conflict` 를 제외한 WARN+ERROR (Hikari leak WARN, SQL ERROR, unhandled exception ERROR 포함)

| 분 (14:xx) | 37 | 38 | 39 | 40 | 41 | 42 | 43 | 44 | 45 | 46 | 47 | 48 | 49 | 50 | 51 |
|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|
| app.log WARN+ERROR | 1 | 2 | 3 | 17 | 31 | 16 | 44 | 17 | 33 | 24 | 24 | 22 | 33 | 11 | 3 |
| nginx-access 5xx | 0 | 0 | 0 | 1 | 9 | 20 | 17 | 13 | 27 | 26 | 14 | 15 | 23 | 18 | 3 |
| nginx-error `[error]` | 0 | 0 | 0 | 0 | 3 | 18 | 7 | 11 | 19 | 22 | 9 | 11 | 16 | 18 | 3 |

- 14시대 합계: app WARN+ERROR 약 289건(`state conflict` 포함), nginx 5xx 186건(500 49 · 502 82 · 504 55), nginx `[error]` 137건(upstream timeout 55건 포함).
- 5xx 186건은 **전부** 14:40 ~ 14:51 에 있고, 14:30 ~ 14:39 에는 0건이다.
- 14:51 이후(~15:29)는 세 파일 모두 이상 징후가 없다. 마지막은 `app.log` 14:51:37(Hikari leak WARN), `nginx-access` 14:51:47(504), `nginx-error` 14:51:47(timeout).

**시작 · 끝 시각 제안**

| 구분 | 시각 (KST) | 근거 |
|---|---|---|
| 최초 이상 징후 | **14:37:42** | 슬로우 쿼리 3(시작 시각, UTC 05:37:42). 앱 쪽 첫 징후는 14:37:52 Hikari leak WARN |
| 사용자 영향(5xx) 시작 | 14:40:52 | `nginx-access` 첫 500 |
| 증상 종료 | **14:51:47** | nginx 마지막 504 · timeout. 앱 마지막 WARN 은 14:51:37 |

→ 분석 핵심 구간은 **14:37:42 ~ 14:51:47** (약 14분).

### 1-4. 앞뒤 여유 제안 (앞쪽을 더 넉넉히)

| 구분 | 여유 | 시각 (KST) | 이유 |
|---|---|---|---|
| 앞 | **-20분** | 14:17:00 | 최초 징후(14:37:42) 이전의 변화를 봐야 한다. 이번 로그에서 5xx · timeout 은 14:40 에야 보이지만, 풀 고갈류 장애는 점진적으로 쌓이다가 터지는 경우가 많아 징후가 5xx 보다 먼저 나타난다. 같은 이유로 슬로우 로그는 앞 20분으로 충분하다. |
| 뒤 | **+10분** | 15:02:00 | 마지막 5xx(14:51:47) 이후 복구(응답 정상화 · 풀 반환)가 확인되면 충분하다. 15:29 까지 로그가 있으므로 여유 안에서 확인 가능하다. |

→ **수집 범위 제안: 14:17 ~ 15:02 KST (약 45분)**. 앞 약 20분 : 증상 14분 : 뒤 약 10분.

**범위 밖이지만 기록해 두는 것**
- 슬로우 쿼리 1·2(09:14, 11:02)는 위 범위 밖이다. 현재 근거로는 이번 장애와의 관계를 판단하지 않는다. 필요하면 후속 단계에서 "범위 밖 선행 징후" 로 따로 본다.
- 14:17 이전과 비교용으로 평소 구간 샘플(예: 13:00 ~ 14:00)을 하나 정해 두면 이후 분석에서 대조가 쉽다.

### 1-5. 한계 · 확인 필요

- `nginx-error.log` 의 KST 가정은 한 건의 교차 일치에 근거한다. 서버 설정으로 확정해야 한다.
- `app.log` 에는 스택트레이스 줄(타임스탬프 없음) 3,444줄이 있어, 분 단위 집계는 타임스탬프가 있는 줄만 대상으로 했다.
- 로그 전체를 읽지 않고 `wc` · `head` · `tail` · `grep` 으로만 집계했다. 건수는 이 방식의 값이다.

## 2. 타임라인

대상: **2026-09-16 14:17 ~ 15:02 KST** (1절 수집 범위). 네 파일을 KST 한 시간축으로 합쳤다.
이 절은 로그에 있는 사건만 적고, 원인 해석은 하지 않는다.

**표기 규칙**
- 출처는 `app` = `app.log`, `acc` = `nginx-access.log`, `err` = `nginx-error.log`, `slow` = `mariadb-slow.log` 의 줄 번호다.
- 반복 메시지는 "첫 발생 시각, 이후 N건/분"으로 묶었고 출처에는 첫 줄과 마지막 줄을 적었다.
- `acc` 와 `slow` 는 초 단위, `app` 은 밀리초 단위, `err` 는 초 단위 시각이다. 파일이 달라 몇 초 이내로 겹치면 **(선후 불확실)** 로 표시했다.
- 원문 발췌는 80자 이내이며, 길면 `…` 로 줄였다.

### 2-1. 범위 앞부분 (14:17 ~ 14:37) — 평소 수준

이 구간의 `app` WARN/ERROR 는 `state conflict` 뿐이고, 5xx · `[error]` 는 0건이다. 요청 수는 분당 32~56건이다.

| 시각 | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 14:17:29 | app:1174, acc:17880 | 학급 1 리포트 조회(200). 14:18:13 에 한 번 더(app:1175, acc:17914). 이 구간의 리포트 호출은 이 2건뿐 | `built class report for class 1: 3 distributions, 10 submissions` |
| 14:19:38 | app:1182, acc:17977 | 재배포 POST 성공(distribution 7) | `redistributed distribution 7 (assignment 5, class 3) reason=STU-1001 결석으로 재배포` |
| 14:33:45 | app:1229, acc:18610 | 재배포 POST 가 409. 같은 메시지가 하루 종일 20건 있는 평소 사건 | `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=2` |

### 2-2. 이상 징후 시작 (14:37 ~ 14:40) — 첫 5xx 이전

| 시각 | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 14:37:42 (시작) ~ 14:37:52 (종료) | slow:15-21 | 슬로우 쿼리 3번째 건. 10초가량 걸리고 조회 행 수가 약 128만. 대상은 `submission` 의 `distribution_id=5`. 시각은 `SET timestamp`(시작)와 `# Time`(종료)에서 UTC→KST 변환 | `Query_time: 9.812417  Lock_time: 0.000071  Rows_sent: 4  Rows_examined: 1284310` |
| 14:37:52.199 | app:1243 | **Hikari 커넥션 누수 경고 첫 발생**(thread exec-6, `Connection@6e8f14d3`). 슬로우 쿼리 종료 시각과 같은 초라 **(선후 불확실)** | `Connection leak detection triggered for org.mariadb.jdbc.Connection@6e8f14d3` |
| 14:38:01.900 | acc:18801, app:1258 | 이 구간 이후 리포트 호출이 늘어나는 첫 건(학급 3, 200). 같은 초 `acc` 와 `app` 은 시각 차 1초 미만 | `built class report for class 3: 3 distributions, 10 submissions` |
| 14:38:01.902 | app:1259 | 14:37:52 에 보고된 누수 커넥션이 풀에 반환됨(thread exec-6). 직전 줄(app:1258)과 같은 스레드 | `Previously reported leaked connection … was returned to the pool (unleaked)` |
| 14:38:29 | acc:18817, app:1278 | 재배포 POST 성공(distribution 5, `reason=null`). 다른 성공 건은 사유가 있다. 슬로우 쿼리 3번째 건의 `distribution_id=5` 와 같은 값이다(관찰만 기록) | `redistributed distribution 5 (assignment 3, class 3) reason=null` |

**리포트 호출 증가** — 14:38 에 3건/분으로 늘기 시작해 14:40~14:50 에 분당 10~19건(최대 19건, 14:43 · 14:46), 14:51 에 5건으로 줄어든다. 이 호출은 `GET /api/classes/{1,2,3}/report` 이다. 전체 요청 수(분당 34~58건)는 구간 내내 그대로이고 14:38~14:51 의 리포트 호출만 늘었다. 하루 중 이 구간 밖의 리포트 호출은 53건이다.

**누수 경고 반복** — 14:37:52 첫 발생(app:1243), 이후 14:38 2건, 14:39 3건, 14:40~14:50 분당 9~14건(평균 약 11건), 14:51 3건. 마지막은 14:51:37(app:5251). 합계 134건. 한 건마다 반환(`Previously reported …`) INFO 가 134건 따라 나온다(마지막 14:51:49, app:5267). 누수로 보고된 커넥션 ID 는 5종류(`2ac19f07` · `3f6c2a91` · `5b0e9e0c` · `6e8f14d3` · `71d4b8e2`)이다.

### 2-3. 사용자 영향 구간 (14:40:52 ~ 14:51:47)

| 시각 | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 14:40:52 | acc:18918 | **첫 5xx**. `/api/units/M6-2/items` 500 | `"GET /api/units/M6-2/items HTTP/1.1" 500 162` |
| 14:40:52.319 | app:1521-1523 | **풀 대기 시간 초과 첫 발생**(WARN+ERROR) 직후 같은 경로의 unhandled exception. 같은 요청으로 보이나 acc 가 초 단위라 **(선후 불확실)** | `itembank-pool - Connection is not available, request timed out after 3000ms` |
| (같은 줄) | app:1522 | 메시지 안에 풀 상태가 기록되어 있다 | `total=5, active=5, idle=0, waiting=4` |
| 14:41:06 | acc:18933, err:6 | **첫 504**와 nginx `upstream timed out` 첫 발생. 같은 초라 **(선후 불확실)** | `upstream timed out (110: Connection timed out) while reading response header` |
| 14:42:01 | err:10, acc:18970 | nginx `upstream server temporarily disabled` 경고 첫 발생. 같은 초에 첫 502(`/api/classes/2/report`)가 기록되어 **(선후 불확실)** | `upstream server temporarily disabled while reading response header` |
| 14:42:01 | err:11 | nginx `connect() failed (111: Connection refused)` 첫 발생 | `connect() failed (111: Connection refused) while connecting to upstream` |
| 14:42:04 | err:12 | nginx `no live upstreams` 첫 발생(`/api/units/M6-2/items`) | `no live upstreams while connecting to upstream` |
| 14:42:42 | acc:18998, app:2174 | 재배포 POST 409(distribution 1). 평소와 같은 메시지 | `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=1` |
| 14:44:19 | acc:19078, app:2929 | 재배포 POST 성공(distribution 8) | `redistributed distribution 8 (assignment 6, class 2) reason=보강 수업 후 재배포` |
| 14:49:51 | app:4980-4981 | **앱 쪽 마지막 풀 대기 시간 초과** | `unhandled exception on /api/units/M5-3/items` |
| 14:50:22 | acc:19321, app:5107 | 재배포 POST 409(distribution 3). 14:51:52(acc:19386, app:5268), 14:52:09(acc:19401, app:5269)에도 409 | `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=3` |
| 14:51:02 | err:166 | nginx `no live upstreams` 마지막 | `no live upstreams while connecting to upstream` |
| 14:51:37 | app:5251 | 누수 경고 마지막 | `Connection leak detection triggered for org.mariadb.jdbc.Connection@6e8f14d3` |
| 14:51:47 | acc:19381, err:168 | **마지막 504**와 마지막 nginx 에러. 같은 초라 **(선후 불확실)** | `"GET /api/classes/3/report HTTP/1.1" 504 167` |

**반복 메시지 묶음 (분당 건수)**

| 메시지 | 첫 발생 | 이후 분당 건수 | 합계 · 마지막 |
|---|---|---|---|
| app `Connection is not available, request timed out after 3000ms` ERROR + `SQL Error: 0` WARN + `unhandled exception on …` ERROR(건당 1줄씩, 같은 수) | 14:40:52 (app:1521) | 14:40=1, 41=6, 42=2, 43=10, 44=2, 45=8, 46=4, 47=5, 48=4, 49=7 | 각 49건 · 14:49:51 (app:4981) |
| acc 500 (분당 건수가 위 app 의 unhandled exception 과 같음) | 14:40:52 (acc:18918) | 14:40=1, 41=6, 42=2, 43=10, 44=2, 45=8, 46=4, 47=5, 48=4, 49=7 | 49건 · 14:49 |
| acc 502 | 14:42:01 (acc:18970) | 14:42=13, 43=2, 44=5, 45=14, 46=16, 47=6, 48=6, 49=8, 50=11, 51=1 | 82건 · 14:51 |
| acc 504 / err `upstream timed out` | 14:41:06 (acc:18933, err:6) | 14:41=3, 42=5, 43=5, 44=6, 45=5, 46=6, 47=3, 48=5, 49=8, 50=7, 51=2 | 각 55건 · 14:51:47 (acc:19381, err:168) |
| err `no live upstreams` | 14:42:04 (err:12) | 14:42=11, 43=1, 44=4, 45=12, 46=14, 47=5, 48=5, 49=6, 50=9, 51=1 | 68건 · 14:51:02 (err:166) |
| err `connect() failed (111: Connection refused)` | 14:42:01 (err:11) | 14:42=2, 43=1, 44=1, 45=2, 46=2, 47=1, 48=1, 49=2, 50=2 | 14건 · 14:50:52 (err:159) |
| err `upstream server temporarily disabled` WARN | 14:42:01 (err:10) | 14:42=2, 43=1, 44=4, 45=3, 46=3, 47=1, 48=4, 49=4, 50=4 | 26건 · 14:50:52 (err:158) |

- 502 82건은 err 의 `connect() failed` 14건 + `no live upstreams` 68건과 건수가 같다(대응 관계는 이 절에서 단정하지 않는다).
- 504 55건과 err `upstream timed out` 55건도 건수가 같다.
- `app` 의 풀 대기 시간 초과는 14:49:51 에 마지막이지만, `acc` 의 5xx 는 14:51:47 까지 이어진다.

### 2-4. 범위 뒷부분 (14:52 ~ 15:02)

| 시각 | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 14:52:09 | acc:19401, app:5269 | 재배포 POST 409(distribution 4). 이 시각 이후 이 구간에서 5xx 없음 | `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=4` |
| 14:52 ~ 15:02 | acc:19382-19842 | 5xx 0건, 분당 요청 수 35~47건으로 평소와 같음 | (건수 집계) |
| 14:59 · 15:01 | acc | 리포트 호출 각 2건 · 1건(전부 200) | `/api/classes/{n}/report` |

### 2-5. 알려진 한계

- `acc` 의 줄 번호는 응답 완료 시각 기준이라 요청이 접수된 시각과 다르다. 느린 요청은 `acc` 에서 `app` 보다 늦게 나타날 수 있다.
- 같은 초 안의 선후는 `app`(밀리초)만 정확히 안다. 파일 사이는 위 표의 (선후 불확실) 표시를 따른다.
- 이 절은 사건만 나열했다. 사건 사이의 관계나 원인은 다음 단계에서 다룬다.

## 3. 가설과 검증

이 절은 가설만 세운다. **어느 가설이 맞는지는 판정하지 않는다.** 지지 근거는 2절 타임라인과, 가설을 세우려고 추가로 확인한 코드 · 설정 · 로그 사실이다. 출처 표기는 2절을 따르고 `app` · `acc` · `err` · `slow` 는 각 로그의 줄 번호다.

### 3-1. 증상과 원인을 구분한다

가장 많이 나온 메시지는 증상으로 본다. 아래는 원인이 아니라 **관측된 결과**다.

| 증상 | 규모 | 비고 |
|---|---|---|
| `Connection is not available, request timed out after 3000ms (total=5, active=5, idle=0 …)` | 49건 (app:1521~4981) | 풀의 5개가 모두 사용 중이라는 결과. 누가 왜 오래 쥐고 있었는지는 이 메시지에 없다 |
| 500 / 502 / 504 | 49 / 82 / 55건 | 풀 고갈의 결과(500) 또는 그 뒤에 nginx 가 겪은 결과(502 · 504)로 보이나 이 절에서 확인 대상이다 |
| `unhandled exception on …` | 49건 | 위 풀 대기 시간 초과와 분당 건수가 같다 |

원인 가설은 "커넥션을 **오래 쥔 쪽**이 누구이고 왜 오래 쥐었는가", 그리고 "그 부담이 **왜 14:37~14:38 에** 시작되었는가"를 설명해야 한다.

### 3-2. 가설을 세우는 데 쓴 추가 사실

타임라인 밖에서 확인한 사실이다. 가설에 쓰기 전 이 사실 자체도 아래 검증 대상이다.

- **누수 경고의 출처**: `app.log` 에서 누수 경고 134건의 스택을 보고, 스택에서 처음 나오는 `com.example` 프레임을 세면 134건 모두 `ReportService.buildClassReport` 이다(`awk '/stack trace follows/{s=1;next} s&&/com\.example/{print $2; s=0}' app.log | sort | uniq -c`). 호출 경로는 `ReportController.classReport(ReportController.java:22)` 이다.
- **누수 임계값**: `modern/api/src/main/resources/application.yml:18` `leak-detection-threshold: 10000`(10초), `:14` `maximum-pool-size: 5`, `:15` `connection-timeout: 3000`.
- **획득 시각의 역산**(로그에 기록된 값이 아니라 계산한 값): 첫 누수 경고 14:37:52.199(app:1243) − 10초 ≈ 14:37:42.2. 이 시각은 슬로우 쿼리 시작 14:37:42(slow:20)와 같은 초다. 같은 스레드(exec-6)가 14:38:01.902 에 반환됐다(app:1259).
- **리포트 코드**: `ReportService.java:41` `@Transactional`, `:52-54` 배포마다 제출 조회 반복, `:69-71` 주석 TODO 와 `sign()` 호출(`SIGNATURE_ROUNDS = 200_000`, `:23`).
- **슬로우 쿼리 대상**: `distribution_id=5`(slow:21). `distribution 5` 는 학급 3 소속이다(app:1278 `class 3`). 14:38:01 리포트도 학급 3 이다(app:1258).
- **스키마**: `db/mariadb/init/01-schema.sql:112` 에 `idx_submission_distribution (distribution_id)` 인덱스가 정의되어 있다.
- **코드에 안 쓰이는 것**: `GradingServerClient` 는 `modern/api/src/main` 어디에서도 호출되지 않는다. 이번 장애의 가설 대상에서 뺀다.
- **저장소에 없는 것**: nginx 설정 파일, 배포 이력, 14:37 이전 기동 기록. `app.log` 에는 재시작 · 설정 재적재 관련 줄이 없다(2절 확인).

### 3-3. 가설

#### H1. 애플리케이션 코드 — 트랜잭션 안에서의 장시간 처리

| 항목 | 내용 | 로그 실행 결과 (2026-10-06 실행) |
|---|---|---|
| 1. 가설 | `ReportService.buildClassReport` 가 DB 조회를 끝낸 뒤에도 `@Transactional` 안에서 `sign()`(SHA-256 20만 회)를 돌려 커넥션을 쥔 시간이 길고, 리포트 호출이 늘어난 14:38 부터 풀 5개가 이 호출들에 점유되어 다른 요청이 커넥션을 못 얻었다. | **유지 (부분)** — 점유 주체가 `buildClassReport` 임은 확인했다(스택 134/134). 다만 점유 시간 중 `sign()` 의 몫은 로그로 알 수 없고, 14:37:52 이전 리포트 50건은 누수가 0건이었다(같은 코드)는 점은 이 가설만으로 설명되지 않는다. |
| 2. 지지 근거 | 누수 경고 134건의 스택이 전부 `buildClassReport` 로 시작(3-2). app:1243 첫 경고, app:1258 리포트 완료 로그 → app:1259 같은 스레드 반환(리포트가 끝나자 반환). 리포트 호출 증가: 14:38 3건/분 → 14:40~14:50 분당 10~19건(acc:18801~19300 대, 2절 "리포트 호출 증가"). 코드 `ReportService.java:41` · `:69-71`. | 재확인함. ① 누수 스택 첫 `com.example` 프레임 134/134 가 `buildClassReport`. ② 누수 경고→반환 간격 134쌍: 최소 5.5 · 중앙값 8.9 · 최대 11.5 · 평균 9.0초. 임계값 10초와 합치면 건당 점유 약 15.5~21.5초. ③ 14:37:52 이후 리포트 완료 로그 140건 중 누수 경고 134건. |
| 3. 반증 조건 | (a) `sign()` 1회 실행이 로컬에서 수십 ms 수준이라 리포트 1건의 점유 시간(≥10초)을 설명하지 못한다. 이 경우 H1 단독으로는 부족하고 점유 시간의 다른 요인(H2 등)이 필요하다. (b) 누수 경고 스택의 134건 중 `buildClassReport` 가 아닌 것이 있다. (c) 누수 경고 외에 풀을 점유하는 다른 `@Transactional` 메서드가 같은 시간대의 느린 요청으로 확인된다. | (a) `sign()` 소요 시간: **로그에 없어 미확인**(로그 외 측정 필요). (b) 해당 없음 — 134건 전부 `buildClassReport`. (c) 해당 없음 — 다른 메서드의 누수 경고 0건. 참고: 14:37:52 이전 리포트 50건은 누수 0건(app:1~1242) → 같은 코드가 평소에는 10초 안에 끝났다. |
| 4. 확인 방법 | ① 스택 집계: `awk '/stack trace follows/{s=1;next} s&&/com\.example/{print $2; s=0}' app.log \| sort \| uniq -c` ② `sign()` 시간 측정: scratch 에서 `jshell` 로 `MessageDigest.getInstance("SHA-256")` 을 20만 회 반복해 걸린 시간 확인, 또는 `modern/api` 에서 `./gradlew test --tests 'com.example.assignment.*Report*'` 로 기존 테스트의 소요 시간 확인 ③ `app.log` 에서 누수 경고 → 반환 사이 간격(`Previously reported … returned` 줄과 경고 줄의 시각 차)을 `grep -n 'leak detection\|Previously reported' app.log` 로 뽑아 비교 | ①③ 실행함(3-6). ② `sign()` 측정은 로그가 아니라 실행 환경이 필요해 이번에 실행하지 않음. |

#### H2. DB · 쿼리 — `submission` 조회 지연

| 항목 | 내용 | 로그 실행 결과 (2026-10-06 실행) |
|---|---|---|
| 1. 가설 | `submission` 테이블에서 `distribution_id` 로 조회하는 쿼리가 14:37 경 약 128만 행을 읽는 실행 계획으로 느려졌고(옵티마이저 통계 · 데이터 증가 · 인덱스 미사용 등), 리포트 트랜잭션이 이 조회를 배포 수만큼 반복해 커넥션 점유 시간이 길어졌다. | **기각 (지속 원인으로서)** — 슬로우 로그 기록 하한이 약 2초(최소 기록 2.04초)인데 14:37:52 이후 항목이 0건이다. 리포트가 건당 약 15~21초씩 커넥션을 쥐는 동안 2초 이상 걸린 쿼리는 더 없었다. 단, 14:37:42 의 9.8초 쿼리가 **최초 방아쇠**였는지는 로그만으로 판단 불가(`EXPLAIN` · DB 필요). |
| 2. 지지 근거 | slow:15-21 `Query_time: 9.812417 … Rows_sent: 4  Rows_examined: 1284310`: 4행 반환에 128만 행을 읽음. 이 쿼리는 `distribution_id=5` 이고 학급 3 소속(app:1278). 누수 임계값 역산 획득 시각 14:37:42.2 ≈ 쿼리 시작 14:37:42(3-2). 첫 누수 경고(app:1243)와 쿼리 종료(14:37:52)가 같은 초. 코드가 배포마다 조회를 반복(`ReportService.java:52-54`). | 재확인함. 해당 쿼리는 슬로우 로그에 1건뿐(`select s1_0 …` 1건, `distribution_id=5` 1건, 9.81초, 1,284,310행). 나머지 2건은 2.32초 · 2.04초이고 14:17 이전(slow:1-13)이다. |
| 3. 반증 조건 | (a) `EXPLAIN` 이 `idx_submission_distribution` 을 쓰고 읽는 행이 10행 안팎이다(슬로우 쿼리가 일회성 이었다는 뜻). (b) 같은 시간대에 같은 쿼리가 반복 실행되었다는 증거가 로그에 없고, 리포트 1건의 점유 시간이 쿼리 시간으로 설명되지 않는다. 슬로우 로그는 14:17~15:02 에 이 건 하나뿐이다(slow:15-21)(단, 슬로우 로그의 임계값 설정이 불명이라 "더 없다"가 증거가 되려면 `long_query_time` 확인이 필요). (c) 이 쿼리가 리포트 요청이 아닌 다른 경로(예: 배치 · 점검 작업)에서 나왔다. | (a) `EXPLAIN` 미실행(DB 필요). (b) **해당** — 14:37:52 이후 슬로우 항목 0건(`SET timestamp > 1789537062` 0건). 같은 쿼리의 반복이 없다. (c) 확인 불가 — 슬로우 로그에 호출 경로 정보 없음. |
| 4. 확인 방법 | ① 읽기 전용 계정(`readonly` / `readonly-pass`)으로 `EXPLAIN select … from submission where distribution_id=5 order by student_id;` 와 `SHOW INDEX FROM submission;`, `SELECT COUNT(*) FROM submission;`, `SELECT COUNT(*) FROM submission WHERE distribution_id=5;` ② `SHOW VARIABLES LIKE 'long_query_time';` 로 슬로우 로그 임계값 확인 ③ `mariadb-slow.log` 의 `Thread_id` 와 앱 쪽 스레드의 대응은 로그만으로는 불가하므로 `SHOW PROCESSLIST` 가능 여부 확인 | ① 중 슬로우 로그 부분만 실행함(3-6). `EXPLAIN` · `SHOW INDEX` · `long_query_time` 는 DB 접속이 필요해 실행하지 않음. |

#### H3. 트래픽 · 클라이언트 — 리포트 호출 급증

| 항목 | 내용 | 로그 실행 결과 (2026-10-06 실행) |
|---|---|---|
| 1. 가설 | 14:38 에 외부 LMS 교사 대시보드 쪽에서 학급 리포트 호출이 급증했고(사용자 증가 · 자동 새로고침 · 재시도), 풀 5개로는 동시 처리량이 모자라 이후 5xx 로 이어졌다. 호출 증가는 앱 안이 아니라 앱 **바깥**에서 시작되었다. | **판단 불가 (로그 부족)** — '5xx 뒤 재시도로만 늘었다'는 해석은 기각된다(첫 5xx 이전 15건 전부 200). 그러나 사용자 증가인지, 느려진 응답에 대한 반복 호출인지는 요청 시작 시각 · 응답 시간 로그가 없어 가를 수 없다. |
| 2. 지지 근거 | 리포트 호출이 14:38 에 3건/분으로 시작해 14:40~14:50 에 10~19건/분(2절). 같은 시간에 전체 요청은 분당 34~58건으로 일정(acc:17853~19842) — 리포트 쪽만 늘었다. 하루 중 이 구간 밖의 리포트 호출은 53건뿐이다. 요청 IP 는 `10.20.3.11` · `.12` · `.17` · `.23` 등 교사 쪽 대역(acc:18801~18919). | 재확인함. 14:17~15:02 리포트 호출 176건은 IP 4개: `10.20.3.11` 69 · `.12` 41 · `.17` 37 · `.23` 29. 14:38 이전 호출은 2건(`.11` 14:17:29, `.12` 14:18:13)뿐이고, `.17` 은 14:40:27, `.23` 은 14:40:43 에 처음 나온다. |
| 3. 반증 조건 | (a) 호출 증가가 첫 5xx(14:40:52) **이후** 에만 일어났다면 호출 증가는 원인이 아니라 재시도라는 뜻이 된다. 단 14:38~14:40 에 이미 6건이 있어 이 반증은 일부만 성립한다. (b) 같은 IP 가 같은 주기로 반복 호출한 것이 확인되면 사용자 증가가 아니라 폴링 · 재시도다. (c) 리포트 호출 수가 같은 정도였던 날이 있었는데 장애가 없었다면 호출량만으로는 설명이 안 된다. | (a) **부분 해당** — 첫 5xx(14:40:52) 이전 14:38:01~14:40:51 에 리포트 15건이 전부 200. 증가가 5xx 이후 재시도뿐이라는 해석은 반증된다. (b) **부분 해당** — `.12` 의 학급 3 호출 간격이 26 · 30 · 27 · 31 · 24 · 25 · 22 · 22 · 23 · 19초로 규칙적이다. 같은 IP 재호출 간격 분포(14:38~14:51): ≤5초 47 · 6~15초 25 · 16~40초 92 · 40초 초과 3. 자동 새로고침인지 사람의 조작인지는 로그로 구분 불가. (c) 판단 불가 — 비교할 다른 날 로그 없음. |
| 4. 확인 방법 | ① IP 별 · 분 별 호출: `grep '/report' nginx-access.log \| awk '{print $1}' \| sort \| uniq -c`, 14:38~14:51 로 `awk 'NR>=18801 && NR<=19400'` 같은 줄 범위 제한. ② 14:40:52 이전 리포트 호출(app:1258~1521 구간)의 응답 코드가 모두 200 이라는 점 확인(acc:18801~18919). ③ LMS(외부) 쪽 대시보드 자동 새로고침 주기 설정 · 14:30 전후 공지/수업 시작 시각을 운영 담당에게 확인(로그에 없는 정보) | ①② 실행함(3-6). ③ LMS 대시보드 새로고침 설정은 로그 외 정보라 미확인. |

#### H4. 배포 · 설정 변경 — 14:37 직전의 변경

| 항목 | 내용 | 로그 실행 결과 (2026-10-06 실행) |
|---|---|---|
| 1. 가설 | 14:37 직전에 배포(이미지 · 코드)나 설정 변경이 있었고, 그 변경으로 리포트 처리 방식(트랜잭션 경계 · 서명 반복 횟수) 또는 DB 쪽(통계 갱신 · 인덱스 변경 · 데이터 적재)이 바뀌어 이전에는 없던 점유가 생겼다. | **판단 불가 (로그 부족)** — 로그에 기동 · 재시작 · 배포 기록 자체가 없다(기동 배너 0건, nginx signal/reconfig 0건). 변경이 있었다고도 없었다고도 말할 수 없다. |
| 2. 지지 근거 | **현재 로그에는 직접 근거가 없다.** 근거가 되는 것은 "평소에는 같은 코드가 정상 동작했다"는 대조뿐이다. 리포트 호출은 07:42 부터 이미 처리되었다(app:103, 106, 141 …, 이 시각에는 경고 · 오류 줄 없음). 첫 누수 경고는 하루 중 14:37:52 가 처음이다(app:1243, 이전 `ProxyLeakTask` 0건). 그래서 14:37 직전에 "무엇이 바뀌었는가"를 묻는 가설이다. | 직접 근거 없음 그대로. 확인된 사실은 14:37:52 이전 리포트 50건이 누수 0건, 이후 140건 중 134건이 누수라는 행동 변화뿐이다. |
| 3. 반증 조건 | (a) 앱 컨테이너의 기동 시각이 14:37 보다 한참 앞이고(배포 없음) 설정 파일 변경 이력도 없다. (b) DB 쪽 `ANALYZE TABLE` · 인덱스 변경 · 대량 적재 이력이 14:37 전후에 없다. (c) 07:42~14:17 에도 리포트가 같은 코드로 처리되었고 점유 시간이 비슷하게 길었는데 경고가 없었던 이유가 설명되지 않으면, 코드 변경이 아니라 부하 조건 문제로 본다. | (a) 확인 불가 — 앱 기동 시각 정보 없음. (b) 확인 불가 — DB 쪽 이력 없음. (c) **해당** — 장애 전 50건은 정상 처리되었으므로 코드 변경이 없어도 부하 조건 변화(H3)로 설명할 여지가 남는다. |
| 4. 확인 방법 | ① `git log --since='2026-09-16 00:00' --until='2026-09-16 15:00' -- modern/api db` 와 `git log -p -- modern/api/src/main/resources/application.yml` ② 앱 컨테이너 기동 시각: `docker ps --format '{{.Names}} {{.Status}}'` · `docker inspect --format '{{.State.StartedAt}}' <컨테이너>` (운영 환경이면 배포 이력 시스템) ③ `app.log` 첫 줄(06:43:56)과 기동 배너 유무: `grep -n -i 'Started .* in\|Starting ' app.log \| head` ④ MariaDB: `SELECT TABLE_NAME, UPDATE_TIME FROM information_schema.TABLES WHERE TABLE_SCHEMA='itembank';` | ①③ 실행함(3-6): `app.log` 의 배너 0건, `git log`(저장소 이력, 운영 배포 이력 아님) 결과 없음. ②④ 운영 환경 · DB 접속이 필요해 미실행. |

### 3-4. 가설을 가르는 확인 (판정 아님)

가설은 서로 배타적이지 않다. 아래는 "어떤 결과가 나오면 어느 가설이 약해지는가"를 정리한 것이다. 이 절에서는 확인하지 않았다.

| 확인 | 결과 → 약해지는 가설 |
|---|---|
| `sign()` 20만 회 소요 시간 | 수십 ms → **H1 단독** 약해짐(점유 시간은 다른 요인 필요). 수 초 이상 → H1 강화 쪽으로 해석 |
| `EXPLAIN` 의 `submission` 조회 | 인덱스를 쓰고 소량 행 → **H2** 약해짐 |
| 14:38 이전 리포트 호출 응답 시간 · 같은 IP 반복 주기 | 평소와 같고 호출이 급증한 것이 아님 → **H3** 약해짐 |
| 앱 기동 시각 · 변경 이력 | 14:37 직전 변경이 없음 → **H4** 약해짐 |
| 누수 경고 → 반환 간격(약 9.7초 · 14:37:52→14:38:01.9, app:1243 → app:1259) 이 쿼리 시간 9.8초(slow:18)와 같은 두 번이 아님 | 이 구간의 시간이 쿼리 시간과 sign() 시간 중 어디서 쓰였는지가 H1 과 H2 의 비중을 가른다 |

### 3-5. 아직 모르는 것

- 14:37:52 → 14:38:01.9 사이 약 9.7초(app:1243 ~ app:1259)에 스레드 exec-6 이 무엇을 했는지 로그에 없다.
- 슬로우 로그의 `Thread_id`(slow:17)와 앱 스레드의 대응이 없다. 14:37:42 의 슬로우 쿼리가 exec-6 의 것인지는 시각 일치(추론)일 뿐이다.
- 리포트 1건의 서버 처리 시간은 로그에 기록이 없다(`nginx-access.log` 에 `request_time` 필드가 없다).
- nginx 의 upstream 설정(`max_fails` · `fail_timeout`)을 이 저장소에서 볼 수 없어 502 의 `temporarily disabled`(err:10)가 어떤 설정에서 나온 것인지 확인하지 못했다.

### 3-6. 로그 검증 실행 결과 (2026-10-06)

3-3 의 "확인 방법" 중 로그만으로 할 수 있는 것을 실행했다. 모든 명령은 `incident-logs/a-connection-pool/` 에서 실행했고 파일은 통째로 읽지 않았다. 3-4 의 "이 절에서는 확인하지 않았다"는 이 실행으로 일부 갱신된다.

**판정 요약**

| 가설 | 판정 | 한 줄 이유 | 판단 불가 부분에 필요한 로그 |
|---|---|---|---|
| H1 애플리케이션 코드 | 유지 (부분) | 누수 스택 134/134 가 `buildClassReport`, 건당 점유 약 15.5~21.5초. 단 `sign()` 의 몫 · 14:37 이전 50건이 정상이었던 이유는 모름 | `buildClassReport` 구간별 소요 시간 로그(DB 조회 끝 시각 · `sign()` 시작/끝 시각), 또는 로컬 `sign()` 측정 |
| H2 DB · 쿼리 | 기각 (지속 원인으로서) / 방아쇠 여부는 판단 불가 | 슬로우 기록 하한 ≈2초인데 14:37:52 이후 0건 | `EXPLAIN` 결과 · 14:37 직전 `ANALYZE TABLE`/통계 변경 이력 · `long_query_time` 값 · `general_log` 또는 쿼리별 실행 시간 |
| H3 트래픽 · 클라이언트 | 판단 불가 (로그 부족) | 5xx 전 15건이 전부 200 이라 "재시도뿐" 해석은 기각. 사용자 증가 vs 반복 호출은 못 가름 | nginx `$request_time` · `$upstream_response_time`(요청 시작 시각 포함), LMS 대시보드 새로고침 설정, 전일 같은 시간대 access 로그 |
| H4 배포 · 설정 변경 | 판단 불가 (로그 부족) | 기동 · 배포 기록이 로그에 없음 | 컨테이너 기동 로그(`Started … in … seconds`) · `docker inspect` 의 `StartedAt` · 배포 이력 · `information_schema.TABLES` 의 `UPDATE_TIME` |

**H1 — 누수 스택 출처와 점유 시간**

```
$ awk '/stack trace follows/{s=1;next} s&&/com\.example/{print $2; s=0}' app.log | sort | uniq -c
 134 com.example.assignment.ReportService$$SpringCGLIB$$0.buildClassReport(<generated>)

# 같은 스레드 · 같은 Connection 의 "leak WARN" → "Previously reported … returned" 간격(초), awk 로 쌍을 맞춤
pairs=134 unmatched_returns=0
n=134 min=5.5 median=8.9 max=11.5 mean=9.0
구간 분포: <7s=15  7-10s=70  >=10s=49

$ sed -n '1,1242p' app.log | grep -c 'ReportService.*built class report'   # 첫 누수 경고(app:1243) 이전
50
$ sed -n '1,1242p' app.log | grep -c 'ProxyLeakTask'
0
$ sed -n '1243,$p' app.log | grep -c 'ReportService.*built class report'    # 첫 누수 경고 이후
140
$ sed -n '1243,$p' app.log | grep -c ' WARN .*ProxyLeakTask'
134
```

- 해석 범위: 누수 경고는 획득 후 10초(`application.yml:18`)에 찍히므로 반환까지 건당 점유는 **10초 + 5.5~11.5초 ≈ 15.5~21.5초**다.
- 14:37:52 이전 리포트 50건은 누수가 0건이라 그 건들은 10초 안에 끝났다.
- 이 실행으로 알 수 없는 것: 점유 시간 안에서 DB 조회 · `sign()` · 풀 대기가 각각 얼마였는지.

**H2 — 슬로우 로그**

```
$ grep -o 'Query_time: [0-9.]*' mariadb-slow.log
Query_time: 2.318446  Query_time: 2.041903  Query_time: 9.812417

$ grep -c '^select s1_0' mariadb-slow.log          → 1
$ grep -c 'distribution_id=5' mariadb-slow.log     → 1

$ grep -o 'SET timestamp=[0-9]*' mariadb-slow.log | awk -F= '$2>1789537062' | wc -l
0                                                    # 14:37:42 KST 이후 시작한 슬로우 항목 수
```

- 기록된 최소 `Query_time` 이 2.04초라 기록 하한은 2.04초 이하로 추정한다. `long_query_time` 값 자체는 확인하지 못했다.

**H3 — 리포트 호출 패턴**

```
$ grep '/report' nginx-access.log | awk '{print $1}' | sort | uniq -c | sort -rn           # 하루 전체
  87 10.20.3.11   57 10.20.3.12   56 10.20.3.17   29 10.20.3.23
$ sed -n '17853,19842p' nginx-access.log | grep '/report' | awk '{print $1}' | sort | uniq -c | sort -rn   # 14:17~15:02
  69 10.20.3.11   41 10.20.3.12   37 10.20.3.17   29 10.20.3.23

$ sed -n '18801,18917p' nginx-access.log | grep '/report' | awk '{print $9}' | sort | uniq -c  # 14:38:01~14:40:51 (첫 5xx 이전)
  15 200
$ sed -n '18918,19400p' nginx-access.log | grep '/report' | awk '{print $9}' | sort | uniq -c  # 14:40:52~14:51
  64 200    5 500   32 502   55 504

# 14:38~14:51, 같은 IP 의 연속 호출 간격(경로 구분 없음)
≤5s=47  6-15s=25  16-40s=92  >40s=3

# 14:17 이후 IP 별 첫 리포트 호출
10.20.3.11 14:17:29   10.20.3.12 14:18:13   10.20.3.17 14:40:27   10.20.3.23 14:40:43

# 10.20.3.12 의 학급 3 호출 간격(초), 14:38~14:44
26 30 27 31 24 25 22 22 23 19 1 17 40 18 18 18 3 32 4

# 하루 시간대별 리포트 호출 수
07=2 08=5 09=2 10=11 11=7 12=11 13=8 14=179 15=4
```

- 호출은 4개 IP 에서만 나온다. 하루 중 14시 이전 시간당 최대 11건이던 것이 14시에 179건이 되었다.
- 응답 시간 · 요청 시작 시각 정보가 없어 "응답이 늦어서 다시 호출한 것"과 "원래 호출이 늘어난 것"을 구분하지 못한다.

**H4 — 기동 · 변경 흔적**

```
$ grep -c -i -E 'Started .* in |Starting |Shutting down' app.log      → 0
$ grep -c 'Spring Boot\|ItemBankApplication' app.log                  → 0
$ grep -c -i -E 'signal|exited|reconfig|worker process' nginx-error.log → 0
# app.log 의 타임스탬프 줄 사이 5분 초과 무로그 구간: 없음
$ git log --since='2026-09-16 00:00' --until='2026-09-17 00:00'       → (결과 없음)
```

- `app.log` 첫 줄이 06:43:56 의 일반 요청 로그라서 로그 발췌본일 가능성이 있다. 배너가 0건인 것을 "재시작이 없었다"의 증거로 쓰지 않는다.
- `git log` 는 이 실습 저장소의 이력이다. 운영 배포 이력이 아니다.

**실행하지 않은 확인 (로그가 아니라 실행 환경 · DB 가 필요)**

- H1 ② `sign()` 20만 회 소요 시간(`jshell`) — H1 의 점유 시간 설명 여부를 가르는 가장 직접적인 확인이다.
- H2 ① `EXPLAIN` · `SHOW INDEX` · `COUNT(*)` · `long_query_time`
- H3 ③ LMS 대시보드 새로고침 설정, H4 ②④ 컨테이너 기동 시각 · `information_schema.TABLES`

### 3-7. 판정

3-6 에서 "유지"로 남은 H1 을 중심으로, 로그에 찍힌 클래스 · 설정 키를 단서로 `modern/api` 의 코드와 설정을 읽어 대조했다. 대조 대상은 `ReportController` · `ReportService`(누수 스택의 `buildClassReport`), `SubmissionRepository`(슬로우 쿼리의 출처), `application.yml`(Hikari 키), `DistributionService` · `UnitService` · `ItemService`(같은 풀을 쓰는 다른 경로)다. 벤치마크 · 테스트는 실행하지 않았고, 설정값은 파일에서 읽은 값만 썼다.

> 프롬프트의 "대조할 곳:" 칸이 채워지지 않은 상태여서, 위 대상은 로그에 찍힌 클래스 · 설정 키에서 직접 골랐다. 범위를 바꾸려면 알려 달라.

#### 3-7-1. 코드 · 설정에서 읽은 사실

| 대상 | 읽은 내용 | 위치 |
|---|---|---|
| 리포트 진입점 | `GET /api/classes/{id}/report` → `reportService.buildClassReport(id)` 를 바로 호출. 컨트롤러에는 트랜잭션 · 캐시 · 호출 제한이 없다 | `ReportController.java:20-22` |
| 트랜잭션 범위 | `buildClassReport` 가 `readOnly` 없는 `@Transactional`(메서드 전체가 하나의 트랜잭션) | `ReportService.java:41` |
| 트랜잭션 안의 작업 | ① 학급 조회 `:43` ② 배포 목록 조회 `:46` ③ 배포마다 제출 조회 반복 `:52-54` ④ 서명 `sign()` 호출 `:71` | `ReportService.java` |
| 코드의 자기 진단 | "DB 작업은 위에서 끝났는데 커넥션(트랜잭션)을 쥔 채로 돈다" 라는 TODO 주석 | `ReportService.java:69-70` |
| 서명 반복 횟수 | `SIGNATURE_ROUNDS = 200_000`, `sign()` 이 SHA-256 을 그 횟수만큼 반복 | `ReportService.java:23`, `:88-91` |
| 풀 설정 | `maximum-pool-size: 5` · `connection-timeout: 3000` · `leak-detection-threshold: 10000` · `minimum-idle: 1` | `application.yml:14`, `:15`, `:18`, `:13` |
| 설정 주석 | "풀이 작고 대기 시간이 짧아, 트랜잭션을 오래 쥐는 코드가 있으면 금방 고갈된다" | `application.yml:11-12` |
| OSIV | `open-in-view: false` — 웹 계층까지 커넥션을 쥐는 일은 없다 | `application.yml:22` |
| 커넥션 획득 시점 | 누수 경고 스택에서 `getConnection` 이 `HibernateJpaDialect.beginTransaction` ← `JpaTransactionManager.doBegin` ← `TransactionInterceptor` ← `ReportService.buildClassReport` 에서 호출됨. 즉 메서드 진입 시 트랜잭션 시작과 함께 획득 | `app.log:1245-1254` |
| 슬로우 쿼리 출처 | 슬로우 쿼리 `select s1_0.id,… from submission s1_0 where s1_0.distribution_id=5 order by s1_0.student_id` 는 `findByDistributionIdOrderByStudentIdAsc` 가 만드는 형태. 이 메서드의 호출처는 `ReportService.java:54` 하나뿐(나머지는 테스트) | `slow:21`, `SubmissionRepository.java:8` |
| 인덱스 | `submission.distribution_id` 에 `idx_submission_distribution` 이 정의되어 있다 | `db/mariadb/init/01-schema.sql:112` |
| 다른 경로의 풀 사용 | `UnitService` · `ItemService` 는 클래스 단위 `@Transactional(readOnly = true)` — 같은 풀에서 커넥션을 받는다 | `UnitService.java:10`, `ItemService.java:21` |
| 로그와 설정의 일치 | 풀 고갈 오류 줄의 `request timed out after 3000ms (total=5, active=5 …)` 가 `connection-timeout: 3000` · `maximum-pool-size: 5` 와 같은 값 | `app.log:1522`, `application.yml:14-15` |
| 이력 | `ReportService.java` · `application.yml` 의 커밋은 초기 구성 1건뿐이고 `modern/api` 에 미커밋 변경은 없다 | `git log`, `git status` |

#### 3-7-2. 가설별 판정

| 가설 | 코드 · 설정 대조 판정 | 근거 |
|---|---|---|
| H1 애플리케이션 코드 | **지지한다** | 풀 5개(`application.yml:14`)를 쓰는 서비스에서, `buildClassReport` 가 트랜잭션 시작 시점에 커넥션을 받고(`app.log:1249-1253`) 메서드가 끝날 때까지 쥐며(`ReportService.java:41`), 그 안에서 DB 작업이 끝난 뒤에도 `sign()` 을 돈다(`:71`, `:23`). 코드 주석이 같은 문제를 이미 적어 두었다(`:69-70`). 대기 시간 3초(`:15`)는 로그의 `3000ms` 와 같고, 같은 풀을 쓰는 `UnitService` · `ItemService`(`readOnly` 트랜잭션)가 대기하다 실패하는 구조도 맞는다. |
| H2 DB · 쿼리 | **반박한다** (지속 원인으로서) | 슬로우 쿼리는 리포트의 반복 조회(`ReportService.java:53-54`)에서 나온 쿼리이고, 인덱스가 정의되어 있어(`01-schema.sql:112`) 코드에는 전체 스캔을 일으킬 요인이 없다. 로그에서는 14:37:52 이후 같은 종류의 슬로우 항목이 0건(3-6)이라 점유가 쿼리 지연 때문에 계속된 것이 아니다. 14:37:42 의 9.8초 1회가 방아쇠였는지는 코드로 알 수 없다(실제 실행 계획 · 통계는 코드에 없다). |
| H3 트래픽 · 클라이언트 | **관련 코드를 찾지 못했다** | 호출자는 외부 LMS 이며 저장소에 없다. 다만 코드에 호출 제한 · 캐시가 없다는 점(`ReportController.java:20-22`, `Cacheable`/제한 관련 키워드 검색 결과 없음)은 H1 의 구조에서 호출 증가가 곧 점유 증가로 이어진다는 정도의 간접 정보다. 호출이 왜 늘었는지는 코드로 알 수 없다. |
| H4 배포 · 설정 변경 | **관련 코드를 찾지 못했다** | 저장소 이력은 초기 구성 1건뿐이고(`git log`), 커밋 일자(2026-09-22)가 장애일(2026-09-16)보다 뒤다. 운영 배포 이력 역할을 하지 못한다. |

#### 3-7-3. 채택과 기각

**채택: H1 — `ReportService.buildClassReport` 가 트랜잭션(커넥션)을 쥔 채로 장시간 처리한다.**

- 확신 수준: **중간.**
  - 메커니즘 — "풀이 5개로 작은데 한 요청이 커넥션을 오래 쥐면 고갈되어 다른 요청이 3초 뒤 실패한다" — 은 높은 확신이다. 근거는 코드(`ReportService.java:41`, `:69-71`), 설정(`application.yml:14-15`, `:18`), 로그(`app.log:1245-1254`, `:1522`; 누수 경고 134건 전부 이 메서드 — 3-6)가 모두 같은 방향이라는 점이다.
  - 14:37 부터 점유가 10초를 넘기 시작한 **이유**는 낮은 확신이다.
    - 코드는 고정이고 14:37:52 이전 리포트 50건은 누수가 없었다(3-6).
    - `sign()` 20만 회가 실제로 몇 초인지 이번에는 측정하지 않았다(지시에 따라 미실행).
    - 호출이 늘어 동시 실행이 늘었다는 것(H3)이나 14:37:42 의 9.8초 쿼리(H2)가 점유 시간을 늘렸을 가능성은 이 대조로 가려지지 않는다.
  - 종합하면 "무엇이 풀을 고갈시켰나(구조)"는 H1 로 채택하고, "왜 그날 그 시각에 시작됐나(방아쇠)"는 미해결이다.

**기각 · 미채택 사유**

| 가설 | 처리 | 사유 |
|---|---|---|
| H2 | 기각 (지속 원인으로서) | 인덱스가 있는 단순 조회 코드이고, 14:37:52 이후 슬로우 항목이 0건이다. 1회성 방아쇠 가능성은 남기되 채택하지 않는다. |
| H3 | 미채택 (판단 불가) | "5xx 뒤 재시도뿐"이라는 해석은 로그(첫 5xx 이전 15건 전부 200)로 기각. 호출 증가 자체가 있었는지, 응답 지연에 대한 반복 호출인지는 코드 · 로그로 가릴 수 없다. 방아쇠 후보로 남긴다. |
| H4 | 미채택 (판단 불가) | 장애 시점의 배포 · 설정 변경 증거가 코드 · 로그 어디에도 없다. |

#### 3-7-4. 남은 한계

- `sign()` 20만 회의 실제 소요 시간 — 측정하면 "점유 15.5~21.5초(3-6) 중 `sign()` 의 몫"이 정해진다. 코드만으로는 알 수 없다.
- 14:37 이전 50건이 정상이었던 이유 — 동시 실행 증가와 CPU 경합이 영향을 줬는지는 코드 · 로그에서 확인하지 못했다(추정이며 기록된 사실이 아니다).
- 이 판정은 장애 당시와 같은 코드가 운영에 배포되어 있었다는 전제에 선다. 이 저장소의 커밋 일자는 장애일 이후라 전제를 증명하지 못한다.
