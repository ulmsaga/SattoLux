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
                    - AI: Claude API or ChatGPT API
                    - 로또 결과: 공공 API or 스크래핑
```

## 핵심 흐름

### 번호 자동 생성 흐름
```
스케줄러 (지정 요일)
  → user_config 조회 (요일, 세트수, 랜덤/AI 비율, 분석 회차)
  → 랜덤 세트 생성 (완전 랜덤 1~45, 6개)
  → AI 세트 생성 (최근 N회 데이터 → AI API 호출 → 번호 반환)
  → lotto_set DB 저장
  → 사용자 조회 시 노출
```

### AI 번호 생성 상세
```
1. lotto_result에서 최근 N회 당첨 번호 조회
2. Claude or ChatGPT API에 데이터 전달 + 프롬프트
3. API 응답에서 번호 파싱
4. 유효성 검증 (1~45, 중복 없음, 6개) 후 저장
```

### 추첨 결과 비교 흐름
```
스케줄러 (토요일 추첨 후)
  → 동행복권 공공 API 조회 (불가 시 스크래핑)
  → lotto_result 저장
  → 해당 주차 lotto_set과 비교
  → lotto_compare 저장
  → 결과 화면 노출
  → (옵션) Noti 발송
```

### 인증 흐름
```
클라이언트
  → RSA 공개키 요청
  → 비밀번호 RSA 암호화 전송 (1차 인증)
  → OTP 전송 (2차 인증, Skip 가능)
  → JWT 발급 + SSE 연결
  → Refresh Token 로테이션 (세션 유지)
  → 로그아웃 (토큰 폐기)
```

## DB 테이블 (초안)

| 테이블 | 설명 |
|--------|------|
| `user` | 사용자 계정 |
| `refresh_token` | Refresh Token 관리 |
| `user_config` | 요일/세트수/AI 설정 (사용자별) |
| `lotto_set` | 생성된 번호 세트 |
| `lotto_result` | 추첨 결과 (공공 API or 스크래핑) |
| `lotto_compare` | 세트 vs 결과 비교 |

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
| 로또 결과 수집 | **동행복권 비공식 API** (무료, 인증 없음) |
| 배포 환경 | 서버 배포 (로컬 테스트 선행) |
| 시크릿 관리 | OS 환경변수 방식 (.env 파일 없음) |
| 번호 생성 | 랜덤 + AI 분석 혼합 (비율 설정 가능) |

## 로또 결과 API

```
GET https://www.dhlottery.co.kr/common.do?method=getLottoNumber&drwNo={회차}
```

- 인증 없음, 무료
- 응답: `drwtNo1~6` (당첨번호), `bnusNo` (보너스), `drwNoDate` (추첨일), `returnValue` (success/fail)

## 확인 필요 사항

- [ ] 무료 Noti 수단 확인 (FCM Web Push 등)
- [x] AI API 확정 → OpenAI GPT-4o (`OPENAI_API_KEY` — Codex CLI 키 재사용)
