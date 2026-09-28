(() => {
  const page = document.querySelector("#activity-detail");
  if (!page) return;

  const container = document.querySelector("#activity-map");
  const notice = document.querySelector("#activity-map-status");
  const pointElements = document.querySelectorAll("#walk-point-data [data-lat][data-lng]");

  const points = [...pointElements]
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

  if (points.length === 0) {
    notice.hidden = false;
    return;
  }

  try {
    if (!window.kakao?.maps?.Map || !window.kakao?.maps?.Polyline) {
      throw new Error("Map SDK unavailable");
    }

    container.hidden = false;

    const path = points.map(
      (point) => new kakao.maps.LatLng(point.latitude, point.longitude)
    );

    const map = new kakao.maps.Map(container, {
      center: path[0],
      level: 3,
    });

    const primaryColor =
      getComputedStyle(document.documentElement).getPropertyValue("--color-primary").trim() || "#ff6b35";

    new kakao.maps.Polyline({
      map,
      path,
      strokeWeight: 5,
      strokeColor: primaryColor,
      strokeOpacity: 0.9,
      strokeStyle: "solid",
    });

    new kakao.maps.Marker({
      map,
      position: path[0],
      title: "산책 시작",
    });

    if (path.length > 1) {
      new kakao.maps.Marker({
        map,
        position: path[path.length - 1],
        title: "산책 종료",
      });

      const bounds = new kakao.maps.LatLngBounds();
      path.forEach((position) => bounds.extend(position));
      map.setBounds(bounds);
    }
  } catch (error) {
    console.error("활동 경로 지도 표시 실패:", error);
    container.hidden = true;
    notice.textContent = "지도를 불러오지 못했어요. 잠시 후 다시 확인해주세요.";
    notice.hidden = false;
  }
})();