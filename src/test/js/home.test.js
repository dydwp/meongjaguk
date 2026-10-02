// 메인 화면 위젯 (home-widgets.js 날씨·체크, home-routes.js 추천 3개, walk-map.js 지도, scroll-top.js)
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { openPage, template, response, settle } = require("./support/browser");

const HERE = { coords: { latitude: 37.5443, longitude: 127.0374 } };

function todayText() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

// ---------- 오늘의 산책 날씨 ----------

/** 오후 1시 기준, 오후 4~6시가 18도·비 없음으로 가장 좋은 예보 */
function forecast({ temp = 21, code = 0 } = {}) {
  const hours = Array.from({ length: 24 }, (_, h) => h);
  return {
    current: { time: "2026-09-30T13:00", temperature_2m: temp, weather_code: code },
    hourly: {
      time: hours.map((h) => `2026-09-30T${String(h).padStart(2, "0")}:00`),
      temperature_2m: hours.map((h) => (h === 16 || h === 17 ? 18 : 28)),
      precipitation_probability: hours.map((h) => (h === 16 || h === 17 ? 0 : 40)),
      weather_code: hours.map(() => code),
    },
  };
}

function openWeather({ geolocation = { position: HERE }, weather = forecast(), air = { current: { pm10: 40, pm2_5: 10 } }, session } = {}) {
  return openPage({
    html: template("index.html"),
    scripts: ["home-widgets.js"],
    geolocation,
    sessionStorage: session,
    fetch: (url) => {
      if (url.includes("air-quality")) return air instanceof Error ? air : response(air);
      return weather instanceof Error ? weather : response(weather);
    },
  });
}

const weatherText = (page) => ["icon", "temp", "grade", "detail"].map((k) => page.$(`[data-weather-${k}]`).textContent);

test("날씨·미세먼지로 산책 등급과 오늘 추천 시간을 보여주고 30분간 보관한다", async () => {
  const page = openWeather();
  await settle();

  assert.deepEqual(weatherText(page), ["☀️", "21°", "산책 좋음", "미세먼지 보통 · 오후 4~6시 추천"]);
  assert.equal(page.$("[data-weather-body]").hidden, false);
  assert.equal(page.$("[data-weather-status]").hidden, true);
  // 위치는 소수 2자리(약 1km)로 줄여서 보냄
  assert.ok(page.calls.fetch.every((c) => c.url.includes("latitude=37.54&longitude=127.04")));
  assert.ok(JSON.parse(page.window.sessionStorage.getItem("meongjaguk-weather")).view);
  page.close();
});

test("비가 오거나 너무 덥고 춥거나 미세먼지가 나쁘면 '산책 주의'", async () => {
  for (const [options, grade, icon] of [
    [{ weather: forecast({ code: 61 }) }, "산책 주의", "🌧️"],
    [{ weather: forecast({ temp: 33 }) }, "산책 주의", "☀️"],
    [{ air: { current: { pm10: 100, pm2_5: 10 } } }, "산책 주의", "☀️"],
    [{ weather: forecast({ temp: 2, code: 3 }) }, "산책 보통", "☁️"],
  ]) {
    const page = openWeather(options);
    await settle();
    assert.equal(page.$("[data-weather-grade]").textContent, grade);
    assert.equal(page.$("[data-weather-icon]").textContent, icon);
    page.close();
  }
});

test("미세먼지 조회가 실패해도 날씨는 보여주고, 날씨가 실패하면 안내한다", async () => {
  const noAir = openWeather({ air: new Error("air down") });
  await settle();
  assert.match(noAir.$("[data-weather-detail]").textContent, /^미세먼지 정보 없음 · /);
  noAir.close();

  const down = openWeather({ weather: response({}, { status: 500 }) });
  await settle();
  assert.equal(down.$("[data-weather-status]").textContent, "날씨 정보를 불러오지 못했어요. 잠시 후 다시 확인해주세요.");
  down.close();
});

test("위치 권한이 없으면 서울시청 기준으로, 보관된 날씨가 있으면 조회 없이 보여준다", async () => {
  const denied = openWeather({ geolocation: { error: { code: 1 } } });
  await settle();
  assert.ok(denied.calls.fetch[0].url.includes("latitude=37.57&longitude=126.98"));
  denied.close();

  const view = { icon: "🌤️", temp: "19°", grade: "산책 좋음", detail: "보관된 날씨" };
  const cached = openWeather({ session: { "meongjaguk-weather": { savedAt: Date.now(), view } } });
  await settle();
  assert.equal(cached.calls.fetch.length, 0);
  assert.deepEqual(weatherText(cached), ["🌤️", "19°", "산책 좋음", "보관된 날씨"]);
  cached.close();
});

// ---------- 나가기 전 체크 ----------

function openChecklist(saved) {
  return openPage({
    html: template("index.html"),
    scripts: ["home-widgets.js"],
    geolocation: {},
    localStorage: saved ? { "meongjaguk-walk-checklist": saved } : {},
  });
}
const checked = (page) => page.$$("[data-walk-checklist] input").filter((i) => i.checked).map((i) => i.value);

test("체크 상태를 오늘 날짜로 저장하고, 다시 열면 이어서 보여준다", () => {
  const page = openChecklist();
  const leash = page.$('[data-walk-checklist] input[value="leash"]');
  leash.checked = true;
  leash.dispatchEvent(new page.window.Event("change"));

  const saved = JSON.parse(page.window.localStorage.getItem("meongjaguk-walk-checklist"));
  assert.deepEqual(saved, { date: todayText(), checked: ["leash"] });
  page.close();

  const reopened = openChecklist(saved);
  assert.deepEqual(checked(reopened), ["leash"]);
  reopened.close();
});

test("어제 체크한 내용은 초기화되고, 산책이 끝나면 모두 해제된다", () => {
  const yesterday = openChecklist({ date: "2000-01-01", checked: ["leash", "water"] });
  assert.deepEqual(checked(yesterday), []);
  yesterday.close();

  const page = openChecklist({ date: todayText(), checked: ["leash", "water"] });
  assert.deepEqual(checked(page), ["leash", "water"]);
  page.window.dispatchEvent(new page.window.CustomEvent("walk:stopped"));
  assert.deepEqual(checked(page), []);
  page.close();
});

// ---------- 메인 추천 산책로 3개 ----------

const route = (n) => ({ rank: n, title: `추천 ${n}`, distance_m: 1500, estimated_minutes: 20, points: [] });

function openHomeRoutes({ session, geolocation = { position: HERE }, fetch } = {}) {
  return openPage({
    html: template("index.html"),
    scripts: ["home-routes.js"],
    sessionStorage: session,
    geolocation,
    fetch,
    setup(window) {
      window.document.querySelector("#home-route-list").setAttribute("data-detail-url", "/course-detail");
    },
  });
}

test("추천 산책로 페이지와 같은 보관 목록이 있으면 앞의 3개를 바로 보여준다", async () => {
  const page = openHomeRoutes({ session: { "walk-recommendations:v1": { routes: [1, 2, 3, 4, 5, 6].map(route) } } });
  await settle();

  const titles = page.$$("#home-route-list h3").map((h) => h.textContent);
  assert.deepEqual(titles, ["추천 1", "추천 2", "추천 3"]);
  assert.equal(page.calls.fetch.length, 0);
  assert.equal(page.$("#home-route-status").hidden, true);
  page.close();
});

test("보관 목록이 없으면 현재 위치로 6개를 받아 추천 페이지와 같은 형식으로 보관한다", async () => {
  const page = openHomeRoutes({ fetch: () => response({ routes: [1, 2, 3, 4, 5, 6].map(route) }) });
  await settle();

  assert.equal(page.calls.fetch[0].url, "/api/routes/recommend");
  assert.equal(JSON.parse(page.calls.fetch[0].body).top_k, 6);
  assert.equal(page.$$("#home-route-list .route-item").length, 3);
  const saved = JSON.parse(page.window.sessionStorage.getItem("walk-recommendations:v1"));
  assert.equal(saved.routes.length, 6);
  assert.equal(saved.requestedCount, 6);
  assert.equal(saved.hasMore, true);

  page.click("#home-route-list .route-item");
  assert.ok(page.window.sessionStorage.getItem("walk-route:route-1"));
  page.close();
});

test("위치 거부·서버 오류·빈 결과 때 안내 문구를 보여준다", async () => {
  for (const [options, message] of [
    [{ geolocation: { error: { code: 1 } } }, "위치 접근을 허용하면 내 주변 산책로를 추천받을 수 있어요."],
    [{ fetch: () => response({}, { status: 503 }) }, "추천 산책로를 불러오지 못했어요. 잠시 후 다시 시도해주세요."],
    [{ fetch: () => response({ routes: [] }) }, "주변에 추천할 산책로가 없어요."],
    [{ geolocation: null }, "현재 위치를 확인할 수 없어 추천 산책로를 보여드릴 수 없어요."],
  ]) {
    const page = openHomeRoutes(options);
    await settle();
    assert.equal(page.$("#home-route-status").textContent, message);
    assert.equal(page.$("#home-route-status").hidden, false);
    page.close();
  }
});

// ---------- 산책 지도 (walk-map.js) ----------

test("지도는 산책 중이면 걸은 경로를 그리고, 새 위치·산책 종료 신호에 맞춰 다시 그린다", async () => {
  const points = [{ lat: 37.5, lng: 127.0 }, { lat: 37.501, lng: 127.0 }];
  const page = openPage({
    html: template("index.html"),
    scripts: ["walk-map.js"],
    kakao: true,
    geolocation: {},
    localStorage: { "meongjaguk-walk-active": "true", "meongjaguk-walk-points": points },
  });
  const { maps, polylines, markers } = page.calls.kakao;

  assert.equal(maps.length, 1);
  assert.ok(page.$("[data-walk-map]").parentElement.classList.contains("has-map"));
  const line = polylines[0];
  assert.equal(line.path.length, 2);
  assert.equal(markers[0].map, maps[0], "시작 지점 표시");
  assert.equal(maps[0].center.lat, 37.501, "마지막 위치로 이동");

  page.window.localStorage.setItem("meongjaguk-walk-points", JSON.stringify([...points, { lat: 37.502, lng: 127.0 }]));
  page.window.dispatchEvent(new page.window.CustomEvent("walk:position", { detail: { lat: 37.502, lng: 127.0 } }));
  assert.equal(line.path.length, 3);

  page.window.localStorage.setItem("meongjaguk-walk-active", "false");
  page.window.dispatchEvent(new page.window.CustomEvent("walk:stopped"));
  assert.equal(line.path.length, 0);
  assert.equal(markers[0].map, null);
  page.close();
});

test("산책 기록 화면에서 추천 경로로 시작하면 추천 경로·범례를 함께 표시한다", () => {
  const recommended = {
    title: "서울숲 코스",
    points: [{ sequence: 2, latitude: 37.545, longitude: 127.044 }, { sequence: 1, latitude: 37.544, longitude: 127.043 }],
  };
  const page = openPage({
    html: template("walk/record.html"),
    url: "http://localhost:8081/walk-record?route=r-1",
    scripts: ["walk-map.js"],
    kakao: true,
    geolocation: {},
    sessionStorage: { "walk-route:r-1": recommended },
  });

  assert.equal(page.$("[data-walk-title]").textContent, "서울숲 코스");
  assert.equal(page.$("[data-walk-route-legend]").hidden, false);
  assert.deepEqual(page.calls.kakao.polylines[0].path.map((p) => p.lat), [37.544, 37.545]);
  assert.deepEqual(page.calls.kakao.markers.slice(0, 2).map((m) => m.options.title), ["추천 경로 시작", "추천 경로 도착"]);
  page.close();
});

// ---------- 동행 산책 기록 화면 (추가: 김환중) ----------

const MEETING = {
  meetingId: 10,
  title: "저녁 산책",
  points: [{ sequence: 2, latitude: 37.552, longitude: 127.041 }, { sequence: 1, latitude: 37.551, longitude: 127.04 }],
};
const WALKED = [{ lat: 37.5, lng: 127.0 }, { lat: 37.501, lng: 127.0 }];

function openMeetingWalk({ url = "http://localhost:8081/walk-record?meetingId=10", meeting = MEETING, localStorage } = {}) {
  return openPage({
    html: template("walk/record.html"),
    url,
    scripts: ["walk-map.js"],
    kakao: true,
    geolocation: {},
    localStorage,
    fetch: () => (meeting instanceof Error ? meeting : response(meeting)),
  });
}

test("동행 산책 시작 화면이면 모집글 제목과 모집 코스를 계획 경로로 표시한다", async () => {
  const page = openMeetingWalk();

  assert.equal(page.$("[data-walk-title]").textContent, "동행 산책");
  await settle();

  assert.deepEqual(page.calls.fetch.map((c) => c.url), ["/api/meetings/10"]);
  assert.equal(page.$("[data-walk-title]").textContent, "동행 산책 · 저녁 산책");
  assert.equal(page.$("[data-walk-route-legend]").hidden, false);
  assert.equal(page.$("[data-walk-planned-label]").textContent, "모집 코스");
  const { polylines, markers, maps } = page.calls.kakao;
  assert.equal(polylines.length, 2, "실제 경로 + 모집 코스");
  assert.deepEqual(polylines[1].path.map((p) => p.lat), [37.551, 37.552]);
  assert.equal(polylines[0].options.strokeColor, "#E08F4F", "실제 경로는 추천 산책로와 같은 색");
  assert.deepEqual(markers.slice(-2).map((m) => m.options.title), ["모집 코스 시작", "모집 코스 도착"]);
  assert.ok(maps[0].bounds, "모집 코스에 맞춰 범위 조정");
  page.close();
});

test("모집 정보를 못 불러오거나 좌표가 없으면 제목은 '동행 산책', 지도는 실제 경로만", async () => {
  for (const meeting of [new TypeError("network"), { ...MEETING, points: [] }]) {
    const page = openMeetingWalk({
      meeting,
      localStorage: { "meongjaguk-walk-active": "true", "meongjaguk-walk-meeting-id": "10", "meongjaguk-walk-points": WALKED },
    });
    await settle();

    assert.equal(page.$("[data-walk-title]").textContent, "동행 산책");
    assert.equal(page.$("[data-walk-route-legend]").hidden, true);
    const { polylines } = page.calls.kakao;
    assert.equal(polylines.length, 1, "계획 경로 없음");
    assert.equal(polylines[0].path.length, 2, "실제 경로는 그대로");
    assert.ok(page.consoleLog.some(([level]) => level === "error"), "콘솔에 기록");
    page.close();
  }
});

test("산책 중이면 주소에 번호가 없어도 저장된 동행 모집글로 표시한다", async () => {
  const page = openMeetingWalk({
    url: "http://localhost:8081/walk-record",
    localStorage: { "meongjaguk-walk-active": "true", "meongjaguk-walk-meeting-id": "10", "meongjaguk-walk-points": WALKED },
  });
  await settle();

  assert.deepEqual(page.calls.fetch.map((c) => c.url), ["/api/meetings/10"]);
  assert.equal(page.$("[data-walk-title]").textContent, "동행 산책 · 저녁 산책");
  assert.equal(page.calls.kakao.polylines.length, 2);
  assert.equal(page.$("[data-walk-route-legend]").hidden, false);
  page.close();
});

test("개인 산책 중에 동행 주소를 열어도 자유 산책 그대로 보여주고 모집글을 조회하지 않는다", async () => {
  const page = openMeetingWalk({
    localStorage: { "meongjaguk-walk-active": "true", "meongjaguk-walk-points": WALKED },
  });
  await settle();

  assert.equal(page.calls.fetch.length, 0);
  assert.equal(page.$("[data-walk-title]").textContent, "자유 산책");
  assert.equal(page.$("[data-walk-route-legend]").hidden, true);
  const { polylines, markers } = page.calls.kakao;
  assert.equal(polylines.length, 1);
  assert.equal(polylines[0].options.strokeColor, "#5C8D4E", "자유 산책 선 색");
  assert.equal(markers[0].options.title, "시작");
  page.close();
});

test("카카오맵 SDK가 없으면 지도 없이 안내만 한다", () => {
  const page = openPage({
    html: template("walk/record.html"),
    url: "http://localhost:8081/walk-record?route=r-1",
    scripts: ["walk-map.js"],
    sessionStorage: { "walk-route:r-1": { title: "코스", points: [{ sequence: 1, latitude: 1, longitude: 1 }, { sequence: 2, latitude: 2, longitude: 2 }] } },
  });

  assert.equal(page.$("[data-walk-route-status]").textContent, "지도를 불러오지 못했어요. 잠시 후 다시 확인해주세요.");
  assert.equal(page.$("[data-walk-route-status]").hidden, false);
  page.close();
});

// ---------- 맨 위로 버튼 ----------

test("맨 위로 버튼은 300px 이상 내려가면 보이고, 누르면 맨 위로 이동한다", () => {
  const page = openPage({ html: template("layout/default.html"), scripts: ["scroll-top.js"] });
  const btn = page.$("[data-scroll-top]");
  assert.equal(btn.hidden, true);

  Object.defineProperty(page.window, "scrollY", { value: 450, configurable: true });
  page.window.dispatchEvent(new page.window.Event("scroll"));
  assert.equal(btn.hidden, false);

  page.click(btn);
  assert.deepEqual(page.calls.scrolls, [{ top: 0, behavior: "smooth" }]);
  page.close();
});
