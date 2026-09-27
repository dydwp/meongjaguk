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
