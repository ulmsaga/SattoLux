# SattoLux
토요일 밤, 별빛처럼 쏟아지는 행운 ✨

## DB 초기화

`.env`의 `DB_URL`, `DB_USER`, `DB_PASSWORD`를 기준으로 두 DB에 동일 스키마를 적용합니다.

```bash
chmod +x scripts/init-db.sh
./scripts/init-db.sh
```

- 대상 DB: `SATTOLUX_DEV_DB`, `SATTOLUX_DB`
- 스키마 파일: `backend/src/main/resources/db/schema.sql`
- 방식: Docker의 `mysql` 클라이언트 이미지를 사용해 원격 MySQL에 적용

개발용 관리자 계정과 기본 생성 규칙까지 넣으려면 아래처럼 실행합니다.

```bash
./scripts/init-db.sh --with-dev-seed
```

- 개발용 공유 계정 ID/PW: `.env`의 `LOGIN_USER`, `LOGIN_PW`
- 선택값: `LOGIN_EMAIL`이 없으면 `${LOGIN_USER}@sattolux.local`로 시드됨
- 적용 대상: `SATTOLUX_DEV_DB`만

### 개발용 공유 계정

| 항목 | 값 |
|------|-----|
| ID | `.env`의 `LOGIN_USER` |
| PW | `.env`의 `LOGIN_PW` |
| Role | `ADMIN` |
| OTP | `OFF` |

## 추첨 결과 동기화

동행복권 최신 JSON endpoint를 사용해 `SATTOLUX_DEV_DB`, `SATTOLUX_DB` 양쪽에 동일한 추첨 결과를 upsert 합니다.

```bash
chmod +x scripts/sync-draw-results.sh
./scripts/sync-draw-results.sh
```

- endpoint: `https://www.dhlottery.co.kr/lt645/selectPstLt645InfoNew.do`
- referer: `https://www.dhlottery.co.kr/lt645/result`
- 기본값: 개발 DB 마지막 저장 회차 다음부터 최신 회차까지 동기화

좁은 범위만 검증하려면 회차 범위를 직접 지정할 수 있습니다.

```bash
./scripts/sync-draw-results.sh --from 1218 --to 1220
```

## 백엔드 로컬 실행

```bash
cd backend
source ../.env
./mvnw spring-boot:run
```
