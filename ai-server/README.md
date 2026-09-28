# 멍자국 AI 산책로 추천 서버

## AI 기능 정의

사용자의 현재 위치를 기준으로 주변 산책 후보 경로를 생성하고,
경로 특성을 XGBoost 모델로 평가하여 추천 산책로를 반환합니다.

## 동작 순서

```text
현재 위치(latitude, longitude)
        ↓
FastAPI 추천 요청
        ↓
서울 보행 도로망에서 주변 경로 탐색
        ↓
후보 산책 경로 생성
        ↓
경로 Feature 계산
- 거리
- 경로 중복도
- 보행로 비율
- 주거도로 비율
- 주요도로 비율
- 녹지 비율
        ↓
XGBoost 적합도 예측
        ↓
추천 경로 반환
```

## 주요 파일

### 추천 제목과 설명

`POST /api/routes/recommend`의 `routes` 항목에는 기존 필드와 함께
`title`, `description`이 포함됩니다. 외부 LLM 호출 없이
`app/route_description.py`의 규칙으로 생성합니다.

- `seoul_green.gpkg`의 유효한 `name`과 도형을 사용합니다.
- 경로의 실제 도로 도형 주변 100m 안에서 인접 길이가 가장 긴 장소를 선택합니다.
  도로 도형이 없으면 도로 양 끝 좌표를 연결합니다.
  동일한 이름의 도형은 합치고, 동률이면 거리와 이름 순으로 결정합니다.
- 장소명이 있으면 `서울숲 반려견 산책 코스`처럼 제목을 만듭니다.
  장소를 통과한다고 단정하지 않고 설명에는 `서울숲 주변`으로 표현합니다.
- 장소명이 없으면 0.4 이상인 녹지 → 주거지역 → 보행로 비율 순으로 제목을 선택합니다.
  주요도로 비율이 0.2 이상이면 `조용한` 표현을 피하고 통행 주의 문장을 추가합니다.
- 설명에는 거리·예상 시간과 주요 특징을 문장으로 담습니다.
  공원 출입 허용 여부, 실제 소음, 노면 상태는 추정하지 않습니다.
- 이름 없는 데이터, 비어 있는 녹지, 주변 장소가 없는 경로도 특징 기반 설명을 반환합니다.
  녹지 좌표 변환과 공간 인덱스 생성은 서버 초기화 시 한 번만 수행합니다.

예시:

```json
{
  "title": "서울숲 반려견 산책 코스",
  "description": "서울숲 주변을 둘러보며 약 2.6km를 걷는 코스로, 예상 소요 시간은 약 40분입니다. 녹지 가까이 이어지는 구간과 보행로가 포함되어, 주변 풍경을 살피며 걷기 좋은 경로입니다."
}
```

테스트 (`ai-server` 디렉터리에서 실행):

```bash
python -m unittest discover -s tests -v
```

```text
ai-server/
├── app/
│   ├── main.py
│   └── route_service.py
├── data/
│   ├── seoul_walk.graphml
│   └── seoul_green.gpkg
├── model/
│   └── walk_route_model.json
├── notebooks/
│   └── route_generation_cleaned.ipynb
├── scripts/
│   └── prepare_osm_data.py
└── requirements.txt
```

- `main.py` : FastAPI 실행 및 추천 API 정의
- `route_service.py` : 경로 생성, Feature 계산, XGBoost 추천 처리
- `seoul_walk.graphml` : 서울 보행 도로망 데이터
- `seoul_green.gpkg` : 서울 공원·녹지 데이터
- `walk_route_model.json` : 학습된 XGBoost 모델

## 최초 환경 설정

Anaconda Prompt에서 가상환경을 생성합니다.

```bash
conda create -n meongjaguk python=3.12
conda activate meongjaguk
```

`ai-server` 폴더에서 필요한 라이브러리를 설치합니다.

```bash
pip install -r requirements.txt
conda install -c conda-forge pyrosm fiona
```

> 최초 1회만 설정하면 됩니다.

## 실행 방법

`ai-server` 폴더에서 가상환경을 활성화합니다.

```bash
conda activate meongjaguk
```

FastAPI 서버를 실행합니다.

```bash
python -m uvicorn app.main:app --reload
```

정상 실행 주소:

```text
http://127.0.0.1:8000
```

Swagger API 문서:

```text
http://127.0.0.1:8000/docs
```

추천 API:

```text
POST /api/routes/recommend
```

요청 예시:

```json
{
  "latitude": 위도 값,
  "longitude": 경도 값
}
```

정상 응답 시 현재 위치를 기준으로 추천 산책로와 경로 좌표를 반환합니다.
