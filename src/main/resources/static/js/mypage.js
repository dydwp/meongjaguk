(function () {
  "use strict";

  /* ---------- 마이페이지 알림 메시지 ---------- */
  function clearMypageMessages() {
    document.querySelectorAll(".mypage-message").forEach(function (message) {
      message.remove();
    });
  }

  if (document.querySelector(".mypage-message")) {
    setTimeout(clearMypageMessages, 5000);
  }

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
        var refreshUrl = tab.getAttribute("data-refresh-url");

        clearMypageMessages();

        if (refreshUrl) {
          window.location.href = refreshUrl;
          return;
        }

        tabs.forEach(function (item) {
          item.classList.remove("active");
        });
        tab.classList.add("active");

        document.querySelectorAll("[data-tab-panel]").forEach(function (panel) {
          panel.hidden = panel.getAttribute("data-tab-panel") !== target;
        });

        var url = new URL(window.location.href);
        url.searchParams.set("tab", target);
        url.searchParams.delete("petId");
        window.history.replaceState(null, "", url);
      });
    });
  });

  /* ---------- 동행 신청 목록 더보기 ---------- */
  document.querySelectorAll("[data-expandable-list]").forEach(function (list) {
    var desktopLimit = parseInt(list.getAttribute("data-visible-count") || "5", 10);
    var items = Array.from(list.children).filter(function (item) {
      return item.hasAttribute("data-expandable-item");
    });
    var toggle = list.querySelector("[data-expandable-toggle]");
    var expanded = false;

    function getLimit() {
      return window.matchMedia("(max-width: 520px)").matches ? 3 : desktopLimit;
    }

    function render() {
      var limit = getLimit();

      items.forEach(function (item, index) {
        item.hidden = !expanded && index >= limit;
      });

      if (!toggle) {
        return;
      }

      if (items.length <= limit) {
        toggle.hidden = true;
        return;
      }

      toggle.hidden = false;
      toggle.textContent = expanded
        ? "접기"
        : "더보기 (" + (items.length - limit) + ")";
    }

    if (!toggle) {
      return;
    }

    toggle.addEventListener("click", function () {
      expanded = !expanded;
      render();
    });

    window.addEventListener("resize", render);
    render();
  });
})();