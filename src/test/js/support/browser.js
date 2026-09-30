/**
 * 프론트 테스트용 가짜 브라우저 (jsdom)
 * - 실제 Thymeleaf 템플릿 HTML 위에서 static/js 스크립트를 그대로 실행
 * - fetch / 카카오맵 / GPS / confirm·alert / 페이지 이동은 가짜로 바꿔서 기록
 */
const fs = require("node:fs");
const path = require("node:path");
const { JSDOM, VirtualConsole } = require("jsdom");
const { after } = require("node:test");

// 테스트가 중간에 실패해도 열린 페이지(타이머 포함)를 모두 닫아서 프로세스가 멈추지 않게 함
const openWindows = new Set();
after(() => openWindows.forEach((w) => w.close()));

const ROOT = path.resolve(__dirname, "../../../..");
const JS_DIR = path.join(ROOT, "src/main/resources/static/js");
const TEMPLATE_DIR = path.join(ROOT, "src/main/resources/templates");

/** 템플릿 파일 원본 (th: 속성은 브라우저가 무시하므로 그대로 사용) */
function template(name) {
  return fs.readFileSync(path.join(TEMPLATE_DIR, name), "utf8");
}

/** fetch 응답 흉내 */
function response(body, { status = 200, redirected = false } = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    redirected,
    json: async () => {
      if (body === undefined) throw new SyntaxError("Unexpected end of JSON input");
      return JSON.parse(JSON.stringify(body));
    },
  };
}

/** 카카오맵 SDK 흉내: 만들어진 지도·선·마커를 모두 기록 */
function fakeKakao() {
  const created = { maps: [], polylines: [], markers: [], overlays: [] };
  class LatLng {
    constructor(lat, lng) { this.lat = lat; this.lng = lng; }
    getLat() { return this.lat; }
    getLng() { return this.lng; }
  }
  class LatLngBounds {
    constructor() { this.points = []; }
    extend(p) { this.points.push(p); }
  }
  class Map {
    constructor(container, options) {
      this.container = container; this.options = options; this.bounds = null; this.center = options.center;
      created.maps.push(this);
    }
    setBounds(b) { this.bounds = b; }
    relayout() {}
    panTo(p) { this.center = p; }
  }
  class Polyline {
    constructor(options) { this.options = options; this.path = Array.from(options.path); created.polylines.push(this); }
    setPath(p) { this.path = Array.from(p); }
  }
  class Marker {
    constructor(options) { this.options = options; this.map = options.map || null; created.markers.push(this); }
    setPosition(p) { this.options.position = p; }
    setMap(m) { this.map = m; }
  }
  class CustomOverlay {
    constructor(options) { this.options = options; this.map = options.map || null; created.overlays.push(this); }
    setPosition(p) { this.options.position = p; }
    setMap(m) { this.map = m; }
  }
  return { sdk: { maps: { LatLng, LatLngBounds, Map, Polyline, Marker, CustomOverlay } }, created };
}

/** IntersectionObserver 흉내: 테스트에서 trigger()로 "화면에 보임"을 발생 */
function installIntersectionObserver(window, observers) {
  window.IntersectionObserver = class {
    constructor(callback) { this.callback = callback; this.targets = new Set(); observers.push(this); }
    observe(el) { this.targets.add(el); }
    unobserve(el) { this.targets.delete(el); }
    disconnect() { this.targets.clear(); this.disconnected = true; }
    trigger(el) {
      const targets = el ? [el] : [...this.targets];
      this.callback(targets.map((target) => ({ target, isIntersecting: true })), this);
    }
  };
}

/**
 * 페이지 열기
 * @param {object} options
 *  - html: 페이지 HTML (template('...')로 실제 템플릿 사용)
 *  - url: 주소 (쿼리스트링 포함)
 *  - scripts: 실행할 static/js 파일 이름 목록
 *  - fetch: (url, init) => response(...) 또는 Promise
 *  - kakao: true면 가짜 카카오맵 설치
 *  - geolocation: { position } | { error } | null(지원 안 함) | 객체 직접
 *  - confirm: true/false 또는 함수
 *  - localStorage / sessionStorage: 미리 넣어 둘 값 {key: value}
 *  - setup(window): 스크립트 실행 전에 DOM을 손볼 때
 */
function openPage(options = {}) {
  const virtualConsole = new VirtualConsole();
  const consoleLog = [];
  virtualConsole.on("error", (...args) => consoleLog.push(["error", ...args]));
  virtualConsole.on("warn", (...args) => consoleLog.push(["warn", ...args]));
  virtualConsole.on("jsdomError", (e) => {
    if (!String(e.message).includes("Not implemented")) consoleLog.push(["jsdomError", e.message]);
  });

  const dom = new JSDOM(options.html || "<!doctype html><html><body></body></html>", {
    url: options.url || "http://localhost:8081/",
    runScripts: "outside-only",
    pretendToBeVisual: true,
    virtualConsole,
  });
  const { window } = dom;
  const { document } = window;
  openWindows.add(window);

  // ----- 기록용 -----
  const calls = { fetch: [], alerts: [], confirms: [], navigations: [], scrolls: [], timeouts: [] };
  const observers = [];

  // ----- fetch -----
  window.fetch = async (url, init = {}) => {
    const call = { url: String(url), method: (init.method || "GET").toUpperCase(), headers: init.headers || {}, body: init.body, init };
    calls.fetch.push(call);
    if (!options.fetch) throw new TypeError("fetch not mocked: " + url);
    const result = await options.fetch(call.url, call);
    if (result instanceof Error) throw result;
    return result;
  };

  // ----- 알림창 -----
  window.alert = (message) => calls.alerts.push(message);
  window.confirm = (message) => {
    calls.confirms.push(message);
    return typeof options.confirm === "function" ? options.confirm(message) : options.confirm !== false;
  };

  // ----- 브라우저에 없는 기능 흉내 -----
  window.matchMedia = options.matchMedia || ((query) => ({ matches: false, media: query, addEventListener() {}, removeEventListener() {} }));
  window.scrollTo = (arg) => calls.scrolls.push(JSON.parse(JSON.stringify(arg)));
  window.URL.createObjectURL = () => "blob:preview";
  window.URL.revokeObjectURL = () => {};
  let uuid = 0;
  Object.defineProperty(window.crypto, "randomUUID", { value: () => `route-${++uuid}`, configurable: true });
  installIntersectionObserver(window, observers);

  if (options.kakao) {
    const kakao = fakeKakao();
    window.kakao = kakao.sdk;
    calls.kakao = kakao.created;
  }

  // ----- GPS -----
  const geo = { watchers: [], cleared: [], requests: 0 };
  if (options.geolocation !== null) {
    const g = options.geolocation || {};
    Object.defineProperty(window.navigator, "geolocation", {
      configurable: true,
      value: {
        getCurrentPosition(success, error) {
          geo.requests++;
          if (g.error) setTimeout(() => error && error(g.error), 0);
          else if (g.position) setTimeout(() => success(g.position), 0);
          // 둘 다 없으면 응답 없음 (위치 확인 중)
        },
        watchPosition(success, error) {
          geo.watchers.push({ success, error });
          return geo.watchers.length;
        },
        clearWatch(id) { geo.cleared.push(id); },
      },
    });
  }

  // ----- 저장소 -----
  for (const [key, value] of Object.entries(options.localStorage || {})) {
    window.localStorage.setItem(key, typeof value === "string" ? value : JSON.stringify(value));
  }
  for (const [key, value] of Object.entries(options.sessionStorage || {})) {
    window.sessionStorage.setItem(key, typeof value === "string" ? value : JSON.stringify(value));
  }

  if (options.setup) options.setup(window, calls);

  // ----- 페이지 이동 기록: 스크립트 안의 window.location 만 가짜로 바꿈 -----
  const realLocation = window.location;
  const fakeLocation = {
    get href() { return realLocation.href; },
    set href(value) { calls.navigations.push(String(value)); },
    get search() { return realLocation.search; },
    get pathname() { return realLocation.pathname; },
    get origin() { return realLocation.origin; },
    assign(value) { calls.navigations.push(String(value)); },
    replace(value) { calls.navigations.push(String(value)); },
    reload() { calls.navigations.push("reload"); },
    toString() { return realLocation.href; },
  };
  window.__testWindow = new Proxy(window, {
    get(target, prop) {
      if (prop === "location") return fakeLocation;
      const value = Reflect.get(target, prop);
      return typeof value === "function" && !/^[A-Z]/.test(String(prop)) ? value.bind(target) : value;
    },
    set(target, prop, value) { return Reflect.set(target, prop, value); },
  });

  for (const name of options.scripts || []) {
    const source = fs.readFileSync(path.join(JS_DIR, name), "utf8").replace(/^﻿/, "");
    window.eval(`(function (window) {\n${source}\n}).call(window.__testWindow, window.__testWindow);\n//# sourceURL=${name}`);
  }

  return {
    window,
    document,
    calls,
    geo,
    observers,
    consoleLog,
    $: (selector) => document.querySelector(selector),
    $$: (selector) => [...document.querySelectorAll(selector)],
    click(selectorOrEl) {
      const el = typeof selectorOrEl === "string" ? document.querySelector(selectorOrEl) : selectorOrEl;
      if (!el) throw new Error("element not found: " + selectorOrEl);
      el.dispatchEvent(new window.MouseEvent("click", { bubbles: true, cancelable: true }));
      return el;
    },
    close() { openWindows.delete(window); window.close(); },
  };
}

/** 대기 중인 Promise·타이머(0ms)를 모두 처리 */
async function settle(times = 10) {
  for (let i = 0; i < times; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
}

module.exports = { openPage, template, response, settle, ROOT };

/** 서버가 보내는 형식(시간대 없는 한국 시간, LocalDateTime)으로 "n시간 전" 만들기 */
function localDateTime(msAgo = 0) {
  const d = new Date(Date.now() - msAgo);
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

module.exports.localDateTime = localDateTime;
