// 추천 산책로 리스트 조회
async function loadCourses() {
  try {
    const response = await fetch("/api/courses/routes");

    if (!response.ok) {
      throw new Error(`코스 조회 실패 : ${response.status}`);
    }

    const courses = await response.json();

    if (courses.length === 0) {
      courseList.textContent = "등록된 추천 산책로가 없습니다.";
      return;
    }

    const cards = courses.map(createCoursesCard);
    courseList.replaceChildren(...cards);
  } catch (error) {
    console.error("코스를 불러오지 못했습니다.", error);
  }
}

const courseList = document.querySelector("#course-list");

if (courseList) {
  loadCourses();
}

function createCoursesCard(courses) {
  const card = document.createElement("a");

  card.className = "route-item";

  card.href = `/course-detail?courseId=${encodeURIComponent(courses.courseId)}`;

  card.innerHTML = `
      <div class="route-thumb">🐾</div>
      <div>
        <h3>한강 벚꽃길 코스 <span class="tag">추천</span></h3>
        <p>
          강변을 따라 걷는 평탄한 코스예요. 소형견도 부담 없이 걸을 수 있어요.
        </p>
        <span class="meta">2.3km · 약 35분</span>
      </div>
  `;

  card.querySelector("h3").textContent = courses.courseName;
  card.querySelector("p").textContent = courses.description ?? "";

  const distance =
    courses.distanceM != null
      ? `${(courses.distanceM / 1000).toFixed(1)}km`
      : "";

  const minutes =
    courses.estimatedMinutes != null ? `약 ${courses.estimatedMinutes}분` : "";

  card.querySelector(".meta").textContent = [distance, minutes]
    .filter(Boolean)
    .join(" · ");

  return card;
}

(() => {
  const list = document.querySelector("#walk-recommend-list");
  if (!list) return;
  const status = document.querySelector("#walk-recommend-status");
  const retry = document.querySelector("#walk-recommend-retry");
  let loading = false;

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
    list.replaceChildren();
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
      if (!Array.isArray(data.routes) || data.routes.some(route => !route || typeof route !== "object")) {
        throw new Error("Invalid recommendation response");
      }
      if (data.routes.length === 0) {
        status.textContent = "주변에 추천할 산책로가 없어요. 다른 위치에서 다시 확인해주세요.";
        return;
      }
      list.replaceChildren(...data.routes.map(createWalkRouteCard));
      status.textContent = `내 주변 산책로 ${data.routes.length}개를 찾았어요.`;
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
  loadRecommendedRoutes();
})();
