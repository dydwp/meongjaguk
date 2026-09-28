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
     localStorage로 "지금 산책 중인지", 시작 시각, 지나온 GPS 좌표를 저장해서
     홈에서 시작한 산책을 산책 기록 화면에서도, 로그인 후에도 이어서 보여줍니다.
  ------------------------------------ */
  var WALK_ACTIVE_KEY = "mungjaguk-walk-active";
  var WALK_START_KEY = "mungjaguk-walk-start";
  var WALK_POINTS_KEY = "mungjaguk-walk-points"; // [{lat, lng, t}, ...] (담당: 박용제)

  // GPS 오차 걸러내기 기준 (담당: 박용제)
  var MAX_ACCURACY_M = 50;  // 정확도가 50m보다 나쁜 위치는 버림
  var MIN_MOVE_M = 5;       // 직전 좌표에서 5m 이상 움직였을 때만 기록 (제자리 흔들림 무시)

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
    localStorage.setItem(WALK_POINTS_KEY, "[]");
  }
  function stopWalking() {
    localStorage.setItem(WALK_ACTIVE_KEY, "false");
    localStorage.removeItem(WALK_START_KEY);
    localStorage.removeItem(WALK_POINTS_KEY);
    window.dispatchEvent(new CustomEvent("walk:stopped")); // 지도에서 경로 지우기 (walk-map.js)
  }

  /* ----- GPS 좌표 저장/거리 계산 (담당: 박용제) ----- */
  function getWalkPoints() {
    try {
      return JSON.parse(localStorage.getItem(WALK_POINTS_KEY)) || [];
    } catch (e) {
      return [];
    }
  }
  // 두 좌표 사이 거리(m) - 하버사인 공식
  function metersBetween(a, b) {
    var R = 6371000;
    var toRad = Math.PI / 180;
    var dLat = (b.lat - a.lat) * toRad;
    var dLng = (b.lng - a.lng) * toRad;
    var h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(a.lat * toRad) * Math.cos(b.lat * toRad) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return R * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
  }
  // 지금까지 걸은 거리(m) = 좌표들 사이 거리를 모두 더한 값
  function walkedMeters() {
    var points = getWalkPoints();
    var total = 0;
    for (var i = 1; i < points.length; i++) {
      total += metersBetween(points[i - 1], points[i]);
    }
    return Math.round(total);
  }
  // 새 위치가 들어오면 오차를 걸러서 좌표 목록에 추가
  function addWalkPoint(position) {
    if (!isWalking()) return;
    var c = position.coords;
    if (c.accuracy > MAX_ACCURACY_M) return;

    var point = { lat: c.latitude, lng: c.longitude, t: position.timestamp || Date.now() };
    var points = getWalkPoints();
    var last = points[points.length - 1];
    if (last && metersBetween(last, point) < MIN_MOVE_M) return;

    points.push(point);
    localStorage.setItem(WALK_POINTS_KEY, JSON.stringify(points));
  }

  function pad2(n) { return n < 10 ? "0" + n : String(n); }
  function formatElapsed(ms) {
    var totalSec = Math.max(0, Math.floor(ms / 1000));
    var m = Math.floor(totalSec / 60);
    var s = totalSec % 60;
    return pad2(m) + ":" + pad2(s);
  }
  function formatDistance(meters) {
    return (meters / 1000).toFixed(2) + " km";
  }

  /* ---------- 산책 기록 저장 (담당: 박용제) ----------
     회원이 산책을 종료하면 POST /api/walks 로 기록을 보냄
  ------------------------------------ */
  function saveWalkRecord(startedAt, endedAt, distanceM) {
    var headers = { "Content-Type": "application/json" };
    var tokenMeta = document.querySelector('meta[name="_csrf"]');
    var headerMeta = document.querySelector('meta[name="_csrf_header"]');
    if (tokenMeta && headerMeta && tokenMeta.content) {
      headers[headerMeta.content] = tokenMeta.content; // 스프링 시큐리티 보안 토큰
    }

    return fetch("/api/walks", {
      method: "POST",
      headers: headers,
      body: JSON.stringify({
        courseId: null,                 // 자유 산책
        startedAt: startedAt,
        endedAt: endedAt,
        distanceM: distanceM,           // 실제 GPS로 잰 거리
        points: getWalkPoints()         // 지나간 좌표 목록 [{lat, lng, t}, ...]
      })
    }).then(function (res) {
      if (!res.ok) throw new Error("저장 실패: " + res.status);
      return res.json();
    });
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
    var watchId = null;   // GPS 추적 번호 (담당: 박용제)

    if (!idle && !active && !startBtn && !endBtn && !autoStartHost) return;

    if (autoStartHost && !isWalking()) startWalking();

    function tick() {
      var elapsed = Date.now() - walkStartedAt();
      if (elapsedEl) elapsedEl.textContent = formatElapsed(elapsed);
      if (distanceEl) distanceEl.textContent = formatDistance(walkedMeters());
    }

    function stopTimer() {
      if (timer) { clearInterval(timer); timer = null; }
    }

    /* ----- GPS 추적 시작/중지 (담당: 박용제) ----- */
    function setChip(text) {
      if (chipEl) chipEl.textContent = text;
    }
    function startTracking() {
      if (watchId !== null) return;
      if (!navigator.geolocation) {
        setChip("이 브라우저는 위치 기능을 지원하지 않아요");
        return;
      }
      watchId = navigator.geolocation.watchPosition(
        function (position) {
          addWalkPoint(position);
          // 지도에 현재 위치·경로 다시 그리기 (walk-map.js)
          window.dispatchEvent(new CustomEvent("walk:position", {
            detail: { lat: position.coords.latitude, lng: position.coords.longitude }
          }));
          setChip("실시간 위치 추적 중");
          tick();
        },
        function (err) {
          if (err.code === err.PERMISSION_DENIED) {
            setChip("위치 권한을 허용해야 거리가 기록돼요");
          } else {
            setChip("위치를 찾는 중이에요");
          }
        },
        { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 }
      );
    }
    function stopTracking() {
      if (watchId !== null) {
        navigator.geolocation.clearWatch(watchId);
        watchId = null;
      }
    }

    function render() {
      var walking = isWalking();
      if (idle) idle.hidden = walking;
      if (active) active.hidden = !walking;
      if (chipEl) {
        chipEl.textContent = walking ? "위치를 찾는 중이에요" : "지도 미리보기";
        chipEl.classList.toggle("floating", walking);
      }
      stopTimer();
      if (walking) {
        startTracking();
        tick();
        timer = setInterval(tick, 1000);
      } else {
        stopTracking();
      }
    }

    // 산책 종료 처리 (저장 여부와 상관없이 공통)
    function finishWalk() {
      stopTracking();
      stopWalking();
      stopTimer();
      var redirect = endBtn.getAttribute("data-end-redirect");
      if (redirect) {
        window.location.href = redirect;
      } else {
        render();
      }
    }

    if (startBtn) {
      startBtn.addEventListener("click", function () {
        // [비회원] 산책 기록은 회원만 가능 → 로그인으로 안내 (담당: 박용제)
        if (startBtn.getAttribute("data-login-required") === "true") {
          if (confirm("산책을 기록하려면 로그인이 필요해요. 로그인할까요?")) {
            window.location.href = "/login";
          }
          return;
        }
        startWalking();
        render();
      });
    }
    if (endBtn) {
      endBtn.addEventListener("click", function () {
        // [비회원] 기록 저장은 로그인이 필요 (담당: 박용제)
        // 확인 → 산책을 끝내지 않고 로그인으로 이동. 산책 상태는 localStorage에
        //        남아 있어서 로그인 후 메인으로 돌아오면 산책이 그대로 이어짐
        // 취소 → 저장 없이 산책 종료
        if (endBtn.getAttribute("data-login-required") === "true") {
          var goLogin = confirm(
            "산책 기록을 저장하려면 로그인이 필요해요.\n" +
            "로그인하면 지금 산책을 이어서 저장할 수 있어요. 로그인할까요?"
          );
          if (goLogin) {
            window.location.href = "/login";
            return;
          }
          finishWalk(); // 취소 → 저장 없이 종료
          return;
        }

        // [회원] 기록 저장 후 종료. 실패하면 산책을 유지해서 다시 누를 수 있게 함
        endBtn.disabled = true;
        saveWalkRecord(walkStartedAt(), Date.now(), walkedMeters())
          .then(function () {
            alert("산책 기록을 저장했어요!");
            finishWalk();
          })
          .catch(function (err) {
            console.error(err);
            alert("산책 기록 저장에 실패했어요. 잠시 후 다시 눌러주세요.");
          })
          .finally(function () {
            endBtn.disabled = false;
          });
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