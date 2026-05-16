# S05 지난 결과 이력 — 최근 커밋 리뷰

> 작성자: 김태호 수석 엔지니어
> 작성일: 2026-05-16
> 리뷰 범위: 최근 커밋 3개 (dda47fc, 095d748, 699ab5d)

## 총평

S05 지난 결과 이력 기능은 BE/FE 양쪽이 기존 `getWeekResult` 패턴을 그대로 차용해 일관성 있게 구현됐고, 이력 집계를 쿼리 2회로 끝내 N+1을 피한 점은 합격이다. 다만 상세 API(`/history/{year}/{month}/{week}`)가 경로 변수에 대한 입력 검증 없이 그대로 SQL로 흘려보내고, 추첨 결과 조회 쿼리(`findWeekDrawResultByScope`)에 `user_seq` 필터가 없어 다른 사용자의 주차 정보로 응답이 만들어질 수 있는 권한/검증 공백이 있다. FE 상세 화면은 API 실패 시 에러 배너와 "데이터 없음" UI가 동시에 노출되는 어색한 상태가 존재한다. `rank` 예약어 backtick 수정(699ab5d)은 매퍼 전체를 확인한 결과 누락 없이 일관 적용됐다. 전반적으로 출시 가능한 수준이나 아래 Major 2건은 머지 전 정리할 것을 권한다.

## 커밋별 요약

- `dda47fc` — BE 이력 조회 API 2종 신설. `GET /api/result/history`(주차별 이력 목록), `GET /api/result/history/{year}/{month}/{week}`(당첨 세트 상세). DAO 3개 메서드, 매퍼 쿼리 3개, `ResultHistoryItemResponse` DTO 추가. 집계는 서비스 레이어에서 Map 기반으로 처리.
- `095d748` — FE 목록/상세 화면. `ResultHistoryPage`(주차 리스트 + 등수 배지), `ResultHistoryDetailPage`(당첨 세트 표시, S03 시각 패턴 차용), `SideMenu` 메뉴 항목 추가, `App.jsx` 라우트 2개 등록, `api/result.js` 함수 2개 추가.
- `699ab5d` — `findRankSummariesForUser`, `findRankedSetsByScope` 쿼리에서 MySQL 8 예약어 `rank`에 backtick 누락분 보강(6곳). 단순 핫픽스.

## 심각도 요약

| 심각도 | 건수 |
|--------|------|
| 🔴 Critical | 0 |
| 🟠 Major | 2 |
| 🟡 Minor | 5 |
| 🔵 Suggestion | 3 |

## 발견 사항

### 🟠 Major

#### [M-1] 상세 API의 경로 변수 입력 검증 부재

- **위치**: `backend/.../result/controller/ResultController.java:46-53`
- **문제**: `@PathVariable int year/month/week`를 검증 없이 받아 그대로 서비스·DAO로 전달한다. `int` 파싱 실패(`/history/abc/5/1`)는 Spring이 400으로 막아주지만, `0`·음수·`/history/2026/99/0` 같은 의미상 잘못된 값은 검증 없이 SQL `WHERE` 절로 흘러간다. 결과는 빈 세트지만, 200으로 "데이터 없음" 응답이 나가 클라이언트가 정상 주차와 잘못된 주차를 구분할 수 없다.
- **영향**: 잘못된 URL을 직접 입력해도 에러가 아닌 정상 응답으로 처리됨. 향후 이력 외 경로(즐겨찾기, 공유 링크 등)로 진입점이 늘면 디버깅이 어려워진다.
- **권장 조치**: `month`(1~12), `week`(1~6), `year`(현실적 하한, 예: 2020 이상) 범위를 컨트롤러 또는 서비스 진입부에서 검증하고, 벗어나면 `400 Bad Request`(`ResponseStatusException`)로 응답할 것. 기존 `getWeekResult`가 `Integer` nullable로 동작하는 것과 달리 상세 API는 필수 경로 변수이므로 검증 책임이 명확하다.

#### [M-2] `findWeekDrawResultByScope`에 `user_seq` 필터 없음 — 타 사용자 주차 정보 노출 가능

- **위치**: `backend/.../mapper/result/ResultMapper.xml:104-123` (호출: `ResultServiceImpl.java:169`)
- **문제**: `getResultHistoryDetail`은 세트 목록(`findRankedSetsByScope`)에는 `user_seq`를 거는 반면, 추첨 결과 헤더(`findWeekDrawResultByScope`)는 `user_seq` 없이 `satto_number_set`을 JOIN해 `target_year/month/week`만으로 draw 결과를 찾는다. 즉 호출자가 해당 주차에 세트를 한 건도 만들지 않았어도, **다른 사용자가** 그 주차에 세트를 만들었다면 추첨 회차·당첨 번호·추첨일이 응답에 채워진다.
- **영향**: 추첨 번호 자체는 공개 정보라 정보 유출 심각도는 낮지만, "본인 이력"이라는 화면 의미와 어긋난다. 본인이 만들지 않은 임의 주차 URL을 찍어도 추첨 헤더가 표시되어, history 목록에 없는 주차가 상세에서는 정상 화면처럼 보인다.
- **권장 조치**: 상세 API는 "사용자가 세트를 보유한 주차"로 제한되어야 한다. (1) `findRankedSetsByScope` 결과가 비어 있고 동시에 사용자가 해당 주차 세트를 보유하지 않으면 `404`로 응답하거나, (2) `findWeekDrawResultByScope`에 `user_seq` 조건을 추가해 본인 세트가 있는 주차만 헤더를 채우도록 한다. `getWeekResult`(달력 fallback 경로)와 공유되는 쿼리이므로, 별도 파라미터 추가 또는 신규 쿼리 분리를 검토할 것.

### 🟡 Minor

#### [m-1] 상세 화면: API 실패 시 에러 배너와 "데이터 없음" UI 동시 노출

- **위치**: `frontend/src/pages/ResultHistoryDetailPage.jsx:25-29, 75-99`
- **문제**: `catch`에서 `error`만 세팅하고 `result`는 `null`로 남는다. `loading`이 끝나면 헤더 섹션이 항상 렌더되므로, API 실패 시 화면에 빨간 에러 배너 + "이 주차의 추첨 결과 정보가 없습니다" + "당첨된 세트가 없습니다"가 한꺼번에 표시된다. 사용자는 "실패"인지 "데이터 없음"인지 구분할 수 없다.
- **권장 조치**: `error`가 있을 때는 에러 전용 화면(또는 에러 배너 + 뒤로가기 버튼)만 렌더하고, 본문(`section` 2개)은 `!error && result` 조건으로 가드할 것. 목록 화면(`ResultHistoryPage`)은 빈 목록만 그리므로 상대적으로 덜 어색하지만 동일하게 점검 권장.

#### [m-2] 상세 화면: API 실패해도 라우트 변경 시 재요청 안 됨

- **위치**: `frontend/src/pages/ResultHistoryDetailPage.jsx:21-32`
- **문제**: `loading` 초기값이 `true`이고 `useEffect`는 `[year, month, week]` 의존이라 동작 자체는 맞다. 다만 한 번 `error` 상태가 되면 `error`를 비우지 않은 채 다음 주차로 이동 시 이전 에러가 잠깐 남을 수 있다. `load()` 진입부에서 `setLoading(true); setError('')`를 명시하지 않았다(목록 페이지도 동일). `ResultPage`는 `load()` 첫 줄에서 `setLoading(true); setError('')`를 호출한다 — 패턴 불일치.
- **권장 조치**: `load()` 시작부에 `setLoading(true); setError('')`를 추가해 기존 `ResultPage` 패턴과 맞출 것.

#### [m-3] `topRank` 계산이 동일 스트림을 두 번 평가

- **위치**: `backend/.../result/service/impl/ResultServiceImpl.java:160-162`
- **문제**: `summary.stream().mapToInt(...).min().isPresent()`로 한 번, `...min().getAsInt()`로 또 한 번, 같은 최솟값 계산을 2회 수행한다. 데이터량이 작아 성능 문제는 없으나 불필요하다. 게다가 `findRankSummariesForUser` 쿼리가 이미 `smr.rank` 오름차순으로 정렬돼 들어오므로, 집계 리스트의 첫 원소가 곧 `topRank`다.
- **권장 조치**: `summary.stream().mapToInt(RankCount::rank).min().stream().boxed().findFirst().orElse(null)` 또는 `OptionalInt`를 한 번만 받아 분기. 혹은 정렬 보장을 활용해 `summary.isEmpty() ? null : summary.get(0).rank()`.

#### [m-4] `getResultHistory`의 `hasMatch`는 `rankSummary`와 의미 중복

- **위치**: `backend/.../result/dto/ResultHistoryItemResponse.java:8-11`, `ResultServiceImpl.java:163`
- **문제**: `hasMatch = !summary.isEmpty()`로 계산되어, `rankSummary`가 비었는지와 항상 동치다. FE(`ResultHistoryPage.jsx:62`)도 `item.hasMatch`로만 분기한다. 필드가 하나 더 있어 나쁠 건 없지만, 두 값이 어긋날 수 없는 파생 필드를 DTO에 두면 향후 한쪽만 바뀔 때 버그 소지가 된다.
- **권장 조치**: 그대로 둬도 무방하나, 유지한다면 "파생 필드"임을 주석으로 명시하거나 FE에서 `rankSummary.length > 0`으로 판단해 필드를 제거하는 방향을 검토.

#### [m-5] 상세 화면 `RANK_TONE`/`RANK_LABEL` 미정의 rank 방어 없음

- **위치**: `frontend/src/pages/ResultHistoryDetailPage.jsx:8-15, 124-126`
- **문제**: `RANK_TONE[item.rank]`, `RANK_LABEL[item.rank]`는 rank 1~5만 정의돼 있다. `findRankedSetsByScope`가 `rank IS NOT NULL`만 거르므로 BE `determineRank`상 1~5만 들어오는 게 정상이지만, 데이터 이상(예: 과거 데이터 마이그레이션, rank=0)이 발생하면 `className`이 `undefined`가 되고 배지 텍스트가 빈 값으로 렌더된다. 목록 페이지(`RANK_BADGE`)도 동일.
- **권장 조치**: `RANK_TONE[item.rank] ?? '<기본 톤>'`, `RANK_LABEL[item.rank] ?? `${item.rank}등`` 형태의 fallback을 둘 것. 비용이 거의 없는 방어 코드다.

### 🔵 Suggestion

#### [S-1] 상세 API 권한·존재 검증을 서비스 진입부로 통일

- **위치**: `backend/.../result/service/impl/ResultServiceImpl.java:167-196`
- **내용**: M-1, M-2를 함께 해결하는 형태로, `getResultHistoryDetail` 진입부에서 (a) 파라미터 범위 검증, (b) 본인 세트 보유 여부 확인을 묶어 처리하면 컨트롤러는 얇게 유지된다. `getWeekResult`의 fallback 로직과 코드 중복이 있으니 draw 헤더 → `ResultWeekResponse` 매핑부를 private 헬퍼로 추출하는 것도 같이 고려.

#### [S-2] FE: 로딩 스피너 문구 중복 정의

- **위치**: `frontend/src/pages/ResultHistoryPage.jsx:27-37`, `ResultHistoryDetailPage.jsx:35-44`
- **내용**: 두 페이지의 로딩 박스 마크업이 사실상 동일하다. `ResultPage`까지 합치면 3곳이다. 공통 `<LoadingPanel message="..." />` 컴포넌트로 추출하면 시각 일관성과 유지보수성이 올라간다. 이번 PR 범위를 넘어서므로 후속 정리 항목으로 제안.

#### [S-3] `scopeKey` 대신 타입 안전한 키 고려

- **위치**: `backend/.../result/service/impl/ResultServiceImpl.java:198-200`
- **내용**: `year + "-" + month + "-" + week` 문자열 키는 구분자가 있어 충돌은 없지만, record 키(`record ScopeKey(int year,int month,int week)`)를 쓰면 의도가 명확하고 문자열 파싱 실수 여지가 없다. 소규모 개선 제안.

## 잘된 점

- 이력 집계를 쿼리 2회(`findAllScopesForUser` + `findRankSummariesForUser`)로 끝내고 서비스에서 Map join 하여 N+1을 피했다. 주차 수가 늘어도 쿼리 횟수가 일정하다.
- `699ab5d`의 `rank` backtick 수정은 매퍼 전체(`upsertMatchResult`, `findMatchedSetsByScope`, `findRankSummariesForUser`, `findRankedSetsByScope`)를 확인한 결과 컬럼 별칭 포함 모든 사용처에 일관 적용됐다. 누락 없음.
- FE가 `ResultPage`(S03)의 `RANK_LABEL`/`RANK_TONE`, `NumberBall`, 로딩/에러 패턴을 그대로 재사용해 화면 간 시각 일관성이 유지된다.
- 네이밍 규칙 준수: 코드·주석·UI 문구에서 "lotto/로또" 표현 없이 "Satto/satto_" 및 "추첨/번호 세트"로 일관 표기됐다.
- DTO를 Java `record`로 정의해 불변성과 간결성을 확보했고, `topRank`를 `Integer`(nullable)로 둬 미당첨 주차를 자연스럽게 표현했다.
- 상세 화면이 `winningNumbers?.includes(n)`로 일치 번호를 강조하고, `result?.items?.length ?? 0` 등 옵셔널 체이닝으로 null을 방어한 부분은 견고하다.

## 종합 권고

(우선순위 순)

1. **[M-2]** `getResultHistoryDetail`을 "본인 세트 보유 주차"로 제한 — `user_seq` 필터 추가 또는 미보유 시 404 처리. 화면 의미와 데이터 접근 범위를 일치시킬 것.
2. **[M-1]** 상세 API 경로 변수(`year/month/week`) 범위 검증을 추가하고 잘못된 값은 400으로 응답. S-1과 묶어 서비스 진입부에서 한 번에 처리 권장.
3. **[m-1]** 상세 화면에서 API 실패 시 본문 섹션을 가드해 에러/빈상태가 동시 노출되지 않도록 수정.
4. **[m-2]** FE `load()` 진입부에 `setLoading(true); setError('')` 추가 — 기존 `ResultPage` 패턴과 정합성 확보.
5. **[m-3]·[m-4]·[m-5]** `topRank` 중복 평가 정리, `hasMatch` 파생 필드 처리 방침 결정, FE rank 배지 fallback 추가 — 머지 후 후속 정리로 묶어도 무방.
6. **[S-1~S-3]** 공통 로딩 컴포넌트 추출, 매핑 헬퍼 추출, 타입 안전 키 등 리팩터링 제안은 별도 정리 PR로.
