# SattoLux 배포 가이드

이 문서만 보고 Linux 서버에 SattoLux를 배포할 수 있도록 정리했다.

기준:
- 서버 계정: `eva`
- 앱 경로: `/opt/sattolux`
- 프론트 공개 포트: `80`
- 백엔드 내부 포트: `8081`
- 프론트 서빙: `NGINX`
- 백엔드 구동: `systemd + Spring Boot jar`

---

## 1. 최종 배포 구조

```text
브라우저
  -> http://ulmsaga34.cafe24.com
  -> NGINX :80
     - /          -> /opt/sattolux/frontend/dist
     - /api/*     -> http://127.0.0.1:8081
  -> Spring Boot :8081
  -> MySQL
```

---

## 2. 서버에 준비되어 있어야 할 것

- Java 17
- Node 16
- NGINX
- MySQL
- `eva` 계정

확인 예시:

```bash
java -version
node -v
nginx -v
whoami
```

---

## 3. 배포 파일 위치

- 앱 루트: `/opt/sattolux`
- 백엔드 jar: `/opt/sattolux/backend/target/sattolux.jar`
- 환경변수 파일: `/opt/sattolux/.env`
- systemd 파일 원본: `/opt/sattolux/scripts/deploy/sattolux.service`
- systemd 설치 위치: `/etc/systemd/system/sattolux.service`
- NGINX 설정 원본: `/opt/sattolux/scripts/deploy/nginx-sattolux.conf`
- 로컬 번들 스크립트: `scripts/build-deploy-bundle.sh`
- 로컬 번들 출력 경로: `dist/deploy/`
- 상용 계정/룰 동기화 스크립트: `scripts/sync-prod-accounts.sh`

---

## 4. 소스 배치와 권한

소스를 `/opt/sattolux`에 두고, `eva`가 읽고 쓸 수 있게 맞춘다.

```bash
sudo mkdir -p /opt
sudo chown -R eva:eva /opt
```

이미 소스를 복사해뒀다면:

```bash
sudo chown -R eva:eva /opt/sattolux
```

로그 디렉터리:

```bash
sudo mkdir -p /var/log/sattolux
sudo chown -R eva:eva /var/log/sattolux
```

---

## 5. `.env` 준비

파일 위치:

```bash
/opt/sattolux/.env
```

최소 필수 값:

```dotenv
DB_URL=jdbc:mysql://127.0.0.1:13306/SATTOLUX_DB
DB_USER=sattolux
DB_PASSWORD=change-me
JWT_SECRET=change-me
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8081
SPRING_SQL_INIT_MODE=never
```

Claude 추천 기능을 쓸 경우:

```dotenv
CLAUDE_API_KEY=your-key
```

운영 SMTP를 쓸 경우:

```dotenv
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=mailer
MAIL_PASSWORD=change-me
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
```

권한 최소화:

```bash
chmod 600 /opt/sattolux/.env
chown eva:eva /opt/sattolux/.env
```

주의:
- 운영에서는 `SPRING_SQL_INIT_MODE=never`
- `.env`에 실제 비밀번호/시크릿을 넣고 git에는 올리지 않는다

상용 계정/룰 동기화에 사용하는 값:

```dotenv
LOGIN_ADMIN=admin
LOGIN_ADMIN_PW=change-me
LOGIN_ADMIN_EMAIL=admin@sattolux.local

LOGIN_USER=ulmsaga
LOGIN_PW=change-me
LOGIN_EMAIL=ulmsaga@sattolux.local
```

---

## 6. 프론트 빌드

```bash
cd /opt/sattolux/frontend
npm install
npm run build
```

빌드 결과:

```bash
/opt/sattolux/frontend/dist
```

---

## 7. 백엔드 빌드

```bash
cd /opt/sattolux/backend
./mvnw -q -DskipTests package
```

jar 확인:

```bash
ls -l /opt/sattolux/backend/target/sattolux.jar
```

---

## 8. 백엔드 수동 실행 확인

systemd 등록 전에 한 번 직접 실행해서 환경변수와 DB 연결을 확인한다.

```bash
cd /opt/sattolux
./scripts/run-backend.sh --env-file /opt/sattolux/.env --profile prod --port 8081
```

정상 기동 확인:

```bash
curl -sS http://127.0.0.1:8081/api/health
```

예상 응답 예:

```json
{"status":"UP","service":"sattolux"}
```

확인이 끝나면 `Ctrl + C`로 종료한다.

---

## 9. 상용 계정/룰 동기화

상용 DB에 `admin`, `ulmsaga` 계정과 기본 룰 2개를 보장하려면 아래 스크립트를 실행한다.

```bash
cd /opt/sattolux
./scripts/sync-prod-accounts.sh --env-file /opt/sattolux/.env --database SATTOLUX_DB
```

동기화 대상:
- `admin` (`ADMIN`)
- `ulmsaga` (`USER`)

각 계정에 보장되는 룰:
- `RANDOM / LOCAL` 5세트
- `HOT_NUMBER / CLAUDE` 5세트

특징:
- `schema`는 건드리지 않는다
- `app_user`, `generation_rule`만 반영한다
- 계정이 없으면 생성
- 계정이 있으면 비밀번호/이메일/권한을 `.env` 기준으로 갱신
- 각 사용자 `sort_order=1,2` 룰을 기본값으로 보장

확인 예시는 작업 후 DB에서 별도로 조회하면 된다.

---

## 10. systemd 서비스 등록

설치:

```bash
sudo cp /opt/sattolux/scripts/deploy/sattolux.service /etc/systemd/system/sattolux.service
sudo systemctl daemon-reload
sudo systemctl enable sattolux
sudo systemctl start sattolux
```

상태 확인:

```bash
sudo systemctl status sattolux
```

로그 확인:

```bash
journalctl -u sattolux -f
tail -f /var/log/sattolux/backend.log
```

운영 명령:

```bash
sudo systemctl restart sattolux
sudo systemctl stop sattolux
sudo systemctl start sattolux
```

현재 서비스 파일 핵심:
- `User=eva`
- `Group=eva`
- `.env`는 `/opt/sattolux/.env`
- 실행 스크립트는 `scripts/run-backend.sh`

---

## 11. NGINX 설정

예시 파일:

```bash
/opt/sattolux/scripts/deploy/nginx-sattolux.conf
```

설치 예시:

```bash
sudo cp /opt/sattolux/scripts/deploy/nginx-sattolux.conf /etc/nginx/conf.d/sattolux.conf
sudo nginx -t
sudo systemctl restart nginx
```

설정 내용 요약:
- `listen 80`
- 정적 파일 루트: `/opt/sattolux/frontend/dist`
- `/api/` 요청은 `127.0.0.1:8081`로 프록시
- SPA 라우팅은 `try_files ... /index.html`

확인:

```bash
curl -sS http://127.0.0.1
curl -sS http://127.0.0.1/api/health
```

---

## 12. 실제 배포 순서

처음 배포:

```bash
cd /opt/sattolux/frontend
npm install
npm run build

cd /opt/sattolux/backend
./mvnw -q -DskipTests package

cd /opt/sattolux
./scripts/sync-prod-accounts.sh --env-file /opt/sattolux/.env --database SATTOLUX_DB
./scripts/run-backend.sh --env-file /opt/sattolux/.env --profile prod --port 8081
```

수동 확인 후:

```bash
sudo cp /opt/sattolux/scripts/deploy/sattolux.service /etc/systemd/system/sattolux.service
sudo cp /opt/sattolux/scripts/deploy/nginx-sattolux.conf /etc/nginx/conf.d/sattolux.conf
sudo systemctl daemon-reload
sudo systemctl enable sattolux
sudo systemctl restart sattolux
sudo nginx -t
sudo systemctl restart nginx
```

---

## 13. 로컬에서 빌드 후 업로드하는 방식

로컬에서 서버 업로드용 파일만 묶으려면 아래 스크립트를 실행한다.

```bash
./scripts/build-deploy-bundle.sh
```

생성 결과:

- 디렉터리: `dist/deploy/sattolux-deploy-YYYYMMDD-HHMMSS/`
- 압축 파일: `dist/deploy/sattolux-deploy-YYYYMMDD-HHMMSS.tar.gz`

번들 포함 항목:

- `frontend/dist`
- `backend/target/sattolux.jar`
- `scripts/run-backend.sh`
- `scripts/sync-prod-accounts.sh`
- `scripts/deploy/README.md`
- `scripts/deploy/sattolux.service`
- `scripts/deploy/nginx-sattolux.conf`

서버에는 이 번들을 풀고 문서 순서대로 적용하면 된다.

---

## 14. 재배포 순서

소스 갱신 후:

```bash
cd /opt/sattolux/frontend
npm run build

cd /opt/sattolux/backend
./mvnw -q -DskipTests package

cd /opt/sattolux
./scripts/sync-prod-accounts.sh --env-file /opt/sattolux/.env --database SATTOLUX_DB

sudo systemctl restart sattolux
sudo systemctl restart nginx
```

---

## 15. 장애 확인 포인트

백엔드가 안 뜰 때:
- `.env` 값 확인
- `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` 확인
- `journalctl -u sattolux -f`
- `/var/log/sattolux/backend.log`

프론트가 안 열릴 때:
- `frontend/dist` 존재 여부
- `nginx -t`
- `/var/log/nginx/sattolux-error.log`

API만 안 될 때:
- `curl http://127.0.0.1:8081/api/health`
- NGINX 프록시 설정의 `/api/` 확인

---

## 16. 배포 체크리스트

- [ ] `/opt/sattolux` 소유권이 `eva:eva`
- [ ] `/var/log/sattolux` 소유권이 `eva:eva`
- [ ] `/opt/sattolux/.env` 작성 완료
- [ ] `/opt/sattolux/.env` 권한 `600`
- [ ] `scripts/sync-prod-accounts.sh` 실행 완료
- [ ] `frontend/dist` 생성 완료
- [ ] `backend/target/sattolux.jar` 생성 완료
- [ ] `curl http://127.0.0.1:8081/api/health` 정상
- [ ] `systemctl status sattolux` 정상
- [ ] `nginx -t` 정상
- [ ] `http://도메인` 접속 정상
- [ ] `http://도메인/api/health` 정상
