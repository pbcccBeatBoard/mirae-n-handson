// item-bank · 문항 검색(search.php) 동작 보존 테스트
//
// 대상 규칙: docs/item-bank/BUSINESS-RULES.md 의 BR-01~BR-12(검색 조건), BR-22~BR-29(정렬·페이지·제외 조건).
// 케이스 14개 = 정상 3 · 경계값 6 · 빈 값·누락 2 · 이상한 값 3 (README.md "케이스 작성 규칙"의 최소치를 넘김).
//
// - 대상 주소는 lib/target.mjs 가 정한다(TARGET_BASE_URL, 없으면 item-bank 레거시 기본 주소 8081).
// - 기대값을 손으로 적지 않는다. npm run baseline -- item-bank 로 찍은 스냅샷이 기준이다.
// - 이관 후에는 TARGET_BASE_URL 만 바꿔 같은 케이스를 돌린다(경로는 lib/target.mjs 의 PATH_ALIASES 가 처리).
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'item-bank';

describe('item-bank · 문항 검색(search.php)', () => {
  describe('정상 입력', () => {
    it('키워드 검색 — 일반 검색어(분수) [BR-01, BR-02]', async () => {
      const result = await fetchNormalized(MODULE, '/search.php', { q: '분수' });
      expect(result).toMatchSnapshot();
    });

    it('단원 지정 검색 — 실제 등록된 코드(M5-1) [BR-04, BR-06]', async () => {
      const result = await fetchNormalized(MODULE, '/search.php', { unit: 'M5-1' });
      expect(result).toMatchSnapshot();
    });

    it('난이도 지정 검색 — 범위 안의 값(3) [BR-08]', async () => {
      const result = await fetchNormalized(MODULE, '/search.php', { level: 3 });
      expect(result).toMatchSnapshot();
    });
  });

  describe('경계값', () => {
    it('키워드 길이 100자 — 자르기 직전 [BR-01]', async () => {
      const result = await fetchNormalized(MODULE, '/search.php', { q: '가'.repeat(100) });
      expect(result).toMatchSnapshot();
    });

    it('키워드 길이 101자 — 자르기 시작 [BR-01]', async () => {
      const result = await fetchNormalized(MODULE, '/search.php', { q: '가'.repeat(101) });
      expect(result).toMatchSnapshot();
    });

    it('난이도 빈 값 — level<5 주석·코드 불일치 재현 [BR-07]', async () => {
      const result = await fetchNormalized(MODULE, '/search.php', { level: '' });
      expect(result).toMatchSnapshot();
    });

    it('난이도 5 명시 — 빈 값(BR-07)과 대조 [BR-07, BR-08]', async () => {
      const result = await fetchNormalized(MODULE, '/search.php', { level: 5 });
      expect(result).toMatchSnapshot();
    });

    it('페이지 번호 999 — 허용 최대값 [BR-27]', async () => {
      const result = await fetchNormalized(MODULE, '/search.php', { page: 999 });
      expect(result).toMatchSnapshot();
    });

    it('페이지 번호 1000 — 999 초과 시작(보정) [BR-27]', async () => {
      const result = await fetchNormalized(MODULE, '/search.php', { page: 1000 });
      expect(result).toMatchSnapshot();
    });
  });

  describe('빈 값 · 누락', () => {
    it('파라미터 전체 생략 — 기본 동작 [BR-07, BR-22, BR-26, BR-28, BR-29]', async () => {
      const result = await fetchNormalized(MODULE, '/search.php', {});
      expect(result).toMatchSnapshot();
    });

    it('키워드 빈 문자열 명시 — 파라미터 생략과 구분 [BR-01, BR-02]', async () => {
      const result = await fetchNormalized(MODULE, '/search.php', { q: '' });
      expect(result).toMatchSnapshot();
    });
  });

  describe('이상한 값', () => {
    it('난이도 음수값(-1) [BR-09]', async () => {
      const result = await fetchNormalized(MODULE, '/search.php', { level: -1 });
      expect(result).toMatchSnapshot();
    });

    it('태그 아주 긴 문자열(60자, 50자 제한 초과) [BR-10]', async () => {
      const result = await fetchNormalized(MODULE, '/search.php', { tag: '가'.repeat(60) });
      expect(result).toMatchSnapshot();
    });

    it('존재하지 않는 단원 코드(형식은 맞음, Z9-9) [BR-04, BR-06]', async () => {
      const result = await fetchNormalized(MODULE, '/search.php', { unit: 'Z9-9' });
      expect(result).toMatchSnapshot();
    });
  });
});
