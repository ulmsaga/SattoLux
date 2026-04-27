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
스케줄러 (지정 요일)
  → generation_rule 조회 (활성 row 전체)
  → row별 전략/엔진 실행
    - RANDOM + LOCAL: 서버에서 완전 랜덤 1~45, 6개 생성
    - HOT_NUMBER + CLAUDE: 최근 N회 통계를 Claude API에 보내 추천 세트 수신
    - MIXED + CLAUDE: RANDOM + HOT_NUMBER 성향을 함께 반영해 추천 세트 수신
  → satto_number_set DB 저장
  → 사용자 조회 시 노출
```

### 현재 구현된 백엔드 API
- `GET /api/make-week-num/rules`
  - 로그인 사용자의 활성 generation_rule 조회
- `POST /api/make-week-num/generate`
  - 현재 주차 기준 번호 생성 실행
- `POST /api/make-week-num/generate?force=true`
  - 요일 검증을 무시하고 수동 생성
  - 현재는 `RANDOM + LOCAL` 생성 완료
  - `HOT_NUMBER + CLAUDE`는 Claude 호출 후 실패 시 `HOT_NUMBER + LOCAL`로 fallback
  - `MIXED`는 아직 미구현
- `GET /api/make-week-num/current-week`
  - 현재 주차 기준 저장된 번호 세트 조회

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
  → 동행복권 JSON endpoint 조회 (`lt645/selectPstLt645InfoNew.do` + Referer)
  → satto_draw_result 저장
  → 해당 주차 satto_number_set과 비교
  → satto_match_result 저장
  → 결과 화면 노출
  → (옵션) Noti 발송
```

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
  - 계정 정보는 `.env`의 `LOGIN_USER`, `LOGIN_PW`, 선택값 `LOGIN_EMAIL` 기준
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
