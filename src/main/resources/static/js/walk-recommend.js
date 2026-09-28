(() => {
  const list = document.querySelector("#walk-recommend-list");
  if (!list) return;
  const status = document.querySelector("#walk-recommend-status");
  const retry = document.querySelector("#walk-recommend-retry");
  const cacheKey = "walk-recommendations:v1";
  let loading = false;

  function showLoadingCards() {
    const count = Math.min(Number(list.dataset.limit) || 3, 3);
    const cards = Array.from({ length: count }, () => {
      const card = document.createElement("div");
      card.className = "route-item walk-recommend-loading-card";
      card.setAttribute("aria-hidden", "true");

      const distance = document.createElement("div");
      distance.className =
        "route-thumb route-distance walk-recommend-loading-distance";

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

  function validRoutes(routes) {
    return (
      Array.isArray(routes) &&
      routes.every(
        (route) => route && typeof route === "object" && !Array.isArray(route),
      )
    );
  }

  function renderRoutes(routes) {
    list.replaceChildren(...routes.map(createWalkRouteCard));
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
        status.textContent =
          "상세 정보를 열지 못했어요. 잠시 후 다시 시도해주세요.";
      }
    };
    card.addEventListener("click", rememberRoute);
    card.addEventListener("auxclick", rememberRoute);
    const thumb = document.createElement("div");
    thumb.className = "route-thumb route-distance";
    const distance =
      Number.isFinite(route.distance_m) && route.distance_m >= 0
        ? (route.distance_m / 1000).toFixed(1)
        : "—";
    const distanceValue = document.createElement("strong");
    distanceValue.className = "route-distance-value";
    distanceValue.textContent = distance;
    const distanceUnit = document.createElement("span");
    distanceUnit.className = "route-distance-unit";
    distanceUnit.textContent = "KM";
    thumb.setAttribute(
      "aria-label",
      distance === "—" ? "거리 정보 없음" : `거리 ${distance}킬로미터`,
    );
    thumb.append(distanceValue, distanceUnit);

    const info = document.createElement("div");
    const title = document.createElement("h3");
    title.textContent = route.title || `추천 산책로 ${route.rank ?? index + 1}`;
    const description = document.createElement("p");
    description.textContent =
      route.description || "현재 위치를 기준으로 추천한 산책 경로예요.";
    const meta = document.createElement("span");
    meta.className = "meta";
    meta.textContent =
      [
        Number.isFinite(route.distance_m)
          ? `${(route.distance_m / 1000).toFixed(1)}km`
          : null,
        Number.isFinite(route.estimated_minutes)
          ? `약 ${route.estimated_minutes}분`
          : null,
      ]
        .filter(Boolean)
        .join(" · ") || "거리와 소요 시간 정보가 없어요.";

    info.append(title, description, meta);
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
      status.textContent =
        "현재 위치를 확인할 수 없어요. 다른 브라우저에서 이용해주세요.";
      return;
    }
    loading = true;
    const previousCards = [...list.childNodes];
    list.setAttribute("aria-busy", "true");
    retry.hidden = true;
    showLoadingCards();
    status.textContent =
      "현재 위치를 확인하고 있어요. 위치 접근을 허용해주세요.";
    let locating = true;

    const top_k = Number(list.getAttribute("data-limit"));

    try {
      const position = await getCurrentPosition();
      locating = false;
      status.textContent =
        "주변 산책로를 추천하고 있어요. 잠시만 기다려주세요.";
      const { latitude, longitude } = position.coords;
      const response = await fetch(
        "http://127.0.0.1:8000/api/routes/recommend",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Accept: "application/json",
          },
          body: JSON.stringify({ latitude, longitude, top_k }),
        },
      );
      if (!response.ok)
        throw new Error(`Recommendation request failed: ${response.status}`);
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
      list.replaceChildren(...previousCards);
      status.textContent = locating
        ? error.code === 1
          ? "위치 접근을 허용하면 주변 산책로를 추천받을 수 있어요."
          : "현재 위치를 확인하지 못했어요. 잠시 후 다시 시도해주세요."
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
