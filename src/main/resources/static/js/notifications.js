/**
 * 헤더 알림 (종 아이콘) - 담당: 박용제
 *  - 페이지를 열면 안 읽은 알림 수를 배지로 표시
 *  - 종을 누르면 아래로 알림 창이 열리고 최신순 10개 표시 → 열면 전부 읽음 처리
 *  - 바깥을 누르거나 Esc를 누르면 닫힘
 * 지금은 "동행 신청 수락" 알림만 있습니다. (NotificationController)
 */
(function () {
  "use strict";

  var root = document.querySelector("[data-notifications]");
  if (!root) return;
  var toggle = root.querySelector("[data-notification-toggle]");
  var badge = root.querySelector("[data-notification-badge]");
  var panel = root.querySelector("[data-notification-panel]");
  var list = root.querySelector("[data-notification-list]");
  var empty = root.querySelector("[data-notification-empty]");

  function csrfHeaders() {
    var headers = {};
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header && token.content) headers[header.content] = token.content;
    return headers;
  }

  // 안 읽은 알림이 있으면 빨간 점만 표시 (개수는 화면에 안 쓰고 스크린리더용 이름에만)
  function setBadge(count) {
    badge.hidden = !count;
    toggle.setAttribute("aria-label", count ? "알림 " + count + "개 안 읽음" : "알림");
  }

  // "방금 전", "5분 전", "3시간 전", "어제", "9/28"
  function timeText(value) {
    var date = new Date(value);
    if (isNaN(date)) return "";
    var diffMin = Math.floor((Date.now() - date.getTime()) / 60000);
    if (diffMin < 1) return "방금 전";
    if (diffMin < 60) return diffMin + "분 전";
    if (diffMin < 60 * 24) return Math.floor(diffMin / 60) + "시간 전";
    if (diffMin < 60 * 48) return "어제";
    return (date.getMonth() + 1) + "/" + date.getDate();
  }

  function render(items) {
    list.replaceChildren();
    if (!items.length) {
      empty.textContent = "새 알림이 없어요.";
      empty.hidden = false;
      return;
    }
    empty.hidden = true;
    items.forEach(function (item) {
      var li = document.createElement("li");
      var el = document.createElement(item.link ? "a" : "div");
      el.className = "notification-item" + (item.read ? "" : " is-unread");
      if (item.link) el.href = item.link;

      var title = document.createElement("strong");
      title.textContent = item.title;
      var message = document.createElement("span");
      message.className = "notification-message";
      message.textContent = item.message;
      var time = document.createElement("span");
      time.className = "notification-time";
      time.textContent = timeText(item.createdAt);

      el.append(title, message, time);
      li.appendChild(el);
      list.appendChild(li);
    });
  }

  function load() {
    return fetch("/api/notifications", { headers: { Accept: "application/json" }, credentials: "same-origin" })
      .then(function (res) {
        if (!res.ok || res.redirected) throw new Error("Notification request failed: " + res.status);
        return res.json();
      });
  }

  function markAllRead() {
    return fetch("/api/notifications/read", {
      method: "POST",
      headers: csrfHeaders(),
      credentials: "same-origin"
    }).then(function (res) {
      if (res.ok) setBadge(0);
    });
  }

  function open() {
    panel.hidden = false;
    toggle.setAttribute("aria-expanded", "true");
    empty.textContent = "알림을 불러오는 중이에요.";
    empty.hidden = false;
    list.replaceChildren();
    load()
      .then(function (data) {
        render(data.items || []);
        // 이번에 열 때는 안 읽은 알림을 강조해서 보여주고, 서버에는 읽음으로 저장
        if (data.unreadCount > 0) return markAllRead();
      })
      .catch(function (err) {
        console.error("알림 조회 실패:", err);
        empty.textContent = "알림을 불러오지 못했어요.";
        empty.hidden = false;
      });
  }

  function close() {
    panel.hidden = true;
    toggle.setAttribute("aria-expanded", "false");
  }

  toggle.addEventListener("click", function (e) {
    e.stopPropagation();
    if (panel.hidden) open();
    else close();
  });
  document.addEventListener("click", function (e) {
    if (!panel.hidden && !root.contains(e.target)) close();
  });
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && !panel.hidden) {
      close();
      toggle.focus();
    }
  });

  // 처음: 안 읽은 수만 배지로
  load()
    .then(function (data) { setBadge(data.unreadCount); })
    .catch(function (err) { console.warn("알림 수 조회 실패:", err); });
})();
