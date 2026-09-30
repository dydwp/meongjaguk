# AI 산책로 단위 테스트

ai-server 디렉터리에서 실행합니다.

```powershell
python -m pip install -r requirements-dev.txt
python -m pytest -v
```

별도 가상환경을 사용하는 경우:

```powershell
python -m venv .venv
.venv/Scripts/python.exe -m pip install -r requirements-dev.txt
.venv/Scripts/python.exe -m pytest -v
```

- `test_route_service.py`: 추천 개수, 점수 순위와 제한, 모델 입력 컬럼과 값,
  응답 필드, 반올림, 예상 시간, 장소명 연결, 좌표 순서, 거리 기반 도로 비율,
  역방향 중복 거리, 순환 경로 및 빈 후보를 검증합니다.
- `test_route_description.py`: 장소명, fallback 우선순위, 실제 임계값
  (녹지/주거/보행로 0.4, 큰 도로 0.2), 거리/시간 표기, 잘못된 비율,
  장소명 없는 경우를 검증합니다.
- `conftest.py`: 모듈 실행 **이전** GraphML/GPKG 로더와 XGBoost 생성자를
  Mock 처리합니다. 추천 테스트의 후보 데이터와 예측 점수, 장소 조회도 Mock입니다.
  매 테스트마다 독립된 서비스 모듈을 생성하므로 Mock 전역 상태가 남지 않습니다.
  네트워크 연결과 실제 파일/모델 로더는 기본적으로 호출 시 테스트를 실패시킵니다.

NetworkX의 작은 메모리 그래프와 pandas DataFrame은 실제 자료구조를 사용합니다.
전체 서울 지도 없이도 입력부터 결과까지의 Python 계산과 분기를 검증할 수 있습니다.
공간 데이터의 정확도, 실제 모델 성능, 외부 서비스 연결은 이 단위 테스트의 검증 범위가 아닙니다.
장소 조회 실패는 현재 구현의 정상적인 `None` 반환을 검증하며, 운영 코드에 없는
임의의 예외 복구 동작은 가정하지 않습니다.
