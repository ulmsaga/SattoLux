# SattoLux 코드베이스 리뷰

> 작성자: Reviewer Bot (독립 코드 리뷰어)
> 작성일: 2026-05-16
> 리뷰 범위: 전체 코드베이스 (backend + frontend)

## 총평

SattoLux는 Spring Boot + MyBatis 백엔드와 React + Vite 프론트엔드로 구성된 번호 생성/결과 조회 서비스다. 전반적으로 계층 분리(controller/service/dao)가 명확하고, 인증 설계(RSA 비밀번호 전송, JWT, OTP, 계정 잠금, Refresh Token 로테이션 + 해시 저장)는 동일 규모 프로젝트 평균 이상으로 잘 잡혀 있다. MyBatis 매퍼는 전부 `#{}` 바인딩을 사용해 SQL 인젝션 위험이 없고, `rank` 예약어도 backtick 처리가 되어 있다. 그러나 **보안 측면에서 심각한 결함이 하나 있다**: OTP 검증 단계가 발급된 인증 세션과 OTP 코드의 소유자 일치만 확인할 뿐, 1차 인증을 거치지 않고도 access 토큰을 가진 사용자가 `pin-setup`처럼 인증된 API를 사용할 수 있는 점, 그리고 Refresh Token 재사용 탐지(reuse detection)가 없다는 점이 우려된다. 또한 프론트엔드 토큰 갱신 로직의 모듈 레벨 guard(`_initialRefreshDone`)가 B003 버그(3회 새로고침 시 로그아웃)의 직접적 원인으로 보인다. 코드 품질 면에서는 `Map<String,Object>` + 수동 `getString/getLong/getInt` 캐스팅 헬퍼가 5개 클래스에 거의 동일하게 중복되어 있어 유지보수 부담이 크다.

## 심각도 요약

| 심각도 | 건수 |
|--------|------|
| 🔴 Critical | 2 |
| 🟠 Major | 6 |
| 🟡 Minor | 7 |
| 🔵 Suggestion | 5 |

## 발견 사항

### 🔴 Critical

#### [C-1] 프론트엔드 토큰 갱신 guard가 영구히 닫혀 B003(새로고침 로그아웃)을 유발

- **위치**: `frontend/src/context/AuthContext.jsx:10`, `frontend/src/context/AuthContext.jsx:79-91`
- **문제**: 모듈 레벨 변수 `_initialRefreshDone`은 한 번 `true`가 되면 페이지가 완전히 재로드(F5 등)되어 JS 모듈이 새로 평가되기 전까지 절대 `false`로 돌아가지 않는다. 그런데 새로고침 시 초기 refresh effect는 `_initialRefreshDone` 가드로 보호되어 있어, `apiRefresh`가 성공/실패하더라도 가드는 해제되지 않는다. 더 심각한 실제 원인은 **Refresh Token 로테이션과의 상호작용**이다:
  1. `LoginServiceImpl.refresh()`(`backend/.../LoginServiceImpl.java:227`)는 매 호출마다 기존 RT를 `revoke`하고 새 RT를 발급한다(one-time use).
  2. 새로고침 1회 → `apiRefresh` 호출 → 성공 시 `saveTokens`로 새 RT가 `sessionStorage`에 저장된다.
  3. 그러나 React StrictMode(개발) 또는 effect 재실행/경합 상황에서 동일 RT로 `refresh`가 **두 번** 날아가면, 두 번째 호출은 이미 revoke된 RT를 제시하므로 401을 받고 `sessionStorage.removeItem(RT_KEY)` → 로그아웃된다.
  4. 또한 `scheduleRefresh`의 타이머와 초기 refresh effect가 같은 RT를 두고 경합할 수 있다. 새 토큰 저장 전에 타이머가 만료되거나, 새로고침 직후 stale RT로 갱신이 트리거되면 revoke된 토큰으로 갱신을 시도한다.
- **영향**: 사용자가 의도치 않게 로그아웃된다. "3회 새로고침"이라는 재현 패턴은 RT 로테이션 + 가드 race 누적 결과로 설명된다. 자세한 분석은 하단 [B003 버그 분석] 참고.
- **권장 조치**: (1) 진행 중인 refresh를 단일 Promise로 묶어 중복 호출을 차단(in-flight dedup). (2) RT 로테이션 시 백엔드에 grace period(직전 RT 1회 재사용 허용) 또는 RT 재사용 탐지 + 토큰 패밀리 무효화를 도입. (3) 프론트는 `_initialRefreshDone` 같은 모듈 전역 가드 대신 `useRef` + AbortController로 effect 클린업을 정확히 구현. (4) `axios` 응답 인터셉터에서 401 → 단일 refresh → 재시도 패턴으로 일원화.

#### [C-2] OTP 검증 실패 시 시도 횟수 경합 + access 토큰만으로 PIN 설정 가능

- **위치**: `backend/.../LoginServiceImpl.java:170-197` (verifyOtpAndIssueToken), `LoginController.java:58-64` (pinSetup)
- **문제**: 두 가지 이슈가 결합되어 있다.
  - **OTP 시도 횟수 경합**: `findLatestActiveOtp` → `increaseOtpAttempt` → `markOtpUsed` 흐름이 트랜잭션/락 없이 실행된다. 동일 OTP에 대해 동시에 여러 검증 요청이 들어오면 `attemptCount`를 각자 stale 값으로 읽어, `MAX_OTP_ATTEMPTS=5` 제한을 우회해 무차별 대입(brute-force) 시도 횟수를 늘릴 수 있다. OTP는 6자리(100만 분의 1)라 시도 횟수 제한이 사실상 유일한 방어선이다.
  - **PIN 설정 권한 범위**: `/api/auth/pin-setup`은 `anyRequest().authenticated()`로만 보호되어 임의의 access 토큰 보유자가 PIN을 (재)설정할 수 있다. 비밀번호 재확인 단계가 없어, access 토큰이 탈취되면 공격자가 자신이 아는 PIN으로 덮어쓰고 이후 PIN 로그인으로 영속 접근할 수 있다.
- **영향**: OTP 무차별 대입 방어 약화, access 토큰 탈취 시 계정 영속 장악 경로 제공.
- **권장 조치**: (1) `verifyOtpAndIssueToken`에 `@Transactional` 적용 및 OTP row를 `SELECT ... FOR UPDATE` 또는 조건부 `UPDATE ... WHERE attempt_count < 5`(affected rows 확인)로 원자적으로 처리. (2) PIN 설정/변경 시 현재 비밀번호 재인증(또는 별도 step-up 인증)을 요구.

### 🟠 Major

#### [M-1] JWT 검증에서 토큰 type을 두 번 파싱하고 audience/issuer 미검증

- **위치**: `backend/.../JwtAuthenticationFilter.java:30-31`, `backend/.../JwtUtil.java:56-71`
- **문제**: `isValid(token)`이 내부에서 `parse()`를 호출하고, 필터는 다시 `parse()`를 호출해 동일 토큰을 2회 파싱·서명검증한다(성능 낭비, 사소). 더 중요한 것은 JWT에 `issuer`/`audience` 클레임이 없고 검증도 하지 않는다는 점이다. 또한 access/refresh 토큰이 **동일한 시크릿**으로 서명되어 `type` 클레임 문자열 검사에만 의존한다. `type` 검사를 누락한 경로가 생기면 refresh 토큰을 access 토큰으로 오용할 수 있다.
- **영향**: 토큰 혼동(token confusion) 공격 표면 존재, 불필요한 CPU 사용.
- **권장 조치**: `parse()` 결과를 한 번만 구해 재사용. `issuer`/`audience` 클레임 추가 및 `requireIssuer/requireAudience`로 검증. 가능하면 access/refresh에 분리된 키 사용.

#### [M-2] RSA 키 저장소가 무제한 증가 — 메모리 누수 / DoS

- **위치**: `backend/.../RsaUtil.java:24-34`
- **문제**: `generateKeyPair`는 `keyStore`(ConcurrentHashMap)에 KeyPair를 넣지만, 항목은 `decrypt`가 호출될 때만 `remove`된다. 사용자가 `/api/auth/rsa-key`만 반복 호출하고 로그인을 완료하지 않으면 2048비트 KeyPair가 무한정 쌓인다. 이 엔드포인트는 인증 없이 누구나 접근 가능하므로 메모리 고갈 DoS가 가능하다. TTL/만료 스윕이 없다(`AuthSessionStore`는 TTL이 있는 것과 대조적).
- **영향**: 인증되지 않은 외부 공격자에 의한 메모리 고갈.
- **권장 조치**: 키 항목에 생성 시각을 부여하고 `@Scheduled` 스윕으로 N분 경과분 제거. 또는 Caffeine 등 만료 캐시 사용. 동시에 IP/세션당 키 발급 rate limit 적용.

#### [M-3] OTP 무차별 대입에 대한 IP/계정 rate limit 부재

- **위치**: `backend/.../LoginServiceImpl.java:152-197`, `LoginController.java:67-83`
- **문제**: `otp/send`와 `otp/verify`는 `permitAll`이고, 발급 rate limit이 없다. `sendOtp`를 반복 호출하면 매번 새 OTP가 `saveOtp`로 INSERT되고 `findLatestActiveOtp`는 가장 최근 것을 반환하므로, 공격자가 OTP를 계속 재발급하며 각 OTP에 대해 5회씩 시도를 누적할 수 있다(C-2의 경합과 결합 시 더 악화). 메일 폭탄(mail flooding)도 가능하다.
- **영향**: OTP 무차별 대입 가능성 증가, 피해자 메일함 스팸.
- **권장 조치**: `authSessionToken`/계정/IP 단위로 OTP 재발급 간격(예: 60초) 및 시간당 횟수 제한. 활성 OTP가 있으면 신규 발급 차단 또는 기존 것 재사용.

#### [M-4] CORS가 `allowCredentials=true` + 와일드카드 패턴 — 설정 오용 위험

- **위치**: `backend/.../CorsConfig.java:16-26`
- **문제**: 현재 기본값은 명시적 origin 목록이라 안전하지만, `setAllowedOriginPatterns`(와일드카드 허용 메서드)를 `setAllowCredentials(true)`와 함께 쓰고 있다. `app.cors.allowed-origin-patterns`에 `*` 또는 `https://*.cafe24.com` 같은 패턴이 환경변수로 주입되면, 자격증명을 동반한 임의 origin 요청이 허용되어 CSRF류 공격에 노출된다(`csrf().disable()` 상태라 더 위험). 설정 실수 한 줄로 보안이 무너지는 구조다.
- **영향**: 환경변수 오설정 시 credential 포함 cross-origin 공격 가능.
- **권장 조치**: `setAllowedOrigins`(정확 매칭)로 전환하거나, 패턴 입력값에 `*` 단독/광역 와일드카드가 포함되면 기동 실패하도록 검증 추가. 운영 origin은 코드/설정에 명시 고정.

#### [M-5] `runLatestResultManualTest`가 전체 사용자 번호 세트를 무차별 처리

- **위치**: `backend/.../ResultController.java:62-66`, `ResultServiceImpl.java:230-262`
- **문제**: 관리자 전용이긴 하나 `runLatestResultManualTest()`는 인자가 없고, `resultDao.findNumberSetsByScope(year, month, week)`(`ResultMapper.xml:60`)는 `user_seq` 필터가 없어 **모든 사용자**의 해당 주차 세트를 가져와 매칭/알림을 생성한다. 스케줄러(`collectLatestResults`)에서는 이 동작이 의도된 것이지만, 수동 테스트 API가 동일 메서드를 재사용하면서 관리자가 "테스트" 한 번에 전체 사용자에게 결과 알림 SSE를 발사하게 된다. `result.findNumberSetsByScope`와 `makeWeekNum.findNumberSetsByScope`가 같은 이름이지만 한쪽만 user 필터가 있어 혼동을 부른다.
- **영향**: 관리자의 테스트 행위가 전체 사용자에게 실 알림을 보냄 — 운영 사고 가능.
- **권장 조치**: 매퍼 ID를 구분(예: `findAllUserNumberSetsByScope`)하고, 수동 테스트는 대상 사용자 범위를 명시적으로 받도록 분리. 크로스 유저 조회 메서드에는 주석으로 의도를 명기.

#### [M-6] 스케줄 번호 생성이 사용자 단위로 트랜잭션 격리되지 않음

- **위치**: `backend/.../MakeWeekNumServiceImpl.java:191-213`
- **문제**: `generateScheduledWeekNumbers()`에 `@Transactional`이 붙어 있고, 루프 내에서 사용자별 `generateCurrentWeekNumbers()`(이 메서드 역시 `@Transactional`, 같은 빈 내부 호출이라 프록시를 거치지 않아 별도 트랜잭션이 생기지 않음)를 호출한다. 즉 전체가 하나의 트랜잭션이다. 한 사용자 처리 중 예외는 `catch`로 잡아 로깅하지만, `catch` 이후에도 동일 트랜잭션이 계속되어 일부 사용자에서 발생한 unchecked 예외가 트랜잭션을 rollback-only로 마킹하면 최종 커밋이 통째로 실패할 수 있다. 또 `generateCurrentWeekNumbers`가 외부 Claude API(`ClaudeNumberGenerator`)를 호출하므로, 네트워크 지연 동안 DB 트랜잭션/커넥션이 장시간 점유된다(HikariCP pool-size=10).
- **영향**: 한 사용자 실패가 다른 사용자 생성을 롤백시킬 위험, 외부 API 호출 중 DB 커넥션 장기 점유로 풀 고갈.
- **권장 조치**: 사용자 단위 처리를 별도 트랜잭션 경계(별도 빈으로 분리하거나 `REQUIRES_NEW`)로 격리. 외부 API 호출은 트랜잭션 밖에서 수행하고, 생성된 결과만 짧은 트랜잭션으로 저장.

### 🟡 Minor

#### [m-1] `application-local.yml`에 실 시크릿이 평문으로 디스크에 존재

- **위치**: `backend/src/main/resources/application-local.yml:6`, `:10`, `:14`
- **문제**: `.gitignore`의 `**/application-local.yml` 패턴으로 git 추적은 되지 않음(확인 완료). 그러나 파일에 운영 가능성이 있는 DB 비밀번호(`tndfPans2013^_`), JWT 시크릿, **실제 Anthropic API 키**(`sk-ant-api03-...`)가 평문으로 들어 있다. 개발자 머신 유출/백업/IDE 인덱싱 등으로 노출될 수 있고, API 키는 즉시 폐기·재발급이 권장된다.
- **영향**: 시크릿 유출 시 DB 접근 및 Anthropic 과금 도용.
- **권장 조치**: 노출된 API 키 즉시 폐기. 로컬 시크릿도 OS 키체인/환경변수/`.env`(역시 gitignore)로 관리. 문서에 "git 미추적이라 안전"이 아니라 "민감정보이므로 공유 금지"임을 명기.

#### [m-2] schema.sql 주석에 "로또" 네이밍 규칙 위반

- **위치**: `backend/src/main/resources/db/schema.sql:107`
- **문제**: 프로젝트 네이밍 규칙은 "lotto/로또" 사용 금지(Satto/SattoLux 사용)인데, `satto_draw_result` 테이블 정의 위 주석이 `-- 로또 추첨 결과`로 되어 있다. 또한 외부 API 변수명 `dhlottery.co.kr`은 동행복권 실제 도메인이라 불가피하나, 주석/문구는 규칙 대상이다.
- **영향**: 네이밍 일관성 위반(기능 영향 없음).
- **권장 조치**: 주석을 `-- Satto 추첨 결과` 등으로 수정. 코드 전반 grep으로 잔여 위반 점검(현재 이 1건만 확인됨).

#### [m-3] `JwtAuthenticationFilter`가 SSE accessToken을 쿼리스트링으로 수용

- **위치**: `backend/.../JwtAuthenticationFilter.java:55-60`, `frontend/.../AuthContext.jsx:130`
- **문제**: `EventSource`가 커스텀 헤더를 못 보내는 한계 때문에 `/api/auth/sse?accessToken=...`로 토큰을 쿼리 파라미터에 싣는다. 쿼리스트링은 액세스 로그, 프록시 로그, Referer 헤더, 브라우저 히스토리에 남아 토큰이 노출되기 쉽다.
- **영향**: access 토큰이 서버/중간 로그에 평문 기록될 수 있음.
- **권장 조치**: SSE 인증을 단명(short-lived) 일회용 토큰으로 분리하거나, 쿠키 기반(HttpOnly) SSE 인증을 검토. 최소한 access 토큰 유효기간을 짧게 유지(현재 1h)하고 SSE용 토큰 TTL을 별도로 더 짧게.

#### [m-4] `RsaUtil.decrypt`가 `new String(...)`에 charset 미지정

- **위치**: `backend/.../RsaUtil.java:43`
- **문제**: `new String(cipher.doFinal(encrypted))`는 플랫폼 기본 charset에 의존한다. 비밀번호/PIN에 ASCII만 쓰면 문제가 없으나, 비-ASCII 문자나 다른 기본 charset 환경에서 복호화 결과가 깨질 수 있다.
- **영향**: 환경 의존적 인증 실패 가능성(낮음).
- **권장 조치**: `new String(bytes, StandardCharsets.UTF_8)`로 명시.

#### [m-5] `getWeekResult`에서 `findWeekDrawResultByScope`가 user 필터 없음

- **위치**: `backend/.../ResultMapper.xml:104-123`, `ResultServiceImpl.java:90`
- **문제**: `findWeekDrawResultByScope`는 `satto_number_set`을 JOIN하되 `user_seq` 조건이 없다. 추첨 결과(`satto_draw_result`) 자체는 전 사용자 공통 데이터라 결과적으로 노출되는 정보는 당첨번호뿐(개인정보 아님)이라 실질 피해는 작다. 다만 "해당 주차에 누군가 번호를 생성했는가" 여부가 간접 노출되고, 매칭 항목(`items`)은 `findMatchedSetsByScope`로 user 필터가 있어 일관성이 어긋난다.
- **영향**: 정보 노출은 경미하나 쿼리 의도 불명확.
- **권장 조치**: 추첨 결과 조회는 `satto_draw_result`에서 draw_no 기준으로 직접 조회하도록 단순화하거나, 의도를 주석으로 명시.

#### [m-6] `ClaudeNumberGenerator`가 RestClient를 매 호출 새로 빌드 + 타임아웃 미설정

- **위치**: `backend/.../ClaudeNumberGenerator.java:45-64`, `ResultServiceImpl.java:411-415`
- **문제**: `generate()`와 `fetchDrawResult()`가 호출마다 `restClientBuilder.build()`로 새 클라이언트를 만든다. 더 중요한 것은 connect/read 타임아웃 설정이 없어, Anthropic API나 동행복권 API가 응답하지 않으면 스레드(및 M-6에서 지적한 DB 트랜잭션)가 무한정 블로킹될 수 있다.
- **영향**: 외부 API 장애가 스케줄러/요청 스레드 행(hang)으로 전파.
- **권장 조치**: 명시적 connect/read timeout 설정. 클라이언트는 빈으로 한 번만 구성해 재사용. 외부 호출에 재시도/서킷브레이커 고려.

#### [m-7] `logout` API가 인증 없이 임의 RT를 revoke 가능

- **위치**: `backend/.../LoginController.java:108-112`, `SecurityConfig.java:37`
- **문제**: `/api/auth/logout`은 `permitAll`이고 body의 `refreshToken`만 받아 revoke한다. 공격자가 타인의 RT 값을 알면(예: m-3 로그 노출) 인증 없이 그 세션을 강제 종료시킬 수 있다. RT는 해시로만 저장되어 값 추측은 어렵지만, 인증 컨텍스트 없이 상태 변경을 허용하는 설계는 바람직하지 않다.
- **영향**: RT 값 노출 시 타 사용자 세션 강제 종료(저위험 DoS).
- **권장 조치**: logout을 인증된 엔드포인트로 옮기고 토큰 주체와 RT 소유자 일치를 확인하거나, 최소한 RT 값 노출 경로(m-3)를 함께 차단.

### 🔵 Suggestion

#### [S-1] `Map<String,Object>` + 수동 캐스팅 헬퍼 중복 제거

- **위치**: `LoginServiceImpl.java:351-384`, `MakeWeekNumServiceImpl.java:545-578`, `ResultServiceImpl.java:494-526`, `NotificationServiceImpl.java:87-119`, `GenerationRuleConfigServiceImpl.java:168-190`
- **문제**: `getString/getLong/getInt/getBoolean/toLocalDateTime` 같은 캐스팅 헬퍼가 거의 동일하게 5개 클래스에 복붙되어 있다. DAO가 `Map`을 반환하기 때문에 발생하는 보일러플레이트다.
- **권장 조치**: MyBatis `resultType`을 도메인/Record DTO로 지정해 `map-underscore-to-camel-case`로 자동 매핑하거나, 공통 `RowMapperUtil` 클래스로 헬퍼를 단일화.

#### [S-2] `ResultServiceImpl`의 `@Transactional protected` 메서드는 self-invocation으로 무효

- **위치**: `backend/.../ResultServiceImpl.java:264`, `:297`
- **문제**: `saveDrawResultAndCompare`가 `protected`이고 같은 클래스 내부에서 호출되어 Spring AOP 프록시를 거치지 않는다. 따라서 메서드 단위 `@Transactional`이 실제로 적용되지 않고, 호출자(`collectLatestResults`, `runLatestResultManualTest`)의 트랜잭션에 편승한다. 의도한 트랜잭션 경계가 아닐 수 있다.
- **권장 조치**: 트랜잭션 경계를 명확히 하려면 별도 빈으로 추출하거나, 클래스 레벨 트랜잭션 전략을 의식적으로 문서화.

#### [S-3] `collectLatestResults`의 `while(true)` 루프에 상한 없음

- **위치**: `backend/.../ResultServiceImpl.java:62-77`
- **문제**: 외부 API가 매번 유효 데이터를 반환하면 루프가 무한정 돈다. 정상 시 곧 `null`이 나와 종료되지만, API 응답 이상(항상 같은 회차 반환 등) 시 무한 루프 + 매 회 DB 쓰기가 발생할 수 있다.
- **권장 조치**: 1회 수집 최대 회차 수(예: 한 번에 최대 10회차) 상한을 둬 방어.

#### [S-4] 프론트엔드 PIN 사용 가능 여부가 하드코딩된 단일 계정

- **위치**: `frontend/src/pages/LoginPage.jsx:11`, `:27`
- **문제**: `const PIN_USER = 'ulmsaga'`로 특정 아이디일 때만 PIN 탭을 노출한다. 백엔드 `pinLogin`은 모든 계정을 지원하므로, 다른 사용자는 PIN을 설정해도 UI에서 쓸 수 없다. 데모/개인용이라면 의도적일 수 있으나 확장성 측면에서 부적절하다.
- **권장 조치**: 사용자별 `pinHash` 설정 여부를 백엔드에서 받아 PIN 탭 노출을 결정하거나, 입력한 아이디로 PIN 사용 가능 여부를 조회.

#### [S-5] `axios` 응답 인터셉터 부재 — 401 공통 처리 없음

- **위치**: `frontend/src/api/client.js` 전체
- **문제**: 요청 인터셉터로 토큰 주입만 하고, 응답 인터셉터가 없다. 토큰 만료 시 각 API 호출이 개별적으로 401을 처리해야 하며, 만료 직후 자동 갱신·재시도 로직이 없어 일시적 인증 실패가 사용자에게 그대로 노출된다(C-1/B003과 연결).
- **권장 조치**: 응답 인터셉터에서 401 감지 → in-flight 단일 refresh → 원요청 재시도, 실패 시 `clearAuth` 호출하는 표준 패턴 도입.

## 잘된 점

- **인증 설계의 기본기**: 비밀번호/PIN을 RSA-OAEP(SHA-256)로 전송 후 서버에서만 복호화, 저장은 BCrypt 해시. Refresh Token은 평문이 아닌 SHA-256 해시로 DB 저장하고 로테이션(one-time use)한다. 평문 비밀번호 저장은 어디에도 없다.
- **계정 보호**: 로그인 5회 실패 시 15분 잠금, OTP 5회 시도 제한, 잠금 자동 해제(`normalizeLockState`) 등 brute-force 완화 로직이 갖춰져 있다.
- **SQL 안전성**: 모든 MyBatis 매퍼가 `#{}` 파라미터 바인딩을 사용하고 `${}` 문자열 결합이 없다. `rank` 예약어도 일관되게 backtick 처리되어 있다(최근 커밋 `699ab5d`에서 보강된 흔적).
- **권한 검사**: `requireAdmin` 헬퍼로 관리자 전용 엔드포인트를 일관되게 보호하고, `DevAdminController`는 `@Profile("dev")`로 운영 환경에서 비활성화된다. SSE 연결 시 토큰 주체와 요청 사용자 일치를 검증한다.
- **JWT 시크릿 길이 검증**: `JwtUtil` 생성자에서 시크릿이 32바이트 미만이면 기동 실패시켜 약한 키 사용을 방지한다.
- **동시성 자료구조**: `SseConnectionManager`, `AuthSessionStore`, `RsaUtil`이 `ConcurrentHashMap`/`CopyOnWriteArrayList`를 사용하고, SSE emitter의 onCompletion/onTimeout/onError 정리와 heartbeat가 구현되어 있다.
- **월 경계 주차 처리**: 번호 생성 주차와 추첨 주차가 월 경계에서 어긋나는 케이스(`findCalendarWeekNumberSets`, `saveDrawResultAndCompare`의 fallback)를 명시적 주석과 함께 다루고 있다.
- **계층 분리**: controller/service/dao + DTO(Record) 구조가 모듈별로 일관되며, `dist/`·`target/`·시크릿 파일이 `.gitignore`로 제대로 제외되어 있다.

## B003 버그 분석

**증상**: 3회 새로고침 시 로그아웃.

**관련 코드**:
- `frontend/src/context/AuthContext.jsx:10` — `let _initialRefreshDone = false` (모듈 전역)
- `frontend/src/context/AuthContext.jsx:79-91` — 새로고침 시 초기 refresh effect
- `frontend/src/context/AuthContext.jsx:37-53` — `scheduleRefresh` 타이머
- `backend/.../LoginServiceImpl.java:211-230` — `refresh()`가 RT를 revoke 후 새로 발급(로테이션)

**분석**:

1. **RT 로테이션이 핵심 원인**. `refresh()`는 매 호출 시 제시된 RT를 `revokeRefreshToken`으로 무효화하고 새 RT를 발급한다. 즉 같은 RT는 단 한 번만 유효하다. 프론트는 `saveTokens`에서 새 RT를 `sessionStorage`에 덮어쓴다.

2. **모듈 가드는 동일 모듈 평가 주기 내에서만 동작**. `_initialRefreshDone`은 React StrictMode의 effect 이중 실행은 막지만, 실제 페이지 새로고침(F5)에서는 JS 모듈이 새로 평가되어 `false`로 초기화된다. 따라서 새로고침마다 초기 refresh effect는 정상적으로 1회 실행된다 — 여기까지는 의도대로다.

3. **그러나 race window가 존재한다**:
   - 새로고침 직후 초기 effect가 `apiRefresh(stale RT)`를 호출한다.
   - 동시에, 직전 세션에서 살아남은 상태나 `scheduleRefresh` 타이머, 또는 빠른 연속 새로고침으로 인해 **이전 새로고침의 in-flight refresh 응답이 아직 도착하지 않은 채** 다음 새로고침이 또 `apiRefresh`를 같은(아직 교체되지 않은) RT로 호출할 수 있다.
   - 사용자가 빠르게 연속으로 새로고침하면: 1회차 refresh 응답이 새 RT를 `sessionStorage`에 쓰기 전에 2회차 새로고침이 발생 → 2회차는 1회차가 이미 서버에서 revoke한 RT를 제시 → 401 → `sessionStorage.removeItem(RT_KEY)` → 로그아웃.
   - "3회"라는 숫자는 네트워크 지연·타이머 만료·StrictMode 잔여 실행이 누적되어 race가 실제로 터지기까지 평균적으로 필요한 횟수로 보인다(환경에 따라 가변).

4. **응답 인터셉터 부재**가 이를 악화시킨다(`api/client.js`). 401에 대한 공통 복구(단일 refresh 재시도)가 없어, 한 번 stale RT로 실패하면 곧장 로그아웃으로 직행한다.

**결론**: B003은 "모듈 레벨 guard" 만으로는 해결되지 않는다. guard는 StrictMode 이중 실행만 막을 뿐, **RT 로테이션 + refresh 호출 중복/경합**이라는 근본 원인은 그대로다.

**권장 수정(우선순위 순)**:
1. **In-flight refresh 단일화**: 모듈 레벨에 `let _refreshPromise = null`을 두고, refresh가 진행 중이면 동일 Promise를 반환·재사용한다. 초기 effect, `scheduleRefresh` 타이머, 응답 인터셉터가 모두 이 단일 Promise를 공유하게 한다.
2. **백엔드 RT 재사용 grace/탐지**: revoke된 직후의 RT를 짧은 grace period(예: 10초) 동안 1회 허용하거나, 토큰 패밀리(family) 개념으로 재사용 탐지 시에만 전체 무효화. 현재처럼 즉시 hard-revoke는 경합에 취약하다.
3. **응답 인터셉터 도입**: `client.js`에 401 → 단일 refresh → 원요청 재시도, 최종 실패 시에만 `clearAuth`.
4. `_initialRefreshDone` 모듈 전역 가드 제거하고 위 in-flight dedup으로 대체.

## 종합 권고

우선순위 순으로 정리한다.

1. **[C-1 / B003] 프론트 토큰 갱신 경합 해소** — in-flight refresh 단일화 + 백엔드 RT 재사용 grace/탐지 + axios 응답 인터셉터 도입. 사용자 체감 버그(로그아웃)라 최우선.
2. **[C-2] OTP 검증 원자화 + PIN 설정 step-up 인증** — `verifyOtpAndIssueToken`에 트랜잭션·조건부 UPDATE 적용, PIN 설정 시 비밀번호 재인증 요구.
3. **[m-1] 노출된 Anthropic API 키 즉시 폐기·재발급** — `application-local.yml`의 실 시크릿. git 미추적이라도 평문 보관 위험. 즉시 조치 가능한 항목.
4. **[M-2 / M-3] 인증 전 엔드포인트 rate limit** — RSA 키 발급 메모리 누수 차단(TTL 스윕 + IP rate limit), OTP 발급/검증 rate limit. DoS 및 brute-force 방어.
5. **[M-6 / m-6] 스케줄러 트랜잭션·외부 호출 격리** — 사용자 단위 트랜잭션 분리, 외부 API 호출을 트랜잭션 밖으로, connect/read 타임아웃 설정. 운영 안정성.
6. **[M-5] 수동 테스트 API의 크로스 유저 처리 분리** — 매퍼 ID 구분 및 대상 사용자 범위 명시. 운영 사고 예방.
7. **[M-1 / M-4] JWT·CORS 하드닝** — issuer/audience 검증, CORS는 정확 매칭 origin + 와일드카드 입력 방어.
8. **[m-2] 네이밍 규칙 위반 수정** — `schema.sql:107` 주석의 "로또" → "Satto".
9. **[Minor/Suggestion 잔여 항목]** — m-3(SSE 토큰), m-4(charset), m-7(logout 인증), S-1(Map 캐스팅 중복 제거), S-3(루프 상한), S-5(인터셉터)를 리팩터링 사이클에서 정리.

---

> 본 문서는 정적 코드 리뷰 결과이며, 동적 테스트/침투 테스트로 추가 검증을 권장한다.
> — Reviewer Bot
