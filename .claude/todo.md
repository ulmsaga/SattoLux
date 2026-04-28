# SattoLux — Todo

## 현재 정리 메모 (2026-04-28)

- FE/BE 로컬 실행 확인 완료
  - FE: `http://localhost:8080`
  - BE: `http://localhost:8081`
- 인증 플로우 확인 완료
  - RSA 로그인
  - OTP Skip 계정 토큰 발급
  - `GET /api/auth/me` 확인
- 번호 생성 기능 확인 완료
  - `RANDOM / LOCAL` 생성 성공
  - `HOT_NUMBER / CLAUDE` 생성 성공
  - `MIXED`는 현재 범위 제외, 차기 업그레이드 예정
- 번호 생성 화면 / 설정 화면 구현 완료
- 마킹 보기 화면 설계 확정
  - `5세트 = 1장`
  - `한 화면 = 한 구역`
  - `1장 E → 2장 A` 연속 스와이프
- 자동 생성 스케줄러 / 수동 생성 가능 조건 구현 완료
- 결과/알림 기능 1차 구현 완료
  - 결과 수집 스케줄러
  - 세트 비교 로직
  - 결과 화면
  - 알림 DB 저장
  - SSE push
  - TopBar 알림 뱃지 / 패널
  - ADMIN 수동 결과 테스트 API / 화면 버튼
- 결과 조회 API 오류 수정 완료
  - `/api/result/week` 500 원인: MySQL 예약어 `rank`
  - mapper 수정 후 정상 응답 확인
- 수동 결과 테스트 실검증 완료
  - 테스트 세트 준비 성공: `2026-04-25` 기준 `2026년 4월 4주차`, 10세트
  - 지난주 결과 테스트 성공: `1221회`
  - 결과 조회 API 정상 응답 확인
  - unread 알림 1건 생성 확인
- 권한 분기 검증 완료
  - `role_code=ADMIN/USER` 기준 유지
  - 일반 사용자 dev 계정 생성 확인
  - 일반 사용자 `ADMIN` 전용 API 접근 시 `403` 확인
- dev 계정 보안 기준 보강
  - 일반 사용자 비밀번호는 `GENERAL_LOGIN_PW` 별도 env 필수
  - 관리자 비밀번호 상속 기본값 제거
- 남은 핵심 검증
  - FE에서 SSE 실수신 뱃지 반영 확인
  - 읽음 처리 포함 FE 동작 최종 확인

## Phase 0. 사전 확인 (개발 전)

- [x] 동행복권 API 확인 → 비공식 API 사용 확정 (무료, 인증 없음)
- [x] 카카오 알림톡 확인 → 무료 없음, **Mailpit으로 확정**
- [x] Claude API 키 확정 → Anthropic Claude (`CLAUDE_API_KEY`)
- [ ] 무료 Noti 수단 확인 (FCM Web Push 등, 현재 스킵)

## Phase 1. 프로젝트 기반 세팅

- [x] FE 프로젝트 생성 (Vite 4.4 + React 18, Node 16 호환)
- [x] FE 패키지 설치 (Tailwind 3.4, AG-Grid, Axios 1.x, react-router-dom)
  - [ ] shadcn/ui 초기화 (Phase 3에서 진행 — 화면 시작 시점)
- [x] Vite proxy 설정 (`/api` → `localhost:8081`)
- [x] BE 프로젝트 생성 (Spring Boot 3.5.0 + Java 17)
- [x] BE 패키지 구조 생성 (`core/{config,auth,util}` + `module/{login,makeweeknum,result,config}`)
- [x] 기본 CORS, Security, MyBatis 설정
- [x] 환경변수 방식 시크릿 관리 설정 (`application.yml` + `${ENV}`)
- [x] Health 엔드포인트 (`GET /api/health`) + FE 연동 화면
- [x] 로컬 JAVA_HOME → Java 17 변경 (`~/.zshrc`)
- [x] FE/BE 빌드 검증 통과
- [x] MySQL DB/스키마 생성 (개발/상용 동일 스키마 + 개발 DB 시드)

## Phase 2. 인증/인가

- [x] RSA 키 발급 API
- [x] 1차 인증 (비밀번호 검증)
- [x] 2차 인증 OTP — Skip 옵션 포함
  - [x] 카카오 알림톡 무료 없음 확인 → **Mailpit(이메일) 확정**
- [x] JWT 발급 + SSE 연결
- [x] Refresh Token 로테이션
- [x] 로그아웃
- [x] 로그인 실패 횟수 / 계정 잠금 처리
- [x] OTP / Refresh Token 해시 저장

## Phase 3. 레이아웃 & 공통 UI

- [x] LoginLayer 구현
- [x] MainLayer 구현 (TopBar + SideMenu + MainContent)
- [x] 햄버거 메뉴 슬라이드 동작
- [x] 반응형 (Mobile First → Desktop)
  - [x] shadcn/ui 코어 의존성 설치 + 수동 초기화 (Button, Input)
  - [x] RSA 암호화: PKCS1v1.5 → OAEP(SHA-256/SHA-256) 으로 변경 (Web Crypto API 호환)

## Phase 4. 핵심 기능 — 번호 생성

- [x] `generation_rule` 설정 UI (요일, 전략, 엔진, 세트수, 분석 회차, 사용 여부)
- [x] 주간 총 세트 수 검증 (row 합계: 1 / 5 / 10 / 15 / 20)
- [x] 완전 랜덤 번호 생성 로직
- [x] HOT_NUMBER 번호 생성 로직
- [ ] MIXED 번호 생성 로직
  - 현재 범위 제외 — 차기 업그레이드 시 진행
- [ ] Claude 호출 조합 엔진 구현
  - [ ] HOT_NUMBER / MIXED rule 묶음 요청 포맷 정의
    - MIXED는 현재 범위 제외, 차기 업그레이드 시 반영
  - [x] Claude API 구현체
  - [x] 실패 시 LOCAL fallback
- [x] 스케줄러 (지정 요일 자동 생성)
- [x] 스케줄러 시각 properties 관리 (초기값: 오전 9시)
- [x] 자동 생성 누락 시 수동 생성 활성 조건 정의/구현
- [x] 번호 조회 화면 (S02)
- [x] 마킹 보기 화면 (S02-1)

## Phase 5. 핵심 기능 — 결과 비교

- [x] 로또 결과 수집 방법 확정 → 동행복권 JSON endpoint 사용 (`lt645/selectPstLt645InfoNew.do` + Referer)
- [x] 결과 수집 스케줄러 (토요일 21:00~23:00, 5분 간격 폴링)
  - [x] DB에 해당 회차 결과가 이미 있으면 즉시 종료
  - [x] 결과 미오픈 시 skip
  - [x] 일요일 보정 수집 추가 (`00:00`, `09:00`)
- [x] 세트 vs 결과 비교 로직
- [x] 결과 화면 (S03)
- [x] 결과 도착 알림 생성 (DB 저장 + unread 관리)
- [x] 결과 도착 SSE push
- [x] 알림 클릭 시 결과 페이지 deep link 이동
- [x] 실제 추첨 결과 기준 end-to-end 검증
- [x] 지난주 10세트 기준 수동 테스트 기능
  - `POST /api/result/admin/manual-test-latest`
  - `POST /api/result/admin/manual-test-prepare`
  - 결과 화면의 `지난주 결과 테스트` 버튼
  - [x] 지난주 기준 테스트 세트 10건 준비
  - [x] 최신 결과 API 조회 후 지난주 세트와 비교
  - [ ] 결과 화면 / 알림 / SSE까지 함께 검증
- [ ] 스케줄러 테스트 전략 반영
  - [x] local/test 전용 짧은 cron 구성 (`scheduler-test`, 10초 간격)
  - [x] 스케줄러 실행 로그 / savedCount / skip reason 보강
  - [x] 시간 판정 정책 단위 테스트 추가

## Phase 6. Noti (옵션)

- [ ] 무료 Noti 수단 확인 (FCM Web Push 등)
  - 가능 시 구현 / 불가 시 스킵
- [x] TopBar 알림 뱃지 / 알림 패널 UI
- [x] 알림 읽음 처리 / unread count 조회 API
- [ ] SSE 실수신 포함 실사용 시나리오 검증

## Phase 7. 테스트

### FE
- [ ] Vitest 단위 테스트 — 유틸, 훅, 컴포넌트
- [ ] Playwright E2E — 로그인 플로우
- [ ] Playwright E2E — 번호 조회 플로우
- [ ] Playwright E2E — 결과 비교 플로우
- [ ] 전체 테스트 통과 확인 후 `vite build`

### BE
- [ ] JUnit 단위 테스트 — Service, Util
- [ ] Spring Boot 통합 테스트 — API 엔드포인트 + DB (TestContainers)
- [ ] 핵심 로직 커버리지 80% 이상 확인
- [ ] 전체 테스트 통과 확인 후 `mvn package`

## Phase 8. 마무리 & 배포

- [ ] 에러 코드 정리 (`errors.md`)
- [ ] 서버 배포
  - [x] Spring Boot 운영 실행 스크립트 (`scripts/run-backend.sh`)
  - [x] systemd service 예시 파일 (`scripts/deploy/sattolux.service`)
  - [x] systemd 설치/기동 문서 (`scripts/deploy/README.md`)
  - [ ] NGINX 설정 (정적 파일 + `/api` → `:8081` 프록시)
  - [ ] Mailpit Docker 설치 (`http://ulmsaga34.cafe24.com:8025`)
  - [ ] OS 환경변수 등록 (DB, LLM API Key 등)
  - [ ] Spring Boot jar 배포 (:8081, 외부 미노출)
  - [ ] 도메인 연결 확인 (http://ulmsaga34.cafe24.com)
