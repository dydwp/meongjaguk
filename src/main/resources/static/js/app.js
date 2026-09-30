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

  /* ---------- 산책 상태 공유 (홈 화면 위젯 <-> 산책 기록 화면) ----------
     localStorage로 "지금 산책 중인지", 시작 시각, 지나온 GPS 좌표를 저장해서
     홈에서 시작한 산책을 산책 기록 화면에서도, 로그인 후에도 이어서 보여줍니다.
  ------------------------------------ */
  var WALK_ACTIVE_KEY = "mungjaguk-walk-active";
  var WALK_START_KEY = "mungjaguk-walk-start";
  var WALK_POINTS_KEY = "mungjaguk-walk-points"; // [{lat, lng, t}, ...] (담당: 박용제)
  var WALK_PLANNED_KEY = "mungjaguk-walk-planned";
  var WALK_PET_IDS_KEY = "mungjaguk-walk-pet-ids"; // 산책시 함께 산책하는 반려견

  function readSelectedRoute() {
    var routeKey = new URLSearchParams(window.location.search).get("route");
    if (
      !document.querySelector("[data-auto-start-walk]") ||
      !routeKey ||
      !/^[a-zA-Z0-9-]+$/.test(routeKey)
    )
      return null;
    try {
      var route = JSON.parse(sessionStorage.getItem("walk-route:" + routeKey));
      if (
        !route ||
        !Array.isArray(route.points) ||
        route.points.length < 2 ||
        route.points.length > 5000 ||
        !Number.isFinite(route.distance_m) ||
        route.distance_m <= 0 ||
        !Number.isFinite(route.estimated_minutes) ||
        route.estimated_minutes <= 0 ||
        route.points.some(function (point) {
          return (
            !point ||
            !Number.isInteger(point.sequence) ||
            !Number.isFinite(point.latitude) ||
            !Number.isFinite(point.longitude) ||
            Math.abs(point.latitude) > 90 ||
            Math.abs(point.longitude) > 180
          );
        })
      )
        return null;
      return {
        title:
          typeof route.title === "string" && route.title.trim()
            ? route.title.trim().slice(0, 100)
            : "추천 산책로",
        description:
          typeof route.description === "string"
            ? route.description.slice(0, 1000)
            : "",
        distanceM: Math.round(route.distance_m),
        estimatedMinutes: Math.round(route.estimated_minutes),
        points: route.points.map(function (point) {
          return {
            sequence: point.sequence,
            latitude: point.latitude,
            longitude: point.longitude,
          };
        }),
      };
    } catch (error) {
      console.error("추천 산책로 보관 실패:", error);
      return null;
    }
  }

  function getPlannedRoute() {
    try {
      return JSON.parse(localStorage.getItem(WALK_PLANNED_KEY));
    } catch (error) {
      return null;
    }
  }

  function getWalkPetIds() {
    try {
      return JSON.parse(localStorage.getItem(WALK_PET_IDS_KEY)) || [];
    } catch (e) {
      return [];
    }
  }

  function getSelectedWalkPets() {
    var selectedIds = getWalkPetIds();

    if (!selectedIds.length) {
      return Promise.resolve([]);
    }

    return fetch("/api/pet-profile/pets")
      .then(function (res) {
        if (!res.ok) {
          throw new Error("반려견 목록 조회 실패: " + res.status);
        }
        return res.json();
      })
      .then(function (pets) {
        return pets.filter(function (pet) {
          return selectedIds.includes(Number(pet.id));
        });
      })
      .catch(function (err) {
        console.error(err);
        return [];
      });
  }

  function setWalkPetIds(petIds) {
    localStorage.setItem(WALK_PET_IDS_KEY, JSON.stringify(petIds || []));
  }

  function selectWalkPets() {
    return fetch("/api/pet-profile/pets")
      .then(function (res) {
        if (!res.ok) {
          throw new Error("반려견 목록 조회 실패: " + res.status);
        }
        return res.json();
      })
      .then(function (pets) {
        if (!pets.length) {
          setWalkPetIds([]);
          return true;
        }

        return openWalkPetModal(pets);
      })
      .catch(function (err) {
        console.error(err);
        alert("반려견 정보를 불러오지 못했습니다.");
        return false;
      });
  }

  function openWalkPetModal(pets) {
    return new Promise(function (resolve) {
      var overlay = document.createElement("div");
      overlay.className = "walk-pet-modal-overlay";

      var modal = document.createElement("div");
      modal.className = "walk-pet-modal";
      modal.setAttribute("role", "dialog");
      modal.setAttribute("aria-modal", "true");
      modal.setAttribute("aria-labelledby", "walkPetModalTitle");

      var title = document.createElement("h2");
      title.id = "walkPetModalTitle";
      title.textContent = "함께 산책할 반려견";

      var description = document.createElement("p");
      description.className = "walk-pet-modal-description";
      description.textContent = "함께 산책하는 반려견을 선택해주세요. 선택하지 않아도 산책할 수 있어요.";

      var list = document.createElement("div");
      list.className = "walk-pet-modal-list";

      pets.forEach(function (pet) {
        var label = document.createElement("label");
        label.className = "walk-pet-option";

        var checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.value = String(pet.id);
        checkbox.name = "walkPet";

        var photo = document.createElement("div");
        photo.className = "walk-pet-option-photo";

        if (pet.profileImage) {
          var img = document.createElement("img");
          img.src = pet.profileImage;
          img.alt = pet.name;
          photo.appendChild(img);
        } else {
          photo.textContent = "🐾";
        }

        var info = document.createElement("span");
        info.className = "walk-pet-option-info";

        var name = document.createElement("strong");
        name.textContent = pet.name;

        var summary = document.createElement("span");
        var summaryParts = [];

        if (pet.breed) summaryParts.push(pet.breed);
        if (pet.sizeLabel) summaryParts.push(pet.sizeLabel);
        if (pet.ageInYears != null) summaryParts.push(pet.ageInYears + "세");

        summary.textContent = summaryParts.join(" · ");

        info.appendChild(name);
        if (summaryParts.length) info.appendChild(summary);

        label.appendChild(checkbox);
        label.appendChild(photo);
        label.appendChild(info);
        list.appendChild(label);
      });

      var actions = document.createElement("div");
      actions.className = "walk-pet-modal-actions";

      var cancelBtn = document.createElement("button");
      cancelBtn.type = "button";
      cancelBtn.className = "btn btn-outline";
      cancelBtn.textContent = "취소";

      var startBtn = document.createElement("button");
      startBtn.type = "button";
      startBtn.className = "btn btn-primary";
      startBtn.textContent = "산책 시작";

      actions.appendChild(cancelBtn);
      actions.appendChild(startBtn);

      modal.appendChild(title);
      modal.appendChild(description);
      modal.appendChild(list);
      modal.appendChild(actions);
      overlay.appendChild(modal);
      document.body.appendChild(overlay);

      function closeModal(result) {
        overlay.remove();
        resolve(result);
      }

      cancelBtn.addEventListener("click", function () {
        closeModal(false);
      });

      startBtn.addEventListener("click", function () {
        var petIds = Array.from(
          list.querySelectorAll('input[name="walkPet"]:checked')
        ).map(function (checkbox) {
          return Number(checkbox.value);
        });

        setWalkPetIds(petIds);
        closeModal(true);
      });

      overlay.addEventListener("click", function (event) {
        if (event.target === overlay) {
          closeModal(false);
        }
      });
    });
  }

  // 메인 - 산책시작시 반려견 이름 표시
  function renderWalkPetSummary() {
    var summaryEls = document.querySelectorAll("[data-walk-pet-summary]");
    var nameEls = document.querySelectorAll("[data-walk-pet-names]");

    if (!summaryEls.length) return;

    if (!isWalking()) {
      summaryEls.forEach(function (el) {
        el.hidden = true;
      });
      return;
    }

    getSelectedWalkPets().then(function (pets) {
      if (!pets.length) {
        summaryEls.forEach(function (el) {
          el.hidden = true;
        });
        return;
      }

      var names = pets.map(function (pet) {
        return pet.name;
      }).join(" · ");

      nameEls.forEach(function (el) {
        el.textContent = names;
      });

      summaryEls.forEach(function (el) {
        el.hidden = false;
      });
    });
  }

  // 산책 기록 상세 페이지 - 반려견 카드 렌더링
  function renderWalkPetDetail() {
    var detail = document.querySelector("[data-walk-pet-detail]");
    var list = document.querySelector("[data-walk-pet-list]");

    if (!detail || !list || !isWalking()) return;

    getSelectedWalkPets().then(function (pets) {
      if (!pets.length) {
        detail.hidden = true;
        return;
      }

      list.innerHTML = "";

      pets.forEach(function (pet) {
        var card = document.createElement("div");
        card.className = "walk-current-pet-card";

        var photo = document.createElement("div");
        photo.className = "walk-current-pet-photo";

        if (pet.profileImage) {
          var img = document.createElement("img");
          img.src = pet.profileImage;
          img.alt = pet.name;
          photo.appendChild(img);
        } else {
          photo.textContent = "🐾";
        }

        var info = document.createElement("div");
        info.className = "walk-current-pet-info";

        var name = document.createElement("strong");
        name.textContent = pet.name;

        var summary = document.createElement("span");
        var summaryParts = [];

        if (pet.breed) summaryParts.push(pet.breed);
        if (pet.sizeLabel) summaryParts.push(pet.sizeLabel);
        if (pet.ageInYears != null) summaryParts.push(pet.ageInYears + "세");

        summary.textContent = summaryParts.join(" · ");

        info.appendChild(name);

        if (summaryParts.length) {
          info.appendChild(summary);
        }

        if (pet.activityLevelLabel) {
          var activity = document.createElement("span");
          activity.className = "walk-current-pet-activity";
          activity.textContent = "활동성 " + pet.activityLevelLabel;
          info.appendChild(activity);
        }

        card.appendChild(photo);
        card.appendChild(info);
        list.appendChild(card);
      });

      detail.hidden = false;
    });
  }

  // GPS 오차 걸러내기 기준 (담당: 박용제)
  // 데스크톱 브라우저의 낮은 위치 정확도를 고려해 200m 이하 좌표를 기록
  var MAX_ACCURACY_M = 200;
  // var MAX_ACCURACY_M = 50;  // 정확도가 50m보다 나쁜 위치는 버림
  var MIN_MOVE_M = 5; // 직전 좌표에서 5m 이상 움직였을 때만 기록 (제자리 흔들림 무시)

  function isWalking() {
    return localStorage.getItem(WALK_ACTIVE_KEY) === "true";
  }
  function walkStartedAt() {
    var v = localStorage.getItem(WALK_START_KEY);
    return v ? parseInt(v, 10) : Date.now();
  }
  function startWalking() {
    var selectedRoute = readSelectedRoute();
    localStorage.removeItem(WALK_PLANNED_KEY);
    if (selectedRoute) {
      localStorage.setItem(WALK_PLANNED_KEY, JSON.stringify(selectedRoute));
    }
    localStorage.setItem(WALK_ACTIVE_KEY, "true");
    localStorage.setItem(WALK_START_KEY, String(Date.now()));
    localStorage.setItem(WALK_POINTS_KEY, "[]");
  }
  function stopWalking() {
    localStorage.setItem(WALK_ACTIVE_KEY, "false");
    localStorage.removeItem(WALK_START_KEY);
    localStorage.removeItem(WALK_POINTS_KEY);
    localStorage.removeItem(WALK_PLANNED_KEY);
    localStorage.removeItem(WALK_PET_IDS_KEY);
    localStorage.removeItem("mungjaguk-walk-checklist"); // 나가기 전 체크 초기화 (home-widgets.js와 같은 저장 이름)
    window.dispatchEvent(new CustomEvent("walk:stopped")); // 지도 경로·체크 표시 지우기 (walk-map.js, home-widgets.js)
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
    var h =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(a.lat * toRad) *
      Math.cos(b.lat * toRad) *
      Math.sin(dLng / 2) *
      Math.sin(dLng / 2);
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

    var point = {
      lat: c.latitude,
      lng: c.longitude,
      t: position.timestamp || Date.now(),
    };
    var points = getWalkPoints();
    var last = points[points.length - 1];
    if (last && metersBetween(last, point) < MIN_MOVE_M) return;

    points.push(point);
    localStorage.setItem(WALK_POINTS_KEY, JSON.stringify(points));
  }

  function pad2(n) {
    return n < 10 ? "0" + n : String(n);
  }
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
        courseId: null, // 자유 산책
        startedAt: startedAt,
        endedAt: endedAt,
        distanceM: distanceM, // 실제 GPS로 잰 거리
        points: getWalkPoints(), // 지나간 좌표 목록 [{lat, lng, t}, ...]
        recommendedRoute: getPlannedRoute(),
        petIds: getWalkPetIds() // 함께 산책한 반려견 목록
      }),
    }).then(function (res) {
      if (!res.ok) throw new Error("저장 실패: " + res.status);
      return res.json();
    });
  }

  (function initWalkWidget() {
    // 메인은 왼쪽 버튼 + 오른쪽 카드 두 곳이 같이 바뀌므로 모두 토글
    var idles = document.querySelectorAll("[data-walk-idle]");
    var actives = document.querySelectorAll("[data-walk-active]");
    var startBtn = document.querySelector("[data-start-walk]");
    var endBtn = document.querySelector("[data-end-walk]");
    var cancelBtn = document.querySelector("[data-cancel-walk]"); // 저장 없이 취소 (산책 기록 화면)
    var distanceEl = document.querySelector("[data-walk-distance]"); // "0.00 km" (산책 기록 화면)
    var distanceNumEl = document.querySelector("[data-walk-distance-num]"); // 숫자만, 단위는 화면에 따로 (메인)
    var elapsedEl = document.querySelector("[data-walk-elapsed]");
    var chipEl = document.querySelector("[data-walk-chip]");
    var autoStartHost = document.querySelector("[data-auto-start-walk]");
    var timer = null;
    var watchId = null; // GPS 추적 번호 (담당: 박용제)

    if (
      !idles.length &&
      !actives.length &&
      !startBtn &&
      !endBtn &&
      !autoStartHost
    )
      return;

    if (autoStartHost) {
      if (!isWalking()) {
        selectWalkPets().then(function (canStart) { // 산책 반려견 선택
          if (!canStart) return;

          startWalking();
          render();
          renderWalkPetDetail();
        });
      } else {
        var selectedRoute = readSelectedRoute();
        if (selectedRoute) {
          localStorage.setItem(WALK_PLANNED_KEY, JSON.stringify(selectedRoute));
        }
      }
    }

    function tick() {
      var elapsed = Date.now() - walkStartedAt();
      if (elapsedEl) elapsedEl.textContent = formatElapsed(elapsed);
      if (distanceEl) distanceEl.textContent = formatDistance(walkedMeters());
      if (distanceNumEl)
        distanceNumEl.textContent = (walkedMeters() / 1000).toFixed(2);
    }

    function stopTimer() {
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
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
          window.dispatchEvent(
            new CustomEvent("walk:position", {
              detail: {
                lat: position.coords.latitude,
                lng: position.coords.longitude,
              },
            }),
          );
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
        { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 },
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
      idles.forEach(function (el) {
        el.hidden = walking;
      });
      actives.forEach(function (el) {
        el.hidden = !walking;
      });
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
      renderWalkPetSummary();
      renderWalkPetDetail();
    }

    // 산책 종료 처리 (저장·취소 여부와 상관없이 공통). redirect가 있으면 그 화면으로 이동
    function finishWalk(redirect) {
      stopTracking();
      stopWalking();
      stopTimer();
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

        selectWalkPets().then(function (canStart) {
          if (!canStart) return;

          startWalking();
          render();
        });
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
            "로그인하면 지금 산책을 이어서 저장할 수 있어요. 로그인할까요?",
          );
          if (goLogin) {
            window.location.href = "/login";
            return;
          }
          finishWalk(endBtn.getAttribute("data-end-redirect")); // 취소 → 저장 없이 종료
          return;
        }

        // [회원] 기록 저장 후 종료. 실패하면 산책을 유지해서 다시 누를 수 있게 함
        endBtn.disabled = true;
        saveWalkRecord(walkStartedAt(), Date.now(), walkedMeters())
          .then(function () {
            alert("산책 기록을 저장했어요!");
            finishWalk(endBtn.getAttribute("data-end-redirect"));
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

    // 산책 취소: 기록을 저장하지 않고 산책을 끝냄 (회원·비회원 공통)
    if (cancelBtn) {
      cancelBtn.addEventListener("click", function () {
        if (
          !confirm("산책을 취소할까요? 지금까지 걸은 기록은 저장되지 않아요.")
        )
          return;
        finishWalk(cancelBtn.getAttribute("data-cancel-redirect"));
      });
    }

    render();
  })();
})();
