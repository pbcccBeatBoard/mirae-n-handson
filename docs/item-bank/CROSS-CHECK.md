# 문항 은행 레거시(PHP) 비즈니스 규칙 — 코드 기준 추출

- 대상: `legacy/item-bank-php/` 의 `search.php`, `register.php`, `units.php` (`vendor/` 는 읽지 않음, `docs/` 는 참조하지 않음)
- 원칙: 코드에 있는 것만 기재. 주석과 코드가 다르면 코드 기준.
- 줄번호는 각 파일 기준이다.

## 1. 검색 조건 조합 (단원 · 난이도 · 태그 · 키워드)

### CX-01 — 검색 조건은 AND 로 결합된다
모든 조건(키워드 · 단원 · 난이도 · 태그)은 `WHERE 1=1` 에 `AND` 로 이어 붙는다.
- 근거: `search.php:44`, `:98`, `:118`, `:226`, `:244`
```php
$where    = " WHERE 1=1";
$where .= " AND unit_code = ?";
```

### CX-02 — 검색 대상은 공개 문항 뷰(`v_item_public`)뿐이다
목록과 건수 모두 `v_item_public` 을 조회한다. 어떤 상태의 문항이 이 뷰에 포함되는지는 이 폴더의 코드로는 알 수 없다(뷰 정의는 범위 밖).
- 근거: `search.php:519-526`
```php
$from   = " FROM v_item_public";
$countSql = "SELECT COUNT(*) AS cnt" . $from . $where;
```

### CX-03 — 단원은 코드와 정확히 일치해야 한다 (대소문자 변환 · 형식 보정 없음)
입력값을 trim 만 하고 `unit_code = ?` 로 비교한다. 형식이 틀리거나 소문자여도 경고만 내고 입력값 그대로 조회한다.
- 근거: `search.php:109-121`
```php
if (!preg_match('/^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$/', $unit)) { $warnings[] = ...
$where .= " AND unit_code = ?";
```

### CX-04 — 등록되지 않은 단원 코드도 조회하며, 경고만 표시한다
`unit` 테이블에 없는 코드면 경고 문구를 추가하되 검색은 그대로 실행한다.
- 근거: `search.php:141-143`
```php
if ($unitName === '') {
    $warnings[] = '등록되지 않은 단원 코드입니다: ' . $unit;
```

### CX-05 — 난이도가 비어 있으면 난이도 5 문항은 제외된다 (주석과 코드 불일치)
주석은 "전체 난이도 검색 (1~5 모두 포함)" 이라고 하지만, 코드는 `level < 5` 를 붙여 난이도 5 를 뺀다. 코드 기준으로 난이도 미지정 검색에는 난이도 5 가 나오지 않는다.
- 근거: `search.php:213-216`
```php
// 난이도 값이 비어 있으면 전체 난이도 검색 (1~5 모두 포함)
if ($level == '') {
    $where .= " AND level < 5";
```

### CX-06 — 난이도 5 문항은 `level=5` 를 명시해야만 검색된다
`1~5` 한 자리 숫자면 `level = ?` 정확 일치. CX-05 와 합치면 난이도 5 는 명시 지정 때만 노출된다.
- 근거: `search.php:217-221`
```php
else if (preg_match('/^[1-5]$/', $level)) {
    $where .= " AND level = ?";
```

### CX-07 — 범위 밖 난이도 값은 정수로 캐스팅해 그대로 비교한다
1~5 형식이 아니면 경고 후 `(int)` 값으로 `level = ?` 비교한다. 예: `abc` → 0 으로 비교.
- 근거: `search.php:223-229`
```php
$warnings[] = '난이도는 1~5 사이여야 합니다.';
$values[] = (int)$level;
```

### CX-08 — 태그는 이름 정확 일치이며 단일 값만 받는다
`item_tag`·`tag` 를 `EXISTS` 로 조회하고 `t.name = ?` 로 비교한다. `%`·`_` 가 있으면 "부분 일치 미지원" 경고를 낸다. 태그는 50자로 잘린다.
- 근거: `search.php:236-248`
```php
if (mb_strlen($tag, 'UTF-8') > 50) { $tag = mb_substr($tag, 0, 50, 'UTF-8');
$where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
        . " WHERE it.item_id = v_item_public.id AND t.name = ?)";
```

### CX-09 — 미등록 태그도 조회하며 경고만 표시한다
- 근거: `search.php:258-260`
```php
if (!$tagKnown) {
    $warnings[] = '등록되지 않은 태그입니다: ' . $tag;
```

### CX-10 — 키워드는 제목 또는 지문의 부분 일치(LIKE)이며 100자로 잘린다
trim 후 100자 초과는 자르고 경고. `%`·`_` 는 이스케이프하지 않아 와일드카드로 동작한다(경고만 표시).
- 근거: `search.php:85-101`
```php
$like = '%' . $q . '%';
$where .= " AND (title LIKE ? OR stem LIKE ?)";
```

### CX-11 — 같은 이름의 파라미터가 배열로 오면 첫 값만 사용한다
- 근거: `search.php:54-56` (다른 파라미터도 동일)
```php
$q = is_array($params['q']) ? (string)reset($params['q']) : (string)$params['q'];
```

### CX-12 — 검색 값은 모두 바인딩 파라미터로 전달된다
값은 `?` + `bind_param` 으로 넘기며, 타입 문자열 길이와 값 개수가 다르면 예외로 중단한다.
- 근거: `search.php:535-539`
```php
if (strlen($types) !== count($values)) {
    throw new RuntimeException('검색 조건 조립 오류');
```

## 2. 목록 기본 정렬 · 페이지 · 제외 조건

### CX-13 — 기본 정렬은 난이도 내림차순, 같으면 ID 오름차순
`sort` 가 비어 있으면 적용된다.
- 근거: `search.php:317-319`
```php
case '':
    $orderBy = " ORDER BY level DESC, id ASC";
```

### CX-14 — 알 수 없는 정렬 기준은 경고 후 기본 정렬로 대체한다
- 근거: `search.php:322-325`
```php
$warnings[] = '알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.';
$orderBy = " ORDER BY level DESC, id ASC";
```

### CX-15 — 정렬 기준은 id · title · unit · level · created 로 화이트리스트되며, 모두 `id ASC` 를 보조키로 쓴다 (id 정렬 제외)
정렬 컬럼은 `switch` 로 고정 문자열만 쓴다. `dir` 은 `asc`/`desc` 외에는 경고 후 무시한다. 기본 방향: `level`·`created` 는 desc, 나머지는 asc.
- 근거: `search.php:266-331`
```php
case 'unit':  ... " ORDER BY unit_code DESC, level DESC, id ASC"
case 'level': ... $dir === 'asc' ? " ORDER BY level ASC, id ASC" : " ORDER BY level DESC, id ASC"
```
- 참고: `unit` 정렬은 asc 여도 `level DESC` 이다(`:297`).

### CX-16 — 페이지 크기는 20건이고 페이지 번호는 1~999 로 보정된다
숫자가 아니면 1페이지, 0 이하는 1, 999 초과는 999 로 조정한다.
- 근거: `search.php:336-349`, `:523`
```php
if ($page > 999) { $page = 999;
$limit  = " LIMIT 20 OFFSET " . (int)$offset;
```

### CX-17 — 결과가 0건이면 "검색 결과가 없습니다" 메시지를 표시한다
- 근거: `search.php:657-659`
```php
if ($total === 0) {
    echo '<p id="message">검색 결과가 없습니다</p>' . "\n";
```

### CX-18 — 태그가 없는 문항은 태그 칸을 빈 문자열로 표시한다
- 근거: `search.php:678`
```php
echo '<td>' . h($r['tag_names'] === null ? '' : $r['tag_names']) . '</td>';
```

### CX-19 — 단원 목록의 공개 문항 수는 `status = 'A'` 만 센다
삭제 · 검수 중 문항은 제외한다. 단원은 학년 → 코드 순으로 정렬한다.
- 근거: `units.php:15-19`
```php
(SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count
ORDER BY u.grade ASC, u.code ASC
```

## 3. 등록 시 검증 (`register.php`)

### CX-20 — 제목은 trim 후 5자 이상 200자 이하다
공백 제외 계산은 하지 않고 앞뒤 공백만 제거한다. 길이는 `mb_strlen` 기준.
- 근거: `register.php:41`, `:49-54`
```php
if (mb_strlen($title, 'UTF-8') < 5) {
if (mb_strlen($title, 'UTF-8') > 200) {
```

### CX-21 — 지문은 필수다 (trim 후 빈 문자열 불가, 길이 상한 검증 없음)
- 근거: `register.php:56-58`
```php
if ($stem === '') {
    $errors[] = '지문을 입력해야 합니다.';
```

### CX-22 — 단원은 `unit` 테이블에 존재하는 id 여야 한다
- 근거: `register.php:60-68`
```php
if ((string)$u['id'] === $unitId) { $unitOk = true; }
if (!$unitOk) { $errors[] = '단원을 선택해야 합니다.';
```

### CX-23 — 난이도는 `1`~`5` 한 자리 숫자여야 한다
- 근거: `register.php:70-72`
```php
if (!preg_match('/^[1-5]$/', $level)) {
```

### CX-24 — 태그는 선택 사항이며, 목록에 없는 태그 id 는 오류 없이 조용히 버려진다
유효한 태그만 저장한다. 잘못된 태그가 섞여도 오류 메시지는 없다.
- 근거: `register.php:73-81`
```php
if ((string)$t['id'] === (string)$tid) { $validTagIds[] = (int)$tid; }
```
- 참고: 같은 태그 id 가 중복 전송되면 `$validTagIds` 에도 중복 저장되어 `item_tag` 삽입이 실패할 수 있다(`:78`, `:105-111`). 중복 제거 코드는 없다.

### CX-25 — 신규 문항은 검수 중(`R`) 상태로 저장된다
등록 즉시 검색에 나오지 않는다는 것이 코드 주석과 안내 문구의 전제이다. (검색 뷰가 `R` 을 제외하는지는 이 폴더 코드만으로는 확인 불가 — CX-02 참고.)
- 근거: `register.php:84`, `:92-95`, `:116`
```php
VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())
```

### CX-26 — 새 ID 는 `MAX(id)+1` 로 채번하고, 문항과 태그를 한 트랜잭션으로 저장한다
실패 시 롤백하고 "저장 중 오류" 만 표시한다.
- 근거: `register.php:85-90`, `:114`, `:119-123`
```php
$conn->begin_transaction();
"SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM item FOR UPDATE"
```

## 4. 범위 관련 확인 사항

- **수정(update) 검증 규칙은 없다.** `legacy/item-bank-php/` 에는 수정 화면 · 수정 쿼리가 없다(`index.php`, `register.php`, `search.php`, `units.php` 뿐). 그래서 "등록 · 수정 시 검증" 중 수정 쪽 규칙은 추출하지 않았다.
- `v_item_public` 뷰 정의(어떤 상태 · 조건으로 제외하는지)는 DB 초기화 스크립트에 있으며 이번 범위(`item-bank-php` 폴더)에 포함되지 않아 읽지 않았다. 제외 조건은 CX-19(`status='A'`, units.php) 와 CX-25(`R` 로 저장) 로만 간접 확인된다.
- `inc/db.php`, `inc/layout.php`, `index.php` 에는 검색 · 검증 규칙이 없어 규칙 추출 대상에서 제외했다(표시용 `h()` 등 보조 코드).
