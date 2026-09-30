(() => {
  const list = document.querySelector("#walk-recommend-list");
  if (!list) return;
  const status = document.querySelector("#walk-recommend-status");
  const retry = document.querySelector("#walk-recommend-retry");
  const moreStatus = document.querySelector("#walk-recommend-more-status");
  const moreRetry = document.querySelector("#walk-recommend-more-retry");
  const sentinel = document.querySelector("#walk-recommend-sentinel");
  const initialLimit = Number(list.dataset.limit) || 6;
  const pageSize = 3;
  const cacheKey = "walk-recommendations:v1";
  let loading = false;
  let routes = [];
  let origin = null;
  let requestedCount = 0;
  let hasMore = false;
  let moreError = false;
  let pauseMore = false;
  let observer = null;

  function showLoadingCards(append = false) {
    const count = append ? pageSize : Math.min(initialLimit, pageSize);
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
    if (append) list.append(...cards);
    else list.replaceChildren(...cards);
  }

  function removeLoadingCards() {
    list.querySelectorAll(".walk-recommend-loading-card").forEach((card) => card.remove());
  }

  function syncMoreState() {
    observer?.unobserve(sentinel);
    moreRetry.hidden = !moreError;
    moreStatus.hidden = loading || (!moreError && (hasMore || routes.length === 0));
    moreStatus.textContent = moreError
      ? "추가 산책로를 불러오지 못했어요."
      : "더 추천할 산책로가 없어요.";
    sentinel.hidden = loading || !hasMore || routes.length === 0 || moreError || pauseMore;
    if (!sentinel.hidden) observer?.observe(sentinel);
  }

  function saveRoutes() {
    try {
      sessionStorage.setItem(cacheKey, JSON.stringify({
        routes, origin, requestedCount, hasMore,
      }));
    } catch (error) {
      console.error("추천 목록 보관 실패:", error);
    }
  }

  function validRoutes(routes) {
    return (
      Array.isArray(routes) &&
      routes.every(
        (route) => route && typeof route === "object" && !Array.isArray(route),
      )
    );
  }

  function renderRoutes() {
    list.replaceChildren(...routes.map(createWalkRouteCard));
    status.textContent = routes.length
      ? `내 주변 산책로 ${routes.length}개를 찾았어요.`
      : "주변에 추천할 산책로가 없어요. 다른 위치에서 다시 확인해주세요.";
    list.setAttribute("aria-busy", "false");
    retry.disabled = loading;
    syncMoreState();
  }

  function restoreRoutes() {
    try {
      const saved = sessionStorage.getItem(cacheKey);
      if (saved === null) return false;
      const cached = JSON.parse(saved);
      const savedRoutes = Array.isArray(cached) ? cached : cached.routes;
      if (!validRoutes(savedRoutes)) throw new Error("Invalid recommendation cache");
      routes = savedRoutes;
      origin = !Array.isArray(cached) && Number.isFinite(cached.origin?.latitude)
        && Number.isFinite(cached.origin?.longitude) ? cached.origin : null;
      requestedCount = !Array.isArray(cached) && Number.isInteger(cached.requestedCount)
        ? Math.max(cached.requestedCount, routes.length) : Math.max(initialLimit, routes.length);
      hasMore = !Array.isArray(cached) && typeof cached.hasMore === "boolean"
        ? cached.hasMore : routes.length >= initialLimit;
      renderRoutes();
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

  async function requestRoutes(position, top_k) {
    const response = await fetch("http://127.0.0.1:8000/api/routes/recommend", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ ...position, top_k }),
    });
    if (!response.ok)
      throw new Error(`Recommendation request failed: ${response.status}`);
    const data = await response.json();
    if (!validRoutes(data.routes)) throw new Error("Invalid recommendation response");
    return data.routes;
  }

  function routeKey(route) {
    return Number.isInteger(route.candidate_id)
      ? `candidate:${route.candidate_id}`
      : JSON.stringify(route.points ?? [route.title, route.distance_m]);
  }

  async function loadRecommendedRoutes() {
    if (loading) return;
    if (!navigator.geolocation) {
      status.textContent =
        "현재 위치를 확인할 수 없어요. 다른 브라우저에서 이용해주세요.";
      return;
    }
    loading = true;
    pauseMore = false;
    moreError = false;
    const previousCards = [...list.childNodes];
    list.setAttribute("aria-busy", "true");
    retry.disabled = true;
    syncMoreState();
    showLoadingCards();
    status.textContent =
      "현재 위치를 확인하고 있어요. 위치 접근을 허용해주세요.";
    let locating = true;

    try {
      const position = await getCurrentPosition();
      locating = false;
      status.textContent =
        "주변 산책로를 추천하고 있어요. 잠시만 기다려주세요.";
      const nextOrigin = {
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
      };
      const nextRoutes = await requestRoutes(nextOrigin, initialLimit);
      routes = nextRoutes;
      origin = nextOrigin;
      requestedCount = initialLimit;
      hasMore = routes.length === initialLimit;
      saveRoutes();
      renderRoutes();
    } catch (error) {
      console.error("산책로 추천 실패:", error);
      list.replaceChildren(...previousCards);
      pauseMore = true;
      status.textContent = locating
        ? error.code === 1
          ? "위치 접근을 허용하면 주변 산책로를 추천받을 수 있어요."
          : "현재 위치를 확인하지 못했어요. 잠시 후 다시 시도해주세요."
        : "추천 산책로를 불러오지 못했어요. 잠시 후 다시 시도해주세요.";
    } finally {
      loading = false;
      list.setAttribute("aria-busy", "false");
      retry.disabled = false;
      syncMoreState();
    }
  }

  async function loadMore() {
    if (loading || !hasMore || routes.length === 0) return;
    loading = true;
    moreError = false;
    list.setAttribute("aria-busy", "true");
    retry.disabled = true;
    syncMoreState();
    showLoadingCards(true);
    moreStatus.textContent = "산책로를 더 추천하고 있어요.";
    moreStatus.hidden = false;

    try {
      if (!origin) {
        const position = await getCurrentPosition();
        origin = { latitude: position.coords.latitude, longitude: position.coords.longitude };
      }
      const nextCount = Math.max(requestedCount, routes.length) + pageSize;
      const returned = await requestRoutes(origin, nextCount);
      const seen = new Set(routes.map(routeKey));
      const additions = returned.filter((route) => {
        const key = routeKey(route);
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      }).slice(0, pageSize);

      removeLoadingCards();
      const firstIndex = routes.length;
      routes.push(...additions);
      list.append(...additions.map((route, index) => createWalkRouteCard(route, firstIndex + index)));
      requestedCount = nextCount;
      hasMore = returned.length === nextCount && additions.length > 0;
      status.textContent = `내 주변 산책로 ${routes.length}개를 찾았어요.`;
      saveRoutes();
    } catch (error) {
      console.error("추가 산책로 추천 실패:", error);
      removeLoadingCards();
      moreError = true;
    } finally {
      loading = false;
      list.setAttribute("aria-busy", "false");
      retry.disabled = false;
      syncMoreState();
    }
  }

  retry.addEventListener("click", loadRecommendedRoutes);
  moreRetry.addEventListener("click", loadMore);
  if ("IntersectionObserver" in window) {
    observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) loadMore();
    }, { rootMargin: "0px 0px 200px 0px" });
  } else {
    window.addEventListener("scroll", () => {
      if (!sentinel.hidden && sentinel.getBoundingClientRect().top < window.innerHeight + 200)
        loadMore();
    }, { passive: true });
  }
  if (!restoreRoutes()) loadRecommendedRoutes();
})();
