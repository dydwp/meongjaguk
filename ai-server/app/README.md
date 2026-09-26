## Python AI 환경 설정

AI 산책로 추천 기능은 FastAPI 서버로 실행합니다.

### 1. Conda 가상환경 생성

Anaconda Prompt에서 아래 명령어를 실행합니다.

```bash
conda create -n meongjaguk python=3.12
```

가상환경을 활성화합니다.

```bash
conda activate meongjaguk
```

### 2. AI 서버 폴더 이동

프로젝트 루트 기준으로 `ai-server` 폴더로 이동합니다.

```bash
cd ai-server
```

### 3. 파일 구조

```text
ai-server/
├── app/
│   ├── __init__.py
│   ├── main.py
│   └── route_service.py
│
├── data/
│   ├── seoul_walk.graphml
│   └── seoul_green.gpkg
│
├── model/
│   └── walk_route_model.json
│
├── notebooks/
│   └── route_generation_cleaned.ipynb
│
├── scripts/
│   └── prepare_osm_data.py
│
└── requirements.txt
```

### 4. Python 라이브러리 설치

`ai-server` 폴더에서 아래 명령어를 실행합니다.

```bash
pip install -r requirements.txt
```

Windows 환경에서 `pyrosm`, `fiona`는 pip 설치 시 오류가 발생할 수 있으므로 Conda를 이용하여 설치합니다.

```bash
conda install -c conda-forge pyrosm fiona
```

설치 확인:

```bash
python -c "import pyrosm; print(pyrosm.__version__)"
```

### 5. AI 데이터 파일 확인

AI 추천 서버 실행을 위해 다음 파일이 필요합니다.

```text
ai-server/data/seoul_walk.graphml
ai-server/data/seoul_green.gpkg
ai-server/model/walk_route_model.json
```

각 파일의 역할은 다음과 같습니다.

- `seoul_walk.graphml`: 서울 지역 보행 도로망 데이터
- `seoul_green.gpkg`: 서울 지역 공원 및 녹지 데이터
- `walk_route_model.json`: 산책로 적합도를 예측하는 XGBoost 모델

AI 서버는 실행 시 위 파일을 로컬에서 읽어 사용하며, 사용자 요청마다 외부 Overpass API를 호출하지 않습니다.

> `south-korea-latest.osm.pbf`는 `prepare_osm_data.py`로 로컬 데이터를 다시 생성할 때만 필요하며, 일반적인 FastAPI 실행에는 필요하지 않습니다.

## FastAPI 실행

가상환경을 활성화합니다.

```bash
conda activate meongjaguk
```

`ai-server` 폴더로 이동합니다.

```bash
cd ai-server
```

FastAPI 서버를 실행합니다.

```bash
python -m uvicorn app.main:app --reload
```

정상적으로 실행되면 다음 주소에서 API 문서를 확인할 수 있습니다.

```text
http://127.0.0.1:8000/docs
```

산책로 추천 API:

```text
POST /api/routes/recommend
```

요청 예시:

```json
{
  "latitude": 37.62135557384376,
  "longitude": 126.92621133239408
}
```

정상 응답 시 현재 위치를 기준으로 추천 산책로 4개를 반환합니다.

## Spring Boot 실행

FastAPI 서버가 실행된 상태에서 별도의 터미널을 실행합니다.

프로젝트 루트 폴더에서 Spring Boot를 실행합니다.

Windows:

```bash
.\mvnw.cmd spring-boot:run
```

정상적으로 실행되면 브라우저에서 접속합니다.

```text
http://localhost:8081
```

브라우저에서 현재 위치 권한을 허용하면 Vanilla JavaScript에서 현재 좌표를 가져와 FastAPI 산책로 추천 API를 호출합니다.

## 전체 실행 순서

```text
1. MySQL 실행
   ↓
2. DB 생성 및 SQL 파일 실행
   ↓
3. application.properties DB 정보 설정
   ↓
4. Conda meongjaguk 환경 활성화
   ↓
5. FastAPI 실행 (127.0.0.1:8000)
   ↓
6. Spring Boot 실행 (localhost:8081)
   ↓
7. 브라우저 localhost:8081 접속
   ↓
8. 현재 위치 권한 허용
   ↓
9. AI 추천 산책로 조회
```

## FastAPI CORS 설정

Spring Boot에서 FastAPI를 호출하기 위해 `ai-server/app/main.py`에서 다음 Origin을 허용합니다.

```python
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:8081"
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
```

## AI 추천 기능 동작 구조

```text
사용자 현재 위치
        ↓
Vanilla JavaScript
        ↓
FastAPI
        ↓
로컬 서울 보행 도로망 데이터
        ↓
후보 산책 경로 생성
        ↓
녹지 / 보행로 / 주거도로 / 주요도로 / 중복도 Feature 추출
        ↓
XGBoost 산책 적합도 예측
        ↓
추천 산책로 TOP 4 반환
```
