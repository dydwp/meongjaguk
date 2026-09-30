from unittest.mock import MagicMock

import pandas as pd
import pytest


## 추천 개수·순위, 빈 후보, Feature 입력, 응답 구조, 점수 제한, 예상 시간, 좌표 변환, 거리·도로 비율 검증


### 추천 산책로의 요청한 개수만큼 추천 결과가 나오는지 테스트
@pytest.mark.parametrize("top_k,count,expected", [(3, 6, 3), (5, 6, 5), (5, 2, 2)])
def test_recommend_routes_respects_top_k(service, recommendation_data, top_k, count, expected):
    recommendation_data([3.0] * count)
    assert len(service.recommend_routes(37.5, 127.0, top_k=top_k)) == expected


### 추천 결과가 모델 점수 높은 순서대로 정렬되는지 체크하는 테스트
def test_recommend_routes_orders_by_score(service, recommendation_data):
    recommendation_data([3.2, 4.8, 4.1])
    results = service.recommend_routes(37.5, 127.0)
    assert [(r["rank"], r["candidate_id"], r["score"]) for r in results] == [
        (1, 2, 4.8), (2, 3, 4.1), (3, 1, 3.2)]


### 추천할 후보가 없을때 빈 목록을 반환하는지 테스트
def test_recommend_routes_returns_empty_when_no_candidates(service, recommendation_data):
    recommendation_data([])
    assert service.recommend_routes(37.5, 127.0) == []
    service.walk_model.predict.assert_not_called()


### AI모델에 필요한 6개 feature만 올바르게 전달하는지 테스트
def test_recommend_routes_passes_only_model_features(service, recommendation_data):
    data = recommendation_data([3.2])
    service.recommend_routes(37.5, 127.0)
    assert service.FEATURE_COLUMNS == ["distance_m", "overlap_ratio", "walkway_ratio",
                                       "residential_ratio", "major_road_ratio", "green_ratio"]
    actual = service.walk_model.predict.call_args.args[0]
    pd.testing.assert_frame_equal(actual, data.frame[service.FEATURE_COLUMNS])


### 추천 결과에 필요한 상세 정보가 빠짐없이 포함되는지 테스트
def test_recommend_routes_returns_response_fields(service, recommendation_data):
    recommendation_data([4.1234])
    result = service.recommend_routes(37.5, 127.0)[0]
    assert set(result) == {"rank", "candidate_id", "distance_m", "estimated_minutes",
                           "score", "overlap_ratio", "walkway_ratio", "residential_ratio",
                           "major_road_ratio", "green_ratio", "points", "title", "description"}
    assert result["score"] == 4.12
    assert result["overlap_ratio"] == 12.35
    assert result["walkway_ratio"] == 0.457
    assert result["residential_ratio"] == 0.321
    assert result["major_road_ratio"] == 0.123
    assert result["green_ratio"] == 0.568
    assert result["title"] and result["description"]
    assert result["points"][0] == {"sequence": 1, "latitude": 37.5, "longitude": 127.0}


### AI 예측점수가 1~5점 범위로 제한되는지 테스트
def test_recommend_routes_clips_prediction_scores(service, recommendation_data):
    recommendation_data([0.2, 6.5])
    assert [r["score"] for r in service.recommend_routes(37.5, 127.0)] == [5.0, 1.0]


### 추천 결과의 거리 반올림과 예상 요소 시간 계산이 올바른지 테스트
@pytest.mark.parametrize("distance,rounded,minutes", [(2649, 2649, 40), (2000, 2000, 30),
                                                       (3000, 3000, 45), (2034.6, 2035, 31)])
def test_recommend_routes_calculates_duration(service, recommendation_data, distance, rounded, minutes):
    recommendation_data([4.0], [distance])
    result = service.recommend_routes(37.5, 127.0)[0]
    assert result["distance_m"] == rounded
    assert result["estimated_minutes"] == minutes


### 조회된 장소명이 추천 결과의 제목에 반영되는지 테스트
def test_recommend_routes_uses_mocked_place_name(service, recommendation_data, graph):
    recommendation_data([4.0])
    service.ROUTE_PLACE_LOOKUP.find.return_value = "서울숲"
    result = service.recommend_routes(37.5, 127.0)[0]
    assert "서울숲" in result["title"]
    service.ROUTE_PLACE_LOOKUP.find.assert_called_once_with(graph, [1, 2, 3, 1])


### 추천 경로의 노드를 위도·경도 좌표로 올바르게 변환하는지 테스트
def test_route_to_coordinates_preserves_order_and_closed_loop(service, graph):
    assert service.route_to_coordinates(graph, [2, 1, 2]) == [
        {"sequence": 1, "latitude": 37.501, "longitude": 127.001},
        {"sequence": 2, "latitude": 37.5, "longitude": 127.0},
        {"sequence": 3, "latitude": 37.501, "longitude": 127.001}]

### 경로의 도로 유형별 비율을 거리 기준으로 계산하는지 테스트
def test_extract_route_features_uses_distance_weighted_highway_ratios(service, graph, monkeypatch):
    monkeypatch.setattr(service, "calculate_green_ratio", MagicMock(return_value=0.75))
    features = service.extract_route_features(graph, [1, 2, 3, 1], MagicMock())
    assert features == pytest.approx(dict(distance_m=400, overlap_ratio=0,
                                         walkway_ratio=0.25, residential_ratio=0.5,
                                         major_road_ratio=0.25, green_ratio=0.75))


### 같은 길을 반대 방향으로 다시 걸었을때 중복 거리로 계산하는지 테스트
def test_analyze_route_counts_reverse_edge_as_overlap(service, graph):
    assert service.analyze_route(graph, [1, 2, 1]) == pytest.approx(
        dict(distance_m=200, duplicate_distance_m=100, overlap_ratio=50))


### 출발점과 경유지를 순서대로 연결해 출발점으로 돌아오는 경로를 만드는지 테스트
def test_generate_loop_route_connects_waypoints(service, graph):
    assert service.generate_loop_route(graph, 1, [2, 3]) == [1, 2, 3, 1]


### 주변 경유지로 사용할 후보 노드가 없을때, 후보 경로를 생성하지 않고 빈 목록[]을 반환하는지 테스트
def test_generate_route_candidates_returns_empty_without_nearby_nodes(service, graph, monkeypatch):
    monkeypatch.setattr(service, "create_walk_graph", MagicMock(return_value=(graph, 1)))
    monkeypatch.setattr(service, "get_candidate_nodes", MagicMock(return_value=[]))
    assert service.generate_route_candidates(37.5, 127.0) == (graph, [])


### 후보 경로가 없을떄도 모델이 입력에 필요한 컬럼 구조를 유지하는지 테스트
def test_create_route_dataframe_returns_empty_feature_columns(service, graph):
    frame = service.create_route_dataframe(graph, [], MagicMock())
    assert frame.empty
    assert list(frame[service.FEATURE_COLUMNS].columns) == service.FEATURE_COLUMNS
