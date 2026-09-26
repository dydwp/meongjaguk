from pathlib import Path
from collections import defaultdict
import math

import numpy as np
import pandas as pd
import networkx as nx
import osmnx as ox
import geopandas as gpd

from xgboost import XGBRegressor
from shapely.geometry import Point


# =========================================================
# 설정
# =========================================================

BASE_DIR = Path(__file__).resolve().parent.parent

MODEL_PATH = BASE_DIR / "model" / "walk_route_model.json"
GRAPH_PATH = BASE_DIR / "data" / "seoul_walk.graphml"
GREEN_PATH = BASE_DIR / "data" / "seoul_green.gpkg"


G = ox.load_graphml(
    GRAPH_PATH,
    edge_dtypes={
        "oneway": str,
        "reversed": str,
    }
)

GREEN_AREAS = gpd.read_file(
    GREEN_PATH,
    engine="fiona"
)

MODEL_PATH = (
    BASE_DIR
    / "model"
    / "walk_route_model.json"
)

FEATURE_COLUMNS = [
    "distance_m",
    "overlap_ratio",
    "walkway_ratio",
    "residential_ratio",
    "major_road_ratio",
    "green_ratio",
]

ox.settings.use_cache = True
ox.settings.requests_timeout = 120
ox.settings.overpass_url = "https://overpass.private.coffee/api"


# =========================================================
# XGBoost 모델 로드
# =========================================================

def load_walk_model():
    model = XGBRegressor()
    model.load_model(MODEL_PATH)

    return model


walk_model = load_walk_model()


# =========================================================
# 방향 계산
# =========================================================

def calculate_bearing(
    lat1,
    lon1,
    lat2,
    lon2
):
    lat1 = math.radians(lat1)
    lat2 = math.radians(lat2)

    diff_lon = math.radians(
        lon2 - lon1
    )

    x = (
        math.sin(diff_lon)
        * math.cos(lat2)
    )

    y = (
        math.cos(lat1)
        * math.sin(lat2)
        - math.sin(lat1)
        * math.cos(lat2)
        * math.cos(diff_lon)
    )

    bearing = math.degrees(
        math.atan2(x, y)
    )

    return (bearing + 360) % 360


# =========================================================
# 현재 위치 주변 보행 그래프 생성
# =========================================================

def create_walk_graph(latitude, longitude, dist=1000):
    start_node = ox.distance.nearest_nodes(
        G,
        X=longitude,
        Y=latitude
    )

    local_G = nx.ego_graph(
        G,
        start_node,
        radius=dist,
        distance="length"
    )

    return local_G, start_node


# =========================================================
# 후보 노드 생성
# =========================================================

def get_candidate_nodes(
    G,
    latitude,
    longitude,
    min_radius,
    max_radius
):
    candidate_nodes = []

    for node, data in G.nodes(
        data=True
    ):
        distance = (
            ox.distance.great_circle(
                latitude,
                longitude,
                data["y"],
                data["x"]
            )
        )

        if (
            min_radius
            <= distance
            <= max_radius
        ):
            candidate_nodes.append(node)

    return candidate_nodes


# =========================================================
# 방향별 경유지 선택
# =========================================================

def select_waypoints(
    G,
    candidate_nodes,
    target_bearings,
    latitude,
    longitude
):
    waypoints = []

    for target_bearing in target_bearings:

        best_node = None
        smallest_diff = float("inf")

        for node in candidate_nodes:

            node_data = G.nodes[node]

            bearing = calculate_bearing(
                latitude,
                longitude,
                node_data["y"],
                node_data["x"]
            )

            diff = abs(
                (
                    bearing
                    - target_bearing
                    + 180
                )
                % 360
                - 180
            )

            if diff < smallest_diff:
                smallest_diff = diff
                best_node = node

        if best_node is not None:
            waypoints.append(
                best_node
            )

    return waypoints


# =========================================================
# 순환 경로 생성
# =========================================================

def generate_loop_route(
    G,
    start_node,
    waypoints
):
    route_nodes = (
        [start_node]
        + waypoints
        + [start_node]
    )

    loop_route = []

    for i in range(
        len(route_nodes) - 1
    ):
        section = nx.shortest_path(
            G,
            source=route_nodes[i],
            target=route_nodes[i + 1],
            weight="length"
        )

        if i == 0:
            loop_route.extend(
                section
            )
        else:
            loop_route.extend(
                section[1:]
            )

    return loop_route


# =========================================================
# 거리 / 중복률 분석
# =========================================================

def analyze_route(
    G,
    route
):
    total_distance = 0

    edge_distances = (
        defaultdict(list)
    )

    for u, v in zip(
        route[:-1],
        route[1:]
    ):
        edge_data = (
            G.get_edge_data(u, v)
        )

        if not edge_data:
            continue

        min_length = min(
            data["length"]
            for data
            in edge_data.values()
        )

        total_distance += min_length

        edge_key = tuple(
            sorted((u, v))
        )

        edge_distances[
            edge_key
        ].append(min_length)

    duplicate_distance = 0

    for distances in (
        edge_distances.values()
    ):
        if len(distances) > 1:
            duplicate_distance += sum(
                distances[1:]
            )

    overlap_ratio = (
        duplicate_distance
        / total_distance
        * 100
        if total_distance > 0
        else 0
    )

    return {
        "distance_m":
            total_distance,

        "duplicate_distance_m":
            duplicate_distance,

        "overlap_ratio":
            overlap_ratio
    }


# =========================================================
# 도로 유형 비율
# =========================================================

def calculate_highway_ratios(
    G,
    route
):
    highway_distances = {}

    total_distance = 0

    for u, v in zip(
        route[:-1],
        route[1:]
    ):
        edge_data = (
            G.get_edge_data(u, v)
        )

        if not edge_data:
            continue

        edge = min(
            edge_data.values(),
            key=lambda data:
                data["length"]
        )

        highway = edge.get(
            "highway",
            "unknown"
        )

        length = edge["length"]

        if isinstance(
            highway,
            list
        ):
            highway = highway[0]

        highway_distances[
            highway
        ] = (
            highway_distances.get(
                highway,
                0
            )
            + length
        )

        total_distance += length

    if total_distance == 0:
        return {}

    return {
        highway:
            distance
            / total_distance

        for highway, distance
        in highway_distances.items()
    }


# =========================================================
# 녹지 데이터 조회
# =========================================================

def get_green_areas(latitude, longitude, dist=1000):
    point = gpd.GeoSeries(
        [Point(longitude, latitude)],
        crs="EPSG:4326"
    ).to_crs(epsg=5179).iloc[0]

    green_areas_5179 = GREEN_AREAS.to_crs(epsg=5179)

    nearby_green = green_areas_5179[
        green_areas_5179.geometry.intersects(
            point.buffer(dist)
        )
    ].copy()

    return nearby_green.to_crs(epsg=4326)


# =========================================================
# 녹지 비율 계산
# =========================================================

def calculate_green_ratio(
    G,
    route,
    green_areas,
    buffer_m=50
):
    if green_areas.empty:
        return 0.0

    route_nodes = (
        G.subgraph(route)
    )

    route_gdf = (
        ox.graph_to_gdfs(
            route_nodes,
            nodes=True,
            edges=False
        )
    )

    if route_gdf.empty:
        return 0.0

    route_gdf = (
        route_gdf.to_crs(
            epsg=5179
        )
    )

    green_gdf = (
        green_areas.to_crs(
            epsg=5179
        )
    )

    green_buffer = (
        green_gdf
        .geometry
        .buffer(buffer_m)
        .union_all()
    )

    near_green = (
        route_gdf
        .geometry
        .intersects(
            green_buffer
        )
    )

    return float(
        near_green.sum()
        / len(route_gdf)
    )


# =========================================================
# Feature 추출
# =========================================================

def extract_route_features(
    G,
    route,
    green_areas
):
    analysis = analyze_route(
        G,
        route
    )

    ratios = (
        calculate_highway_ratios(
            G,
            route
        )
    )

    walkway_ratio = (
        ratios.get(
            "footway",
            0
        )
        + ratios.get(
            "path",
            0
        )
        + ratios.get(
            "pedestrian",
            0
        )
        + ratios.get(
            "living_street",
            0
        )
    )

    residential_ratio = (
        ratios.get(
            "residential",
            0
        )
    )

    major_road_ratio = (
        ratios.get(
            "primary",
            0
        )
        + ratios.get(
            "primary_link",
            0
        )
        + ratios.get(
            "secondary",
            0
        )
        + ratios.get(
            "secondary_link",
            0
        )
    )

    green_ratio = (
        calculate_green_ratio(
            G,
            route,
            green_areas
        )
    )

    return {
        "distance_m":
            float(
                analysis[
                    "distance_m"
                ]
            ),

        "overlap_ratio":
            float(
                analysis[
                    "overlap_ratio"
                ]
            ),

        "walkway_ratio":
            float(
                walkway_ratio
            ),

        "residential_ratio":
            float(
                residential_ratio
            ),

        "major_road_ratio":
            float(
                major_road_ratio
            ),

        "green_ratio":
            float(
                green_ratio
            )
    }


# =========================================================
# 후보 경로 생성
# =========================================================

def generate_route_candidates(
    latitude,
    longitude
):
    G, start_node = (
        create_walk_graph(
            latitude,
            longitude
        )
    )

    bearing_sets = []

    for start_angle in range(
        0,
        120,
        10
    ):
        bearing_sets.append([
            start_angle,
            (
                start_angle
                + 120
            )
            % 360,
            (
                start_angle
                + 240
            )
            % 360
        ])

    radius_ranges = [
        (250, 400),
        (300, 450),
        (300, 500),
        (350, 500),
        (350, 550)
    ]

    candidates = []

    for (
        min_radius,
        max_radius
    ) in radius_ranges:

        candidate_nodes = (
            get_candidate_nodes(
                G,
                latitude,
                longitude,
                min_radius,
                max_radius
            )
        )

        if not candidate_nodes:
            continue

        for bearings in (
            bearing_sets
        ):
            try:
                waypoints = (
                    select_waypoints(
                        G,
                        candidate_nodes,
                        bearings,
                        latitude,
                        longitude
                    )
                )

                if (
                    len(waypoints)
                    != len(bearings)
                ):
                    continue

                route = (
                    generate_loop_route(
                        G,
                        start_node,
                        waypoints
                    )
                )

                analysis = (
                    analyze_route(
                        G,
                        route
                    )
                )

                if (
                    2000
                    <= analysis[
                        "distance_m"
                    ]
                    <= 3000
                ):
                    candidates.append({
                        "radius_range": (
                            min_radius,
                            max_radius
                        ),

                        "bearings":
                            bearings,

                        "route":
                            route,

                        "distance_m":
                            analysis[
                                "distance_m"
                            ],

                        "duplicate_distance_m":
                            analysis[
                                "duplicate_distance_m"
                            ],

                        "overlap_ratio":
                            analysis[
                                "overlap_ratio"
                            ]
                    })

            except (
                nx.NetworkXNoPath,
                nx.NodeNotFound
            ):
                continue

    return G, candidates


# =========================================================
# 중복 경로 제거
# =========================================================

def remove_exact_duplicates(
    candidates
):
    unique_candidates = []
    seen_routes = set()

    for candidate in candidates:

        route_key = tuple(
            candidate["route"]
        )

        if (
            route_key
            not in seen_routes
        ):
            seen_routes.add(
                route_key
            )

            unique_candidates.append(
                candidate
            )

    return unique_candidates


def route_similarity(
    route_a,
    route_b
):
    edges_a = {
        tuple(
            sorted((u, v))
        )

        for u, v in zip(
            route_a[:-1],
            route_a[1:]
        )
    }

    edges_b = {
        tuple(
            sorted((u, v))
        )

        for u, v in zip(
            route_b[:-1],
            route_b[1:]
        )
    }

    intersection = (
        edges_a & edges_b
    )

    union = (
        edges_a | edges_b
    )

    if not union:
        return 0.0

    return (
        len(intersection)
        / len(union)
    )


def remove_similar_routes(
    candidates,
    similarity_threshold=0.8
):
    result = []

    for candidate in candidates:

        is_similar = False

        for saved in result:

            similarity = (
                route_similarity(
                    candidate[
                        "route"
                    ],
                    saved[
                        "route"
                    ]
                )
            )

            if (
                similarity
                >= similarity_threshold
            ):
                is_similar = True
                break

        if not is_similar:
            result.append(
                candidate
            )

    return result


# =========================================================
# 후보 DataFrame 생성
# =========================================================

def create_route_dataframe(
    G,
    candidates,
    green_areas
):
    columns = [
        "candidate_id",
        "distance_m",
        "overlap_ratio",
        "walkway_ratio",
        "residential_ratio",
        "major_road_ratio",
        "green_ratio",
        "radius_min",
        "radius_max"
    ]

    if not candidates:
        return pd.DataFrame(
            columns=columns
        )

    dataset = []

    for i, candidate in enumerate(
        candidates,
        start=1
    ):
        features = (
            extract_route_features(
                G,
                candidate["route"],
                green_areas
            )
        )

        features[
            "candidate_id"
        ] = i

        features[
            "radius_min"
        ] = (
            candidate[
                "radius_range"
            ][0]
        )

        features[
            "radius_max"
        ] = (
            candidate[
                "radius_range"
            ][1]
        )

        dataset.append(
            features
        )

    return pd.DataFrame(
        dataset
    )[columns]


# =========================================================
# 현재 위치 기준 후보 데이터 생성
# =========================================================

def generate_route_dataset(
    latitude,
    longitude
):
    G, candidates = (
        generate_route_candidates(
            latitude,
            longitude
        )
    )

    candidates = (
        remove_exact_duplicates(
            candidates
        )
    )

    candidates = (
        remove_similar_routes(
            candidates,
            similarity_threshold=0.8
        )
    )

    green_areas = (
        get_green_areas(
            latitude,
            longitude
        )
    )

    route_df = (
        create_route_dataframe(
            G,
            candidates,
            green_areas
        )
    )

    return (
        G,
        candidates,
        route_df
    )


# =========================================================
# OSM 노드 → 위도/경도 변환
# =========================================================

def route_to_coordinates(
    G,
    route
):
    coordinates = []

    for sequence, node in enumerate(
        route,
        start=1
    ):
        node_data = G.nodes[node]

        coordinates.append({
            "sequence":
                sequence,

            "latitude":
                float(
                    node_data["y"]
                ),

            "longitude":
                float(
                    node_data["x"]
                )
        })

    return coordinates


# =========================================================
# AI 산책로 추천
# =========================================================

def recommend_routes(
    latitude,
    longitude,
    top_k=4
):
    G, candidates, route_df = (
        generate_route_dataset(
            latitude,
            longitude
        )
    )

    if route_df.empty:
        return []

    X_input = (
        route_df[
            FEATURE_COLUMNS
        ]
    )

    predictions = (
        walk_model.predict(
            X_input
        )
    )

    route_df = route_df.copy()

    route_df[
        "predicted_score"
    ] = np.clip(
        predictions,
        1.0,
        5.0
    )

    top_routes = (
        route_df
        .sort_values(
            "predicted_score",
            ascending=False
        )
        .head(top_k)
    )

    results = []

    for rank, (_, row) in enumerate(
        top_routes.iterrows(),
        start=1
    ):
        candidate_index = (
            int(
                row["candidate_id"]
            )
            - 1
        )

        candidate = (
            candidates[
                candidate_index
            ]
        )

        points = (
            route_to_coordinates(
                G,
                candidate["route"]
            )
        )

        distance_m = int(
            round(
                float(
                    row["distance_m"]
                )
            )
        )

        estimated_minutes = int(
            round(
                distance_m / 66.7
            )
        )

        results.append({
            "rank":
                rank,

            "candidate_id":
                int(
                    row["candidate_id"]
                ),

            "distance_m":
                distance_m,

            "estimated_minutes":
                estimated_minutes,

            "score":
                round(
                    float(
                        row[
                            "predicted_score"
                        ]
                    ),
                    2
                ),

            "overlap_ratio":
                round(
                    float(
                        row[
                            "overlap_ratio"
                        ]
                    ),
                    2
                ),

            "walkway_ratio":
                round(
                    float(
                        row[
                            "walkway_ratio"
                        ]
                    ),
                    3
                ),

            "residential_ratio":
                round(
                    float(
                        row[
                            "residential_ratio"
                        ]
                    ),
                    3
                ),

            "major_road_ratio":
                round(
                    float(
                        row[
                            "major_road_ratio"
                        ]
                    ),
                    3
                ),

            "green_ratio":
                round(
                    float(
                        row[
                            "green_ratio"
                        ]
                    ),
                    3
                ),

            "points":
                points
        })

    return results