from pathlib import Path

import osmnx as ox
from pyrosm import OSM


BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = BASE_DIR / "data"

PBF_PATH = DATA_DIR / "south-korea-latest.osm.pbf"
GRAPH_PATH = DATA_DIR / "seoul_walk.graphml"


# 서울을 포함하는 대략적인 범위
SEOUL_BBOX = [
    126.76,  # west
    37.41,   # south
    127.19,  # east
    37.72,   # north
]


def create_seoul_walk_graph():
    print("서울 보행 도로망 추출 시작...")

    osm = OSM(
        str(PBF_PATH),
        bounding_box=SEOUL_BBOX
    )

    nodes, edges = osm.get_network(
        network_type="walking",
        nodes=True
    )

    print("nodes:", len(nodes))
    print("edges:", len(edges))

    G = osm.to_graph(
        nodes,
        edges,
        graph_type="networkx",
        network_type="walking",
        simplify=True
    )

    ox.save_graphml(
        G,
        filepath=GRAPH_PATH
    )

    print("저장 완료:")
    print(GRAPH_PATH)



def create_seoul_green_data():
    print("서울 녹지 데이터 추출 시작...")

    osm = OSM(
        str(PBF_PATH),
        bounding_box=SEOUL_BBOX
    )

    custom_filter = {
        "leisure": ["park", "garden"],
        "landuse": [
            "grass",
            "forest",
            "recreation_ground"
        ],
        "natural": ["wood"]
    }

    green_areas = osm.get_data_by_custom_criteria(
        custom_filter=custom_filter,
        filter_type="keep"
    )

    if green_areas is None or green_areas.empty:
        print("녹지 데이터를 찾지 못했습니다.")
        return

    green_path = DATA_DIR / "seoul_green.gpkg"

    green_areas.to_file(
      green_path,
      driver="GPKG",
      engine="fiona"
    )

    print("녹지 개수:", len(green_areas))
    print("저장 완료:")
    print(green_path)


if __name__ == "__main__":
    create_seoul_green_data()
    