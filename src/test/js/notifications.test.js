// 헤더 알림 (notifications.js) - fragments/header.html
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { openPage, template, response, settle, localDateTime } = require("./support/browser");

const CSRF_META = '<meta name="_csrf" content="token-123"><meta name="_csrf_header" content="X-CSRF-TOKEN">';

function openHeader(fetchHandler) {
  return openPage({
    html: template("fragments/header.html").replace("<head>", "<head>" + CSRF_META),
    scripts: ["notifications.js"],
    fetch: fetchHandler,
  });
}

const minutesAgo = (n) => localDateTime(n * 60000);

const ITEMS = [
  { notificationId: 3, title: "새 동행 신청이 왔어요", message: "민준님이 신청했어요.", link: "/mypage?tab=requests", read: false, createdAt: minutesAgo(0) },
  { notificationId: 2, title: "새 댓글이 달렸어요", message: "서연님: 같이 가요", link: "/course-detail-shared?meetingId=1", read: true, createdAt: minutesAgo(5) },
  { notificationId: 1, title: "동행 신청이 수락됐어요", message: "함께 걷게 됐어요.", link: null, read: true, createdAt: minutesAgo(60 * 30) },
];

test("페이지를 열면 안 읽은 알림이 있을 때만 배지를 보여준다", async () => {
  const page = openHeader(() => response({ unreadCount: 2, items: ITEMS }));
  await settle();

  assert.equal(page.$("[data-notification-badge]").hidden, false);
  assert.equal(page.$("[data-notification-toggle]").getAttribute("aria-label"), "알림 2개 안 읽음");
  assert.equal(page.calls.fetch[0].url, "/api/notifications");
  page.close();

  const none = openHeader(() => response({ unreadCount: 0, items: [] }));
  await settle();
  assert.equal(none.$("[data-notification-badge]").hidden, true);
  none.close();
});

test("종을 누르면 알림 목록을 그리고, 안 읽은 알림이 있으면 CSRF 토큰과 함께 읽음 처리한다", async () => {
  const page = openHeader((url, call) =>
    call.method === "POST" ? response(undefined, { status: 204 }) : response({ unreadCount: 1, items: ITEMS }));
  await settle();

  page.click("[data-notification-toggle]");
  await settle();

  const panel = page.$("[data-notification-panel]");
  assert.equal(panel.hidden, false);
  assert.equal(page.$("[data-notification-toggle]").getAttribute("aria-expanded"), "true");

  const items = page.$$(".notification-item");
  assert.equal(items.length, 3);
  assert.equal(items[0].tagName, "A");
  assert.equal(items[0].getAttribute("href"), "/mypage?tab=requests");
  assert.ok(items[0].classList.contains("is-unread"));
  assert.ok(!items[1].classList.contains("is-unread"));
  assert.equal(items[2].tagName, "DIV"); // 링크 없는 알림
  assert.deepEqual(
    page.$$(".notification-time").map((el) => el.textContent),
    ["방금 전", "5분 전", "어제"],
  );

  const readCall = page.calls.fetch.find((c) => c.method === "POST");
  assert.equal(readCall.url, "/api/notifications/read");
  assert.equal(readCall.headers["X-CSRF-TOKEN"], "token-123");
  assert.equal(page.$("[data-notification-badge]").hidden, true);
  page.close();
});

test("모두 읽은 상태면 읽음 요청을 보내지 않는다", async () => {
  const page = openHeader(() => response({ unreadCount: 0, items: [] }));
  await settle();
  page.click("[data-notification-toggle]");
  await settle();

  assert.equal(page.$("[data-notification-empty]").textContent, "새 알림이 없어요.");
  assert.equal(page.calls.fetch.filter((c) => c.method === "POST").length, 0);
  page.close();
});

test("알림을 불러오지 못하면 안내 문구를 보여준다 (로그아웃되어 로그인 화면으로 돌려진 경우 포함)", async () => {
  const page = openHeader(() => response({}, { status: 200, redirected: true }));
  await settle();
  page.click("[data-notification-toggle]");
  await settle();

  assert.equal(page.$("[data-notification-empty]").textContent, "알림을 불러오지 못했어요.");
  page.close();
});

test("바깥을 누르거나 Esc를 누르면 알림 창이 닫힌다", async () => {
  const page = openHeader(() => response({ unreadCount: 0, items: [] }));
  await settle();
  const panel = page.$("[data-notification-panel]");

  page.click("[data-notification-toggle]");
  await settle();
  page.click(page.document.body);
  assert.equal(panel.hidden, true);

  page.click("[data-notification-toggle]");
  await settle();
  page.document.dispatchEvent(new page.window.KeyboardEvent("keydown", { key: "Escape" }));
  assert.equal(panel.hidden, true);
  assert.equal(page.$("[data-notification-toggle]").getAttribute("aria-expanded"), "false");

  page.click("[data-notification-toggle]");
  page.click("[data-notification-toggle]"); // 다시 누르면 닫힘
  assert.equal(panel.hidden, true);
  page.close();
});
