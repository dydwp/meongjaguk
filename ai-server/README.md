# 멍자국 AI 산책로 추천 서버

사용자의 현재 위치를 기준으로 서울 보행 도로망에서 산책 후보 경로를 생성하고,
경로 특성을 XGBoost 모델로 평가해 추천 산책로와 경로 좌표를 반환하는 FastAPI 서버입니다.
추천 제목과 설명은 외부 LLM 호출 없이 규칙 기반으로 생성합니다.

## 환경 설정 및 실행

Python 3.12를 사용합니다. 저장소 루트에서 Anaconda Prompt를 열고 실행합니다.

```bash
conda create -n meongjaguk python=3.12
conda activate meongjaguk
cd ai-server
python -m pip install -r requirements.txt
```

이하 명령은 `ai-server` 디렉터리에서 실행합니다.
서버 시작 시 다음 파일을 로드하므로 실행 전에 준비되어 있어야 합니다.

| 파일 | 용도 |
| --- | --- |
| `data/seoul_walk_runtime.graphml` | 서버가 사용하는 서울 보행 도로망 |
| `data/seoul_green.gpkg` | 서울 공원·녹지 데이터 |
| `model/walk_route_model.json` | 학습된 XGBoost 모델 |

```bash
python -m uvicorn app.main:app --reload
```

- 서버 상태 확인: <http://127.0.0.1:8000/>
- Swagger API 문서 및 요청 실행: <http://127.0.0.1:8000/docs>

## API

### 서버 상태 확인

`GET /`

```json
{
  "message": "AI 산책로 추천 서버 실행 중"
}
```

### 산책로 추천

`POST /api/routes/recommend`

요청 본문:

```json
{
  "latitude": 37.5445,
  "longitude": 127.0374,
  "top_k": 3
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `latitude` | float | 예 | 현재 위치의 위도 |
| `longitude` | float | 예 | 현재 위치의 경도 |
| `top_k` | int | 아니요 | 요청할 추천 개수. 기본값 `3`이며 양의 정수를 사용합니다. |

응답의 최상위 필드는 `latitude`, `longitude`, `count`, `routes`입니다.
`count`는 실제 반환한 경로 수이며, 후보가 부족하면 요청한 개수보다 적을 수 있습니다.
후보가 없으면 `count: 0`, `routes: []`를 반환합니다.

`routes`의 각 항목:

| 필드 | 설명 |
| --- | --- |
| `title`, `description` | 규칙 기반으로 생성한 추천 제목과 설명 |
| `rank`, `candidate_id` | 추천 순위(1부터 시작)와 후보 경로 ID |
| `distance_m` | 총 거리(m), 정수로 반올림 |
| `estimated_minutes` | 예상 소요 시간(분), `distance_m / 66.7`을 반올림 |
| `score` | 모델 예측 점수를 1~5로 제한한 값. 높은 점수순으로 반환 |
| `overlap_ratio` | 경로 중복도 |
| `walkway_ratio` | 보행로 비율 |
| `residential_ratio` | 주거도로 비율 |
| `major_road_ratio` | 주요도로 비율 |
| `green_ratio` | 녹지 비율 |
| `points` | 이동 순서대로 정렬된 좌표 목록. 각 항목은 `sequence`(1부터 시작), `latitude`, `longitude`로 구성 |

요청 본문 검증 실패 시 HTTP 422, 추천 처리 중 예외 발생 시 HTTP 500을 반환합니다.
현재 브라우저 요청에 허용된 CORS 출처는 `http://localhost:8081`입니다.
설정은 [app/main.py](app/main.py)에서 확인할 수 있습니다.

## 추천 동작

```text
현재 위치(latitude, longitude)
    ↓
서울 보행 도로망에서 주변 경로 탐색
    ↓
후보 산책 경로 생성 및 중복·유사 경로 제거
    ↓
경로 특성 계산
(거리, 경로 중복도, 보행로·주거도로·주요도로·녹지 비율)
    ↓
XGBoost 적합도 예측 및 점수순 정렬
    ↓
추천 제목·설명 생성 및 경로 좌표 반환
```

### 추천 제목과 설명

[app/route_description.py](app/route_description.py)의 규칙으로 생성합니다.

- `seoul_green.gpkg`의 유효한 장소명(`name`)과 도형을 사용합니다.
- 경로의 도로 도형 주변 100m 안에서 인접 길이가 가장 긴 장소를 선택합니다.
  도로 도형이 없으면 도로 양 끝 좌표를 연결합니다.
  동일한 이름의 도형은 합치고, 동률이면 거리와 이름 순으로 결정합니다.
- 장소명이 있으면 `서울숲 반려견 산책 코스`처럼 제목을 만듭니다.
  장소를 통과한다고 단정하지 않고 설명에는 `서울숲 주변`으로 표현합니다.
- 장소명이 없으면 0.4 이상인 녹지 → 주거지역 → 보행로 비율 순으로 제목을 선택합니다.
  주요도로 비율이 0.2 이상이면 `조용한` 표현을 피하고 통행 주의 문장을 추가합니다.
- 설명에는 거리·예상 시간과 주요 특징을 담습니다.
  공원 출입 허용 여부, 실제 소음, 노면 상태는 추정하지 않습니다.
- 이름 없는 데이터, 비어 있는 녹지, 주변 장소가 없는 경로도 특징 기반 설명을 반환합니다.
  녹지 좌표 변환과 공간 인덱스 생성은 서버 초기화 시 한 번만 수행합니다.

## 프로젝트 구조

```text
ai-server/
├── app/
│   ├── main.py                         # FastAPI 엔드포인트 및 CORS 설정
│   ├── route_service.py                # 경로 생성, 특성 계산, 모델 예측
│   └── route_description.py            # 주변 장소 조회 및 추천 문구 생성
├── data/
│   ├── seoul_walk.graphml              # 원본 보행 도로망
│   ├── seoul_walk_runtime.graphml      # 서버용 경량 도로망
│   └── seoul_green.gpkg                # 공원·녹지 데이터
├── model/
│   └── walk_route_model.json           # 학습된 XGBoost 모델
├── notebooks/
│   └── route_generation_cleaned.ipynb
├── scripts/
│   ├── prepare_osm_data.py             # OSM 원본에서 지도 데이터 추출
│   └── slim_graphml.py                 # 서버용 경량 GraphML 생성
├── tests/                             # 추천 로직 및 문구 생성 단위 테스트
├── Dockerfile
├── pytest.ini
├── requirements.txt                   # 서버 의존성
└── requirements-dev.txt               # 서버 의존성 및 pytest
```

## 데이터 준비

필요한 데이터가 이미 있으면 이 과정은 생략합니다.

### 런타임 도로망 생성

`data/seoul_walk.graphml`에서 서버에 필요한 속성만 남긴
`data/seoul_walk_runtime.graphml`을 생성합니다.

```bash
python scripts/slim_graphml.py
```

경량화 과정에서 도로의 `geometry` 속성은 제거됩니다.
따라서 이 파일을 사용하는 서버의 장소 조회는 도로 양 끝 좌표를 연결한 선을 사용합니다.

### OSM 원본에서 데이터 추출

추출 작업에는 서버 실행 의존성 외에 `pyrosm`이 필요합니다.
`data/south-korea-latest.osm.pbf`를 준비한 뒤 실행합니다.

```bash
conda install -c conda-forge pyrosm fiona
python scripts/prepare_osm_data.py
```

현재 스크립트의 기본 실행은 **녹지 데이터(`seoul_green.gpkg`)만 생성**합니다.
원본 보행 도로망도 생성하려면 다음과 같이 제공된 함수를 호출합니다.

```bash
python -c "from scripts.prepare_osm_data import create_seoul_walk_graph; create_seoul_walk_graph()"
python scripts/slim_graphml.py
```

## Docker 실행

현재 Dockerfile은 앱과 모델을 이미지에 포함하며, 지도 데이터는 별도로 마운트해야 합니다.
다음은 PowerShell 기준 실행 예시입니다.

```powershell
docker build -t meongjaguk-ai .
docker run --rm -p 8000:8000 --mount "type=bind,source=$($PWD.Path)/data,target=/app/data,readonly" meongjaguk-ai
```

## 테스트

```bash
python -m pip install -r requirements-dev.txt
python -m pytest -v
```

추천 순위·응답 필드·경로 계산·추천 문구를 단위 테스트합니다.
지도 및 모델 로더는 Mock 처리하므로 실제 서울 지도나 모델 파일을 로드하지 않습니다.
검증 범위와 상세 내용은 [tests/README.md](tests/README.md)를 참고하세요.
