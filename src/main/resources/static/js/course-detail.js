(() => {
  const page = document.querySelector("#course-detail");
  if (!page) return;
  const status = document.querySelector("#course-status");
  const content = document.querySelector("#course-content");
  const params = new URLSearchParams(window.location.search);
  const courseId = params.get("courseId");
  const isRecommended = params.has("route");
  const shareButton = document.querySelector("#course-share");
  let loadedCourse = null;
  let saving = false;
  let saved = false;
  const savedKey = `walk-route-saved:${params.get("route")}`;
  try {
    saved = isRecommended && sessionStorage.getItem(savedKey) === "true";
  } catch (error) {
    console.error("코스 저장 상태 확인 실패:", error);
  }

  shareButton.addEventListener("click", async (event) => {
    event.preventDefault();
    if (!loadedCourse || saving) return;
    if (!isRecommended || saved) {
      shareButton.textContent = "공유 완료";
      shareButton.disabled = true;
      return;
    }
    saving = true;
    shareButton.disabled = true;
    shareButton.textContent = "공유 중…";
    try {
      const first = [...(loadedCourse.points ?? [])].sort(
        (a, b) => a.sequence - b.sequence,
      )[0];
      if (
        !first ||
        !Number.isFinite(first.latitude) ||
        !Number.isFinite(first.longitude) ||
        !Number.isFinite(loadedCourse.distanceM) ||
        !Number.isFinite(loadedCourse.estimatedMinutes)
      ) {
        shareButton.textContent = "다시 추천받아주세요";
        return;
      }
      const headers = {
        "Content-Type": "application/json",
        Accept: "application/json",
      };
      if (page.dataset.csrfHeader && page.dataset.csrfToken) {
        headers[page.dataset.csrfHeader] = page.dataset.csrfToken;
      }
      const response = await fetch(page.dataset.saveUrl, {
        method: "POST",
        credentials: "same-origin",
        headers,
        body: JSON.stringify({
          name: loadedCourse.courseName,
          description: loadedCourse.description ?? null,
          distanceM: Math.round(loadedCourse.distanceM),
          estimatedMinutes: Math.round(loadedCourse.estimatedMinutes),
          feature: loadedCourse.feature,
          region: loadedCourse.region ?? null,
          startLatitude: first.latitude,
          startLongitude: first.longitude,
          thumbnailImg: loadedCourse.thumbnailImg ?? null,
        }),
      });
      if (response.redirected || response.status === 401) {
        shareButton.textContent = "로그인 후 공유해주세요";
        return;
      }
      if (response.status === 403) {
        shareButton.textContent = "새로고침 후 다시 시도";
        return;
      }
      if (response.status !== 201)
        throw new Error(`Course save failed: ${response.status}`);
      saved = true;
      try {
        sessionStorage.setItem(savedKey, "true");
      } catch (error) {
        console.error("코스 저장 상태 보관 실패:", error);
      }
      shareButton.textContent = "공유 완료";
    } catch (error) {
      console.error("산책 코스 저장 실패:", error);
      shareButton.textContent = "다시 시도해주세요";
    } finally {
      saving = false;
      shareButton.disabled = saved;
    }
  });

  function showRecommendedMap(points) {
    const container = document.querySelector("#map");
    const notice = document.querySelector("#course-map-status");
    if (
      !Array.isArray(points) ||
      points.length < 2 ||
      points.some(
        (point) =>
          !point ||
          !Number.isFinite(point.latitude) ||
          !Number.isFinite(point.longitude) ||
          Math.abs(point.latitude) > 90 ||
          Math.abs(point.longitude) > 180 ||
          !Number.isFinite(point.sequence),
      )
    ) {
      notice.textContent =
        "이 산책로의 위치 정보를 확인할 수 없어요. 다시 추천받아주세요.";
      notice.hidden = false;
      return;
    }
    try {
      if (!window.kakao?.maps?.Map) throw new Error("Map SDK unavailable");
      const sorted = [...points].sort((a, b) => a.sequence - b.sequence);
      const path = sorted.map(
        (point) => new kakao.maps.LatLng(point.latitude, point.longitude),
      );
      container.hidden = false;
      const map = new kakao.maps.Map(container, { center: path[0], level: 4 });
      new kakao.maps.Polyline({
        map,
        path,
        strokeWeight: 5,
        strokeColor: "#2f8060",
        strokeOpacity: 0.9,
        strokeStyle: "solid",
      });
      const bounds = new kakao.maps.LatLngBounds();
      path.forEach((point) => bounds.extend(point));
      const fitRoute = () => {
        map.relayout();
        map.setBounds(bounds, 60, 40, 40, 40);
      };
      fitRoute();
      function mark(position, label) {
        new kakao.maps.Marker({ map, position, title: label });
        const text = document.createElement("span");
        text.className = "tag";
        text.style.cssText =
          "background:white;border:1px solid #2f8060;white-space:nowrap;transform:translateY(-42px);";
        text.textContent = label;
        new kakao.maps.CustomOverlay({
          map,
          position,
          content: text,
          yAnchor: 1,
        });
      }
      const first = sorted[0];
      const last = sorted[sorted.length - 1];
      const roundTrip =
        Math.abs(first.latitude - last.latitude) < 0.000001 &&
        Math.abs(first.longitude - last.longitude) < 0.000001;
      mark(path[0], roundTrip ? "시작 · 도착" : "시작");
      if (!roundTrip) mark(path[path.length - 1], "도착");
      window.addEventListener("resize", fitRoute);
    } catch (error) {
      console.error("추천 경로 지도 표시 실패:", error);
      container.hidden = true;
      notice.textContent =
        "지도를 불러오지 못했어요. 잠시 후 다시 확인해주세요.";
      notice.hidden = false;
    }
  }

  function showMap(course) {
    const container = document.querySelector("#map");
    const notice = document.querySelector("#course-map-status");
    const latitude = course.startLatitude;
    const longitude = course.startLongitude;
    if (
      !Number.isFinite(latitude) ||
      !Number.isFinite(longitude) ||
      Math.abs(latitude) > 90 ||
      Math.abs(longitude) > 180
    ) {
      notice.textContent = "이 산책로의 위치 정보가 아직 없어요.";
      notice.hidden = false;
      return;
    }
    try {
      if (!window.kakao?.maps?.Map) throw new Error("Map SDK unavailable");
      container.hidden = false;
      const position = new kakao.maps.LatLng(latitude, longitude);
      const map = new kakao.maps.Map(container, { center: position, level: 3 });
      new kakao.maps.Marker({
        map,
        position,
        title: course.courseName ?? "산책로 시작점",
      });
    } catch (error) {
      console.error("코스 지도 표시 실패:", error);
      container.hidden = true;
      notice.textContent =
        "지도를 불러오지 못했어요. 잠시 후 다시 확인해주세요.";
      notice.hidden = false;
    }
  }

  async function loadCourse() {
    if (!isRecommended && (!courseId || !/^[1-9]\d*$/.test(courseId))) {
      status.textContent = "목록에서 확인할 산책로를 선택해주세요.";
      page.setAttribute("aria-busy", "false");
      return;
    }
    try {
      let course;
      if (isRecommended) {
        const key = params.get("route");
        let route;
        try {
          if (!key || !/^[a-zA-Z0-9-]+$/.test(key))
            throw new Error("Invalid route key");
          route = JSON.parse(sessionStorage.getItem(`walk-route:${key}`));
          if (!route || typeof route !== "object" || Array.isArray(route))
            throw new Error("Missing route");
        } catch (error) {
          console.error("추천 정보 조회 실패:", error);
          status.textContent =
            "추천 정보가 없어요. 목록에서 산책로를 다시 선택해주세요.";
          return;
        }
        course = {
          courseName: route.title || "추천 산책로",
          description: route.description,
          distanceM: route.distance_m,
          estimatedMinutes: route.estimated_minutes,
          feature: "내 주변 추천 코스",
          points: route.points,
        };
      } else {
        const response = await fetch(
          `${page.dataset.apiBase}${encodeURIComponent(courseId)}`,
          {
            headers: { Accept: "application/json" },
          },
        );
        if (response.status === 404) {
          status.textContent =
            "해당 산책로를 찾을 수 없어요. 다른 산책로를 선택해주세요.";
          return;
        }
        if (!response.ok || response.redirected)
          throw new Error(`Course request failed: ${response.status}`);
        course = await response.json();
        if (
          !course ||
          Array.isArray(course) ||
          typeof course !== "object" ||
          course.courseId == null
        ) {
          throw new Error("Invalid course response");
        }
      }

      document.querySelector("#course-name").textContent =
        course.courseName ?? "추천 산책로";
      document.querySelector("#course-feature").textContent =
        course.feature || "추천 코스";
      const region = document.querySelector("#course-region");
      region.textContent = course.region ?? "";
      region.hidden = !course.region;
      document.querySelector("#course-distance").textContent = Number.isFinite(
        course.distanceM,
      )
        ? `거리 · ${(course.distanceM / 1000).toFixed(1)}km`
        : "거리 · 정보 없음";
      document.querySelector("#course-duration").textContent = Number.isFinite(
        course.estimatedMinutes,
      )
        ? `예상 소요시간 · 약 ${course.estimatedMinutes}분`
        : "예상 소요시간 · 정보 없음";
      document.querySelector("#course-description").textContent =
        course.description || "등록된 설명이 없습니다.";
      document.title = `${course.courseName ?? "산책로 상세"} — 멍자국`;
      content.hidden = false;
      loadedCourse = course;
      shareButton.disabled = saved || !isRecommended;
      if (shareButton.disabled) shareButton.textContent = "공유 완료";
      status.hidden = true;
      if (isRecommended) showRecommendedMap(course.points);
      else showMap(course);
    } catch (error) {
      console.error("산책로 상세 조회 실패:", error);
      status.textContent =
        "산책로 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.";
    } finally {
      page.setAttribute("aria-busy", "false");
    }
  }

  loadCourse();
})();
