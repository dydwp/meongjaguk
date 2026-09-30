(() => {
  const page = document.querySelector("#activity-detail");
  if (!page) return;

  const container = document.querySelector("#activity-map");
  const notice = document.querySelector("#activity-map-status");
  const legend = document.querySelector("#activity-route-legend");

  function readPoints(selector) {
    return [...document.querySelectorAll(selector)]
      .map((element) => ({
        latitude: Number(element.dataset.lat),
        longitude: Number(element.dataset.lng),
      }))
      .filter((point) =>
        Number.isFinite(point.latitude) &&
        Number.isFinite(point.longitude) &&
        Math.abs(point.latitude) <= 90 &&
        Math.abs(point.longitude) <= 180
      );
  }

  const plannedPoints = readPoints("#planned-point-data [data-lat][data-lng]");
  const actualPoints = readPoints("#walk-point-data [data-lat][data-lng]");

  if (plannedPoints.length === 0 && actualPoints.length === 0) {
    notice.hidden = false;
    return;
  }

  try {
    if (!window.kakao?.maps?.Map || !window.kakao?.maps?.Polyline) {
      throw new Error("Map SDK unavailable");
    }

    const maps = window.kakao.maps;
    const plannedPath = plannedPoints.map(
      (point) => new maps.LatLng(point.latitude, point.longitude)
    );
    const actualPath = actualPoints.map(
      (point) => new maps.LatLng(point.latitude, point.longitude)
    );
    const allPositions = [...plannedPath, ...actualPath];
    const colors = getComputedStyle(document.documentElement);
    const plannedColor = colors.getPropertyValue("--color-primary").trim() || "#5C8D4E";
    const actualColor = colors.getPropertyValue("--color-accent-hover").trim() || "#E08F4F";

    container.hidden = false;
    const map = new maps.Map(container, {
      center: allPositions[0],
      level: 3,
    });

    if (plannedPath.length) {
      new maps.Polyline({
        map,
        path: plannedPath,
        strokeWeight: 8,
        strokeColor: plannedColor,
        strokeOpacity: 0.9,
        strokeStyle: "solid",
      });
      document.querySelector("#activity-planned-legend").hidden = false;
    }

    if (actualPath.length) {
      new maps.Polyline({
        map,
        path: actualPath,
        strokeWeight: 4,
        strokeColor: actualColor,
        strokeOpacity: 0.9,
        strokeStyle: "solid",
      });
      document.querySelector("#activity-actual-legend").hidden = false;
    }

    legend.hidden = false;

    const markerPath = actualPath.length ? actualPath : plannedPath;
    new maps.Marker({
      map,
      position: markerPath[0],
      title: actualPath.length ? "산책 시작" : "추천 경로 시작",
    });
    if (markerPath.length > 1) {
      new maps.Marker({
        map,
        position: markerPath[markerPath.length - 1],
        title: actualPath.length ? "산책 종료" : "추천 경로 도착",
      });
    }

    if (allPositions.length > 1) {
      const bounds = new maps.LatLngBounds();
      allPositions.forEach((position) => bounds.extend(position));
      map.setBounds(bounds);
    }
  } catch (error) {
    console.error("활동 경로 지도 표시 실패:", error);
    container.hidden = true;
    legend.hidden = true;
    notice.textContent = "지도를 불러오지 못했어요. 잠시 후 다시 확인해주세요.";
    notice.hidden = false;
  }
})();
