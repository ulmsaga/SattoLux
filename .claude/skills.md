# SattoLux — 개발 스킬 정의

## Front-End 스킬

### 버전 제약
- **Node 16** (서버 OS 한계 — CentOS 7)
- **Vite 4.x** (Vite 5는 Node 18+ 필요)
- Axios: **1.15.0 이상** 필수 (보안 이슈)

### 컴포넌트 구조
```
App
├── LoginLayer          # 로그인 전용 영역
└── MainLayer           # 인증 후 영역
    ├── TopBar          # 햄버거 버튼
    ├── SideMenu        # 좌측 슬라이드 메뉴 (1LV)
    └── MainContent     # 메인 화면
```

### 메뉴 동작
- 햄버거 버튼 클릭 → 좌측에서 슬라이드 인/아웃
- 토글 방식 (클릭마다 활성/비활성 전환)
- 기본 상태: 비활성(숨김)
- 메뉴 뎁스: 1LV 단층

### 반응형 기준
- Mobile First (Phone 기준 설계 후 Desktop 대응)
- Tailwind breakpoint 활용

### 로컬 개발 포트
- FE dev server: **8080**
- BE API: **8081**
- Vite proxy: `/api` → `http://localhost:8081`

### 빌드 규칙
- **테스트 전체 통과 없이 빌드 금지**
- FE: `vitest` (단위) + `Playwright` (E2E) 통과 후 `vite build`
- BE: `JUnit` 단위/통합 테스트 통과 후 `mvn package`

---

## Back-End 스킬

### 패키지 구조
```
com.saga.sattolux
├── core
│   ├── config          # Security, CORS, MyBatis, Scheduler 설정
│   ├── auth            # JWT, RSA, OTP
│   └── util            # 공통 유틸
└── module
    ├── login           # 로그인
    ├── makeweeknum     # 번호 생성/조회
    ├── result          # 추첨 결과 비교
    └── config          # 사용자 설정
        ├── controller
        ├── service / impl
        └── dao / impl
```

### MyBatis 사용 규칙
- `sqlSessionTemplate` 사용
- namespace + sqlId 직접 지정 방식
- SQL Mapper: query 문 위주 작성
- **JPA 사용 금지**

### 시크릿 관리 (보안)
- `.env` 파일 **사용 안 함**
- 서버: OS 환경변수로 주입 (`export KEY=value` → systemd 또는 쉘 스크립트)
- 로컬: IntelliJ Run Configuration 환경변수 또는 쉘 변수로 주입
- `application.yml`에는 `${ENV_KEY}` 형태로 참조만

```yaml
# application.yml 예시
spring:
  datasource:
    url: ${DB_URL}           # jdbc:mysql://host:3306/SATTOLUX_DEV_DB or SATTOLUX_DB
    username: ${DB_USER}     # sattolux
    password: ${DB_PASSWORD} # 환경변수로만 주입 — 파일 기재 금지
ai:
  claude:
    api-key: ${CLAUDE_API_KEY}
```

```bash
# 개발 시 쉘에서 주입 (파일 저장 X)
export DB_URL=jdbc:mysql://서버IP:3306/SATTOLUX_DEV_DB
export DB_USER=sattolux
export DB_PASSWORD=****
```

### 보안 구현 위치
- `core.auth` 패키지에 집중
  - RSA 키 생성/관리
  - JWT 발급/검증/로테이션
  - OTP 연동 (카카오 or Mailpit)
  - Spring Security 필터 체인

---

## AI 연동 스킬

### 구조
- AI 제공자를 인터페이스로 추상화 → Claude / ChatGPT 전환 가능
- `core.ai` 패키지에 구현

```
core.ai
├── AiNumberGenerator       # 인터페이스
└── OpenAiNumberGenerator   # OpenAI GPT-4o 구현체
```

### 호출 흐름
1. `lotto_result`에서 최근 N회 당첨 번호 조회
2. AI API 호출 (프롬프트 + 데이터)
3. 응답 파싱 → 번호 유효성 검증 (1~45, 중복 없음, 6개)
4. 실패 시 완전 랜덤으로 fallback

---

## 테스트 스킬

### 원칙
- **테스트 케이스 작성 + 전체 통과 = 빌드 조건**
- 기능 구현과 테스트 케이스는 동시에 작성

### Front-End 테스트
| 구분 | 도구 | 대상 |
|------|------|------|
| 단위 테스트 | Vitest | 유틸, 훅, 컴포넌트 단위 |
| E2E 테스트 | Playwright | 로그인, 번호 조회, 결과 비교 흐름 |

### Back-End 테스트
| 구분 | 도구 | 대상 |
|------|------|------|
| 단위 테스트 | JUnit 5 + Mockito | Service, Util 단위 |
| 통합 테스트 | Spring Boot Test + TestContainers | API 엔드포인트 + DB |
| E2E 테스트 | Playwright (FE 연동) | 전체 사용자 시나리오 |

### 테스트 커버리지 기준
- 핵심 로직 (인증, 번호 생성, 비교): **80% 이상**
- UI 흐름: 주요 시나리오 전수 커버

---

## 공통 스킬

### 에러 코드
- 별도 에러 코드 정리 문서 작성 예정 (`errors.md`)

### Noti 방법 (검토 필요)
- FCM Web Push (무료 티어) 검토
- 불가 시 스킵

### NGINX 설정 (서버)
- FE 정적 파일 서빙 (dist/)
- `/api` 요청 → Spring Boot(8081) 프록시
