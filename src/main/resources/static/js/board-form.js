// 추천 산책로 등록 (/board/new?route={key} 또는 /board/new?courseId={id})
// 추천받은 새 코스는 게시글 등록 시 코스 정보를 함께 보내 한 번에 저장
(() => {
  const page = document.querySelector("#board-form-page");
  if (!page) return;
  const status = document.querySelector("#board-form-status");
  const courseBox = document.querySelector("#board-form-course");
  const form = document.querySelector("#board-form");
  const errorBox = document.querySelector("#board-form-error");
  const submitButton = document.querySelector("#board-form-submit");
  const cancelLink = document.querySelector("#board-form-cancel");
  const backLink = document.querySelector("#board-form-back");
  const params = new URLSearchParams(window.location.search);
  const routeKey = params.get("route");
  const courseId = params.get("courseId");
  let courseSource = null; // { courseId } 또는 { course: {...} }
  let submitting = false;

  const MAX_TITLE = 150;
  const MAX_CONDITION = 500;
  const MAX_DESCRIPTION = 600;
  const MIN_PARTICIPANTS = 2;
  const MAX_PARTICIPANTS = 10;

  function pad(value) {
    return String(value).padStart(2, "0");
  }

  function todayText() {
    const now = new Date();
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  }

  function nowTimeText() {
    const now = new Date();
    return `${pad(now.getHours())}:${pad(now.getMinutes())}`;
  }

  function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
  }

  function hideError() {
    errorBox.textContent = "";
    errorBox.hidden = true;
  }

  function showCourse(course) {
    document.querySelector("#board-form-feature").textContent =
      course.feature || "추천 코스";
    document.querySelector("#board-form-course-name").textContent =
      course.name || "추천 산책로";
    const region = document.querySelector("#board-form-region");
    region.textContent = course.region ?? "";
    region.hidden = !course.region;
    document.querySelector("#board-form-distance").textContent =
      Number.isFinite(course.distanceM)
        ? `거리 · ${(course.distanceM / 1000).toFixed(1)}km`
        : "거리 · 정보 없음";
    document.querySelector("#board-form-duration").textContent =
      Number.isFinite(course.estimatedMinutes)
        ? `예상 소요시간 · 약 ${course.estimatedMinutes}분`
        : "예상 소요시간 · 정보 없음";
    courseBox.hidden = false;
  }

  // 추천받은 코스: 상세 화면과 같은 sessionStorage 값을 사용
  function loadRecommendedCourse() {
    if (!/^[a-zA-Z0-9-]+$/.test(routeKey)) return null;
    const route = JSON.parse(sessionStorage.getItem(`walk-route:${routeKey}`));
    if (!route || typeof route !== "object" || Array.isArray(route)) return null;
    const first = [...(Array.isArray(route.points) ? route.points : [])].sort(
      (a, b) => a.sequence - b.sequence,
    )[0];
    if (
      !first ||
      !Number.isFinite(first.latitude) ||
      !Number.isFinite(first.longitude) ||
      !Number.isFinite(route.distance_m) ||
      !Number.isFinite(route.estimated_minutes)
    ) {
      return null;
    }
    return {
      name: route.title || "추천 산책로",
      description: route.description ?? null,
      distanceM: Math.round(route.distance_m),
      estimatedMinutes: Math.round(route.estimated_minutes),
      feature: "내 주변 추천 코스",
      region: route.region ?? null,
      startLatitude: first.latitude,
      startLongitude: first.longitude,
      thumbnailImg: null,
    };
  }

  // DB에 있는 코스: 기존 코스 조회 API 사용
  async function loadSavedCourse() {
    const response = await fetch(
      `${page.dataset.apiBase}${encodeURIComponent(courseId)}`,
      { headers: { Accept: "application/json" } },
    );
    if (response.status === 404) return null;
    if (!response.ok || response.redirected)
      throw new Error(`Course request failed: ${response.status}`);
    const course = await response.json();
    if (!course || typeof course !== "object" || course.courseId == null)
      throw new Error("Invalid course response");
    return {
      name: course.courseName,
      distanceM: course.distanceM,
      estimatedMinutes: course.estimatedMinutes,
      feature: course.feature,
      region: course.region,
    };
  }

  async function loadCourse() {
    try {
      if (routeKey) {
        let course = null;
        try {
          course = loadRecommendedCourse();
        } catch (error) {
          console.error("추천 정보 조회 실패:", error);
        }
        if (!course) {
          status.textContent =
            "추천 정보가 없어요. 목록에서 산책로를 다시 선택해주세요.";
          backLink.hidden = false;
          return;
        }
        courseSource = { course };
        cancelLink.href = `${page.dataset.courseDetailUrl}?route=${encodeURIComponent(routeKey)}`;
        showCourse(course);
      } else if (courseId && /^[1-9]\d*$/.test(courseId)) {
        const course = await loadSavedCourse();
        if (!course) {
          status.textContent =
            "해당 산책로를 찾을 수 없어요. 다른 산책로를 선택해주세요.";
          backLink.hidden = false;
          return;
        }
        courseSource = { courseId: Number(courseId) };
        cancelLink.href = `${page.dataset.courseDetailUrl}?courseId=${encodeURIComponent(courseId)}`;
        showCourse(course);
      } else {
        status.textContent = "공유할 산책로를 먼저 선택해주세요.";
        backLink.hidden = false;
        return;
      }
      document.querySelector("#board-date").min = todayText();
      status.hidden = true;
      form.hidden = false;
    } catch (error) {
      console.error("산책로 정보 조회 실패:", error);
      status.textContent =
        "산책로 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.";
      backLink.hidden = false;
    } finally {
      page.setAttribute("aria-busy", "false");
    }
  }

  // 서버와 같은 기준으로 먼저 확인 (최종 검증은 서버에서)
  function validate(values) {
    if (!values.title) return "제목을 입력해주세요.";
    if (values.title.length > MAX_TITLE)
      return `제목은 ${MAX_TITLE}자까지 입력할 수 있어요.`;
    if (!values.meetingDate) return "모임 날짜를 선택해주세요.";
    if (!values.meetingTime) return "모임 시간을 선택해주세요.";
    const today = todayText();
    if (values.meetingDate < today) return "지난 날짜는 선택할 수 없어요.";
    if (values.meetingDate === today && values.meetingTime <= nowTimeText())
      return "지난 시간은 선택할 수 없어요.";
    if (values.maxParticipants == null) return "최대 인원을 입력해주세요.";
    if (
      !Number.isInteger(values.maxParticipants) ||
      values.maxParticipants < MIN_PARTICIPANTS ||
      values.maxParticipants > MAX_PARTICIPANTS
    )
      return `최대 인원은 ${MIN_PARTICIPANTS}명부터 ${MAX_PARTICIPANTS}명까지 설정할 수 있어요.`;
    if (
      values.participationCondition &&
      values.participationCondition.length > MAX_CONDITION
    )
      return `참여 조건은 ${MAX_CONDITION}자까지 입력할 수 있어요.`;
    if (values.description && values.description.length > MAX_DESCRIPTION)
      return `설명은 ${MAX_DESCRIPTION}자까지 입력할 수 있어요.`;
    return null;
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!courseSource || submitting) return;
    hideError();

    const fields = form.elements;
    const maxText = fields.maxParticipants.value.trim();
    const values = {
      title: fields.title.value.trim(),
      meetingDate: fields.meetingDate.value || null,
      meetingTime: fields.meetingTime.value || null,
      maxParticipants: maxText === "" ? null : Number(maxText),
      petRequired: fields.petRequired.checked,
      participationCondition: fields.participationCondition.value.trim() || null,
      description: fields.description.value.trim() || null,
    };
    const message = validate(values);
    if (message) {
      showError(message);
      return;
    }

    submitting = true;
    submitButton.disabled = true;
    submitButton.textContent = "등록 중…";
    let done = false;
    try {
      const headers = {
        "Content-Type": "application/json",
        Accept: "application/json",
      };
      if (page.dataset.csrfHeader && page.dataset.csrfToken) {
        headers[page.dataset.csrfHeader] = page.dataset.csrfToken;
      }
      const response = await fetch(page.dataset.submitUrl, {
        method: "POST",
        credentials: "same-origin",
        headers,
        body: JSON.stringify({ ...courseSource, ...values }),
      });
      if (response.redirected || response.status === 401) {
        showError("로그인이 필요해요. 로그인 후 다시 등록해주세요.");
        return;
      }
      if (response.status === 403) {
        showError("요청이 만료됐어요. 새로고침 후 다시 시도해주세요.");
        return;
      }
      const data = await response.json().catch(() => null);
      if (response.status !== 201 || data?.meetingId == null) {
        showError(data?.message || "등록하지 못했어요. 잠시 후 다시 시도해주세요.");
        return;
      }
      done = true;
      // 뒤로 가기로 등록 화면에 돌아와 다시 제출하지 않도록 replace
      window.location.replace(
        `${page.dataset.detailUrl}?meetingId=${encodeURIComponent(data.meetingId)}`,
      );
    } catch (error) {
      console.error("산책로 게시글 등록 실패:", error);
      showError("등록하지 못했어요. 잠시 후 다시 시도해주세요.");
    } finally {
      if (!done) {
        submitting = false;
        submitButton.disabled = false;
        submitButton.textContent = "등록";
      }
    }
  });

  loadCourse();
})();
