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
  var MEETING_KEY = "meongjaguk-walk-meeting-id"; // 동행 산책 모집글 번호, app.js와 같은 저장 이름 (추가: 김환중)
  var DEFAULT_CENTER = { lat: 37.5665, lng: 126.978 }; // 위치를 모를 때: 서울시청
  var el = document.querySelector("[data-walk-map]");
  if (!el) return;

  var walkPage = document.querySelector("[data-auto-start-walk]");
  var routeKey = walkPage ? new URLSearchParams(window.location.search).get("route") : null;
  var routeNotice = document.querySelector("[data-walk-route-status]");
  var routeTitle = document.querySelector("[data-walk-title]");
  var routeLegend = document.querySelector("[data-walk-route-legend]");
  var plannedLabel = document.querySelector("[data-walk-planned-label]"); // 범례의 계획 경로 이름 (추가: 김환중)

  // 경로 좌표가 2개 이상이고 모두 올바르면 순서대로 정렬해서, 아니면 null (추천 산책로·동행 모집 코스 공통) (추가: 김환중)
  function sortedRoutePoints(points) {
    if (!Array.isArray(points) || points.length < 2 ||
        points.some(function (point) {
          return !point || !Number.isFinite(point.sequence) ||
            !Number.isFinite(point.latitude) || !Number.isFinite(point.longitude) ||
            Math.abs(point.latitude) > 90 || Math.abs(point.longitude) > 180;
        })) {
      return null;
    }
    return points.slice().sort(function (a, b) { return a.sequence - b.sequence; });
  }

  function readRecommendedRoute() {
    if (!routeKey) return null;
    try {
      if (!/^[a-zA-Z0-9-]+$/.test(routeKey)) throw new Error("Invalid route key");
      var route = JSON.parse(sessionStorage.getItem("walk-route:" + routeKey));
      var points = route ? sortedRoutePoints(route.points) : null; // (수정: 김환중)
      if (!points) {
        throw new Error("Missing or invalid route points");
      }
      return {
        title: typeof route.title === "string" && route.title.trim()
          ? route.title : "추천 산책로",
        points: points // (수정: 김환중)
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

  // 동행 산책 모집글 번호 (app.js의 실제 연결과 같은 기준) (추가: 김환중)
  // - 산책 중이면 저장된 번호만 봄 (없으면 개인 산책이므로 주소에 번호가 있어도 동행 아님)
  // - 산책 중이 아닐 때만 주소의 meetingId 사용 (숫자만)
  function readMeetingId() {
    if (!walkPage) return null;
    var value = localStorage.getItem(ACTIVE_KEY) === "true"
      ? localStorage.getItem(MEETING_KEY)
      : new URLSearchParams(window.location.search).get("meetingId");
    return value && /^[1-9][0-9]{0,17}$/.test(value) ? value : null;
  }

  // 모집글 제목·코스 불러오기 → { title, points } 또는 실패하면 null (추가: 김환중)
  function loadMeetingRoute(meetingId) {
    return fetch("/api/meetings/" + encodeURIComponent(meetingId))
      .then(function (res) {
        if (!res.ok) throw new Error("HTTP " + res.status);
        return res.json();
      })
      .then(function (meeting) {
        var points = meeting ? sortedRoutePoints(meeting.points) : null;
        if (!points) throw new Error("Missing or invalid meeting course points");
        var title = typeof meeting.title === "string" && meeting.title.trim() ? meeting.title : "";
        if (routeTitle && title) routeTitle.textContent = "동행 산책 · " + title;
        return { title: title, points: points };
      })
      .catch(function (error) {
        console.error("동행 모집 코스 조회 실패:", error); // 제목은 "동행 산책", 지도는 실제 경로만
        return null;
      });
  }

  var recommended = readRecommendedRoute();
  if (recommended && routeTitle) routeTitle.textContent = recommended.title;
  var meetingId = recommended ? null : readMeetingId(); // 추천 산책로가 있으면 추천 산책로 우선 (추가: 김환중)
  if (meetingId && routeTitle) routeTitle.textContent = "동행 산책"; // (추가: 김환중)
  var meetingRoute = meetingId ? loadMeetingRoute(meetingId) : null; // (추가: 김환중)
  var hasPlan = Boolean(recommended || meetingId); // 계획 경로(추천·모집 코스)와 함께 그리는지 (추가: 김환중)
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
  var LINE_COLOR = hasPlan ? ACTUAL_COLOR : PLANNED_COLOR; // (수정: 김환중)
  var plannedPath = []; // 계획 경로 좌표 (추천 경로는 바로, 모집 코스는 불러온 뒤 채움) (수정: 김환중)
  var lastPos = null; // 마지막으로 표시한 내 위치 (추가: 김환중)

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

  var recommendedPoints = recommended ? recommended.points : []; // (수정: 김환중)
  var map = new maps.Map(el, {
    center: recommendedPoints.length
      ? new maps.LatLng(recommendedPoints[0].latitude, recommendedPoints[0].longitude)
      : new maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng), // (수정: 김환중)
    level: recommendedPoints.length ? 4 : 3 // (수정: 김환중)
  });
  el.parentElement.classList.add("has-map"); // 그림(svg) 숨기고 칩을 구석으로

  // 계획 경로(추천 경로·동행 모집 코스) 그리기: 선, 시작·도착 표시, 범위 맞추기, 범례 (수정: 김환중)
  function drawPlannedRoute(points, name) {
    plannedPath = points.map(function (point) {
      return new maps.LatLng(point.latitude, point.longitude);
    });
    new maps.Polyline({
      map: map,
      path: plannedPath,
      strokeWeight: 5,
      strokeColor: PLANNED_COLOR,
      strokeOpacity: 0.9,
      strokeStyle: "solid"
    });
    new maps.Marker({ map: map, position: plannedPath[0], title: name + " 시작" });
    new maps.Marker({ map: map, position: plannedPath[plannedPath.length - 1],
      title: name + " 도착" });
    var routeBounds = new maps.LatLngBounds();
    plannedPath.forEach(function (point) { routeBounds.extend(point); });
    if (lastPos) routeBounds.extend(lastPos); // 코스를 늦게 불러왔으면 내 위치도 함께 (추가: 김환중)
    map.setBounds(routeBounds, 40, 40, 40, 40);
    if (routeLegend) routeLegend.hidden = false;
  }

  if (recommended) drawPlannedRoute(recommended.points, "추천 경로"); // (수정: 김환중)

  // 걸은 경로 선
  var line = new maps.Polyline({
    map: map,
    path: [],
    strokeWeight: 5,
    strokeColor: LINE_COLOR,
    strokeOpacity: 0.9,
    strokeStyle: "solid",
    zIndex: 1 // 나중에 그려지는 모집 코스보다 위에 (추가: 김환중)
  });

  // 시작 지점 표시 (course-detail.js처럼 마커 + "시작" 글씨)
  var startMarker = new maps.Marker({ title: hasPlan ? "실제 산책 시작" : "시작" }); // (수정: 김환중)
  var startLabel = document.createElement("span");
  startLabel.className = "tag";
  startLabel.style.cssText =
    "background:white;border:1px solid " + LINE_COLOR + ";white-space:nowrap;transform:translateY(-42px);";
  startLabel.textContent = hasPlan ? "내 시작" : "시작"; // (수정: 김환중)
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
    lastPos = pos; // (추가: 김환중)
    if (plannedPath.length && firstLocation) { // (수정: 김환중)
      var bounds = new maps.LatLngBounds();
      plannedPath.forEach(function (point) { bounds.extend(point); }); // (수정: 김환중)
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

  // 동행 산책: 모집 코스를 불러온 뒤 계획 경로로 그림 (실패하면 실제 경로만) (추가: 김환중)
  if (meetingRoute) {
    meetingRoute.then(function (route) {
      if (!route) return;
      drawPlannedRoute(route.points, "모집 코스");
      if (plannedLabel) plannedLabel.textContent = "모집 코스";
    });
  }

  // 지도 영역 크기가 바뀌면 지도도 다시 맞춤
  if (window.ResizeObserver) {
    new ResizeObserver(function () { map.relayout(); }).observe(el);
  }
})();
