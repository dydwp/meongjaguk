/* ==========================================================================
   멍자국 — 산책로 게시판 (목록 / 공유 산책로 상세 / 동행 신청·취소)
   담당: 김환중
   ========================================================================== */
(function () {
  "use strict";

  var STATUS_LABEL = {
    RECRUITING: "모집 중",
    CLOSED: "모집 마감",
    IN_PROGRESS: "산책 중",
    COMPLETED: "완료"
  };

  // 목록 카드 지도 미리보기 경로 (기존 목업 경로 재사용)
  var PREVIEW_PATHS = [
    "M40 100 C 90 70, 130 95, 170 65 S 260 40, 320 30",
    "M30 40 C 90 70, 140 30, 190 55 S 280 90, 350 60",
    "M40 90 C 80 50, 150 85, 200 55 S 280 30, 330 45",
    "M30 60 C 80 30, 130 75, 190 50 S 260 20, 340 55",
    "M40 50 C 100 90, 150 40, 210 70 S 290 100, 330 70",
    "M30 70 C 80 40, 140 80, 200 55 S 270 30, 340 50"
  ];

  /* ---------- 공통 유틸 ---------- */
  function pad2(n) { return n < 10 ? "0" + n : String(n); }

  function formatDistance(distanceM, minutes) {
    var parts = [];
    if (distanceM != null) parts.push((distanceM / 1000).toFixed(1) + "km");
    if (minutes != null) parts.push("약 " + minutes + "분");
    return parts.join(" · ");
  }

  // "2026-09-27", "19:00:00" → "2026.09.27 19:00"
  function formatMeetingDateTime(date, time) {
    var d = date ? date.replace(/-/g, ".") : "";
    var t = time ? time.substring(0, 5) : "";
    return [d, t].filter(Boolean).join(" ");
  }

  // "2026-09-20T18:30:00" → "2026.09.20"
  function formatDate(dateTime) {
    return dateTime ? dateTime.substring(0, 10).replace(/-/g, ".") : "";
  }

  // 방금 전 / n분 전 / n시간 전 / 어제 / n일 전
  function formatRelative(dateTime) {
    if (!dateTime) return "";
    var diff = Date.now() - new Date(dateTime).getTime();
    var min = Math.floor(diff / 60000);
    if (min < 1) return "방금 전";
    if (min < 60) return min + "분 전";
    var hour = Math.floor(min / 60);
    if (hour < 24) return hour + "시간 전";
    var day = Math.floor(hour / 24);
    if (day < 2) return "어제";
    return day + "일 전";
  }

  function statusTag(status) {
    var span = document.createElement("span");
    span.className = status === "CLOSED" ? "tag-neutral" : "tag-secondary";
    span.textContent = STATUS_LABEL[status] || status;
    return span;
  }

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }

  /* ======================================================================
     산책로 게시판 목록 (/board)
     ====================================================================== */
  var boardList = document.querySelector("[data-meeting-list]");
  if (boardList) {
    loadMeetings();
  }

  function loadMeetings() {
    fetch("/api/meetings")
      .then(function (res) {
        if (!res.ok) throw new Error("status " + res.status);
        return res.json();
      })
      .then(function (meetings) {
        if (meetings.length === 0) {
          boardList.replaceChildren(el("p", "text-muted", "아직 공유된 산책로가 없어요."));
          return;
        }
        boardList.replaceChildren.apply(boardList, meetings.map(createMeetingCard));
      })
      .catch(function (err) {
        console.error("게시글을 불러오지 못했습니다.", err);
        boardList.replaceChildren(el("p", "text-muted", "게시글을 불러오지 못했어요. 잠시 후 다시 시도해주세요."));
      });
  }

  function createMeetingCard(meeting, index) {
    var card = document.createElement("a");
    card.className = "board-card";
    card.href = "/course-detail-shared?meetingId=" + encodeURIComponent(meeting.meetingId);

    card.innerHTML =
      '<div class="map-preview">' +
      '  <svg viewBox="0 0 400 130" preserveAspectRatio="none">' +
      '    <path fill="none" stroke="var(--color-primary)" stroke-width="3" stroke-dasharray="7 7" stroke-linecap="round"/>' +
      "  </svg>" +
      '  <span class="map-chip">지도 미리보기</span>' +
      '  <span class="map-tag"></span>' +
      "</div>" +
      '<div class="board-body">' +
      "  <h3></h3>" +
      '  <p class="meta" data-host></p>' +
      '  <p class="meta" data-when></p>' +
      '  <p class="meta mb-0" data-distance></p>' +
      "</div>";

    card.querySelector("path").setAttribute("d", PREVIEW_PATHS[index % PREVIEW_PATHS.length]);
    card.querySelector(".map-tag").textContent =
      "참여 " + meeting.currentParticipants + "/" + meeting.maxParticipants;

    var title = card.querySelector("h3");
    title.textContent = meeting.title + " ";
    title.appendChild(statusTag(meeting.status));

    card.querySelector("[data-host]").textContent =
      meeting.hostNickname + " · " + formatRelative(meeting.createdAt);
    card.querySelector("[data-when]").textContent =
      formatMeetingDateTime(meeting.meetingDate, meeting.meetingTime);
    card.querySelector("[data-distance]").textContent =
      formatDistance(meeting.distanceM, meeting.estimatedMinutes);

    return card;
  }

  /* ======================================================================
     공유 산책로 상세 (/course-detail-shared?meetingId={id})
     ====================================================================== */
  var detailRoot = document.querySelector("[data-meeting-detail]");
  if (detailRoot) {
    initDetail();
  }

  function initDetail() {
    var meetingId = new URLSearchParams(window.location.search).get("meetingId");
    if (!meetingId) {
      showNotFound("모집 정보를 찾을 수 없어요.");
      return;
    }

    var loggedIn = detailRoot.getAttribute("data-logged-in") === "true";
    var csrfHeader = detailRoot.getAttribute("data-csrf-header");
    var csrfToken = detailRoot.getAttribute("data-csrf-token");
    var applyBtn = detailRoot.querySelector("[data-apply-btn]");

    fetch("/api/meetings/" + encodeURIComponent(meetingId))
      .then(function (res) {
        if (res.status === 404) {
          showNotFound("모집 정보를 찾을 수 없어요.");
          return null;
        }
        if (!res.ok) throw new Error("status " + res.status);
        return res.json();
      })
      .then(function (meeting) {
        if (!meeting) return;
        renderDetail(meeting);
        renderApplyButton(meeting.myApplicationStatus);
        detailRoot.hidden = false;
      })
      .catch(function (err) {
        console.error("모집 정보를 불러오지 못했습니다.", err);
        showNotFound("모집 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.");
      });

    /* ---------- 동행 신청 / 취소 ---------- */
    applyBtn.addEventListener("click", function () {
      if (!loggedIn) {
        window.location.href = "/login";
        return;
      }
      var current = applyBtn.getAttribute("data-application-status");
      if (!current) {
        requestApplication("POST", "PENDING");
      } else if (current === "PENDING") {
        requestApplication("DELETE", "");
      }
    });

    function requestApplication(method, nextStatus) {
      var headers = {};
      if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;

      applyBtn.disabled = true;
      fetch("/api/meetings/" + encodeURIComponent(meetingId) + "/applications", {
        method: method,
        headers: headers
      })
        .then(function (res) {
          if (res.redirected || res.status === 401) {
            window.location.href = "/login";
            return;
          }
          if (res.ok) {
            renderApplyButton(nextStatus);
            return;
          }
          return res.json()
            .catch(function () { return {}; })
            .then(function (body) {
              alert(body.message || "요청을 처리하지 못했어요. 잠시 후 다시 시도해주세요.");
              renderApplyButton(applyBtn.getAttribute("data-application-status"));
            });
        })
        .catch(function (err) {
          console.error("동행 신청 요청 실패", err);
          alert("요청을 처리하지 못했어요. 잠시 후 다시 시도해주세요.");
          renderApplyButton(applyBtn.getAttribute("data-application-status"));
        });
    }

    // 신청 상태별 버튼: 없음 → 동행 신청 / PENDING → 신청 완료(다시 누르면 취소) / ACCEPTED·REJECTED → 비활성
    function renderApplyButton(status) {
      applyBtn.setAttribute("data-application-status", status || "");
      applyBtn.classList.toggle("is-done", !!status);
      applyBtn.disabled = status === "ACCEPTED" || status === "REJECTED";

      if (status === "PENDING") applyBtn.textContent = "✓ 신청 완료";
      else if (status === "ACCEPTED") applyBtn.textContent = "수락됨";
      else if (status === "REJECTED") applyBtn.textContent = "거절됨";
      else applyBtn.textContent = "동행 신청";
    }
  }

  function renderDetail(meeting) {
    var q = function (sel) { return detailRoot.querySelector(sel); };

    q("[data-status]").replaceChildren(statusTag(meeting.status));
    q("[data-title]").textContent = meeting.title;
    q("[data-course-name]").textContent = "코스 · " + meeting.courseName;
    q("[data-distance]").textContent =
      "거리 · " + (meeting.distanceM != null ? (meeting.distanceM / 1000).toFixed(1) + "km" : "-");
    q("[data-minutes]").textContent =
      "예상 소요시간 · " + (meeting.estimatedMinutes != null ? "약 " + meeting.estimatedMinutes + "분" : "-");
    q("[data-when]").textContent =
      "모임 일시 · " + formatMeetingDateTime(meeting.meetingDate, meeting.meetingTime);
    q("[data-pet]").textContent = "반려견 동반 · " + (meeting.petRequired ? "필수" : "선택");

    var condition = q("[data-condition]");
    if (meeting.participationCondition) {
      condition.textContent = "참여 조건 · " + meeting.participationCondition;
    } else {
      condition.remove();
    }

    var description = q("[data-description]");
    if (meeting.description) description.textContent = meeting.description;
    else description.remove();

    q("[data-host-avatar]").textContent = meeting.hostNickname.charAt(0);
    q("[data-host-name]").textContent = meeting.hostNickname;
    q("[data-shared-at]").textContent = formatDate(meeting.createdAt) + " 공유";

    var avatars = q("[data-participant-avatars]");
    avatars.replaceChildren.apply(avatars, meeting.participantNicknames.map(function (name, i, arr) {
      var avatar = el("div", i === 0 ? "avatar avatar-sm" : "avatar avatar-sm avatar-muted", name.charAt(0));
      avatar.style.border = "2px solid var(--color-surface)";
      if (i < arr.length - 1) avatar.style.marginRight = "-8px";
      return avatar;
    }));
    q("[data-join-count]").textContent = meeting.currentParticipants + "/" + meeting.maxParticipants;
  }

  function showNotFound(message) {
    detailRoot.hidden = true;
    var box = document.querySelector("[data-meeting-empty]");
    box.querySelector("p").textContent = message;
    box.hidden = false;
  }
})();
