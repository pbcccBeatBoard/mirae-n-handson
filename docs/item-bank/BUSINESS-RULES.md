# legacy/item-bank-php 비즈니스 규칙 후보

> 전제: `docs/item-bank/ARCHITECTURE.md`, `docs/item-bank/ERD.md` 를 먼저 읽고 작성했다.
> 코드에 없는 규칙은 쓰지 않았다. 근거가 주석뿐인 항목은 규칙으로 올리지 않고 문서 맨 아래 "주석만 있음" 절에 남겼다. 주석과 코드가 다른 곳은 코드를 기준으로 규칙을 쓰고 주석 내용은 비고에 적었다.

## A. 문항 검색 조건 조합 (단원 · 난이도 · 태그 · 키워드) — `search.php` `buildSearchQuery()`

- ID: BR-01
- 규칙: 검색 키워드(q)가 100자를 넘으면 앞 100자까지만 잘라서 검색한다.
- 근거: search.php:86-89
- 근거 코드:
  ```php
  if (mb_strlen($q, 'UTF-8') > 100) {
      $q = mb_substr($q, 0, 100, 'UTF-8');
      $warnings[] = '키워드가 너무 길어 100자까지만 사용했습니다.';
  }
  ```
- 확신도: 확실
- 비고: 매직 넘버 100 (아래 표 참고)

- ID: BR-02
- 규칙: 키워드가 있으면 제목(title) 또는 지문(stem)에 부분 일치(LIKE)하는 문항만 조회한다.
- 근거: search.php:97-98
- 근거 코드:
  ```php
  $like = '%' . $q . '%';
  $where .= " AND (title LIKE ? OR stem LIKE ?)";
  ```
- 확신도: 확실
- 비고: 없음

- ID: BR-03
- 규칙: 키워드에 `%`나 `_`가 포함되면 이스케이프 없이 LIKE 와일드카드로 그대로 처리되며, 검색은 막지 않고 경고만 표시한다.
- 근거: search.php:94-96
- 근거 코드:
  ```php
  if (strpos($q, '%') !== false || strpos($q, '_') !== false) {
      $warnings[] = '키워드의 % 와 _ 는 와일드카드로 처리됩니다.';
  }
  ```
- 확신도: 확실
- 비고: 없음

- ID: BR-04
- 규칙: 단원 코드(unit) 형식이 정규식(`^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$`)에 맞지 않아도 검색을 막지 않고 경고만 표시한 채 입력값 그대로 조회한다.
- 근거: search.php:110-120
- 근거 코드:
  ```php
  if (!preg_match('/^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$/', $unit)) {
      // 형식이 달라도 그대로 조회한다 (결과는 대개 0건)
      $warnings[] = '단원 코드 형식이 올바르지 않습니다. (예: M5-1)';
  }
  ```
- 확신도: 확실
- 비고: 주석("결과는 대개 0건")과 코드 동작 일치, 불일치 없음

- ID: BR-05
- 규칙: 단원 코드가 대문자가 아니어도 등록을 막지 않고 경고만 표시한 채 입력값 그대로 조건에 사용한다.
- 근거: search.php:115-120
- 근거 코드:
  ```php
  if ($unit !== strtoupper($unit)) {
      $warnings[] = '단원 코드는 대문자로 입력하세요. (입력값 그대로 조회합니다)';
  }
  $where .= " AND unit_code = ?";
  ```
- 확신도: 확실
- 비고: 없음

- ID: BR-06
- 규칙: 단원(unit) 조건을 지정하면 `unit_code`가 정확히 일치하는 문항만 조회한다(부분 일치 아님).
- 근거: search.php:118-120
- 근거 코드:
  ```php
  $where .= " AND unit_code = ?";
  $types .= 's';
  $values[] = $unit;
  ```
- 확신도: 확실
- 비고: 없음

- ID: BR-07
- 규칙: 난이도(level) 조건을 비워두고 검색하면 `level < 5` 조건이 적용되어 **난이도 5인 문항은 검색 결과에서 제외**된다.
- 근거: search.php:213-216
- 근거 코드:
  ```php
  // 난이도 값이 비어 있으면 전체 난이도 검색 (1~5 모두 포함)
  if ($level == '') {
      $where .= " AND level < 5";
  }
  ```
- 확신도: 확실 (SQL 조건문 `level < 5`로 확인)
- 비고: **주석-코드 불일치.** 주석은 "1~5 모두 포함"이라고 되어 있으나 실제 코드는 `level < 5`라 난이도 5 문항이 빠진다. 매직 넘버 5(제외 기준)는 아래 표 참고.

- ID: BR-08
- 규칙: 난이도가 1~5 중 하나로 지정되면 해당 값과 정확히 일치하는 문항만 조회한다.
- 근거: search.php:217-222
- 근거 코드:
  ```php
  else if (preg_match('/^[1-5]$/', $level)) {
      $where .= " AND level = ?";
      $types .= 'i';
      $values[] = (int)$level;
  }
  ```
- 확신도: 확실
- 비고: 매직 넘버 1~5 (아래 표 참고)

- ID: BR-09
- 규칙: 난이도 값이 1~5 형식이 아니면 정수로 캐스팅한 값(대개 0)을 그대로 조건에 사용해 조회하고 경고만 표시한다.
- 근거: search.php:223-230
- 근거 코드:
  ```php
  else {
      // 1~5 밖의 값은 그대로 정수로 비교한다 (대개 0건)
      $warnings[] = '난이도는 1~5 사이여야 합니다.';
      $where .= " AND level = ?";
      $values[] = (int)$level;
  }
  ```
- 확신도: 확실
- 비고: 없음

- ID: BR-10
- 규칙: 태그 이름(tag)이 50자를 넘으면 앞 50자까지만 사용해 검색한다.
- 근거: search.php:240-243
- 근거 코드:
  ```php
  if (mb_strlen($tag, 'UTF-8') > 50) {
      $tag = mb_substr($tag, 0, 50, 'UTF-8');
      $warnings[] = '태그 이름이 너무 길어 50자까지만 사용했습니다.';
  }
  ```
- 확신도: 확실
- 비고: 매직 넘버 50 (아래 표 참고)

- ID: BR-11
- 규칙: 태그(tag) 조건을 지정하면 이름이 정확히 일치하는 태그가 하나라도 연결된 문항만 조회한다(EXISTS 서브쿼리, 부분 일치 아님).
- 근거: search.php:244-248
- 근거 코드:
  ```php
  $where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
          . " WHERE it.item_id = v_item_public.id AND t.name = ?)";
  ```
- 확신도: 확실
- 비고: `item_tag.item_id = v_item_public.id` 관계는 `docs/item-bank/ERD.md`의 "추정" 관계와 동일

- ID: BR-12
- 규칙: 등록되지 않은 태그 이름으로 검색해도 조회를 막지 않고 경고만 표시한다(결과는 보통 0건).
- 근거: search.php:250-260
- 근거 코드:
  ```php
  if (!$tagKnown) {
      $warnings[] = '등록되지 않은 태그입니다: ' . $tag;
  }
  ```
- 확신도: 확실
- 비고: 없음

## B. 등록 · 수정 시의 검증 — `register.php`

> `legacy/item-bank-php` 안에는 문항 **수정(edit/update)** 화면·엔드포인트가 없다(코드 전체에 `UPDATE item` 문 없음, `search.php`/`units.php`/`register.php` 어디에도 수정 폼 없음). 따라서 "수정 시 검증" 규칙은 작성하지 않았다 — 코드에 없는 것을 상식으로 채우지 않는다는 원칙에 따름.

- ID: BR-13
- 규칙: 문항 제목이 5자 미만이면 등록을 거부한다.
- 근거: register.php:49-51
- 근거 코드:
  ```php
  if (mb_strlen($title, 'UTF-8') < 5) {
      $errors[] = '제목은 5자 이상 입력해야 합니다.';
  }
  ```
- 확신도: 확실
- 비고: 매직 넘버 5 (아래 표 참고)

- ID: BR-14
- 규칙: 문항 제목이 200자를 넘으면 등록을 거부한다.
- 근거: register.php:52-54
- 근거 코드:
  ```php
  if (mb_strlen($title, 'UTF-8') > 200) {
      $errors[] = '제목은 200자를 넘을 수 없습니다.';
  }
  ```
- 확신도: 확실
- 비고: 매직 넘버 200 — `item.title VARCHAR(200)`(01-schema.sql:23)과 일치

- ID: BR-15
- 규칙: 지문(stem)이 빈 문자열이면 등록을 거부한다.
- 근거: register.php:56-58
- 근거 코드:
  ```php
  if ($stem === '') {
      $errors[] = '지문을 입력해야 합니다.';
  }
  ```
- 확신도: 확실
- 비고: 없음

- ID: BR-16
- 규칙: 선택한 단원 ID가 조회된 단원 목록에 없으면 등록을 거부한다.
- 근거: register.php:60-68
- 근거 코드:
  ```php
  $unitOk = false;
  foreach ($units as $u) {
      if ((string)$u['id'] === $unitId) {
          $unitOk = true;
      }
  }
  if (!$unitOk) {
      $errors[] = '단원을 선택해야 합니다.';
  }
  ```
- 확신도: 확실
- 비고: 없음

- ID: BR-17
- 규칙: 난이도가 1~5 중 하나의 형식이 아니면 등록을 거부한다.
- 근거: register.php:70-72
- 근거 코드:
  ```php
  if (!preg_match('/^[1-5]$/', $level)) {
      $errors[] = '난이도는 1~5 사이여야 합니다.';
  }
  ```
- 확신도: 확실
- 비고: 매직 넘버 1~5 (아래 표 참고)

- ID: BR-18
- 규칙: 제출된 태그 ID 중 태그 목록에 없는 값은 오류 없이 조용히 제외하고 등록 자체는 막지 않는다.
- 근거: register.php:74-81
- 근거 코드:
  ```php
  foreach ($tagIds as $tid) {
      foreach ($tags as $t) {
          if ((string)$t['id'] === (string)$tid) {
              $validTagIds[] = (int)$tid;
          }
      }
  }
  ```
- 확신도: 확실
- 비고: 무효 태그 ID에 대한 `$errors[]` 추가 없음 — 검증 실패로 취급하지 않음

- ID: BR-19
- 규칙: 새로 등록된 문항은 상태가 항상 `'R'`(검수중)로 저장되며, 등록만으로 바로 공개(`'A'`)되지 않는다.
- 근거: register.php:84, register.php:92-95
- 근거 코드:
  ```php
  // 등록 직후에는 검수중(R) 상태로 들어간다 → 검수 완료 전까지 검색 화면에 나오지 않는다.
  $stmt = $conn->prepare(
      "INSERT INTO item (id, unit_id, title, stem, level, status, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())"
  );
  ```
- 확신도: 확실 (SQL 리터럴 `'R'`로 확인)
- 비고: 주석과 코드 일치, 불일치 없음

- ID: BR-20
- 규칙: 새 문항의 ID는 `item` 테이블의 현재 최댓값 + 1로 채번한다(자동 증가 컬럼이 아님).
- 근거: register.php:87
- 근거 코드:
  ```php
  $res = $conn->query("SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM item FOR UPDATE");
  ```
- 확신도: 확실
- 비고: 없음

- ID: BR-21
- 규칙: 등록 처리 중 예외가 발생하면 트랜잭션 전체를 롤백하고, 사용자에게는 공통 오류 메시지만 보여준다(구체적 오류는 로그에만 남긴다).
- 근거: register.php:119-123
- 근거 코드:
  ```php
  } catch (Exception $e) {
      $conn->rollback();
      Log::error('item register failed', array('msg' => $e->getMessage()));
      $errors[] = '저장 중 오류가 발생했습니다.';
  }
  ```
- 확신도: 확실
- 비고: 없음

## C. 목록의 기본 정렬과 제외 조건

- ID: BR-22
- 규칙: 정렬 조건(sort)을 지정하지 않으면 난이도(level) 내림차순, 동일 난이도는 ID 오름차순으로 정렬한다.
- 근거: search.php:317-320
- 근거 코드:
  ```php
  case '':
      // 기본 정렬: 어려운 문항부터, 같은 난이도면 번호 순
      $orderBy = " ORDER BY level DESC, id ASC";
      break;
  ```
- 확신도: 확실
- 비고: 주석과 코드 일치

- ID: BR-23
- 규칙: 알 수 없는 정렬 기준(sort)이 지정되면 기본 정렬(난이도 내림차순)로 대체하고 경고를 표시한다.
- 근거: search.php:322-326
- 근거 코드:
  ```php
  default:
      $warnings[] = '알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.';
      $sort = '';
      $orderBy = " ORDER BY level DESC, id ASC";
      break;
  ```
- 확신도: 확실
- 비고: 없음

- ID: BR-24
- 규칙: 정렬 방향(dir)이 `asc`/`desc`가 아니면 빈 값으로 취급하고, 값이 비어있지 않았다면 경고를 표시한다.
- 근거: search.php:268-273
- 근거 코드:
  ```php
  if ($dir !== 'asc' && $dir !== 'desc') {
      if ($dir !== '') {
          $warnings[] = '정렬 방향은 asc 또는 desc 만 가능합니다.';
      }
      $dir = '';
  }
  ```
- 확신도: 확실
- 비고: 없음

- ID: BR-25
- 규칙: 정렬 헤더를 처음(dir 미지정 상태로) 클릭할 때 `level`과 `created` 열은 내림차순으로, 그 외 열은 오름차순으로 시작한다.
- 근거: search.php:328-331, search.php:410-413
- 근거 코드:
  ```php
  $dir = ($sort === 'level' || $sort === 'created') ? 'desc' : 'asc';
  ```
  ```php
  } else if ($key === 'level') {
      // 난이도는 첫 클릭에 내림차순
      $nextDir = 'desc';
  }
  ```
- 확신도: 확실
- 비고: 두 지점(요약 표시용 328-331, 정렬 링크용 410-413)이 각각 구현되어 있고 결과는 서로 일치함(둘 다 level→desc)

- ID: BR-26
- 규칙: 검색 결과는 한 페이지에 20건씩 표시한다.
- 근거: search.php:349, search.php:523
- 근거 코드:
  ```php
  $offset = ($page - 1) * 20;
  ```
  ```php
  $limit  = " LIMIT 20 OFFSET " . (int)$offset;
  ```
- 확신도: 확실
- 비고: 매직 넘버 20 (아래 표 참고), 두 지점 모두 동일한 값으로 일관됨

- ID: BR-27
- 규칙: 페이지 번호가 1보다 작으면 1로, 999보다 크면 999로 보정한다(999 초과 시 경고 표시).
- 근거: search.php:342-348
- 근거 코드:
  ```php
  if ($page < 1) {
      $page = 1;
  }
  if ($page > 999) {
      $page = 999;
      $warnings[] = '페이지 번호는 999 를 넘을 수 없습니다.';
  }
  ```
- 확신도: 확실
- 비고: 매직 넘버 999 (아래 표 참고)

- ID: BR-28
- 규칙: 페이지 번호(page)가 숫자 형식이 아니면 1페이지로 대체하며, 값이 빈 문자열이거나 `'1'`이었을 때만 경고 없이 넘어가고 그 외에는 경고를 표시한다.
- 근거: search.php:337-341
- 근거 코드:
  ```php
  if (preg_match('/^[0-9]+$/', $pageRaw)) {
      $page = (int)$pageRaw;
  } else if ($pageRaw !== '' && $pageRaw !== '1') {
      $warnings[] = '페이지 번호가 올바르지 않아 1페이지를 표시합니다.';
  }
  ```
- 확신도: 확실
- 비고: 없음

- ID: BR-29
- 규칙: 검색 결과는 뷰 `v_item_public`을 통해서만 조회되며, 이 뷰는 상태(`status`)가 `'A'`(공개)인 문항만 포함한다 — 즉 `'R'`(검수중)·`'D'`(삭제) 문항은 검색 결과에서 제외된다.
- 근거: search.php:521-522, 01-schema.sql:52-70(뷰 정의 `WHERE i.status = 'A'`, 01-schema.sql:70)
- 근거 코드:
  ```php
  $select = "SELECT id, title, unit_code, level, tag_names, created_at";
  $from   = " FROM v_item_public";
  ```
  ```sql
  FROM item i
  JOIN unit u ON u.id = i.unit_id
  WHERE i.status = 'A';
  ```
- 확신도: 확실
- 비고: 뷰 정의는 `db/mariadb/init/01-schema.sql`에 있고 `search.php`는 이 뷰만 사용한다(item 테이블을 직접 SELECT하지 않음)

- ID: BR-30
- 규칙: 단원 목록 화면의 "공개 문항 수"는 상태가 `'A'`인 문항만 센다(검수중·삭제 문항은 세지 않음).
- 근거: units.php:15-17
- 근거 코드:
  ```php
  // 단원별 공개(status='A') 문항 수. 삭제 · 검수중 문항은 세지 않는다.
  (SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count
  ```
- 확신도: 확실
- 비고: 주석과 코드 일치

## 매직 넘버

| 값 | 위치 | 추정 의미 |
|---|---|---|
| 5 | register.php:49 | 문항 제목 최소 길이(자) |
| 200 | register.php:52 | 문항 제목 최대 길이(자) — `item.title VARCHAR(200)`(01-schema.sql:23)과 일치 |
| 1~5 | register.php:70, search.php:217 | 문항 난이도 허용 범위(정규식 `/^[1-5]$/`) |
| 100 | search.php:86 | 검색 키워드 최대 길이(자) |
| 50 | search.php:240 | 태그 이름 최대 길이(자) |
| 20 | search.php:349, search.php:523 | 검색 결과 페이지당 건수 |
| 999 | search.php:346 | 검색 결과 최대 페이지 번호 |
| 5 | search.php:215 (`level < 5`) | 난이도 미지정 시 결과 제외 기준값 — BR-07 참고. 같은 줄의 주석("1~5 모두 포함")과 실제 동작이 다르다 |

## 코드가 아닌 주석에만 있는 항목 (규칙으로 올리지 않음)

- `inc/layout.php:4` — "표 안에는 세션 · 시각 같은 값을 넣지 않는다"는 주석이 있으나, 이를 강제하는 조건문이나 검증 코드는 없다(현재 `render_header`/`render_footer`가 세션·시각 값을 안 쓰는 것은 사실이지만 "막는 로직"은 존재하지 않음). 주석만 있음.
