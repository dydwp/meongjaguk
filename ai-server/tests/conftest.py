"""Patch resource loaders before executing route_service's module body."""

import importlib.util
from pathlib import Path
import socket
from types import SimpleNamespace
from unittest.mock import MagicMock

import geopandas as gpd
import networkx as nx
import numpy as np
import osmnx as ox
import pandas as pd
import pytest
import xgboost

## 지도 로더·모델·장소 조회 Mock 및 네트워크 차단

@pytest.fixture(autouse=True)
def block_external_resources(monkeypatch):
    def forbidden(*args, **kwargs):
        raise AssertionError("Unit tests must not access network, map files or models")

    monkeypatch.setattr(socket.socket, "connect", forbidden)
    monkeypatch.setattr(socket, "create_connection", forbidden)
    monkeypatch.setattr(ox, "load_graphml", forbidden)
    monkeypatch.setattr(gpd, "read_file", forbidden)
    monkeypatch.setattr(xgboost.XGBRegressor, "load_model", forbidden)
    monkeypatch.setattr(xgboost.XGBRegressor, "predict", forbidden)


@pytest.fixture
def graph():
    graph = nx.MultiDiGraph(crs="EPSG:4326")
    graph.add_node(1, x=127.0, y=37.5)
    graph.add_node(2, x=127.001, y=37.501)
    graph.add_node(3, x=127.002, y=37.502)
    graph.add_edge(1, 2, length=100, highway="footway")
    graph.add_edge(1, 2, length=300, highway="primary")
    graph.add_edge(2, 3, length=200, highway=["residential"])
    graph.add_edge(3, 1, length=100, highway="primary")
    graph.add_edge(2, 1, length=100, highway="footway")
    return graph


@pytest.fixture
def service(monkeypatch, block_external_resources, graph):
    # A fresh, private module avoids leaving mocked globals in app.route_service.
    green = MagicMock(name="green_areas")
    green.empty = True
    monkeypatch.setattr(ox, "load_graphml", MagicMock(return_value=graph))
    monkeypatch.setattr(gpd, "read_file", MagicMock(return_value=green))
    monkeypatch.setattr(xgboost, "XGBRegressor", MagicMock())
    monkeypatch.setattr(ox.settings, "use_cache", ox.settings.use_cache)
    path = Path(__file__).resolve().parents[1] / "app" / "route_service.py"
    spec = importlib.util.spec_from_file_location("_route_service_under_test", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.ROUTE_PLACE_LOOKUP = MagicMock()
    module.ROUTE_PLACE_LOOKUP.find.return_value = None
    return module


@pytest.fixture
def recommendation_data(service, graph, monkeypatch):
    def configure(scores, distances=None):
        distances = distances if distances is not None else [2649] * len(scores)
        rows = [dict(candidate_id=i, distance_m=distance, overlap_ratio=12.345,
                     walkway_ratio=0.4567, residential_ratio=0.3214,
                     major_road_ratio=0.1234, green_ratio=0.5678,
                     radius_min=250, radius_max=400)
                for i, distance in enumerate(distances, 1)]
        frame = pd.DataFrame(rows, columns=["candidate_id", *service.FEATURE_COLUMNS,
                                           "radius_min", "radius_max"])
        candidates = [{"route": [1, 2, 3, 1], "radius_range": (250, 400)}
                      for _ in scores]
        dataset = MagicMock(return_value=(graph, candidates, frame))
        monkeypatch.setattr(service, "generate_route_dataset", dataset)
        service.walk_model.predict.return_value = np.asarray(scores)
        return SimpleNamespace(frame=frame, candidates=candidates, dataset=dataset)
    return configure
