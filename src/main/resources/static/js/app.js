/* ==========================================================================
   멍자국 — 공통 인터랙션 스크립트
   빌드 도구 없이 순수 JS로, 페이지마다 필요한 요소가 있을 때만 동작합니다.
   ========================================================================== */
(function () {
  "use strict";

  /* ---------- 필(Pill) 단일 선택: 크기/활동성 선택 ---------- */
  document.querySelectorAll("[data-pill-group]").forEach(function (group) {
    group.addEventListener("click", function (e) {
      var btn = e.target.closest(".pill");
      if (!btn || !group.contains(btn)) return;
      group.querySelectorAll(".pill").forEach(function (p) {
        p.classList.remove("selected");
        p.setAttribute("aria-pressed", "false");
      });
      btn.classList.add("selected");
      btn.setAttribute("aria-pressed", "true");
    });
  });

  /* ---------- 탭 전환: 마이페이지 ---------- */
  document.querySelectorAll("[data-tabs]").forEach(function (tabsEl) {
    var tabs = tabsEl.querySelectorAll(".tab");
    tabs.forEach(function (tab) {
      tab.addEventListener("click", function () {
        var target = tab.getAttribute("data-tab-target");
        tabs.forEach(function (t) { t.classList.remove("active"); });
        tab.classList.add("active");
        document.querySelectorAll("[data-tab-panel]").forEach(function (panel) {
          panel.hidden = panel.getAttribute("data-tab-panel") !== target;
        });
      });
    });
  });

  /* ---------- 동행 신청 토글 버튼 ---------- */
  document.querySelectorAll("[data-join-btn]").forEach(function (btn) {
    btn.addEventListener("click", function () {
      var joined = btn.getAttribute("data-joined") === "true";
      if (!joined) {
        btn.setAttribute("data-joined", "true");
        btn.textContent = "✓ 신청 완료";
        btn.classList.add("is-done");
        var countEl = document.querySelector("[data-join-count]");
        if (countEl) {
          var parts = countEl.textContent.split("/");
          var n = parseInt(parts[0], 10) + 1;
          countEl.textContent = n + "/" + parts[1];
        }
      } else {
        btn.setAttribute("data-joined", "false");
        btn.textContent = "동행 신청";
        btn.classList.remove("is-done");
        var countEl2 = document.querySelector("[data-join-count]");
        if (countEl2) {
          var parts2 = countEl2.textContent.split("/");
          var n2 = Math.max(0, parseInt(parts2[0], 10) - 1);
          countEl2.textContent = n2 + "/" + parts2[1];
        }
      }
    });
  });

  /* ---------- 신고 링크 ---------- */
  document.querySelectorAll("[data-report-link]").forEach(function (link) {
    link.addEventListener("click", function (e) {
      e.preventDefault();
      link.textContent = "신고가 접수되었어요";
      link.style.pointerEvents = "none";
      link.style.textDecoration = "none";
    });
  });

  /* ---------- 댓글 등록 ---------- */
  document.querySelectorAll("[data-comment-form]").forEach(function (form) {
    var input = form.querySelector("input");
    var btn = form.querySelector("button");
    var list = document.querySelector("[data-comment-list]");
    var countEl = document.querySelector("[data-comment-count]");

    function submit() {
      var val = input.value.trim();
      if (!val || !list) return;
      var item = document.createElement("div");
      item.className = "comment";
      item.innerHTML =
        '<div class="avatar avatar-sm">나</div>' +
        '<div><span class="name">나<span class="time">· 방금 전</span></span>' +
        "<p></p></div>";
      item.querySelector("p").textContent = val;
      list.appendChild(item);
      input.value = "";
      input.focus();
      if (countEl) countEl.textContent = String(parseInt(countEl.textContent || "0", 10) + 1);
    }

    btn.addEventListener("click", submit);
    input.addEventListener("keydown", function (e) {
      if (e.key === "Enter") submit();
    });
  });

  /* ---------- 신청 수락/거절 ---------- */
  document.querySelectorAll("[data-request-item]").forEach(function (item) {
    var acceptBtn = item.querySelector("[data-accept]");
    var rejectBtn = item.querySelector("[data-reject]");
    var actions = item.querySelector(".request-actions");

    function resolve(label) {
      actions.innerHTML = '<span class="tag-neutral">' + label + "</span>";
      item.style.opacity = "0.7";
    }

    if (acceptBtn) acceptBtn.addEventListener("click", function () { resolve("수락됨"); });
    if (rejectBtn) rejectBtn.addEventListener("click", function () { resolve("거절됨"); });
  });

  /* ---------- 산책 상태 공유 (홈 화면 위젯 <-> 산책 기록 화면) ----------
     localStorage로 "지금 산책 중인지"와 시작 시각을 저장해서,
     홈에서 시작한 산책을 산책 기록 화면에서도, 산책 기록 화면에서 시작한(또는
     이어지는) 산책을 홈 위젯에서도 같은 상태로 보여줍니다.
  ------------------------------------ */
  var WALK_ACTIVE_KEY = "mungjaguk-walk-active";
  var WALK_START_KEY = "mungjaguk-walk-start";

  function isWalking() {
    return localStorage.getItem(WALK_ACTIVE_KEY) === "true";
  }
  function walkStartedAt() {
    var v = localStorage.getItem(WALK_START_KEY);
    return v ? parseInt(v, 10) : Date.now();
  }
  function startWalking() {
    localStorage.setItem(WALK_ACTIVE_KEY, "true");
    localStorage.setItem(WALK_START_KEY, String(Date.now()));
  }
  function stopWalking() {
    localStorage.setItem(WALK_ACTIVE_KEY, "false");
    localStorage.removeItem(WALK_START_KEY);
  }
  function pad2(n) { return n < 10 ? "0" + n : String(n); }
  function formatElapsed(ms) {
    var totalSec = Math.max(0, Math.floor(ms / 1000));
    var m = Math.floor(totalSec / 60);
    var s = totalSec % 60;
    return pad2(m) + ":" + pad2(s);
  }
  function formatDistance(ms) {
    var minutes = Math.max(0, ms / 60000);
    var km = minutes * 0.08; // 데모용 — 도보 평균 속도(~4.8km/h) 가정한 시뮬레이션 값
    return km.toFixed(1) + " km";
  }

  (function initWalkWidget() {
    var idle = document.querySelector("[data-walk-idle]");
    var active = document.querySelector("[data-walk-active]");
    var startBtn = document.querySelector("[data-start-walk]");
    var endBtn = document.querySelector("[data-end-walk]");
    var distanceEl = document.querySelector("[data-walk-distance]");
    var elapsedEl = document.querySelector("[data-walk-elapsed]");
    var chipEl = document.querySelector("[data-walk-chip]");
    var autoStartHost = document.querySelector("[data-auto-start-walk]");
    var timer = null;

    if (!idle && !active && !startBtn && !endBtn && !autoStartHost) return;

    if (autoStartHost && !isWalking()) startWalking();

    function tick() {
      var elapsed = Date.now() - walkStartedAt();
      if (elapsedEl) elapsedEl.textContent = formatElapsed(elapsed);
      if (distanceEl) distanceEl.textContent = formatDistance(elapsed);
    }

    function stopTimer() {
      if (timer) { clearInterval(timer); timer = null; }
    }

    function render() {
      var walking = isWalking();
      if (idle) idle.hidden = walking;
      if (active) active.hidden = !walking;
      if (chipEl) {
        chipEl.textContent = walking ? "실시간 위치 추적 중" : "지도 미리보기";
        chipEl.classList.toggle("floating", walking);
      }
      stopTimer();
      if (walking) {
        tick();
        timer = setInterval(tick, 1000);
      }
    }

    if (startBtn) {
      startBtn.addEventListener("click", function () {
        startWalking();
        render();
      });
    }
    if (endBtn) {
      endBtn.addEventListener("click", function () {
        stopWalking();
        stopTimer();
        var redirect = endBtn.getAttribute("data-end-redirect");
        if (redirect) {
          window.location.href = redirect;
        } else {
          render();
        }
      });
    }

    render();
  })();

  /* ---------- 다크 모드 토글 (있는 경우) ---------- */
  var themeToggle = document.getElementById("themeToggle");
  if (themeToggle) {
    var root = document.documentElement;
    var stored = localStorage.getItem("mungjaguk-theme");
    if (stored) root.setAttribute("data-theme", stored);
    var syncLabel = function () {
      var isDark =
        root.getAttribute("data-theme") === "dark" ||
        (!root.getAttribute("data-theme") && window.matchMedia("(prefers-color-scheme: dark)").matches);
      themeToggle.textContent = isDark ? "☀️ 라이트 모드" : "🌙 다크 모드";
    };
    themeToggle.addEventListener("click", function () {
      var next = root.getAttribute("data-theme") === "dark" ? "light" : "dark";
      root.setAttribute("data-theme", next);
      localStorage.setItem("mungjaguk-theme", next);
      syncLabel();
    });
    syncLabel();
  }
})();
