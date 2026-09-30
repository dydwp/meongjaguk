/**
 * 산책 지도 (카카오맵) - 담당: 박용제
 * [data-walk-map] 요소에 카카오맵을 띄우고, 산책 중이면 걸은 경로를 선으로 그립니다.
 * 메인(바로 산책하기)과 산책 기록 화면에서 같이 사용합니다.
 * 카카오맵 SDK는 layout/default.html에서 이미 불러와져 있습니다.
 * 추천 산책로에서 시작한 경우 추천 경로와 실제 이동 경로를 함께 표시합니다.
 *
 * app.js가 보내는 신호
 *  - walk:position : 새 위치를 받았을 때 → 경로 다시 그리고 현재 위치 점 이동
 *  - walk:stopped  : 산책이 끝났을 때   → 경로 지우기
 */
(function () {
  var POINTS_KEY = "meongjaguk-walk-points"; // app.js와 같은 저장 이름
  var ACTIVE_KEY = "meongjaguk-walk-active";
  var DEFAULT_CENTER = { lat: 37.5665, lng: 126.978 }; // 위치를 모를 때: 서울시청
  var el = document.querySelector("[data-walk-map]");
  if (!el) return;

  var walkPage = document.querySelector("[data-auto-start-walk]");
  var routeKey = walkPage ? new URLSearchParams(window.location.search).get("route") : null;
  var routeNotice = document.querySelector("[data-walk-route-status]");
  var routeTitle = document.querySelector("[data-walk-title]");
  var routeLegend = document.querySelector("[data-walk-route-legend]");

  function readRecommendedRoute() {
    if (!routeKey) return null;
    try {
      if (!/^[a-zA-Z0-9-]+$/.test(routeKey)) throw new Error("Invalid route key");
      var route = JSON.parse(sessionStorage.getItem("walk-route:" + routeKey));
      if (!route || !Array.isArray(route.points) || route.points.length < 2 ||
          route.points.some(function (point) {
            return !point || !Number.isFinite(point.sequence) ||
              !Number.isFinite(point.latitude) || !Number.isFinite(point.longitude) ||
              Math.abs(point.latitude) > 90 || Math.abs(point.longitude) > 180;
          })) {
        throw new Error("Missing or invalid route points");
      }
      return {
        title: typeof route.title === "string" && route.title.trim()
          ? route.title : "추천 산책로",
        points: route.points.slice().sort(function (a, b) { return a.sequence - b.sequence; })
      };
    } catch (error) {
      console.error("추천 산책로 조회 실패:", error);
      if (routeNotice) {
        routeNotice.textContent = "추천 경로를 불러오지 못했어요. 자유 산책으로 기록합니다.";
        routeNotice.hidden = false;
      }
      return null;
    }
  }

  var recommended = readRecommendedRoute();
  if (recommended && routeTitle) routeTitle.textContent = recommended.title;
  if (!window.kakao || !window.kakao.maps || !window.kakao.maps.Map) {
    console.warn("카카오맵 SDK가 없어서 지도를 표시하지 않아요 (layout/default.html 확인)");
    if (recommended && routeNotice) {
      routeNotice.textContent = "지도를 불러오지 못했어요. 잠시 후 다시 확인해주세요.";
      routeNotice.hidden = false;
    }
    return;
  }

  var maps = window.kakao.maps;
  var rootStyle = getComputedStyle(document.documentElement);
  var PLANNED_COLOR = rootStyle.getPropertyValue("--color-primary").trim() || "#5C8D4E";
  var ACTUAL_COLOR = rootStyle.getPropertyValue("--color-accent-hover").trim() || "#E08F4F";
  var LINE_COLOR = recommended ? ACTUAL_COLOR : PLANNED_COLOR;
  var recommendedPath = recommended
    ? recommended.points.map(function (point) {
        return new maps.LatLng(point.latitude, point.longitude);
      })
    : [];

  function readPoints() {
    try {
      return JSON.parse(localStorage.getItem(POINTS_KEY)) || [];
    } catch (e) {
      return [];
    }
  }
  function isWalking() {
    return localStorage.getItem(ACTIVE_KEY) === "true";
  }

  var map = new maps.Map(el, {
    center: recommendedPath[0] || new maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
    level: recommendedPath.length ? 4 : 3
  });
  el.parentElement.classList.add("has-map"); // 그림(svg) 숨기고 칩을 구석으로

  if (recommendedPath.length) {
    new maps.Polyline({
      map: map,
      path: recommendedPath,
      strokeWeight: 5,
      strokeColor: PLANNED_COLOR,
      strokeOpacity: 0.9,
      strokeStyle: "solid"
    });
    new maps.Marker({ map: map, position: recommendedPath[0], title: "추천 경로 시작" });
    new maps.Marker({ map: map, position: recommendedPath[recommendedPath.length - 1],
      title: "추천 경로 도착" });
    var routeBounds = new maps.LatLngBounds();
    recommendedPath.forEach(function (point) { routeBounds.extend(point); });
    map.setBounds(routeBounds, 40, 40, 40, 40);
    if (routeLegend) routeLegend.hidden = false;
  }

  // 걸은 경로 선
  var line = new maps.Polyline({
    map: map,
    path: [],
    strokeWeight: 5,
    strokeColor: LINE_COLOR,
    strokeOpacity: 0.9,
    strokeStyle: "solid"
  });

  // 시작 지점 표시 (course-detail.js처럼 마커 + "시작" 글씨)
  var startMarker = new maps.Marker({ title: recommended ? "실제 산책 시작" : "시작" });
  var startLabel = document.createElement("span");
  startLabel.className = "tag";
  startLabel.style.cssText =
    "background:white;border:1px solid " + LINE_COLOR + ";white-space:nowrap;transform:translateY(-42px);";
  startLabel.textContent = recommended ? "내 시작" : "시작";
  var startOverlay = new maps.CustomOverlay({ content: startLabel, yAnchor: 1 });

  // 현재 위치 점
  var dot = new maps.CustomOverlay({
    content: '<div class="walk-map-dot"></div>',
    xAnchor: 0.5,
    yAnchor: 0.5
  });

  function drawPath() {
    var path = isWalking()
      ? readPoints().map(function (p) { return new maps.LatLng(p.lat, p.lng); })
      : [];
    line.setPath(path);

    if (path.length > 0) {
      startMarker.setPosition(path[0]);
      startMarker.setMap(map);
      startOverlay.setPosition(path[0]);
      startOverlay.setMap(map);
    } else {
      startMarker.setMap(null);
      startOverlay.setMap(null);
    }
    return path;
  }

  var firstLocation = true;
  function showMe(lat, lng) {
    var pos = new maps.LatLng(lat, lng);
    dot.setPosition(pos);
    dot.setMap(map);
    if (recommendedPath.length && firstLocation) {
      var bounds = new maps.LatLngBounds();
      recommendedPath.forEach(function (point) { bounds.extend(point); });
      bounds.extend(pos);
      map.setBounds(bounds, 40, 40, 40, 40);
    } else {
      map.panTo(pos);
    }
    firstLocation = false;
  }

  // 처음 열었을 때: 산책 중이면 마지막 위치로, 아니면 내 위치로
  var path = drawPath();
  if (path.length > 0) {
    var last = path[path.length - 1];
    showMe(last.getLat(), last.getLng());
  } else if (navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(
      function (pos) { showMe(pos.coords.latitude, pos.coords.longitude); },
      function () { /* 위치 거부 → 서울시청 중심 그대로 */ },
      { timeout: 10000, maximumAge: 60000 }
    );
  }

  window.addEventListener("walk:position", function (e) {
    drawPath();
    showMe(e.detail.lat, e.detail.lng);
  });
  window.addEventListener("walk:stopped", function () {
    drawPath(); // 산책이 끝났으니 선과 시작 표시 지우기
  });

  // 지도 영역 크기가 바뀌면 지도도 다시 맞춤
  if (window.ResizeObserver) {
    new ResizeObserver(function () { map.relayout(); }).observe(el);
  }
})();
