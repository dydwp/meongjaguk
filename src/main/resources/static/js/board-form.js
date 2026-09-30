// 추천 산책로 등록 (/board/new?route={key} 또는 /board/new?courseId={id})
// 추천받은 새 코스는 게시글 등록 시 코스 정보를 함께 보내 한 번에 저장
// 게시글 수정 (/board/edit?meetingId={id}): 같은 화면에 기존 값을 채우고 PUT으로 저장 (코스는 변경 불가)
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
  const meetingId = params.get("meetingId");
  const editMode = meetingId != null;
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

  function actionText() {
    return editMode ? "수정" : "등록";
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

  // 추천받은 코스의 경로 좌표: 게시판 지도에 경로를 그리기 위해 함께 저장 (추가: 김환중)
  // - 형식이 맞지 않으면 null (서버는 좌표 없이 출발 지점만 저장)
  function loadRecommendedPoints() {
    try {
      const route = JSON.parse(sessionStorage.getItem(`walk-route:${routeKey}`));
      const points = Array.isArray(route?.points) ? route.points : [];
      const valid =
        points.length >= 2 &&
        points.every(
          (point) =>
            point &&
            Number.isFinite(point.sequence) &&
            Number.isFinite(point.latitude) &&
            Number.isFinite(point.longitude),
        );
      if (!valid) return null;
      return points.map((point) => ({
        sequence: point.sequence,
        latitude: point.latitude,
        longitude: point.longitude,
      }));
    } catch (error) {
      console.error("추천 경로 좌표 조회 실패:", error);
      return null;
    }
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

  // 수정: 기존 게시글을 불러와 입력칸을 채움 (작성자가 아니면 안내만)
  async function loadMeeting() {
    if (!/^[1-9]\d*$/.test(meetingId)) return "missing";
    const response = await fetch(
      `${page.dataset.meetingApi}${encodeURIComponent(meetingId)}`,
      { headers: { Accept: "application/json" } },
    );
    if (response.status === 404) return "missing";
    if (!response.ok || response.redirected)
      throw new Error(`Meeting request failed: ${response.status}`);
    const meeting = await response.json();
    if (!meeting.isHost) return "forbidden";

    const fields = form.elements;
    fields.title.value = meeting.title ?? "";
    fields.meetingDate.value = meeting.meetingDate ?? "";
    fields.meetingTime.value = (meeting.meetingTime ?? "").slice(0, 5);
    fields.maxParticipants.value = meeting.maxParticipants ?? "";
    fields.petRequired.checked = !!meeting.petRequired;
    fields.participationCondition.value = meeting.participationCondition ?? "";
    fields.description.value = meeting.description ?? "";
    showCourse({
      name: meeting.courseName,
      distanceM: meeting.distanceM,
      estimatedMinutes: meeting.estimatedMinutes,
      feature: "공유된 코스",
    });
    return "ok";
  }

  function detailHref() {
    return `${page.dataset.detailUrl}?meetingId=${encodeURIComponent(meetingId)}`;
  }

  async function loadCourse() {
    try {
      if (editMode) {
        document.title = "멍자국 — 산책로 게시글 수정";
        document.querySelector("#board-form-heading").textContent = "산책로 게시글 수정";
        document.querySelector("#board-form-sub").textContent =
          "모집 정보를 수정할 수 있어요 (코스는 바꿀 수 없어요)";
        submitButton.textContent = "수정";
        backLink.textContent = "산책로 게시판으로";
        backLink.href = "/board";
        const result = await loadMeeting();
        if (result !== "ok") {
          status.textContent =
            result === "forbidden"
              ? "본인이 작성한 글만 수정할 수 있어요."
              : "모집 정보를 찾을 수 없어요.";
          backLink.hidden = false;
          return;
        }
        courseSource = {};
        cancelLink.href = detailHref();
      } else if (routeKey) {
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
        courseSource = { course, points: loadRecommendedPoints() };
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
    submitButton.textContent = `${actionText()} 중…`;
    let done = false;
    try {
      const headers = {
        "Content-Type": "application/json",
        Accept: "application/json",
      };
      if (page.dataset.csrfHeader && page.dataset.csrfToken) {
        headers[page.dataset.csrfHeader] = page.dataset.csrfToken;
      }
      const response = await fetch(
        editMode ? `${page.dataset.meetingApi}${encodeURIComponent(meetingId)}` : page.dataset.submitUrl,
        {
        method: editMode ? "PUT" : "POST",
        credentials: "same-origin",
        headers,
        body: JSON.stringify({ ...courseSource, ...values }),
        },
      );
      if (response.redirected || response.status === 401) {
        showError(`로그인이 필요해요. 로그인 후 다시 ${actionText()}해주세요.`);
        return;
      }
      if (response.status === 403) {
        showError("요청이 만료됐어요. 새로고침 후 다시 시도해주세요.");
        return;
      }
      const data = await response.json().catch(() => null);
      if (response.status !== (editMode ? 200 : 201) || data?.meetingId == null) {
        showError(data?.message || `${actionText()}하지 못했어요. 잠시 후 다시 시도해주세요.`);
        return;
      }
      done = true;
      // 뒤로 가기로 입력 화면에 돌아와 다시 제출하지 않도록 replace
      window.location.replace(
        `${page.dataset.detailUrl}?meetingId=${encodeURIComponent(data.meetingId)}`,
      );
    } catch (error) {
      console.error(`산책로 게시글 ${actionText()} 실패:`, error);
      showError(`${actionText()}하지 못했어요. 잠시 후 다시 시도해주세요.`);
    } finally {
      if (!done) {
        submitting = false;
        submitButton.disabled = false;
        submitButton.textContent = actionText();
      }
    }
  });

  loadCourse();
})();
