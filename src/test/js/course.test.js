// 추천 산책로 목록·상세 (walk-recommend.js, course-detail.js, courses.js) - course/list.html, course/detail.html
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { openPage, template, response, settle } = require("./support/browser");

const HERE = { coords: { latitude: 37.5443, longitude: 127.0374 } };

function route(n, extra = {}) {
  return {
    candidate_id: n, rank: n, title: `추천 코스 ${n}`, description: `설명 ${n}`,
    distance_m: 1000 * n + 234, estimated_minutes: 10 * n,
    points: [{ sequence: 1, latitude: 37.5, longitude: 127.0 }, { sequence: 2, latitude: 37.51, longitude: 127.01 }],
    ...extra,
  };
}
const routes = (from, to) => Array.from({ length: to - from + 1 }, (_, i) => route(from + i));

// ---------- 추천 산책로 목록 (/routes) ----------

function openRecommend({ geolocation = { position: HERE }, fetch, session } = {}) {
  return openPage({
    html: template("course/list.html"),
    url: "http://localhost:8081/routes",
    scripts: ["courses.js", "walk-recommend.js"],
    geolocation,
    fetch,
    sessionStorage: session,
    setup(window) {
      window.document.querySelector("#walk-recommend-list").setAttribute("data-detail-url", "/course-detail");
    },
  });
}

test("현재 위치로 AI 서버에 6개를 요청해 카드로 보여주고 목록을 보관한다", async () => {
  const page = openRecommend({ fetch: () => response({ routes: routes(1, 6) }) });
  await settle();

  const call = page.calls.fetch[0];
  assert.equal(call.url, "http://127.0.0.1:8000/api/routes/recommend");
  assert.deepEqual(JSON.parse(call.body), { latitude: 37.5443, longitude: 127.0374, top_k: 6 });

  const cards = page.$$("#walk-recommend-list .route-item");
  assert.equal(cards.length, 6);
  assert.equal(cards[0].querySelector("h3").textContent, "추천 코스 1");
  assert.equal(cards[0].querySelector(".route-distance-value").textContent, "1.2");
  assert.equal(cards[0].querySelector(".meta").textContent, "1.2km · 약 10분");
  assert.equal(page.$("#walk-recommend-status").textContent, "내 주변 산책로 6개를 찾았어요.");

  const saved = JSON.parse(page.window.sessionStorage.getItem("walk-recommendations:v1"));
  assert.equal(saved.routes.length, 6);
  assert.equal(saved.hasMore, true);
  assert.equal(page.$("#walk-recommend-sentinel").hidden, false, "더 불러올 수 있으면 무한스크롤 준비");
  page.close();
});

test("카드를 누르면 경로를 sessionStorage에 보관하고 상세 화면 주소로 연결한다", async () => {
  const page = openRecommend({ fetch: () => response({ routes: routes(1, 2) }) });
  await settle();

  const card = page.$("#walk-recommend-list .route-item");
  assert.equal(card.getAttribute("href"), "/course-detail?route=route-1");
  page.click(card);

  assert.equal(JSON.parse(page.window.sessionStorage.getItem("walk-route:route-1")).title, "추천 코스 1");
  page.close();
});

test("보관된 추천 목록이 있으면 위치·서버 요청 없이 바로 보여준다", async () => {
  const page = openRecommend({
    session: { "walk-recommendations:v1": { routes: routes(1, 3), origin: { latitude: 1, longitude: 2 }, requestedCount: 6, hasMore: false } },
    fetch: () => { throw new Error("요청하면 안 됨"); },
  });
  await settle();

  assert.equal(page.$$("#walk-recommend-list .route-item").length, 3);
  assert.equal(page.calls.fetch.length, 0);
  assert.equal(page.geo.requests, 0);
  assert.equal(page.$("#walk-recommend-more-status").textContent, "더 추천할 산책로가 없어요.");
  page.close();
});

test("아래로 내리면 3개를 더 요청하고, 이미 있는 코스는 빼고 붙인다", async () => {
  const page = openRecommend({
    fetch: (url, call) => {
      const topK = JSON.parse(call.body).top_k;
      return response({ routes: topK === 6 ? routes(1, 6) : [...routes(1, 6), route(7), route(8), route(9)] });
    },
  });
  await settle();

  page.observers[0].trigger(page.$("#walk-recommend-sentinel"));
  await settle();

  assert.equal(JSON.parse(page.calls.fetch[1].body).top_k, 9);
  const titles = page.$$("#walk-recommend-list .route-item h3").map((h) => h.textContent);
  assert.equal(titles.length, 9);
  assert.deepEqual(titles.slice(6), ["추천 코스 7", "추천 코스 8", "추천 코스 9"]);
  assert.equal(page.$$(".walk-recommend-loading-card").length, 0);
  page.close();
});

test("위치 권한 거부 / 서버 오류 때 각각 안내한다", async () => {
  const denied = openRecommend({ geolocation: { error: { code: 1 } } });
  await settle();
  assert.equal(denied.$("#walk-recommend-status").textContent, "위치 접근을 허용하면 주변 산책로를 추천받을 수 있어요.");
  assert.equal(denied.calls.fetch.length, 0);
  denied.close();

  const serverDown = openRecommend({ fetch: () => response({}, { status: 500 }) });
  await settle();
  assert.equal(serverDown.$("#walk-recommend-status").textContent, "추천 산책로를 불러오지 못했어요. 잠시 후 다시 시도해주세요.");
  assert.equal(serverDown.$("#walk-recommend-retry").disabled, false);
  serverDown.close();

  const noGps = openRecommend({ geolocation: null });
  await settle();
  assert.equal(noGps.$("#walk-recommend-status").textContent, "현재 위치를 확인할 수 없어요. 다른 브라우저에서 이용해주세요.");
  noGps.close();
});

test("courses.js는 목록 요소(#course-list)가 있을 때만 DB 코스 카드를 그린다", async () => {
  // 현재 course/list.html 에는 #course-list 가 없어서 아무것도 하지 않음
  const listPage = openRecommend({ fetch: () => response({ routes: [] }) });
  await settle();
  assert.equal(listPage.calls.fetch.filter((c) => c.url === "/api/courses/routes").length, 0);
  listPage.close();

  const page = openPage({
    html: '<div id="course-list"></div>',
    scripts: ["courses.js"],
    fetch: () => response([{ courseId: 3, courseName: "한강 코스", description: null, distanceM: 2300, estimatedMinutes: 35 }]),
  });
  await settle();
  const card = page.$("#course-list .route-item");
  assert.equal(card.getAttribute("href"), "/course-detail?courseId=3");
  assert.equal(card.querySelector("h3").textContent, "한강 코스");
  assert.equal(card.querySelector("p").textContent, "");
  assert.equal(card.querySelector(".meta").textContent, "2.3km · 약 35분");
  page.close();
});

// ---------- 산책로 상세 (/course-detail) ----------

function openDetail({ query, session, fetch, kakao = true } = {}) {
  return openPage({
    html: template("course/detail.html"),
    url: "http://localhost:8081/course-detail" + query,
    scripts: ["course-detail.js"],
    sessionStorage: session,
    fetch,
    kakao,
    setup(window) {
      const page = window.document.querySelector("#course-detail");
      page.dataset.apiBase = "/api/courses/";
      page.dataset.formUrl = "/board/new";
      window.document.querySelector("#course-start").setAttribute("href", "/walk-record");
    },
  });
}

test("추천 코스 상세: 보관된 경로로 정보와 지도 경로를 그린다", async () => {
  const page = openDetail({ query: "?route=r-1", session: { "walk-route:r-1": route(2) } });
  await settle();

  assert.equal(page.$("#course-name").textContent, "추천 코스 2");
  assert.equal(page.$("#course-feature").textContent, "내 주변 추천 코스");
  assert.equal(page.$("#course-distance").textContent, "거리 · 2.2km");
  assert.equal(page.$("#course-duration").textContent, "예상 소요시간 · 약 20분");
  assert.equal(page.document.title, "추천 코스 2 — 멍자국");
  assert.equal(page.$("#course-start").getAttribute("href"), "/walk-record?route=r-1");
  assert.equal(page.calls.kakao.polylines.length, 1);
  assert.deepEqual(page.calls.kakao.markers.map((m) => m.options.title), ["시작", "도착"]);

  page.click("#course-share");
  assert.deepEqual(page.calls.navigations, ["/board/new?route=r-1"]);
  page.close();
});

test("등록된 코스 상세: API로 조회하고 출발 지점 마커를 표시한다", async () => {
  const page = openDetail({
    query: "?courseId=3",
    fetch: () => response({ courseId: 3, courseName: "한강 코스", description: "", distanceM: 2600,
      estimatedMinutes: 40, feature: "강변", region: "마포구", startLatitude: 37.55, startLongitude: 126.9 }),
  });
  await settle();

  assert.equal(page.calls.fetch[0].url, "/api/courses/3");
  assert.equal(page.$("#course-region").textContent, "마포구");
  assert.equal(page.$("#course-description").textContent, "등록된 설명이 없습니다.");
  assert.deepEqual(page.calls.kakao.markers.map((m) => m.options.title), ["한강 코스"]);

  page.click("#course-share");
  assert.deepEqual(page.calls.navigations, ["/board/new?courseId=3"]);
  page.close();
});

test("상세를 열 수 없는 경우 안내한다 (코스 미선택 / 없는 코스 / 추천 정보 없음 / 지도 SDK 없음)", async () => {
  const none = openDetail({ query: "" });
  await settle();
  assert.equal(none.$("#course-status").textContent, "목록에서 확인할 산책로를 선택해주세요.");
  none.close();

  const missing = openDetail({ query: "?courseId=99", fetch: () => response({}, { status: 404 }) });
  await settle();
  assert.equal(missing.$("#course-status").textContent, "해당 산책로를 찾을 수 없어요. 다른 산책로를 선택해주세요.");
  missing.close();

  const noRoute = openDetail({ query: "?route=gone" });
  await settle();
  assert.equal(noRoute.$("#course-status").textContent, "추천 정보가 없어요. 목록에서 산책로를 다시 선택해주세요.");
  noRoute.close();

  const noSdk = openDetail({ query: "?route=r-1", session: { "walk-route:r-1": route(1) }, kakao: false });
  await settle();
  assert.equal(noSdk.$("#course-map-status").textContent, "지도를 불러오지 못했어요. 잠시 후 다시 확인해주세요.");
  assert.equal(noSdk.$("#course-content").hidden, false, "지도가 없어도 코스 정보는 보여줌");
  noSdk.close();
});

test("추천 코스 좌표가 망가졌으면 공유 버튼을 막는다", async () => {
  const broken = route(1, { points: [{ sequence: 1, latitude: "abc", longitude: 127 }] });
  const page = openDetail({ query: "?route=r-1", session: { "walk-route:r-1": broken } });
  await settle();

  assert.equal(page.$("#course-map-status").textContent, "이 산책로의 위치 정보를 확인할 수 없어요. 다시 추천받아주세요.");
  page.click("#course-share");
  assert.equal(page.$("#course-share").textContent, "다시 추천받아주세요");
  assert.equal(page.$("#course-share").disabled, true);
  assert.deepEqual(page.calls.navigations, []);
  page.close();
});
