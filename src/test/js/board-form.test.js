// 추천 산책로 등록 (board-form.js) - board/form.html
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { openPage, template, response, settle } = require("./support/browser");

const ROUTE = {
  title: "AI 추천 코스",
  description: "공원 한 바퀴",
  distance_m: 1834.4,
  estimated_minutes: 25.2,
  region: "성동구",
  points: [
    { sequence: 2, latitude: 37.545, longitude: 127.044 },
    { sequence: 1, latitude: 37.544, longitude: 127.043 },
  ],
};

function openForm({ query = "?route=r-1", session = { "walk-route:r-1": ROUTE }, fetch } = {}) {
  return openPage({
    html: template("board/form.html"),
    url: "http://localhost:8081/board/new" + query,
    scripts: ["board-form.js"],
    sessionStorage: session,
    fetch,
    setup(window) {
      const page = window.document.querySelector("#board-form-page");
      Object.assign(page.dataset, {
        apiBase: "/api/courses/", submitUrl: "/api/meetings", detailUrl: "/course-detail-shared",
        courseDetailUrl: "/course-detail", csrfHeader: "X-CSRF-TOKEN", csrfToken: "token-123",
      });
    },
  });
}

function fill(page, values) {
  const f = page.$("#board-form").elements;
  for (const [name, value] of Object.entries(values)) {
    if (typeof value === "boolean") f[name].checked = value;
    else f[name].value = value;
  }
}

function submit(page) {
  page.$("#board-form").dispatchEvent(new page.window.Event("submit", { bubbles: true, cancelable: true }));
}

const VALID = { title: "주말 산책", meetingDate: "2099-10-03", meetingTime: "10:00", maxParticipants: "4" };

test("추천받은 코스 정보를 보여주고 입력 폼을 연다", async () => {
  const page = openForm();
  await settle();

  assert.equal(page.$("#board-form").hidden, false);
  assert.equal(page.$("#board-form-course-name").textContent, "AI 추천 코스");
  assert.equal(page.$("#board-form-feature").textContent, "내 주변 추천 코스");
  assert.equal(page.$("#board-form-region").textContent, "성동구");
  assert.equal(page.$("#board-form-distance").textContent, "거리 · 1.8km");
  assert.equal(page.$("#board-form-duration").textContent, "예상 소요시간 · 약 25분");
  assert.match(page.$("#board-date").min, /^\d{4}-\d{2}-\d{2}$/); // 오늘 이전 날짜 막기
  assert.equal(page.$("#board-form-cancel").getAttribute("href"), "/course-detail?route=r-1");
  page.close();
});

test("추천 정보가 없거나 산책로를 고르지 않았으면 안내하고 폼을 열지 않는다", async () => {
  for (const [query, message] of [
    ["?route=missing", "추천 정보가 없어요. 목록에서 산책로를 다시 선택해주세요."],
    ["", "공유할 산책로를 먼저 선택해주세요."],
    ["?courseId=abc", "공유할 산책로를 먼저 선택해주세요."],
  ]) {
    const page = openForm({ query });
    await settle();
    assert.equal(page.$("#board-form-status").textContent, message);
    assert.equal(page.$("#board-form").hidden, true);
    assert.equal(page.$("#board-form-back").hidden, false);
    page.close();
  }
});

test("등록된 코스(courseId)는 API로 코스 정보를 가져온다", async () => {
  const page = openForm({
    query: "?courseId=3", session: {},
    fetch: () => response({ courseId: 3, courseName: "한강 코스", distanceM: 2600, estimatedMinutes: 40, feature: "강변", region: null }),
  });
  await settle();

  assert.equal(page.calls.fetch[0].url, "/api/courses/3");
  assert.equal(page.$("#board-form-course-name").textContent, "한강 코스");
  assert.equal(page.$("#board-form-region").hidden, true);
  page.close();

  const missing = openForm({ query: "?courseId=99", session: {}, fetch: () => response({}, { status: 404 }) });
  await settle();
  assert.equal(missing.$("#board-form-status").textContent, "해당 산책로를 찾을 수 없어요. 다른 산책로를 선택해주세요.");
  missing.close();
});

test("서버와 같은 기준으로 입력값을 먼저 확인한다", async () => {
  const page = openForm();
  await settle();
  const error = () => page.$("#board-form-error").textContent;

  const cases = [
    [{ ...VALID, title: "  " }, "제목을 입력해주세요."],
    [{ ...VALID, title: "가".repeat(151) }, "제목은 150자까지 입력할 수 있어요."],
    [{ ...VALID, meetingDate: "" }, "모임 날짜를 선택해주세요."],
    [{ ...VALID, meetingTime: "" }, "모임 시간을 선택해주세요."],
    [{ ...VALID, meetingDate: "2000-01-01" }, "지난 날짜는 선택할 수 없어요."],
    [{ ...VALID, maxParticipants: "" }, "최대 인원을 입력해주세요."],
    [{ ...VALID, maxParticipants: "11" }, "최대 인원은 2명부터 10명까지 설정할 수 있어요."],
    [{ ...VALID, maxParticipants: "2.5" }, "최대 인원은 2명부터 10명까지 설정할 수 있어요."],
    [{ ...VALID, participationCondition: "가".repeat(501) }, "참여 조건은 500자까지 입력할 수 있어요."],
    [{ ...VALID, description: "가".repeat(601) }, "설명은 600자까지 입력할 수 있어요."],
  ];
  for (const [values, message] of cases) {
    fill(page, { participationCondition: "", description: "", ...values });
    submit(page);
    await settle(2);
    assert.equal(error(), message);
    assert.equal(page.$("#board-form-error").hidden, false);
  }
  assert.equal(page.calls.fetch.length, 0);
  page.close();
});

test("등록하면 코스·좌표·입력값을 함께 보내고 상세 화면으로 이동한다", async () => {
  const page = openForm({ fetch: () => response({ meetingId: 77 }, { status: 201 }) });
  await settle();

  fill(page, { ...VALID, title: "  주말 산책  ", petRequired: true, participationCondition: " 소형견 " });
  submit(page);
  await settle();

  const call = page.calls.fetch[0];
  assert.equal(call.url, "/api/meetings");
  assert.equal(call.headers["X-CSRF-TOKEN"], "token-123");
  const body = JSON.parse(call.body);
  assert.equal(body.title, "주말 산책");
  assert.equal(body.maxParticipants, 4);
  assert.equal(body.petRequired, true);
  assert.equal(body.participationCondition, "소형견");
  assert.equal(body.description, null);
  assert.equal(body.course.name, "AI 추천 코스");
  assert.equal(body.course.distanceM, 1834);
  assert.deepEqual([body.course.startLatitude, body.course.startLongitude], [37.544, 127.043]); // sequence 1
  assert.equal(body.points.length, 2);
  assert.deepEqual(page.calls.navigations, ["/course-detail-shared?meetingId=77"]);
  page.close();
});

test("등록 실패 원인별로 안내하고 다시 누를 수 있게 한다", async () => {
  for (const [res, message] of [
    [response({}, { status: 401 }), "로그인이 필요해요. 로그인 후 다시 등록해주세요."],
    [response({}, { status: 403 }), "요청이 만료됐어요. 새로고침 후 다시 시도해주세요."],
    [response({ message: "지난 시간은 선택할 수 없어요." }, { status: 400 }), "지난 시간은 선택할 수 없어요."],
    [new Error("network"), "등록하지 못했어요. 잠시 후 다시 시도해주세요."],
  ]) {
    const page = openForm({ fetch: () => res });
    await settle();
    fill(page, VALID);
    submit(page);
    await settle();

    assert.equal(page.$("#board-form-error").textContent, message);
    assert.equal(page.$("#board-form-submit").disabled, false);
    assert.equal(page.$("#board-form-submit").textContent, "등록");
    assert.deepEqual(page.calls.navigations, []);
    page.close();
  }
});
