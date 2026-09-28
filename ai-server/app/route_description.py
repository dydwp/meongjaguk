"""OSM 장소명과 추천 feature를 사용하는 결정적 산책로 설명 생성."""

import math

import geopandas as gpd
from shapely.geometry import LineString
from shapely.ops import unary_union


class RoutePlaceLookup:
    """녹지는 한 번만 미터 좌표계로 변환하고 공간 인덱스로 조회한다."""

    def __init__(self, green_areas, buffer_m=100):
        self.buffer_m = buffer_m
        self.areas = None
        if green_areas.empty or "name" not in green_areas or green_areas.crs is None:
            return
        areas = green_areas.copy()
        areas["name"] = areas["name"].map(
            lambda value: " ".join(value.split()) if isinstance(value, str) else ""
        )
        areas = areas[
            areas["name"].ne("") & areas.geometry.notna()
            & ~areas.geometry.is_empty & areas.geometry.is_valid
        ]
        if not areas.empty:
            self.areas = areas.to_crs(epsg=5179).reset_index(drop=True)
            # 초기화 시 인덱스를 생성해 요청마다 전체 녹지를 순회하지 않는다.
            self.areas.sindex

    def find(self, graph, route):
        if self.areas is None or len(route) < 2:
            return None
        segments = []
        for start, end in zip(route, route[1:]):
            edges = graph.get_edge_data(start, end)
            if not edges:
                continue
            edge = min(edges.values(), key=lambda value: value.get("length", math.inf))
            geometry = edge.get("geometry")
            if geometry is None:
                geometry = LineString([
                    (graph.nodes[start]["x"], graph.nodes[start]["y"]),
                    (graph.nodes[end]["x"], graph.nodes[end]["y"]),
                ])
            segments.append(geometry)
        if not segments:
            return None
        route_geometry = gpd.GeoSeries(
            [unary_union(segments)], crs=graph.graph.get("crs", "EPSG:4326")
        ).to_crs(epsg=5179).iloc[0]
        if route_geometry.is_empty or route_geometry.length == 0:
            return None
        indexes = self.areas.sindex.query(
            route_geometry.buffer(self.buffer_m), predicate="intersects"
        )
        nearby = self.areas.iloc[indexes]
        candidates = []
        for name, group in nearby.groupby("name", sort=True):
            place = unary_union(group.geometry.tolist())
            adjacent_length = route_geometry.intersection(place.buffer(self.buffer_m)).length
            if adjacent_length > 0:
                candidates.append((-adjacent_length, route_geometry.distance(place), name))
        return min(candidates)[2] if candidates else None


def _ratio(features, key):
    try:
        value = float(features.get(key, 0))
    except (TypeError, ValueError):
        return 0.0
    return min(1.0, max(0.0, value)) if math.isfinite(value) else 0.0


def build_route_description(features, distance_m, estimated_minutes, place_name=None):
    """0.4 이상인 특징을 강조한다. 이름 없는 경우 녹지→주거→보행로 순."""
    green = _ratio(features, "green_ratio") >= 0.4
    residential = _ratio(features, "residential_ratio") >= 0.4
    walkway = _ratio(features, "walkway_ratio") >= 0.4
    major_road = _ratio(features, "major_road_ratio") >= 0.2
    name = " ".join(place_name.split()) if isinstance(place_name, str) else ""

    if name:
        title = f"{name} 반려견 산책 코스"
    elif green:
        title = "녹지와 함께하는 반려견 산책 코스"
    elif residential and not major_road:
        title = "조용한 동네 반려견 산책 코스"
    elif walkway:
        title = "걷기 좋은 반려견 산책 코스"
    elif residential:
        title = "동네를 둘러보는 반려견 산책 코스"
    else:
        title = "가볍게 둘러보는 반려견 산책 코스"

    if name:
        setting = f"{name} 주변을 둘러보며"
    elif green and residential:
        setting = "녹지와 주거지역 주변을 따라"
    elif green:
        setting = "녹지 주변을 따라"
    elif residential:
        setting = "동네 주거지역을 둘러보며"
    elif walkway:
        setting = "보행로를 따라"
    else:
        setting = "주변 길을 따라"

    distance = f"{distance_m / 1000:.1f}km" if distance_m >= 1000 else f"{int(distance_m)}m"
    sentences = [
        f"{setting} 약 {distance}를 걷는 코스로, 예상 소요 시간은 약 {estimated_minutes}분입니다."
    ]
    if green and walkway:
        sentences.append("녹지 가까이 이어지는 구간과 보행로가 포함되어, 주변 풍경을 살피며 걷기 좋은 경로입니다.")
    elif green:
        sentences.append("녹지 가까이를 지나는 구간이 있어 반려견과 주변 풍경을 즐기며 산책하기 좋습니다.")
    elif walkway:
        sentences.append("보행로 구간을 중심으로 반려견의 걸음에 맞춰 산책하기 좋은 경로입니다.")
    elif residential:
        sentences.append("주거지역 길을 따라 반려견과 동네를 천천히 둘러볼 수 있습니다.")
    else:
        sentences.append("반려견의 걸음에 맞춰 주변을 천천히 둘러보세요.")
    if major_road:
        sentences.append("큰 도로를 지나는 구간에서는 차량과 주변 통행에 유의해주세요.")
    return {"title": title, "description": " ".join(sentences)}
