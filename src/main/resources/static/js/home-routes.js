/**
 * 메인 "추천 산책로" 3개 - 담당: 박용제
 * 추천 산책로 페이지(walk-recommend.js)와 같은 AI 추천 결과를 사용합니다.
 *  - 같은 탭에서 이미 추천받은 목록(sessionStorage "walk-recommendations:v1")이 있으면 그대로 사용
 *  - 없으면 현재 위치로 AI 서버에 6개를 요청해 같은 형식으로 보관
 *    → /routes 로 가도 다시 요청하지 않고, 같은 목록이 이어서 보임
 * 카드를 누르면 walk-recommend.js와 같은 방식으로 /course-detail?route={key} 로 이동
 */
(() => {
  const list = document.querySelector("#home-route-list");
  if (!list) return;
  const status = document.querySelector("#home-route-status");
  const showCount = Number(list.dataset.limit) || 3;
  const requestCount = 6; // walk-recommend.js 첫 요청 개수와 같게 (보관 형식 공유)
  const cacheKey = "walk-recommendations:v1";
  const apiUrl = "/api/routes/recommend"; // Spring 이 AI 서버로 중계

  function validRoutes(routes) {
    return (
      Array.isArray(routes) &&
      routes.every((route) => route && typeof route === "object" && !Array.isArray(route))
    );
  }

  function readCache() {
    try {
      const cached = JSON.parse(sessionStorage.getItem(cacheKey));
      const routes = Array.isArray(cached) ? cached : cached?.routes;
      return validRoutes(routes) && routes.length ? routes : null;
    } catch (error) {
      return null;
    }
  }

  function saveCache(routes, origin) {
    try {
      sessionStorage.setItem(cacheKey, JSON.stringify({
        routes,
        origin,
        requestedCount: requestCount,
        hasMore: routes.length === requestCount,
      }));
    } catch (error) {
      console.error("추천 목록 보관 실패:", error);
    }
  }

  function showLoading() {
    const cards = Array.from({ length: showCount }, () => {
      const card = document.createElement("div");
      card.className = "route-item walk-recommend-loading-card";
      card.setAttribute("aria-hidden", "true");
      const distance = document.createElement("div");
      distance.className = "route-thumb route-distance walk-recommend-loading-distance";
      const details = document.createElement("div");
      details.className = "walk-recommend-loading-details";
      for (const width of ["65%", "92%", "42%"]) {
        const line = document.createElement("span");
        line.className = "walk-recommend-loading-line";
        line.style.width = width;
        details.append(line);
      }
      card.append(distance, details);
      return card;
    });
    list.replaceChildren(...cards);
  }

  // walk-recommend.js 카드와 같은 모양: 왼쪽 거리(KM), 오른쪽 제목·설명·거리/시간
  function createCard(route, index) {
    const card = document.createElement("a");
    card.className = "route-item";
    const routeKey = crypto.randomUUID();
    card.href = `${list.dataset.detailUrl}?route=${encodeURIComponent(routeKey)}`;
    const rememberRoute = (event) => {
      try {
        sessionStorage.setItem(`walk-route:${routeKey}`, JSON.stringify(route));
      } catch (error) {
        event.preventDefault();
        console.error("추천 경로 보관 실패:", error);
        status.textContent = "상세 정보를 열지 못했어요. 잠시 후 다시 시도해주세요.";
        status.hidden = false;
      }
    };
    card.addEventListener("click", rememberRoute);
    card.addEventListener("auxclick", rememberRoute);

    const hasDistance = Number.isFinite(route.distance_m) && route.distance_m >= 0;
    const km = hasDistance ? (route.distance_m / 1000).toFixed(1) : "—";
    const thumb = document.createElement("div");
    thumb.className = "route-thumb route-distance";
    thumb.setAttribute("aria-label", hasDistance ? `거리 ${km}킬로미터` : "거리 정보 없음");
    const value = document.createElement("strong");
    value.className = "route-distance-value";
    value.textContent = km;
    const unit = document.createElement("span");
    unit.className = "route-distance-unit";
    unit.textContent = "KM";
    thumb.append(value, unit);

    const info = document.createElement("div");
    const title = document.createElement("h3");
    title.textContent = route.title || `추천 산책로 ${route.rank ?? index + 1}`;
    const description = document.createElement("p");
    description.textContent = route.description || "현재 위치를 기준으로 추천한 산책 경로예요.";
    const meta = document.createElement("span");
    meta.className = "meta";
    meta.textContent = [
      hasDistance ? `${km}km` : null,
      Number.isFinite(route.estimated_minutes) ? `약 ${route.estimated_minutes}분` : null,
    ].filter(Boolean).join(" · ") || "거리와 소요 시간 정보가 없어요.";
    info.append(title, description, meta);

    card.append(thumb, info);
    return card;
  }

  function render(routes) {
    const shown = routes.slice(0, showCount);
    list.replaceChildren(...shown.map(createCard));
    list.lastElementChild?.classList.add("mb-0");
    list.setAttribute("aria-busy", "false");
    if (shown.length) {
      status.hidden = true;
    } else {
      status.textContent = "주변에 추천할 산책로가 없어요.";
      status.hidden = false;
    }
  }

  function fail(message) {
    list.replaceChildren();
    list.setAttribute("aria-busy", "false");
    status.textContent = message;
    status.hidden = false;
  }

  function getCurrentPosition() {
    return new Promise((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(resolve, reject, {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 60000,
      });
    });
  }

  async function load() {
    const cached = readCache();
    if (cached) {
      render(cached);
      return;
    }
    if (!navigator.geolocation) {
      fail("현재 위치를 확인할 수 없어 추천 산책로를 보여드릴 수 없어요.");
      return;
    }

    showLoading();
    let locating = true;
    try {
      const position = await getCurrentPosition();
      locating = false;
      const origin = { latitude: position.coords.latitude, longitude: position.coords.longitude };
      const response = await fetch(apiUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ ...origin, top_k: requestCount }),
      });
      if (!response.ok) throw new Error(`Recommendation request failed: ${response.status}`);
      const data = await response.json();
      if (!validRoutes(data.routes)) throw new Error("Invalid recommendation response");
      saveCache(data.routes, origin);
      render(data.routes);
    } catch (error) {
      console.error("메인 추천 산책로 조회 실패:", error);
      fail(locating
        ? error.code === 1
          ? "위치 접근을 허용하면 내 주변 산책로를 추천받을 수 있어요."
          : "현재 위치를 확인하지 못했어요. 잠시 후 다시 시도해주세요."
        : "추천 산책로를 불러오지 못했어요. 잠시 후 다시 시도해주세요.");
    }
  }

  load();
})();
