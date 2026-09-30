from unittest.mock import MagicMock

import pytest

from app.route_description import RoutePlaceLookup, build_route_description

### 전달한 장소명이 제목과 설명에 모두 포함되는지 테스트
def test_build_route_description_uses_place_name():
    result = build_route_description({"green_ratio": 1}, 2649, 40, "서울숲")
    assert "서울숲" in result["title"]
    assert "서울숲" in result["description"]


### 장소명이 없을때, 경로 특성의 비율에 따라 기본 제목이 달라지는지 테스트
@pytest.mark.parametrize("feature,keyword", [("green_ratio", "녹지"),
                                             ("residential_ratio", "조용한 동네"),
                                             ("walkway_ratio", "걷기 좋은")])
@pytest.mark.parametrize("value,active", [(0.39, False), (0.4, True)])
def test_fallback_title_feature_thresholds(feature, keyword, value, active):
    title = build_route_description({feature: value}, 2649, 40)["title"]
    assert (keyword in title) is active
    if not active:
        assert "가볍게" in title


### 큰 도로 비율에 따라 설명에 차량 주의 문구가 포함되는지 테스트
@pytest.mark.parametrize("value,warning", [(0.19, False), (0.2, True)])
def test_major_road_threshold_controls_warning(value, warning):
    result = build_route_description({"major_road_ratio": value}, 2649, 40)
    assert ("차량" in result["description"]) is warning


### 주거지역 경로라도 큰 도로 비율에 따라 제목이 달라지는지 테스트
@pytest.mark.parametrize("value,keyword", [(0.19, "조용한 동네"), (0.2, "동네를 둘러보는")])
def test_major_road_threshold_controls_residential_title(value, keyword):
    result = build_route_description({"residential_ratio": 0.4, "major_road_ratio": value}, 2649, 40)
    assert keyword in result["title"]


### 장소명이 없고 여러 경로 특성이 동시에 해당할 떄 정해진 우선 순위로 제목을 선택하는지 테스트
@pytest.mark.parametrize("features,keyword", [
    ({"green_ratio": 0.4, "residential_ratio": 0.4, "walkway_ratio": 0.4}, "녹지"),
    ({"residential_ratio": 0.4, "walkway_ratio": 0.4}, "조용한 동네"),
    ({"residential_ratio": 0.4, "walkway_ratio": 0.4, "major_road_ratio": 0.2}, "걷기 좋은")])
def test_fallback_title_respects_feature_priority(features, keyword):
    assert keyword in build_route_description(features, 2649, 40)["title"]


### 설명에 거리와 예상 시간이 포함되는지 테스트
@pytest.mark.parametrize("distance,duration,text", [(2649, 40, "2.6km"), (999, 15, "999m"),
                                                    (1000, 15, "1.0km")])
def test_description_contains_distance_and_duration(distance, duration, text):
    description = build_route_description({}, distance, duration)["description"]
    assert text in description
    assert f"{duration}분" in description


### 장소명이 None, 빈문자열, 공백, 숫자여도 기복 제목과 거리, 시간을 포함한 설명을 만드는지 테스트
@pytest.mark.parametrize("name", [None, "", "  ", 123])
def test_description_has_fallback_without_place_name(name):
    result = build_route_description({}, 2649, 40, name)
    assert "가볍게" in result["title"]
    assert "2.6km" in result["description"]
    assert "40분" in result["description"]


### 장소명이 불필요한 공백과 줄바꿈을 정리하는지 테스트
def test_place_name_whitespace_is_normalized():
    result = build_route_description({}, 2649, 40, "  서울숲 \n 공원  ")
    assert "서울숲 공원" in result["title"]


### 녹지 비율이 None, 잘못된 문자열, NaN, 무한대, 음수일때 기본 제목을 사용하는지 테스트
@pytest.mark.parametrize("value", [None, "invalid", float("nan"), float("inf"), -1])
def test_invalid_ratio_uses_default_title(value):
    assert "가볍게" in build_route_description({"green_ratio": value}, 2649, 40)["title"]


### 빈 녹지 데이터 떄문에 장소 조회 결과가 None이어도 예상 시간을 포함한 설명을 정상 생성하는지 테스트
def test_missing_place_lookup_still_produces_description(graph):
    areas = MagicMock()
    areas.empty = True
    name = RoutePlaceLookup(areas).find(graph, [1, 2, 1])
    assert name is None
    assert "40분" in build_route_description({}, 2649, 40, name)["description"]
