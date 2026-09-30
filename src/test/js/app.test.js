// 산책 시작·반려견 선택·기록·종료 (app.js) - index.html(메인), walk/record.html(산책 기록 화면)
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { openPage, template, response, settle } = require("./support/browser");

const CSRF_META = '<meta name="_csrf" content="token-123"><meta name="_csrf_header" content="X-CSRF-TOKEN">';
const KEYS = {
  active: "mungjaguk-walk-active",
  start: "mungjaguk-walk-start",
  points: "mungjaguk-walk-points",
  planned: "mungjaguk-walk-planned",
  petIds: "mungjaguk-walk-pet-ids",
  checklist: "mungjaguk-walk-checklist",
};

const BORI = { id: 1, name: "보리", breed: "말티즈", sizeLabel: "소형견", ageInYears: 3, profileImage: null, activityLevelLabel: "높음" };
const CHOCO = { id: 2, name: "초코", breed: "푸들", sizeLabel: "소형견", ageInYears: 2, profileImage: "/images/pets/choco.png", activityLevelLabel: "" };

/**
 * 서버 흉내
 * - pets : GET /api/pet-profile/pets 응답 (반려견 선택 창에 쓰임, 기본은 반려견 없음)
 * - walks: POST /api/walks 응답
 */
function server({ pets = [], walks = () => response({ walkRecordId: 42 }) } = {}) {
  return (url, call) => {
    if (url === "/api/pet-profile/pets") return pets instanceof Error || typeof pets.json === "function" ? pets : response(pets);
    if (url === "/api/walks") return walks(call);
    throw new Error("unexpected " + url);
  };
}

/** 메인 화면 (loggedIn=false면 비회원: data-login-required="true") */
function openHome({ loggedIn = true, fetch = server(), confirm, localStorage } = {}) {
  return openPage({
    html: template("index.html").replace("<head>", "<head>" + CSRF_META),
    scripts: ["app.js"],
    fetch,
    confirm,
    localStorage,
    setup(window) {
      window.document.querySelectorAll("[data-start-walk], [data-end-walk]").forEach((btn) =>
        btn.setAttribute("data-login-required", String(!loggedIn)));
    },
  });
}

/** 산책 시작 버튼 → (반려견 선택) → 산책 시작까지 */
async function startWalk(page) {
  page.click("[data-start-walk]");
  await settle();
}

function gps(page, lat, lng, accuracy = 10) {
  page.geo.watchers.at(-1).success({ coords: { latitude: lat, longitude: lng, accuracy }, timestamp: Date.now() });
}

const visible = (page, selector) => page.$$(selector).every((el) => !el.hidden);
const hidden = (page, selector) => page.$$(selector).every((el) => el.hidden);
const walking = (page) => page.window.localStorage.getItem(KEYS.active) === "true";
const walkingState = (extra = {}) => ({ [KEYS.active]: "true", [KEYS.start]: String(Date.now()), [KEYS.points]: "[]", ...extra });

test("비회원이 산책 시작을 누르면 로그인 안내 후 확인 시 로그인 화면으로 이동한다", () => {
  const cancel = openHome({ loggedIn: false, confirm: false });
  cancel.click("[data-start-walk]");
  assert.equal(cancel.calls.confirms.length, 1);
  assert.deepEqual(cancel.calls.navigations, []);
  assert.equal(cancel.calls.fetch.length, 0);
  assert.equal(cancel.window.localStorage.getItem(KEYS.active), null);
  cancel.close();

  const ok = openHome({ loggedIn: false, confirm: true });
  ok.click("[data-start-walk]");
  assert.deepEqual(ok.calls.navigations, ["/login"]);
  ok.close();
});

test("등록한 반려견이 없으면 선택 창 없이 바로 산책을 시작하고 GPS 추적을 시작한다", async () => {
  const page = openHome();
  assert.ok(visible(page, "[data-walk-idle]"));
  assert.ok(hidden(page, "[data-walk-active]"));

  await startWalk(page);

  const storage = page.window.localStorage;
  assert.equal(page.$(".walk-pet-modal"), null);
  assert.equal(storage.getItem(KEYS.active), "true");
  assert.ok(Number(storage.getItem(KEYS.start)) > 0);
  assert.equal(storage.getItem(KEYS.points), "[]");
  assert.equal(storage.getItem(KEYS.petIds), "[]");
  assert.ok(hidden(page, "[data-walk-idle]"));
  assert.ok(visible(page, "[data-walk-active]"));
  assert.equal(page.geo.watchers.length, 1);
  page.close();
});

// ---------- 함께 산책할 반려견 선택 ----------

test("반려견이 있으면 선택 창을 띄우고, 고른 반려견과 함께 산책을 시작한다", async () => {
  const page = openHome({ fetch: server({ pets: [BORI, CHOCO] }) });
  await startWalk(page);

  const modal = page.$(".walk-pet-modal");
  assert.ok(modal, "반려견 선택 창");
  assert.equal(modal.getAttribute("role"), "dialog");
  assert.equal(walking(page), false, "고르기 전에는 시작하지 않음");
  const options = page.$$(".walk-pet-option");
  assert.deepEqual(options.map((o) => o.querySelector("strong").textContent), ["보리", "초코"]);
  assert.equal(options[0].querySelector(".walk-pet-option-info span").textContent, "말티즈 · 소형견 · 3세");
  assert.equal(options[0].querySelector(".walk-pet-option-photo").textContent, "🐾");
  assert.equal(options[1].querySelector("img").getAttribute("src"), "/images/pets/choco.png");

  options[1].querySelector("input").checked = true;
  page.click([...modal.querySelectorAll("button")].find((b) => b.textContent === "산책 시작"));
  await settle();

  assert.equal(page.$(".walk-pet-modal-overlay"), null, "창 닫힘");
  assert.equal(walking(page), true);
  assert.equal(page.window.localStorage.getItem(KEYS.petIds), "[2]");
  // 메인 "지금 산책" 카드에 함께 걷는 반려견 이름
  assert.equal(page.$("[data-walk-pet-summary]").hidden, false);
  assert.equal(page.$("[data-walk-pet-names]").textContent, "초코");
  page.close();
});

test("반려견을 하나도 고르지 않아도 산책할 수 있고, 취소하거나 바깥을 누르면 시작하지 않는다", async () => {
  const none = openHome({ fetch: server({ pets: [BORI] }) });
  await startWalk(none);
  none.click([...none.$$(".walk-pet-modal button")].find((b) => b.textContent === "산책 시작"));
  await settle();
  assert.equal(walking(none), true);
  assert.equal(none.window.localStorage.getItem(KEYS.petIds), "[]");
  assert.equal(none.$("[data-walk-pet-summary]").hidden, true);
  none.close();

  const cancel = openHome({ fetch: server({ pets: [BORI] }) });
  await startWalk(cancel);
  cancel.click([...cancel.$$(".walk-pet-modal button")].find((b) => b.textContent === "취소"));
  await settle();
  assert.equal(walking(cancel), false);
  assert.equal(cancel.$(".walk-pet-modal-overlay"), null);
  cancel.close();

  const outside = openHome({ fetch: server({ pets: [BORI] }) });
  await startWalk(outside);
  outside.click(".walk-pet-modal-overlay");
  await settle();
  assert.equal(walking(outside), false);
  outside.close();
});

test("반려견 목록을 불러오지 못하면 알리고 산책을 시작하지 않는다", async () => {
  const page = openHome({ fetch: server({ pets: response({}, { status: 500 }) }) });
  await startWalk(page);

  assert.deepEqual(page.calls.alerts, ["반려견 정보를 불러오지 못했습니다."]);
  assert.equal(walking(page), false);
  page.close();
});

test("새로고침해도 산책 중이면 함께 걷는 반려견 이름을 다시 보여준다", async () => {
  const page = openHome({
    fetch: server({ pets: [BORI, CHOCO] }),
    localStorage: walkingState({ [KEYS.petIds]: "[1,2]" }),
  });
  await settle();

  assert.equal(page.$("[data-walk-pet-names]").textContent, "보리 · 초코");
  assert.equal(page.$("[data-walk-pet-summary]").hidden, false);
  page.close();
});

// ---------- GPS / 경과 시간 ----------

test("GPS 좌표는 정확도가 나쁘거나(200m 초과) 5m 미만으로 움직이면 버리고, 걸은 거리를 계산한다", async () => {
  const page = openHome();
  await startWalk(page);
  const moved = [];
  page.window.addEventListener("walk:position", (e) => moved.push(e.detail));

  gps(page, 37.5, 127.0, 500);          // 정확도 나쁨 → 버림
  gps(page, 37.5, 127.0);               // 첫 좌표
  gps(page, 37.50001, 127.0);           // 약 1m → 버림
  gps(page, 37.501, 127.0);             // 약 111m 북쪽

  const points = JSON.parse(page.window.localStorage.getItem(KEYS.points));
  assert.equal(points.length, 2);
  assert.deepEqual([points[1].lat, points[1].lng], [37.501, 127.0]);
  assert.equal(page.$("[data-walk-distance-num]").textContent, "0.11");
  assert.equal(moved.length, 4); // 지도에는 받은 위치마다 알림
  page.close();
});

test("위치 권한을 거부하면 안내 문구를 보여준다", async () => {
  const page = openPage({
    html: template("walk/record.html"),
    url: "http://localhost:8081/walk-record",
    scripts: ["app.js"],
    fetch: server(),
  });
  await settle();
  page.geo.watchers[0].error({ code: 1, PERMISSION_DENIED: 1 });

  assert.equal(page.$("[data-walk-chip]").textContent, "위치 권한을 허용해야 거리가 기록돼요");
  page.close();
});

test("경과 시간은 저장된 시작 시각 기준으로 mm:ss 로 표시한다", () => {
  const page = openHome({ localStorage: walkingState({ [KEYS.start]: String(Date.now() - 65_000) }) });

  assert.equal(page.$("[data-walk-elapsed]").textContent, "01:05");
  assert.ok(visible(page, "[data-walk-active]")); // 새로고침해도 산책 중 유지
  page.close();
});

// ---------- 산책 종료 ----------

test("회원이 산책을 종료하면 CSRF 토큰과 함께 기록·반려견을 저장하고 산책 상태를 모두 지운다", async () => {
  const page = openHome({ fetch: server({ pets: [BORI, CHOCO] }) });
  await startWalk(page);
  page.$$(".walk-pet-option input").forEach((input) => { input.checked = true; });
  page.click([...page.$$(".walk-pet-modal button")].find((b) => b.textContent === "산책 시작"));
  await settle();
  gps(page, 37.5, 127.0);
  gps(page, 37.501, 127.0);
  page.window.localStorage.setItem(KEYS.checklist, "{}");
  let stopped = 0;
  page.window.addEventListener("walk:stopped", () => stopped++);

  page.click("[data-end-walk]");
  await settle();

  const call = page.calls.fetch.find((c) => c.url === "/api/walks");
  assert.equal(call.method, "POST");
  assert.equal(call.headers["X-CSRF-TOKEN"], "token-123");
  const body = JSON.parse(call.body);
  assert.equal(body.courseId, null);
  assert.equal(body.points.length, 2);
  assert.equal(body.distanceM, 111);
  assert.ok(body.endedAt >= body.startedAt);
  assert.equal(body.recommendedRoute, null);
  assert.deepEqual(body.petIds, [1, 2]);

  assert.deepEqual(page.calls.alerts, ["산책 기록을 저장했어요!"]);
  for (const key of Object.values(KEYS)) {
    assert.ok([null, "false"].includes(page.window.localStorage.getItem(key)), key);
  }
  assert.equal(stopped, 1);
  assert.deepEqual(page.geo.cleared, [1]);
  assert.ok(visible(page, "[data-walk-idle]"));
  assert.equal(page.$("[data-walk-pet-summary]").hidden, true);
  page.close();
});

test("기록 저장에 실패하면 산책을 유지해서 다시 누를 수 있다", async () => {
  const page = openHome({ fetch: server({ walks: () => response({ message: "오류" }, { status: 500 }) }) });
  await startWalk(page);

  page.click("[data-end-walk]");
  await settle();

  assert.deepEqual(page.calls.alerts, ["산책 기록 저장에 실패했어요. 잠시 후 다시 눌러주세요."]);
  assert.equal(walking(page), true);
  assert.equal(page.$("[data-end-walk]").disabled, false);
  page.close();
});

test("비회원이 산책을 종료할 때: 로그인을 고르면 산책을 유지하고, 취소하면 저장 없이 끝낸다", async () => {
  const goLogin = openHome({ loggedIn: false, confirm: true, localStorage: walkingState() });
  goLogin.click("[data-end-walk]");
  assert.deepEqual(goLogin.calls.navigations, ["/login"]);
  assert.equal(walking(goLogin), true);
  goLogin.close();

  const discard = openHome({ loggedIn: false, confirm: false, localStorage: walkingState() });
  discard.click("[data-end-walk]");
  await settle();
  assert.equal(discard.calls.fetch.filter((c) => c.url === "/api/walks").length, 0);
  assert.equal(discard.window.localStorage.getItem(KEYS.active), "false");
  discard.close();
});

// ---------- 산책 기록 화면 (/walk-record) ----------

const ROUTE = {
  title: "  서울숲 한 바퀴  ",
  description: "공원을 따라 걷는 코스",
  distance_m: 2345.6,
  estimated_minutes: 34.6,
  points: [
    { sequence: 1, latitude: 37.544, longitude: 127.043 },
    { sequence: 2, latitude: 37.545, longitude: 127.044 },
  ],
};

function openRecord(url, { sessionStorage, fetch = server(), localStorage } = {}) {
  return openPage({
    html: template("walk/record.html").replace("<head>", "<head>" + CSRF_META),
    url,
    scripts: ["app.js"],
    sessionStorage,
    localStorage,
    fetch,
  });
}

test("산책 기록 화면은 열자마자 반려견을 고르고 산책을 시작하며, 함께 걷는 반려견 카드를 보여준다", async () => {
  const page = openRecord("http://localhost:8081/walk-record", { fetch: server({ pets: [BORI, CHOCO] }) });
  await settle();

  assert.ok(page.$(".walk-pet-modal"), "들어오자마자 선택 창");
  page.$$(".walk-pet-option input")[0].checked = true;
  page.click([...page.$$(".walk-pet-modal button")].find((b) => b.textContent === "산책 시작"));
  await settle();

  assert.equal(walking(page), true);
  assert.equal(page.$("[data-walk-pet-detail]").hidden, false);
  const cards = page.$$(".walk-current-pet-card");
  assert.equal(cards.length, 1);
  assert.equal(cards[0].querySelector("strong").textContent, "보리");
  assert.equal(cards[0].querySelector(".walk-current-pet-activity").textContent, "활동성 높음");
  page.close();
});

test("추천 산책로에서 시작하면 추천 경로를 함께 저장해서 기록에 보낸다", async () => {
  const page = openRecord("http://localhost:8081/walk-record?route=abc-1", {
    sessionStorage: { "walk-route:abc-1": ROUTE },
  });
  await settle();

  assert.equal(walking(page), true);
  const planned = JSON.parse(page.window.localStorage.getItem(KEYS.planned));
  assert.equal(planned.title, "서울숲 한 바퀴");
  assert.equal(planned.distanceM, 2346);
  assert.equal(planned.estimatedMinutes, 35);

  page.click("[data-end-walk]");
  await settle();

  const body = JSON.parse(page.calls.fetch.find((c) => c.url === "/api/walks").body);
  assert.equal(body.recommendedRoute.points.length, 2);
  assert.deepEqual(page.calls.navigations, ["/"]); // data-end-redirect
  page.close();
});

test("추천 경로 정보가 잘못됐으면 자유 산책으로 기록한다", async () => {
  const broken = { ...ROUTE, points: [ROUTE.points[0]] }; // 좌표 1개
  const page = openRecord("http://localhost:8081/walk-record?route=abc-1", { sessionStorage: { "walk-route:abc-1": broken } });
  await settle();
  assert.equal(walking(page), true);
  assert.equal(page.window.localStorage.getItem(KEYS.planned), null);
  page.close();

  const badKey = openRecord("http://localhost:8081/walk-record?route=../../x", { sessionStorage: { "walk-route:../../x": ROUTE } });
  await settle();
  assert.equal(badKey.window.localStorage.getItem(KEYS.planned), null);
  badKey.close();
});

test("산책 취소는 확인 후 저장 없이 끝내고 지정된 화면으로 이동한다", async () => {
  const keep = openRecord("http://localhost:8081/walk-record");
  await settle();
  keep.window.confirm = () => false;
  keep.click("[data-cancel-walk]");
  assert.equal(walking(keep), true);
  keep.close();

  const page = openRecord("http://localhost:8081/walk-record");
  await settle();
  page.click("[data-cancel-walk]");
  await settle();
  assert.equal(page.calls.fetch.filter((c) => c.url === "/api/walks").length, 0);
  assert.equal(page.window.localStorage.getItem(KEYS.active), "false");
  assert.deepEqual(page.calls.navigations, ["/"]);
  page.close();
});

// ---------- 필(Pill) 선택 ----------

test("크기·활동성 버튼은 그룹 안에서 하나만 선택된다", () => {
  const page = openPage({ html: template("dog/profile.html"), scripts: ["app.js"] });
  const group = page.$('[data-pet-field="size"]');

  page.click(group.querySelector('[data-value="SMALL"]'));
  page.click(group.querySelector('[data-value="LARGE"]'));

  const selected = [...group.querySelectorAll(".pill")].filter((p) => p.getAttribute("aria-pressed") === "true");
  assert.deepEqual(selected.map((p) => p.dataset.value), ["LARGE"]);
  assert.ok(group.querySelector('[data-value="LARGE"]').classList.contains("selected"));
  assert.ok(!group.querySelector('[data-value="SMALL"]').classList.contains("selected"));
  page.close();
});
