(() => {
  const list = document.querySelector("#walk-recommend-list");
  if (!list) return;
  const status = document.querySelector("#walk-recommend-status");
  const retry = document.querySelector("#walk-recommend-retry");
  const cacheKey = "walk-recommendations:v1";
  let loading = false;

  function validRoutes(routes) {
    return Array.isArray(routes) && routes.every(route =>
      route && typeof route === "object" && !Array.isArray(route));
  }

  function renderRoutes(routes) {
    list.replaceChildren(...routes.map(createWalkRouteCard));
    status.textContent = routes.length
      ? `내 주변 산책로 ${routes.length}개를 찾았어요.`
      : "주변에 추천할 산책로가 없어요. 다른 위치에서 다시 확인해주세요.";
    list.setAttribute("aria-busy", "false");
    retry.hidden = false;
  }

  function restoreRoutes() {
    try {
      const saved = sessionStorage.getItem(cacheKey);
      if (saved === null) return false;
      const routes = JSON.parse(saved);
      if (!validRoutes(routes)) throw new Error("Invalid recommendation cache");
      renderRoutes(routes);
      return true;
    } catch (error) {
      console.error("추천 목록 복원 실패:", error);
      return false;
    }
  }

  function createWalkRouteCard(route, index) {
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
      }
    };
    card.addEventListener("click", rememberRoute);
    card.addEventListener("auxclick", rememberRoute);
    const thumb = document.createElement("div");
    thumb.className = "route-thumb";
    thumb.textContent = "🐾";
    thumb.setAttribute("aria-hidden", "true");

    const info = document.createElement("div");
    const title = document.createElement("h3");
    title.textContent = route.title || `추천 산책로 ${route.rank ?? index + 1}`;
    const description = document.createElement("p");
    description.textContent = route.description || "현재 위치를 기준으로 추천한 산책 경로예요.";
    const meta = document.createElement("span");
    meta.className = "meta";
    meta.textContent = [
      Number.isFinite(route.distance_m) ? `${(route.distance_m / 1000).toFixed(1)}km` : null,
      Number.isFinite(route.estimated_minutes) ? `약 ${route.estimated_minutes}분` : null,
    ].filter(Boolean).join(" · ") || "거리와 소요 시간 정보가 없어요.";

    info.append(title, description, meta);
    const features = [];
    if (Number.isFinite(route.walkway_ratio)) {
      features.push(`보행로 ${Math.round(route.walkway_ratio * 100)}%`);
    }
    if (Number.isFinite(route.green_ratio)) {
      features.push(`녹지 ${Math.round(route.green_ratio * 100)}%`);
    }
    if (features.length) {
      const detail = document.createElement("p");
      detail.className = "small text-muted";
      detail.textContent = features.join(" · ");
      info.append(detail);
    }
    card.append(thumb, info);
    return card;
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

  async function loadRecommendedRoutes() {
    if (loading) return;
    if (!navigator.geolocation) {
      status.textContent = "현재 위치를 확인할 수 없어요. 다른 브라우저에서 이용해주세요.";
      return;
    }
    loading = true;
    list.setAttribute("aria-busy", "true");
    retry.hidden = true;
    status.textContent = "현재 위치를 확인하고 있어요. 위치 접근을 허용해주세요.";
    let locating = true;

    try {
      const position = await getCurrentPosition();
      locating = false;
      status.textContent = "주변 산책로를 추천하고 있어요. 잠시만 기다려주세요.";
      const { latitude, longitude } = position.coords;
      const response = await fetch("http://127.0.0.1:8000/api/routes/recommend", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ latitude, longitude }),
      });
      if (!response.ok) throw new Error(`Recommendation request failed: ${response.status}`);
      const data = await response.json();
      if (!validRoutes(data.routes)) {
        throw new Error("Invalid recommendation response");
      }
      try {
        sessionStorage.setItem(cacheKey, JSON.stringify(data.routes));
      } catch (error) {
        console.error("추천 목록 보관 실패:", error);
      }
      renderRoutes(data.routes);
    } catch (error) {
      console.error("산책로 추천 실패:", error);
      status.textContent = locating
        ? (error.code === 1
          ? "위치 접근을 허용하면 주변 산책로를 추천받을 수 있어요."
          : "현재 위치를 확인하지 못했어요. 잠시 후 다시 시도해주세요.")
        : "추천 산책로를 불러오지 못했어요. 잠시 후 다시 시도해주세요.";
    } finally {
      loading = false;
      list.setAttribute("aria-busy", "false");
      retry.hidden = false;
    }
  }

  retry.addEventListener("click", loadRecommendedRoutes);
  if (!restoreRoutes()) loadRecommendedRoutes();
})();
