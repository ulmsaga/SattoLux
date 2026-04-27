# SattoLux — 버그 & 오류 리포트

> 버그/오류 발견 즉시 수정하고 이 파일에 기록한다.
> 형식: 발생일 / 증상 / 원인 / 수정 내용 / 관련 파일

---

## B001 · HOT_NUMBER/CLAUDE 세트 5개가 동일한 번호로 생성됨

| 항목 | 내용 |
|------|------|
| 발생일 | 2026-04-27 |
| 심각도 | Medium |
| 증상 | HOT_NUMBER + CLAUDE 엔진으로 5세트 요청 시 모든 세트가 동일한 번호 반환 |
| 원인 | ① `generateHotNumberRule`이 Claude를 세트당 1회씩 총 5회 호출 → 동일한 프롬프트·컨텍스트로 같은 응답 반복 ② temperature=0.2로 너무 낮아 다양성 없음 |
| 수정 | ① Claude를 1회 배치 호출(setCount=5)로 변경 ② temperature 0.2 → 1.0 ③ 프롬프트 명시화: "Output EXACTLY N sets, sets must not be identical" |
| 관련 파일 | `MakeWeekNumServiceImpl.java`, `ClaudeNumberGenerator.java` |

---

## B002 · RSA 암호화 방식 불일치 (브라우저 ↔ 서버)

| 항목 | 내용 |
|------|------|
| 발생일 | 2026-04-27 |
| 심각도 | High (로그인 불가) |
| 증상 | FE에서 Web Crypto API로 RSA-OAEP 암호화 후 전송 시 BE에서 500 오류 |
| 원인 | BE `RsaUtil`이 `Cipher.getInstance("RSA")` (PKCS1v1.5) 사용, Web Crypto API는 OAEP만 지원. 추가로 `OAEPWithSHA-256AndMGF1Padding`의 MGF1 기본값이 SHA-1이라 브라우저(SHA-256/SHA-256)와 불일치 |
| 수정 | BE: `RSA/ECB/OAEPPadding` + `OAEPParameterSpec(SHA-256, MGF1, SHA-256)`로 명시. import 오류(`java.security.spec` → `javax.crypto.spec`)도 함께 수정 |
| 관련 파일 | `RsaUtil.java`, `src/api/auth.js` |

---

## B003 · 페이지 새로고침 시 로그인 화면 순간 표시 + 2~3회 새로고침 시 로그아웃

| 항목 | 내용 |
|------|------|
| 발생일 | 2026-04-27 |
| 심각도 | Medium (UX 문제 + 세션 유실) |
| 증상 | ① 로그인 상태로 새로고침하면 로그인 화면이 순간 나타났다 사라짐 ② 2~3회 빠르게 새로고침 시 로그아웃되어 로그인 화면으로 이동 |
| 원인 | React StrictMode가 개발 모드에서 useEffect를 두 번 실행함. `useEffect` 첫 실행으로 refreshToken 회전(rotate) 성공 → 두 번째 실행이 이미 폐기된 구 refreshToken으로 API 호출 → 401 → `clearAuth()` → 로그아웃 플래시. 추가로 초기 state가 `ready=false`로 시작하면서 첫 렌더에 `AppLoader`가 표시되는 사이 위 시나리오가 개입하면 로그인 화면이 순간 노출됨 |
| 수정 | ① **AbortController**: useEffect cleanup에서 첫 번째 요청 취소 → StrictMode 두 번째 실행 시 토큰 재사용 방지 ② **초기 state**: `sessionStorage`에 RT 유무로 `ready` 초기값 결정 (`ready: !hasStoredToken()`) → RT가 없으면 첫 렌더부터 `ready=true`로 즉시 로그인 화면 표시, RT가 있으면 `ready=false` → 로더 표시 후 API 결과로 원자 업데이트 |
| 관련 파일 | `src/context/AuthContext.jsx`, `src/App.jsx`, `src/api/auth.js` |

---

## R001 · 수정 요청 — 버그 B003 재발 (Auth Flicker 미해결)

| 항목 | 내용 |
|------|------|
| 요청일 | 2026-04-27 |
| 유형 | 수정 요청 |
| 내용 | B003 초기 수정(`auth` 단일 state) 후에도 flash 및 2~3회 새로고침 시 로그아웃 현상 지속. StrictMode 이중 실행이 실제 원인임을 확인하고 AbortController + 초기 state 개선으로 재수정 |
| 결과 | AbortController 방식도 완전하지 않음 → R002로 재수정 요청 등록 |

---

## R002 · 수정 요청 — B003 3회 새로고침 로그아웃 최종 수정

| 항목 | 내용 |
|------|------|
| 요청일 | 2026-04-27 |
| 유형 | 수정 요청 |
| 내용 | AbortController 방식은 네트워크 응답이 빠를 경우 abort가 무의미 — 첫 번째 요청이 cleanup 전에 완료되면 두 번째 실행이 또 구 RT로 호출 → 401 → 로그아웃. 모듈 레벨 변수로 완전 차단 필요 |
| 결과 | `_initialRefreshDone = false` 모듈 레벨 guard 적용 — `AuthContext.jsx` 수정 완료 ✅ |
