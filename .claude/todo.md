# SattoLux — Todo

## Phase 0. 사전 확인 (개발 전)

- [x] 동행복권 API 확인 → 비공식 API 사용 확정 (무료, 인증 없음)
- [x] 카카오 알림톡 확인 → 무료 없음, **Mailpit으로 확정**
- [x] AI API 키 확정 → OpenAI GPT-4o (`OPENAI_API_KEY` — Codex CLI 키 재사용)
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
- [ ] MySQL DB/스키마 생성 (Phase 2 진입 전 — 환경변수 + 사용자 DB 생성 작업)

## Phase 2. 인증/인가

- [ ] RSA 키 발급 API
- [ ] 1차 인증 (비밀번호 검증)
- [ ] 2차 인증 OTP — Skip 옵션 포함
  - [x] 카카오 알림톡 무료 없음 확인 → **Mailpit(이메일) 확정**
- [ ] JWT 발급 + SSE 연결
- [ ] Refresh Token 로테이션
- [ ] 로그아웃

## Phase 3. 레이아웃 & 공통 UI

- [ ] LoginLayer 구현
- [ ] MainLayer 구현 (TopBar + SideMenu + MainContent)
- [ ] 햄버거 메뉴 슬라이드 동작
- [ ] 반응형 (Mobile First → Desktop)

## Phase 4. 핵심 기능 — 번호 생성

- [ ] `user_config` 설정 UI (요일, 총 세트수, 랜덤/AI 비율, 분석 회차, AI 제공자)
- [ ] 완전 랜덤 번호 생성 로직
- [ ] AI 분석 번호 생성 로직
  - [ ] AI 제공자 인터페이스 추상화 (`core.ai`)
  - [ ] Claude API 구현체
  - [ ] ChatGPT API 구현체 (선택)
  - [ ] 실패 시 랜덤 fallback
- [ ] 스케줄러 (지정 요일 자동 생성)
- [ ] 번호 조회 화면 (S02)

## Phase 5. 핵심 기능 — 결과 비교

- [x] 로또 결과 수집 방법 확정 → 동행복권 비공식 API 사용
- [ ] 결과 수집 스케줄러 (토요일)
- [ ] 세트 vs 결과 비교 로직
- [ ] 결과 화면 (S03)

## Phase 6. Noti (옵션)

- [ ] 무료 Noti 수단 확인 (FCM Web Push 등)
  - 가능 시 구현 / 불가 시 스킵

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
  - [ ] NGINX 설정 (정적 파일 + `/api` → `:8081` 프록시)
  - [ ] Mailpit Docker 설치 (`http://ulmsaga34.cafe24.com:8025`)
  - [ ] OS 환경변수 등록 (DB, AI API Key 등)
  - [ ] Spring Boot jar 배포 (:8081, 외부 미노출)
  - [ ] 도메인 연결 확인 (ulmsaga34.cafe24.com:8080)
