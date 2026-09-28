/**
 * 산책 지도 (카카오맵) - 담당: 박용제
 * [data-walk-map] 요소에 카카오맵을 띄우고, 산책 중이면 걸은 경로를 선으로 그립니다.
 * 메인(바로 산책하기)과 산책 기록 화면에서 같이 사용합니다.
 * 카카오맵 SDK는 layout/default.html에서 이미 불러와져 있습니다.
 * 선 색·시작 표시는 추천 산책로 상세(course-detail.js)와 같은 모양으로 맞췄습니다.
 *
 * app.js가 보내는 신호
 *  - walk:position : 새 위치를 받았을 때 → 경로 다시 그리고 현재 위치 점 이동
 *  - walk:stopped  : 산책이 끝났을 때   → 경로 지우기
 */
(function () {
  var POINTS_KEY = "mungjaguk-walk-points"; // app.js와 같은 저장 이름
  var ACTIVE_KEY = "mungjaguk-walk-active";
  var DEFAULT_CENTER = { lat: 37.5665, lng: 126.978 }; // 위치를 모를 때: 서울시청
  var LINE_COLOR = "#2f8060";                          // course-detail.js와 같은 색

  var el = document.querySelector("[data-walk-map]");
  if (!el) return;
  if (!window.kakao || !window.kakao.maps || !window.kakao.maps.Map) {
    console.warn("카카오맵 SDK가 없어서 지도를 표시하지 않아요 (layout/default.html 확인)");
    return;
  }

  var maps = window.kakao.maps;

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
    center: new maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
    level: 3
  });
  el.parentElement.classList.add("has-map"); // 그림(svg) 숨기고 칩을 구석으로

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
  var startMarker = new maps.Marker({ title: "시작" });
  var startLabel = document.createElement("span");
  startLabel.className = "tag";
  startLabel.style.cssText =
    "background:white;border:1px solid " + LINE_COLOR + ";white-space:nowrap;transform:translateY(-42px);";
  startLabel.textContent = "시작";
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

  function showMe(lat, lng) {
    var pos = new maps.LatLng(lat, lng);
    dot.setPosition(pos);
    dot.setMap(map);
    map.panTo(pos);
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