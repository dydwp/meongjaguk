// 반려견 프로필 (pet-profile.js), 마이페이지 (mypage.js), 활동 상세 지도 (activity.js)
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { openPage, template, response, settle } = require("./support/browser");

const API = "http://localhost:8081/api/pet-profile/pets";

// ---------- 반려견 프로필 등록·수정·삭제 ----------

function openPetForm({ query = "", fetch, confirm } = {}) {
  return openPage({
    html: template("dog/profile.html"),
    url: "http://localhost:8081/pet-profile" + query,
    scripts: ["app.js", "pet-profile.js"], // 크기·활동성 선택 버튼은 app.js
    fetch,
    confirm,
    setup(window) {
      const form = window.document.querySelector("#pet-profile-form");
      form.setAttribute("action", "/api/pet-profile/pets");
      Object.assign(form.dataset, { csrfHeader: "X-CSRF-TOKEN", csrfToken: "token-123", successUrl: "/mypage" });
    },
  });
}

function submit(page) {
  page.$("#pet-profile-form").dispatchEvent(new page.window.Event("submit", { bubbles: true, cancelable: true }));
}

/** FormData 안의 petInfo(Blob) → 객체 */
async function petInfo(page, call) {
  const reader = new page.window.FileReader();
  const text = await new Promise((resolve) => {
    reader.onload = () => resolve(reader.result);
    reader.readAsText(call.body.get("petInfo"));
  });
  return JSON.parse(text);
}

function selectFile(page, file) {
  const input = page.$("#pet-photo");
  Object.defineProperty(input, "files", { value: [file], configurable: true });
  input.dispatchEvent(new page.window.Event("change"));
}

test("새 반려견 등록: 입력값을 petInfo(JSON)로 담아 CSRF 토큰과 함께 보내고 마이페이지로 이동한다", async () => {
  const page = openPetForm({ fetch: () => response(undefined, { status: 201 }) });
  const f = page.$("#pet-profile-form").elements;
  f.namedItem("name").value = "  보리 ";
  f.namedItem("breed").value = "말티즈";
  f.namedItem("age").value = "3";
  page.click('[data-pet-field="size"] [data-value="SMALL"]');
  page.click('[data-pet-field="activityLevel"] [data-value="HIGH"]');

  submit(page);
  await settle();

  const call = page.calls.fetch[0];
  assert.equal(call.url, API);
  assert.equal(call.method, "POST");
  assert.equal(call.headers["X-CSRF-TOKEN"], "token-123");
  assert.equal(call.body.get("petInfo").type, "application/json");
  assert.deepEqual(await petInfo(page, call),{ name: "보리", breed: "말티즈", age: 3, size: "SMALL", activityLevel: "HIGH" });
  assert.equal(call.body.get("image"), null);
  assert.equal(call.body.get("removeImage"), null); // 등록에는 없음
  assert.deepEqual(page.calls.navigations, ["/mypage"]);
  page.close();
});

test("이름이 없으면 보내지 않는다", async () => {
  const page = openPetForm({ fetch: () => response(undefined, { status: 201 }) });
  submit(page);
  await settle();
  assert.equal(page.calls.fetch.length, 0);
  page.close();
});

test("사진은 5MB 이하 JPG·PNG만 받는다", () => {
  const page = openPetForm();
  const { File } = page.window;

  selectFile(page, new File(["gif"], "dog.gif", { type: "image/gif" }));
  assert.equal(page.$("#pet-form-status").textContent, "5MB 이하의 JPG 또는 PNG 사진을 선택해주세요.");
  assert.equal(page.$("#pet-photo-preview").hidden, true);

  selectFile(page, new File(["png"], "dog.png", { type: "image/png" }));
  assert.equal(page.$("#pet-photo-preview").hidden, false);
  assert.equal(page.$("#pet-photo-remove").hidden, false);
  assert.equal(page.$("#pet-photo-button").getAttribute("aria-label"), "반려견 사진 변경");
  page.close();
});

const BORI = { id: 5, name: "보리", breed: "말티즈", age: 3, size: "MEDIUM", activityLevel: "LOW", profileImage: "/images/pets/bori.png" };

test("수정 화면: 기존 정보를 채우고, 사진을 지우면 removeImage=true 로 PUT 한다", async () => {
  const page = openPetForm({
    query: "?id=5",
    fetch: (url, call) => (call.method === "GET" ? response(BORI) : response(undefined, { status: 204 })),
  });
  await settle();

  assert.equal(page.calls.fetch[0].url, API + "/5");
  assert.equal(page.$("#pet-form-title").textContent, "반려견 프로필 수정");
  assert.equal(page.$("#pet-submit").textContent.trim(), "수정하기");
  assert.equal(page.$("#pet-delete").hidden, false);
  const f = page.$("#pet-profile-form").elements;
  assert.equal(f.namedItem("name").value, "보리");
  assert.equal(f.namedItem("age").value, "3");
  assert.equal(page.$('[data-pet-field="size"] [aria-pressed="true"]').dataset.value, "MEDIUM");
  assert.equal(page.$("#pet-photo-preview").getAttribute("src"), "/images/pets/bori.png");

  page.click("#pet-photo-remove");
  submit(page);
  await settle();

  const put = page.calls.fetch[1];
  assert.equal(put.method, "PUT");
  assert.equal(put.url, API + "/5");
  assert.equal(put.body.get("removeImage"), "true");
  assert.deepEqual(page.calls.navigations, ["/mypage"]);
  page.close();
});

test("수정 화면: 남의 반려견이거나 잘못된 번호면 안내하고 저장을 막는다", async () => {
  const notMine = openPetForm({ query: "?id=5", fetch: () => response({}, { status: 404 }) });
  await settle();
  assert.equal(notMine.$("#pet-form-status").textContent, "반려견 정보를 찾을 수 없습니다.");
  assert.equal(notMine.$("#pet-submit").disabled, true);
  notMine.close();

  const bad = openPetForm({ query: "?id=abc" });
  await settle();
  assert.equal(bad.$("#pet-form-status").textContent, "잘못된 반려견 정보입니다.");
  assert.equal(bad.calls.fetch.length, 0);
  bad.close();
});

test("저장 실패 원인별 안내 (사진 용량 초과 413 / 만료 403)", async () => {
  for (const [status, message] of [
    [413, "사진 용량이 커서 저장하지 못했어요. 더 작은 사진을 선택해주세요."],
    [403, "저장을 완료하지 못했어요. 새로고침 후 다시 시도해주세요."],
    [500, "반려견 정보를 저장하지 못했어요. 잠시 후 다시 시도해주세요."],
  ]) {
    const page = openPetForm({ fetch: () => response({}, { status }) });
    page.$("#pet-profile-form").elements.namedItem("name").value = "보리";
    submit(page);
    await settle();
    assert.equal(page.$("#pet-form-status").textContent, message);
    assert.equal(page.$("#pet-submit").disabled, false);
    assert.deepEqual(page.calls.navigations, []);
    page.close();
  }
});

test("삭제는 확인 후 DELETE 요청하고, 취소하면 아무것도 하지 않는다", async () => {
  const keep = openPetForm({ query: "?id=5", confirm: false, fetch: () => response(BORI) });
  await settle();
  keep.click("#pet-delete");
  await settle();
  assert.equal(keep.calls.fetch.filter((c) => c.method === "DELETE").length, 0);
  keep.close();

  const page = openPetForm({
    query: "?id=5",
    fetch: (url, call) => (call.method === "GET" ? response(BORI) : response(undefined, { status: 204 })),
  });
  await settle();
  page.click("#pet-delete");
  await settle();

  const del = page.calls.fetch.find((c) => c.method === "DELETE");
  assert.equal(del.url, API + "/5");
  assert.equal(del.headers["X-CSRF-TOKEN"], "token-123");
  assert.deepEqual(page.calls.navigations, ["/mypage"]);
  page.close();
});

// ---------- 마이페이지 탭·더보기·안내 메시지 ----------

function openMyPage({ url = "http://localhost:8081/mypage?tab=pets&petId=3", html, matchMedia } = {}) {
  return openPage({
    html: html || template("member/mypage.html"),
    url,
    scripts: ["mypage.js"],
    matchMedia,
    setup(window, calls) {
      window.setTimeout = (fn, ms) => { calls.timeouts.push(ms); fn(); return 0; }; // 5초 기다리지 않음
    },
  });
}

test("탭을 누르면 해당 패널만 보이고 주소의 tab을 바꾼다 (반려견 필터 petId는 지움)", () => {
  const page = openMyPage();
  page.click('[data-tab-target="shared"]');

  const shown = page.$$("[data-tab-panel]").filter((p) => !p.hidden).map((p) => p.dataset.tabPanel);
  assert.deepEqual(shown, ["shared"]);
  assert.ok(page.$('[data-tab-target="shared"]').classList.contains("active"));
  assert.ok(!page.$('[data-tab-target="pets"]').classList.contains("active"));
  assert.equal(page.window.location.search, "?tab=shared");
  page.close();
});

test("새로고침이 필요한 탭(동행 신청)은 서버 화면으로 이동한다", () => {
  const page = openMyPage({
    html: template("member/mypage.html").replace('data-tab-target="requests"', 'data-tab-target="requests" data-refresh-url="/mypage?tab=requests"'),
  });
  page.click('[data-tab-target="requests"]');
  assert.deepEqual(page.calls.navigations, ["/mypage?tab=requests"]);
  page.close();
});

test("수락·거절 안내 메시지는 5초 뒤 사라진다", () => {
  const page = openMyPage();
  assert.deepEqual(page.calls.timeouts, [5000]);
  assert.equal(page.$$(".mypage-message").length, 0);
  page.close();
});

function listHtml(count) {
  const items = Array.from({ length: count }, (_, i) => `<div data-expandable-item>신청 ${i + 1}</div>`).join("");
  return `<div data-expandable-list data-visible-count="5">${items}<button data-expandable-toggle hidden></button></div>`;
}

test("동행 신청이 5개를 넘으면 더보기로 접고 펼친다 (모바일은 3개)", () => {
  const page = openMyPage({ html: listHtml(7) });
  const visibleCount = () => page.$$("[data-expandable-item]").filter((i) => !i.hidden).length;
  const toggle = page.$("[data-expandable-toggle]");

  assert.equal(visibleCount(), 5);
  assert.equal(toggle.textContent, "더보기 (2)");
  page.click(toggle);
  assert.equal(visibleCount(), 7);
  assert.equal(toggle.textContent, "접기");
  page.close();

  const mobile = openMyPage({ html: listHtml(7), matchMedia: () => ({ matches: true }) });
  assert.equal(mobile.$("[data-expandable-toggle]").textContent, "더보기 (4)");
  mobile.close();

  const few = openMyPage({ html: listHtml(3) });
  assert.equal(few.$("[data-expandable-toggle]").hidden, true);
  few.close();
});

// ---------- 활동 상세 지도 ----------

function openActivity({ planned = [], actual = [], kakao = true } = {}) {
  return openPage({
    html: template("member/activity.html"),
    url: "http://localhost:8081/activity-detail?id=3",
    scripts: ["activity.js"],
    kakao,
    setup(window) {
      const fill = (id, points) => {
        const box = window.document.querySelector(id);
        box.replaceChildren(...points.map(([lat, lng]) => {
          const span = window.document.createElement("span");
          span.dataset.lat = lat;
          span.dataset.lng = lng;
          return span;
        }));
      };
      fill("#planned-point-data", planned);
      fill("#walk-point-data", actual);
    },
  });
}

test("추천 경로와 실제 경로를 서로 다른 선으로 그리고 범례를 보여준다", () => {
  const page = openActivity({
    planned: [["37.5440000", "127.0430000"], ["37.5450000", "127.0440000"]],
    actual: [["37.5441000", "127.0431000"], ["37.5452000", "127.0442000"], ["999", "0"]], // 범위 밖 좌표는 버림
  });
  const { polylines, markers } = page.calls.kakao;

  assert.equal(polylines.length, 2);
  assert.equal(polylines[0].options.strokeWeight, 8); // 추천 경로 (굵게)
  assert.equal(polylines[1].path.length, 2);          // 실제 경로
  assert.deepEqual(markers.map((m) => m.options.title), ["산책 시작", "산책 종료"]);
  assert.equal(page.$("#activity-map").hidden, false);
  assert.equal(page.$("#activity-planned-legend").hidden, false);
  assert.equal(page.$("#activity-actual-legend").hidden, false);
  page.close();
});

test("좌표가 없으면 안내 문구, 지도 SDK가 없으면 오류 안내를 보여준다", () => {
  const empty = openActivity();
  assert.equal(empty.$("#activity-map-status").hidden, false);
  assert.equal(empty.calls.kakao.maps.length, 0);
  empty.close();

  const noSdk = openActivity({ actual: [["37.5", "127.0"]], kakao: false });
  assert.equal(noSdk.$("#activity-map-status").textContent, "지도를 불러오지 못했어요. 잠시 후 다시 확인해주세요.");
  assert.equal(noSdk.$("#activity-map").hidden, true);
  noSdk.close();
});

test("실제 좌표 없이 추천 경로만 있으면 추천 경로 시작·도착을 표시한다", () => {
  const page = openActivity({ planned: [["37.544", "127.043"], ["37.545", "127.044"]] });
  assert.deepEqual(page.calls.kakao.markers.map((m) => m.options.title), ["추천 경로 시작", "추천 경로 도착"]);
  assert.equal(page.$("#activity-actual-legend").hidden, true);
  page.close();
});
