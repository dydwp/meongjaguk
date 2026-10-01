# 멍자국 AWS 배포 가이드

AWS EC2 서버 1대에 Docker Compose로 **MySQL + Spring 앱 + AI 서버**를 함께 실행합니다.
로컬 개발은 레포 루트의 `docker-compose.yml`, 배포는 이 폴더의 파일을 사용합니다.

```
infra/
├── README.md                 ← 이 문서 (배포 순서와 명령어)
├── docker-compose.prod.yml   ← 배포용 실행 설정
├── .env.prod.example         ← 배포 서버 환경변수 양식 (복사해서 .env.prod 로 사용, 커밋 금지)
├── deploy.sh                 ← 전체 배포 스크립트 (코드 받기 → 빌드 → 실행 → 상태 확인)
└── ai-deploy.sh              ← AI 서버만 배포하는 스크립트
```

## 구성

```
사용자 브라우저 ──(80)──→ [EC2]
                           ├─ app (Spring, 8081) ──→ mysql (3306, 외부 비공개)
                           │        └──────────────→ ai-server (8000, 외부 비공개)
                           └─ 상태 확인: /actuator/health
```

- 외부에 여는 포트는 **80(웹)** 과 **22(SSH, 내 IP만)** 두 개입니다.
- AI 추천은 브라우저 → Spring(`/api/routes/recommend`) → AI 서버 순서로 서버 안에서 호출합니다.

## 서버 사양

| 항목 | 값 | 이유 |
|---|---|---|
| 리전 | 서울 `ap-northeast-2` | |
| OS | Ubuntu 24.04 | |
| 인스턴스 | **t3.large (메모리 8GB) 이상** | AI 서버만 약 4.4GB 사용 |
| 디스크 | **30GB 이상** (gp3) | Docker 이미지 + AI 그래프 데이터(LFS) + DB |

> 요금이 시간 단위로 나옵니다. 사용하지 않을 때는 인스턴스를 **중지**하세요. (탄력적 IP는 중지 중에도 소액 과금)

---

## 1. AWS 자원 만들기

웹 콘솔 또는 AWS CLI 중 편한 방법을 사용합니다. 결과는 같습니다.

### 방법 A. 웹 콘솔

1. **EC2 → 키 페어 → 키 페어 생성**: 이름 `meongjaguk-key`, 형식 `.pem` → 파일 보관 (다시 받을 수 없음)
2. **EC2 → 보안 그룹 → 생성**: 이름 `meongjaguk-sg`
   - 인바운드: `SSH(22)` 소스 **내 IP** / `HTTP(80)` 소스 `0.0.0.0/0`
3. **EC2 → 인스턴스 시작**: Ubuntu 24.04, `t3.large`, 키 페어 `meongjaguk-key`, 보안 그룹 `meongjaguk-sg`, 스토리지 `30GiB gp3`
4. **EC2 → 탄력적 IP → 할당 → 인스턴스에 연결** (서버를 껐다 켜도 주소 유지)

### 방법 B. AWS CLI (Git Bash 기준)

```bash
# 0) AWS 계정 연결 (Access Key, 리전 ap-northeast-2 입력)
aws configure

# 1) 키 페어 (서버 접속용 열쇠)
aws ec2 create-key-pair --key-name meongjaguk-key --key-type rsa --key-format pem \
  --query KeyMaterial --output text > meongjaguk-key.pem

# 2) 보안 그룹 (방화벽): SSH 는 내 IP 만, 웹(80)은 모두 허용
SG_ID=$(aws ec2 create-security-group --group-name meongjaguk-sg \
  --description "meongjaguk web server" --query GroupId --output text)
MY_IP=$(curl -s https://checkip.amazonaws.com)
aws ec2 authorize-security-group-ingress --group-id "$SG_ID" --protocol tcp --port 22 --cidr "$MY_IP/32"
aws ec2 authorize-security-group-ingress --group-id "$SG_ID" --protocol tcp --port 80 --cidr 0.0.0.0/0

# 3) 서버(EC2) 만들기: Ubuntu 24.04 최신 이미지, t3.large, 디스크 30GB
AMI_ID=$(aws ssm get-parameter \
  --name /aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id \
  --query Parameter.Value --output text)
INSTANCE_ID=$(aws ec2 run-instances --image-id "$AMI_ID" --instance-type t3.large \
  --key-name meongjaguk-key --security-group-ids "$SG_ID" \
  --block-device-mappings 'DeviceName=/dev/sda1,Ebs={VolumeSize=30,VolumeType=gp3}' \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=meongjaguk}]' \
  --query 'Instances[0].InstanceId' --output text)
aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"

# 4) 고정 IP(탄력적 IP) 연결
ALLOC_ID=$(aws ec2 allocate-address --query AllocationId --output text)
aws ec2 associate-address --instance-id "$INSTANCE_ID" --allocation-id "$ALLOC_ID"

# 5) 서버 주소 확인
aws ec2 describe-instances --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text
```

---

## 2. 서버 준비 (처음 한 번)

```bash
# 서버 접속 (Windows 는 Git Bash 사용, pem 파일이 있는 폴더에서)
chmod 400 meongjaguk-key.pem
ssh -i meongjaguk-key.pem ubuntu@<서버IP>

# --- 여기부터 서버 안 ---
# Docker + Compose 설치 (공식 스크립트)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu     # sudo 없이 docker 사용 → 적용하려면 exit 후 다시 접속

# Git, Git LFS (AI 그래프 데이터용)
sudo apt-get update && sudo apt-get install -y git git-lfs
git lfs install

# (권장) 스왑 2GB: 빌드 중 메모리가 부족할 때 대비
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 3. 코드와 환경변수 준비 (처음 한 번)

```bash
git clone https://github.com/dydwp/meongjaguk.git
cd meongjaguk
git switch main          # 배포할 브랜치
git lfs pull             # AI 그래프 데이터 (수백 MB, 시간 걸림)

cp infra/.env.prod.example infra/.env.prod
nano infra/.env.prod     # 값 채우기 (아래 표 참고) → Ctrl+O 저장, Ctrl+X 종료
chmod 600 infra/.env.prod
```

> 비공개 레포라면 clone 할 때 GitHub 아이디와 **Personal Access Token**(비밀번호 대신)을 입력합니다.

### 환경변수 (`infra/.env.prod`)

| 키 | 값 |
|---|---|
| `APP_PORT` | 비우면 80 |
| `MYSQL_ROOT_PASSWORD` | 로컬과 다른 **강한 비밀번호** |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENTID` / `CLIENTSECRET` | 구글 OAuth |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_KAKAO_CLIENTID` / `CLIENTSECRET` | 카카오 OAuth |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_NAVER_CLIENTID` / `CLIENTSECRET` | 네이버 OAuth |
| `KAKAO_MAP_JSKEY` | 카카오맵 JavaScript 키 |

`SPRING_PROFILES_ACTIVE=prod`, DB 접속 주소, AI 서버 주소는 `docker-compose.prod.yml` 이 자동으로 넣습니다.

## 4. 배포

```bash
./infra/deploy.sh
```

스크립트가 하는 일: ① `git pull` + `git lfs pull` → ② 이미지 빌드·실행 → ③ mysql / app / ai-server 가 **healthy** 될 때까지 확인 → ④ 옛 이미지 정리

처음 실행하면 MySQL 이 `sql/db 테이블 생성 sql` 스크립트로 테이블을 만듭니다. (운영은 `ddl-auto=none` 이라 JPA 가 테이블을 만들지 않음)

### 확인

```bash
curl http://localhost/actuator/health          # {"status":"UP"} 이면 정상
```

브라우저에서 `http://<서버IP>` 접속 → 메인, 로그인, 반려견 사진 업로드, 카카오맵, AI 추천 확인

## 5. 외부 서비스에 서버 주소 등록 (처음 한 번)

| 서비스 | 등록할 곳 | 값 |
|---|---|---|
| 카카오 로그인 | 카카오 개발자 → 앱 → 카카오 로그인 → Redirect URI | `http://<서버주소>/login/oauth2/code/kakao` |
| 네이버 로그인 | 네이버 개발자센터 → 애플리케이션 → 서비스 URL / Callback URL | `http://<서버주소>/login/oauth2/code/naver` |
| 구글 로그인 | Google Cloud Console → 사용자 인증 정보 → 승인된 리디렉션 URI | `http://<서버주소>/login/oauth2/code/google` |
| 카카오맵 | 카카오 개발자 → 앱 → 플랫폼 → Web 사이트 도메인 | `http://<서버주소>` |

> **구글은 IP 주소를 리디렉션 URI로 받지 않습니다.** 도메인을 연결하거나, 임시로 `http://<서버IP>.nip.io` 같은 주소를 사용해야 합니다. (그 경우 브라우저도 그 주소로 접속)

---

## 운영 명령어

레포 루트에서 실행합니다. 길어서 별칭을 만들어 두면 편합니다.

```bash
alias dcp='docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.prod'
```

| 하는 일 | 명령어 |
|---|---|
| 새 버전 배포 | `./infra/deploy.sh` |
| AI 서버만 배포 | `bash infra/ai-deploy.sh` (`SKIP_PULL=1`로 코드 받기 생략) |
| 상태 보기 | `dcp ps` |
| 로그 보기 | `dcp logs -f --tail 100 app` (또는 `ai-server`, `mysql`) |
| 앱만 재시작 | `dcp restart app` |
| 전체 중지 (데이터 유지) | `dcp down` |
| 이전 버전으로 되돌리기 | `git log --oneline` → `git checkout <커밋>` → `SKIP_PULL=1 ./infra/deploy.sh` (끝나면 `git switch main`) |
| DB 백업 | `dcp exec mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" meongjaguk' > backup.sql` |
| 디스크 / 메모리 확인 | `df -h` / `free -h` / `docker stats --no-stream` |

> `dcp down -v` 는 **DB 와 업로드 사진까지 삭제**합니다. 운영 서버에서는 사용하지 마세요.

## 서버 끄기 / 켜기 / 삭제 (내 PC에서, AWS CLI)

```bash
aws ec2 stop-instances  --instance-ids <인스턴스ID>   # 중지 (디스크 요금만)
aws ec2 start-instances --instance-ids <인스턴스ID>   # 다시 켜기 → 컨테이너는 자동 재시작
# 완전히 삭제 (데이터 모두 사라짐)
aws ec2 terminate-instances --instance-ids <인스턴스ID>
aws ec2 release-address --allocation-id <탄력적IP 할당ID>
```

## 문제가 생기면

| 증상 | 확인 |
|---|---|
| 브라우저 접속 안 됨 | 보안 그룹 80 포트 열림 여부, `dcp ps` 에서 app 이 healthy 인지 |
| app 이 unhealthy | `dcp logs --tail 100 app` / `curl localhost/actuator/health` 가 DOWN 이면 DB 문제 |
| ai-server 가 계속 재시작 | 메모리 부족 → `free -h`, 인스턴스 사양 올리기 |
| AI 추천만 실패 | `git lfs pull` 했는지 (`ls -lh ai-server/data` 파일 크기가 수백 MB 인지) |
| 로그인 실패 (redirect_uri 오류) | 5번 외부 서비스 등록 주소와 접속 주소가 같은지 |
| SSH 접속 안 됨 | 보안 그룹 22 포트의 내 IP 가 바뀌지 않았는지 |
