(function () {
  "use strict";

  /* ---------- 마이페이지 알림 메시지 ---------- */
  function clearMypageMessages() {
    document.querySelectorAll(".mypage-message").forEach(function (message) {
      message.remove();
    });
  }

  // 알림은 5초 후 자동 제거
  if (document.querySelector(".mypage-message")) {
    setTimeout(clearMypageMessages, 5000);
  }

  // 뒤로가기/앞으로가기로 캐시된 마이페이지가 복원될 때 이전 알림 제거
  window.addEventListener("pageshow", function (event) {
    if (event.persisted) {
      clearMypageMessages();
    }
  });

  /* ---------- 마이페이지 탭 ---------- */
  document.querySelectorAll("[data-tabs]").forEach(function (tabsEl) {
    var tabs = tabsEl.querySelectorAll(".tab");

    tabs.forEach(function (tab) {
      tab.addEventListener("click", function () {
        var target = tab.getAttribute("data-tab-target");

        tabs.forEach(function (item) {
          item.classList.remove("active");
        });
        tab.classList.add("active");

        document.querySelectorAll("[data-tab-panel]").forEach(function (panel) {
          panel.hidden = panel.getAttribute("data-tab-panel") !== target;
        });

        clearMypageMessages();
      });
    });
  });
})();