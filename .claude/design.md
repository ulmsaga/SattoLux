# SattoLux — 설계

## 서버 환경

| 항목 | 내용 |
|------|------|
| OS | CentOS 7 |
| Java | 17 (직접 설치) |
| NGINX | 직접 설치, FE 정적 파일 서빙 |
| MySQL | Docker (`SATTOLUX_DB` / `SATTOLUX_DEV_DB`), user: `sattolux`) |
| Mailpit | Docker, `http://ulmsaga34.cafe24.com:8025` |
| Node | 16 (OS 한계) |
| 도메인 | ulmsaga34.cafe24.com:8080 |

## 포트 구성

| 환경 | FE | BE |
|------|----|----|
| 로컬 | 8080 | 8081 |
| 서버 | NGINX :8080 (외부 노출) | Spring Boot :8081 (내부 only) |

- NGINX `/api` → `localhost:8081` 프록시 (BE는 외부 미노출)
- 개발/상용 DB 분리: 환경변수 `DB_URL` 하나로 전환 (`SATTOLUX_DEV_DB` ↔ `SATTOLUX_DB`)
- DB 패스워드는 OS 환경변수 `DB_PASSWORD`로만 관리 — 파일 기재 금지
- 번호 자동 생성 스케줄 시각은 Spring properties로 관리, 초기값은 `09:00`
- 스케줄 판정 기준 시간대는 서버 운영 시간대(`Asia/Seoul`)로 고정

## 시스템 구성

```
[사용자 브라우저]
      ↓
  [NGINX :8080]          ← ulmsaga34.cafe24.com:8080
      ↓ (정적 파일 / API 프록시)
  [React SPA]  →  [Spring Boot API :8081]  →  [MySQL (Docker)]
                          ↓
                    [외부 서비스]
                    - OTP: 카카오 or Mailpit
                    - LLM: Anthropic Claude API
                    - 로또 결과: 공공 API or 스크래핑
```

## 핵심 흐름

### 번호 자동 생성 흐름
```
스케줄러 (지정 요일, 기본 09:00)
  → generation_rule 조회 (활성 row 전체)
  → row별 전략/엔진 실행
    - RANDOM + LOCAL: 서버에서 완전 랜덤 1~45, 6개 생성
    - HOT_NUMBER + CLAUDE: 최근 N회 통계를 Claude API에 보내 추천 세트 수신
    - MIXED + CLAUDE: RANDOM + HOT_NUMBER 성향을 함께 반영해 추천 세트 수신
  → satto_number_set DB 저장
  → 사용자 조회 시 노출
```

### 번호 수동 생성 활성 조건
```
현재 시각이 properties에 정의된 자동 생성 시각 이상
  AND 오늘이 사용자의 generation_rule 대상 요일
  AND 현재 주차 기준 satto_number_set이 아직 없음
→ FE에서 "수동 생성" 버튼 활성화
```

- 수동 생성은 "자동 생성 실패 복구" 목적의 보조 수단으로 사용
- 자동 생성 시각 이전에는 수동 생성 버튼을 기본 비활성 상태로 둠
- 운영자가 스케줄 시각을 변경하더라도 FE/BE 판단 기준은 동일한 properties 값 사용

### 현재 구현된 백엔드 API
- `GET /api/make-week-num/rules`
  - 로그인 사용자의 활성 generation_rule 조회
- `GET /api/config/generation-rules`
  - generation_rule 설정 조회
- `PUT /api/config/generation-rules`
  - generation_rule 설정 저장
- `POST /api/make-week-num/generate`
  - 현재 주차 기준 번호 생성 실행
- `POST /api/make-week-num/generate?force=true`
  - 요일 검증을 무시하고 수동 생성
  - `RANDOM + LOCAL` 생성 완료
  - `HOT_NUMBER + CLAUDE`는 Claude 호출 후 실패 시 `HOT_NUMBER + LOCAL`로 fallback
  - `MIXED`는 아직 미구현
- `POST /api/make-week-num/manual-generate`
  - 자동 생성 누락 조건일 때만 수동 생성 허용
- `GET /api/make-week-num/current-week`
  - 현재 주차 기준 저장된 번호 세트 조회
- `GET /api/make-week-num/status`
  - 자동 생성 요일/시각, 수동 생성 가능 여부, 비활성 사유 조회
- `GET /api/notifications`
  - unread count + 최근 알림 목록 조회
- `POST /api/notifications/{notificationId}/read`
  - 알림 읽음 처리
- `POST /api/notifications/admin/replay-result-ready`
  - 관리자 전용 SSE 결과 알림 재전송 테스트
- `GET /api/result/week`
  - 특정 연/월/주차 결과 및 내 번호 비교 결과 조회
- `POST /api/auth/admin/dev-users/ensure-general`
  - dev 환경에서 일반 사용자 계정/기본 rule 보장

### Claude 번호 생성 상세
```
1. satto_draw_result에서 최근 N회 당첨 번호 조회
2. 서버에서 빈도 통계 / 랜덤 seed 재료를 준비
3. 현재 구현은 `rule_id` 1건 단위로 Claude API 호출
4. 응답 번호 세트 유효성 검증 (1~45, 중복 없음, 6개)
5. 호출 실패 또는 quota 초과 시 `HOT_NUMBER + LOCAL` fallback
6. 검증 통과 세트만 `satto_number_set` 저장
```

### 추첨 결과 비교 흐름
```
스케줄러 (토요일 추첨 후)
  → 토요일 21:00 ~ 23:00, 5분 간격으로 동행복권 JSON endpoint 조회
  → 먼저 DB에서 해당 회차 결과 존재 여부 확인
    - 이미 있으면 즉시 종료 (추가 API 호출 안 함)
  → 결과가 없으면 API 호출 (`lt645/selectPstLt645InfoNew.do` + Referer)
  → 결과가 아직 미오픈이면 종료
  → 결과가 있으면 satto_draw_result upsert
  → 해당 주차 satto_number_set과 비교
  → satto_match_result 저장
  → 결과 도착 알림 DB 저장
  → 현재 접속 중 사용자에게 SSE push
  → 결과 화면 노출
  → 일요일 보정 수집 2회 추가 가능
    - 1차: 일요일 00:00
    - 2차: 일요일 09:00
```

- 일요일 보정 수집 목적
  - 토요일 야간 구간에 결과 반영이 지연되더라도 다음날 자동 회수할 수 있게 함
  - 두 보정 시점 모두 먼저 DB를 확인하고, 이미 결과가 저장되어 있으면 외부 API 호출 없이 종료
- 운영 기본안
  - 토요일 `21:00 ~ 23:00` 5분 간격 폴링
  - 일요일 `00:00`, `09:00` 보정 실행
  - 결과가 최초 저장된 이후 동일 회차에 대한 추가 수집은 모두 skip

### 결과 비교 수동 테스트 시나리오
```
1. 현재 주차 satto_number_set 데이터를 비운다.
2. 지난주 기준으로 테스트용 10세트를 생성해 둔다.
3. 관리자가 수동 테스트 기능을 실행한다.
4. 수동 테스트 기능은 최신 추첨 결과(대부분 지난주 회차)를 동행복권 API에서 조회한다.
5. 조회한 최신 결과를 지난주 10세트와 비교한다.
6. satto_match_result 저장, 결과 화면 노출, 알림 생성/SSE push까지 동일 흐름으로 검증한다.
```

- 수동 테스트 목적
  - 토요일 실시간 추첨 시각을 기다리지 않고 결과 비교/알림 흐름을 검증하기 위함
  - 실제 운영 스케줄러와 동일한 비교 로직을 재사용하되, 테스트 대상 주차만 지난주로 고정해 검증 가능하게 함
- 권장 테스트 절차
  - 현재 주차 번호 세트는 제거하거나 제외한다
  - 지난주 기준으로 10세트를 먼저 생성한다
  - 수동 테스트 실행 시 최신 결과를 가져와 지난주 세트와 매칭한다
  - 결과 페이지와 알림 뱃지까지 함께 확인한다

### 결과 수집 스케줄러 테스트 전략
```
1. 비즈니스 로직 검증
   - 스케줄러가 호출하는 결과 수집/비교/알림 로직을 수동 테스트 기능으로 직접 실행한다.

2. 스케줄러 트리거 검증
   - local/test 환경에서는 운영 cron 대신 짧은 주기로 스케줄러를 실행한다.
   - 예: 10초 또는 1분 간격
   - 실행 로그, savedCount, skip 여부를 확인한다.

3. 시간 조건 검증
   - 토요일 21:00~23:00
   - 일요일 00:00
   - 일요일 09:00
   - 이미 결과가 저장된 경우 skip
   위 조건은 정책 로직 단위 테스트로 검증한다.
```

- 테스트 전략 목적
  - 실제 토요일 추첨 시각을 기다리지 않고 결과 수집 스케줄러의 정상 동작을 빠르게 검증하기 위함
  - 스케줄러 실행 여부와 비즈니스 로직 정상 여부를 분리해서 확인하기 위함
- 권장 검증 순서
  - 먼저 수동 테스트 기능으로 결과 수집/비교/알림 흐름을 검증한다
  - 그 다음 local/test 전용 짧은 cron으로 스케줄러가 실제 실행되는지 확인한다
  - 마지막으로 시간 판정 정책을 단위 테스트로 검증한다
- 확인 포인트
  - 스케줄러가 실제로 실행되었는지
  - 조건에 맞으면 외부 API 호출이 수행되었는지
  - DB에 해당 회차 결과가 이미 있으면 skip 되었는지
  - `satto_draw_result`, `satto_match_result`, `app_notification`이 중복 없이 저장되는지
  - FE 알림 뱃지와 SSE 수신이 정상 동작하는지

- 현재 코드 기준 구현 완료 범위
  - 결과 수집 스케줄러
  - `satto_draw_result` upsert
  - `satto_number_set` vs 당첨 결과 비교
  - `satto_match_result` upsert
  - 결과 페이지 조회 API
  - ADMIN 수동 결과 테스트 API (`/api/result/admin/manual-test-latest`)
  - 결과 알림 DB 저장 + SSE push
- 아직 남은 검증
  - 실제 결과 오픈 시점 기준 end-to-end 검증
  - 일요일 `00:00`, `09:00` 보정 스케줄 반영
  - 지난주 기준 테스트 세트 10건 준비 후 수동 테스트 실검증

### 결과 도착 알림 흐름
```
결과 저장 완료
  → "RESULT_READY" 알림 1건 생성 (회차/주차 기준 중복 방지)
  → unread 상태로 DB 저장
  → SSE 이벤트로 FE에 즉시 push
  → FE TopBar 뱃지 +1 표시
  → 사용자가 알림 클릭
  → "2026년 4월 5주차 결과가 도착했습니다." 노출
  → /result?year=2026&month=4&week=5 형태로 이동
  → 읽음 처리
```

- SSE는 실시간 전달용, 알림의 진실 소스는 DB로 관리
- 브라우저 미접속/재연결 상황에서도 unread 알림은 복구 가능해야 함
- 같은 회차에 대해 결과 도착 알림은 1회만 생성
- 관리자만 기존 결과 알림을 SSE로 재전송하는 테스트 기능을 사용할 수 있음

### 권한 정책
- `app_user.role_code`를 그대로 사용
- 권한 값은 문자열 기준
  - `ADMIN`
  - `USER`
- 관리자 전용 기능
  - 테스트 세트 준비
  - 지난주 결과 테스트
  - SSE 알림 재전송
  - dev 일반 사용자 계정 보장 API
- 일반 사용자는 위 기능을 FE에서 숨기고, BE에서도 `403 Forbidden`으로 차단

### 인증 흐름
```
클라이언트
  → RSA 공개키 요청
  → 비밀번호 RSA 암호화 전송 (1차 인증)
  → 계정 상태 / 실패 횟수 / 잠금 시간 검증
  → OTP 전송 (2차 인증, Skip 가능)
  → JWT 발급 + SSE 연결
  → Refresh Token 로테이션 (세션 유지)
  → 로그아웃 (토큰 폐기)
```

## DB 테이블

- `SATTOLUX_DEV_DB`, `SATTOLUX_DB`는 **동일 스키마** 유지
- 개발/상용 DB 간 차이는 데이터만 허용, 테이블 구조 차이는 허용하지 않음
- 인증 관련 테이블과 번호 생성 관련 테이블을 분리 관리

### DB 파일 / 스크립트 기준
- 스키마 원본 파일: `backend/src/main/resources/db/schema.sql`
- 개발용 공유 계정 시드 템플릿: `backend/src/main/resources/db/dev-seed.sql`
- DB 초기화 실행 스크립트: `scripts/init-db.sh`
- 추첨 결과 동기화 스크립트: `scripts/sync-draw-results.sh`
- `./scripts/init-db.sh`
  - `SATTOLUX_DEV_DB`, `SATTOLUX_DB`에 동일 스키마 적용
- `./scripts/init-db.sh --with-dev-seed`
  - `SATTOLUX_DEV_DB`에만 개발용 공유 계정 + 기본 rule 시드 적용
  - 관리자 계정은 `.env`의 `LOGIN_USER`, `LOGIN_PW`, 선택값 `LOGIN_EMAIL` 기준
  - 일반 사용자 계정은 `.env`의 `GENERAL_LOGIN_PW`를 필수로 사용하고, `GENERAL_LOGIN_USER`, `GENERAL_LOGIN_EMAIL`은 선택값으로 사용
  - 일반 사용자 비밀번호는 관리자 비밀번호를 기본값으로 상속하지 않음
- `./scripts/sync-draw-results.sh`
  - `SATTOLUX_DEV_DB`, `SATTOLUX_DB`에 동일한 추첨 결과 upsert
  - 기본값은 DEV DB 마지막 저장 회차 다음부터 최신 회차까지 동기화
  - 좁은 검증 시 `--from`, `--to`로 회차 범위 지정 가능

| 테이블 | 설명 |
|--------|------|
| `app_user` | 로그인/인증/인가용 사용자 계정 |
| `refresh_token` | Refresh Token 해시 저장 및 로테이션 관리 |
| `otp_code` | OTP 코드 해시 저장 및 만료/사용 처리 |
| `generation_rule` | 번호 생성 규칙 row 설정 |
| `satto_number_set` | 생성된 번호 세트 |
| `satto_draw_result` | 추첨 결과 (공공 API or 스크래핑) |
| `satto_match_result` | 세트 vs 결과 비교 |
| `app_notification` | 결과 도착 등 사용자 알림 저장 + unread 관리 |

### `app_user`
- 로그인/인증/인가 전용 테이블
- 비밀번호는 BCrypt 해시만 저장
- 계정 상태, 실패 횟수, 잠금 시간, 마지막 로그인 시각 관리
- 운영상 사용자 수가 적어도 보안 규칙은 느슨하게 두지 않음

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `user_seq` | BIGINT PK | 사용자 순번 PK |
| `user_id` | VARCHAR(50) UNIQUE | 로그인 ID |
| `password_hash` | VARCHAR(255) | BCrypt 해시 |
| `email` | VARCHAR(100) UNIQUE | OTP 수신 이메일 |
| `role_code` | VARCHAR(20) | `ADMIN`, `USER` |
| `otp_enabled` | TINYINT(1) | OTP 사용 여부 |
| `account_status` | VARCHAR(20) | `ACTIVE`, `LOCKED`, `DISABLED` |
| `failed_login_count` | TINYINT | 로그인 실패 횟수 |
| `locked_until` | DATETIME NULL | 잠금 해제 시각 |
| `last_login_at` | DATETIME NULL | 마지막 로그인 시각 |
| `password_changed_at` | DATETIME | 비밀번호 변경 시각 |
| `created_at` | DATETIME | 생성 시각 |
| `updated_at` | DATETIME | 수정 시각 |

### `refresh_token`
- Refresh Token 원문은 저장하지 않고 SHA-256 해시만 저장
- Access Token 재발급 시 기존 토큰 revoke 후 새 토큰 발급

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `token_id` | BIGINT PK | 토큰 식별자 |
| `user_seq` | BIGINT FK | 사용자 참조 |
| `token_hash` | CHAR(64) UNIQUE | Refresh Token SHA-256 해시 |
| `expires_at` | DATETIME | 만료 시각 |
| `revoked_at` | DATETIME NULL | 폐기 시각 |
| `issued_ip` | VARCHAR(45) NULL | 발급 요청 IP |
| `user_agent` | VARCHAR(255) NULL | 발급 요청 UA |
| `created_at` | DATETIME | 생성 시각 |

### `otp_code`
- OTP 원문은 메일 발송 직후 폐기하고 DB에는 해시만 저장
- 재시도 횟수 제한 후 사용 불가 처리

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `otp_id` | BIGINT PK | OTP 식별자 |
| `user_seq` | BIGINT FK | 사용자 참조 |
| `code_hash` | CHAR(64) | OTP SHA-256 해시 |
| `expires_at` | DATETIME | 만료 시각 |
| `attempt_count` | TINYINT | 검증 시도 횟수 |
| `used_yn` | CHAR(1) | 사용 여부 |
| `used_at` | DATETIME NULL | 사용 시각 |
| `created_at` | DATETIME | 생성 시각 |

### `generation_rule`
- 사용자별로 n row까지 저장 가능
- row 1건이 "특정 요일에 특정 방식으로 몇 세트를 생성할지"를 의미
- 예시:
  - row 1: 목요일 / RANDOM / LOCAL / 5세트
  - row 2: 목요일 / HOT_NUMBER / CLAUDE / 5세트 / 최근 1000회
  - row 3: 목요일 / MIXED / CLAUDE / 4세트 / 최근 1000회
- 주간 총 세트 수 제한(1, 5, 10, 15, 20)은 서비스 검증으로 관리

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `rule_id` | BIGINT PK | 규칙 식별자 |
| `user_seq` | BIGINT FK | 사용자 참조 |
| `day_of_week` | TINYINT | `1=월 ~ 5=금` |
| `method_code` | VARCHAR(20) | `RANDOM`, `HOT_NUMBER`, `MIXED` |
| `generator_code` | VARCHAR(20) | `LOCAL`, `CLAUDE` |
| `set_count` | TINYINT | row가 생성할 세트 수 |
| `analysis_draw_count` | INT NULL | `HOT_NUMBER`, `MIXED` 전략에서 사용할 과거 회차 수 |
| `sort_order` | INT | UI 정렬 순서 |
| `use_yn` | CHAR(1) | 활성 여부 |
| `created_at` | DATETIME | 생성 시각 |
| `updated_at` | DATETIME | 수정 시각 |

### `satto_number_set`
- 사용자별 생성 결과 저장
- `target_year`, `target_month`, `target_week_of_month` 기준으로 "n월 n주차" 조회 가능
- 필요 시 실제 로또 회차(`draw_no`)도 함께 저장

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `set_id` | BIGINT PK | 생성 번호 식별자 |
| `user_seq` | BIGINT FK | 사용자 참조 |
| `rule_id` | BIGINT FK NULL | 생성에 사용된 규칙 |
| `target_year` | SMALLINT | 대상 연도 |
| `target_month` | TINYINT | 대상 월 |
| `target_week_of_month` | TINYINT | 대상 월 내 주차 |
| `draw_no` | INT NULL | 연결되는 실제 로또 회차 |
| `method_code` | VARCHAR(20) | 생성 전략 snapshot |
| `generator_code` | VARCHAR(20) | 생성 엔진 snapshot |
| `no1~no6` | TINYINT | 생성 번호 6개 |
| `created_at` | DATETIME | 생성 시각 |

### `satto_draw_result`
- 동행복권 결과 수집 원본 저장 테이블

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `result_id` | BIGINT PK | 결과 식별자 |
| `draw_no` | INT UNIQUE | 로또 회차 |
| `draw_date` | DATE | 추첨일 |
| `no1~no6` | TINYINT | 당첨 번호 6개 |
| `bonus_no` | TINYINT | 보너스 번호 |
| `created_at` | DATETIME | 저장 시각 |

### `satto_match_result`
- 생성 번호와 실제 당첨 결과 비교 저장

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `compare_id` | BIGINT PK | 비교 식별자 |
| `set_id` | BIGINT FK | 생성 번호 참조 |
| `result_id` | BIGINT FK | 추첨 결과 참조 |
| `match_count` | TINYINT | 일치 개수 |
| `bonus_match` | TINYINT(1) | 보너스 일치 여부 |
| `rank` | TINYINT NULL | `1~5`, 미당첨은 NULL |
| `created_at` | DATETIME | 생성 시각 |

## 화면 목록

| ID | 화면명 | 모듈 |
|----|--------|------|
| S01 | 로그인 | `module.login` |
| S02 | 번호 생성/조회 | `module.makeweeknum` |
| S03 | 추첨 결과 비교 | `module.result` |
| S04 | 사용자 설정 | `module.config` |

## 결정 사항

| 항목 | 결정 |
|------|------|
| OTP 방식 | **Mailpit** (카카오 알림톡 무료 플랜 없음) |
| 2차 인증 | Skip 가능하도록 구현 |
| Noti | 추후 검토 (현재 스킵) |
| 로또 결과 수집 | **동행복권 JSON endpoint** (`lt645/selectPstLt645InfoNew.do` + Referer) |
| 배포 환경 | 서버 배포 (로컬 테스트 선행) |
| 시크릿 관리 | OS 환경변수 방식 (.env 파일 없음) |
| 번호 생성 | row 기반 규칙 실행 (`RANDOM`, `HOT_NUMBER`, `MIXED`) + 엔진 분리 (`LOCAL`, `CLAUDE`) |

## 로또 결과 API

```
GET https://www.dhlottery.co.kr/lt645/selectPstLt645InfoNew.do?srchDir=center&srchLtEpsd={회차}
Referer: https://www.dhlottery.co.kr/lt645/result
```

- 브라우저 Referer 없이 직접 호출하면 차단될 수 있음
- 응답은 `data.list[]` 구조이며 `ltEpsd`, `tm1WnNo~tm6WnNo`, `bnsWnNo`, `ltRflYmd` 사용

## 확인 필요 사항

- [ ] 무료 Noti 수단 확인 (FCM Web Push 등)
- [x] LLM API 확정 → Anthropic Claude (`CLAUDE_API_KEY`)
