# SattoLux — 사전 조사 레포트

## 1. 동행복권 로또 결과 API

### 결론
**비공식 내부 API 사용 확정 — 무료, 인증 없음**

### 조사 내용

| 항목 | 내용 |
|------|------|
| 공식 공공 API | 공공데이터포털(data.go.kr) 경유 가능하나 회원가입 + 활용신청 필요 |
| 비공식 내부 API | 동행복권 홈페이지 내부 호출 URL — 별도 인증 없이 직접 사용 가능 |
| 채택 방식 | **비공식 내부 API** (간단, 즉시 사용 가능) |

### API 상세

```
GET https://www.dhlottery.co.kr/common.do?method=getLottoNumber&drwNo={회차번호}
```

**응답 필드**

| 필드 | 설명 |
|------|------|
| `returnValue` | 성공 여부 (`success` / `fail`) |
| `drwNo` | 회차 번호 |
| `drwNoDate` | 추첨일 (yyyy-MM-dd) |
| `drwtNo1~6` | 당첨 번호 6개 |
| `bnusNo` | 보너스 번호 |
| `firstWinamnt` | 1등 당첨금 |
| `firstPrzwnerCo` | 1등 당첨자 수 |
| `totSellamnt` | 총 판매금액 |

**응답 예시**
```json
{
  "returnValue": "success",
  "drwNo": 1000,
  "drwNoDate": "2022-01-29",
  "drwtNo1": 2,
  "drwtNo2": 8,
  "drwtNo3": 19,
  "drwtNo4": 22,
  "drwtNo5": 32,
  "drwtNo6": 42,
  "bnusNo": 39,
  "firstWinamnt": 1246819620,
  "firstPrzwnerCo": 22,
  "totSellamnt": 118628811000
}
```

### 유의사항
- 공식 문서 없는 비공식 API — 동행복권 측 정책 변경 시 URL/구조 변경 가능성 있음
- 호출 빈도는 최소화 (스케줄러 1회/주 수준이므로 문제 없음)

---

## 2. 카카오 알림톡

### 결론
**유료 서비스 — 무료 플랜 없음. Mailpit(이메일)으로 대체 확정**

### 조사 내용

| 항목 | 내용 |
|------|------|
| 무료 플랜 | **없음** |
| 과금 방식 | 건당 과금 (대행사마다 다름) |
| 최저 단가 | 건당 약 5원 ~ 15원 |
| 이용 조건 | 카카오 공식 채널 등록 + 대행사 계약 필요 |
| 주요 대행사 | 솔라피, 알리고, 다이렉트센드 등 |

### 결정
- OTP 2차 인증 → **Mailpit (이메일)** 대체
- Mailpit 주소: `http://ulmsaga34.cafe24.com:8025`
- 소규모(3명) 운영이므로 이메일 방식으로 충분

---

## 3. 기타 검토 사항

| 항목 | 상태 |
|------|------|
| 무료 Noti (FCM Web Push 등) | 미검토 — 현재 스킵 |
| AI API (OpenAI GPT-4o) | **확정** — Codex CLI용 `OPENAI_API_KEY` 재사용 |
