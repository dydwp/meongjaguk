/* ==========================================================================
   멍자국 — 산책로 게시판 (목록 / 공유 산책로 상세 / 동행 신청·취소 / 댓글)
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

  /* ---------- 카카오 지도 (정석진님 추천 산책로 상세와 같은 표시 방식) ---------- */
  var ROUTE_COLOR = "#2f8060";

  function isValidLatLng(lat, lng) {
    return Number.isFinite(lat) && Number.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
  }

  function validPoints(points) {
    if (!Array.isArray(points) || points.length < 2) return null;
    var ok = points.every(function (p) { return p && isValidLatLng(p.latitude, p.longitude); });
    return ok ? points.slice().sort(function (a, b) { return a.sequence - b.sequence; }) : null;
  }

  // 지도로 그릴 수 있는지: 경로 좌표 2개 이상 또는 출발 좌표
  function canDrawMap(data) {
    return !!(window.kakao && kakao.maps && kakao.maps.Map) &&
      (validPoints(data.points) !== null || isValidLatLng(data.startLatitude, data.startLongitude));
  }

  // .map-preview 안의 점선 그림(SVG)과 "지도 미리보기" 글씨를 지도로 교체할 준비
  function prepareMapBox(preview, interactive) {
    var chip = preview.querySelector(".map-chip");
    if (chip) chip.remove();
    preview.classList.add("has-map"); // 기존 CSS: SVG 숨김
    var box = document.createElement("div");
    box.className = "walk-map";       // 기존 CSS: 영역 전체 채우기
    box.style.zIndex = "0";           // 지도가 "참여 n/m" 표시를 덮지 않게
    if (!interactive) box.style.pointerEvents = "none"; // 카드 클릭은 상세 이동
    preview.insertBefore(box, preview.firstChild);
    return box;
  }

  /**
   * 지도 그리기
   * - 경로 좌표가 있으면: 경로 선 + 시작·도착 표시
   * - 출발 좌표만 있으면: 출발 지점 마커
   * - interactive=false 이면 드래그·확대 불가 (목록 카드)
   */
  function drawCourseMap(box, data, interactive) {
    var maps = kakao.maps;
    var sorted = validPoints(data.points);
    var options = { center: null, level: 3 };
    if (!interactive) {
      options.draggable = false;
      options.scrollwheel = false;
      options.disableDoubleClickZoom = true;
      options.keyboardShortcuts = false;
    }

    if (!sorted) {
      var position = new maps.LatLng(data.startLatitude, data.startLongitude);
      options.center = position;
      var single = new maps.Map(box, options);
      new maps.Marker({ map: single, position: position, title: "산책로 시작점" });
      return single;
    }

    var path = sorted.map(function (p) { return new maps.LatLng(p.latitude, p.longitude); });
    options.center = path[0];
    options.level = 4;
    var map = new maps.Map(box, options);
    new maps.Polyline({
      map: map, path: path, strokeWeight: interactive ? 5 : 4,
      strokeColor: ROUTE_COLOR, strokeOpacity: 0.9, strokeStyle: "solid"
    });

    var bounds = new maps.LatLngBounds();
    path.forEach(function (p) { bounds.extend(p); });
    function fit() {
      map.relayout();
      if (interactive) map.setBounds(bounds, 60, 40, 40, 40);
      else map.setBounds(bounds, 36, 20, 16, 20);
    }
    fit();

    function mark(position, label) {
      new maps.Marker({ map: map, position: position, title: label });
      if (!interactive) return; // 작은 카드에서는 라벨 생략
      var text = document.createElement("span");
      text.className = "tag";
      text.style.cssText =
        "background:white;border:1px solid " + ROUTE_COLOR + ";white-space:nowrap;transform:translateY(-42px);";
      text.textContent = label;
      new maps.CustomOverlay({ map: map, position: position, content: text, yAnchor: 1 });
    }
    var first = sorted[0];
    var last = sorted[sorted.length - 1];
    var roundTrip = Math.abs(first.latitude - last.latitude) < 0.000001 &&
      Math.abs(first.longitude - last.longitude) < 0.000001;
    mark(path[0], roundTrip ? "시작 · 도착" : "시작");
    if (!roundTrip) mark(path[path.length - 1], "도착");

    if (interactive) window.addEventListener("resize", fit);
    return map;
  }

  // 목록 카드 지도는 화면에 보일 때만 만들기 (카드가 많아도 느려지지 않게)
  var cardMapObserver = ("IntersectionObserver" in window)
    ? new IntersectionObserver(function (entries) {
        entries.forEach(function (entry) {
          if (!entry.isIntersecting) return;
          cardMapObserver.unobserve(entry.target);
          var box = entry.target;
          try {
            drawCourseMap(box, box._courseData, false);
          } catch (err) {
            console.error("카드 지도 표시 실패", err);
            box.parentElement.classList.remove("has-map"); // 실패하면 점선 그림으로
            box.remove();
          }
        });
      }, { rootMargin: "100px 0px" })
    : null;

  /* ======================================================================
     산책로 게시판 목록 (/board)
     ====================================================================== */
  var boardList = document.querySelector("[data-meeting-list]");
  if (boardList) {
    initBoardList();
  }

  // 무한스크롤: 처음 PAGE_SIZE개를 보여주고, 맨 아래(sentinel)가 보이면 다음 PAGE_SIZE개를 이어서 불러옴
  function initBoardList() {
    var PAGE_SIZE = 6;
    var statusText = document.querySelector("[data-meeting-status]");
    var sentinel = document.querySelector("[data-meeting-sentinel]");
    var cursor = null;       // 마지막으로 불러온 게시글 번호
    var hasNext = true;
    var loading = false;
    var cardCount = 0;       // 지도 미리보기 경로를 돌려 쓰기 위한 순번

    function showStatus(text) {
      statusText.textContent = text;
      statusText.hidden = !text;
    }

    function loadNextPage() {
      if (loading || !hasNext) return;
      loading = true;
      showStatus("게시글을 불러오는 중이에요...");

      var url = "/api/meetings?size=" + PAGE_SIZE + (cursor != null ? "&cursor=" + encodeURIComponent(cursor) : "");
      fetch(url)
        .then(function (res) {
          if (!res.ok) throw new Error("status " + res.status);
          return res.json();
        })
        .then(function (page) {
          page.items.forEach(function (meeting) {
            boardList.appendChild(createMeetingCard(meeting, cardCount++));
          });
          cursor = page.nextCursor;
          hasNext = page.hasNext;

          if (cardCount === 0) {
            showStatus("아직 공유된 산책로가 없어요.");
          } else {
            showStatus("");
          }
          if (!hasNext) {
            observer.disconnect();
          }
        })
        .catch(function (err) {
          console.error("게시글을 불러오지 못했습니다.", err);
          hasNext = false;
          observer.disconnect();
          showStatus("게시글을 불러오지 못했어요. 잠시 후 다시 시도해주세요.");
        })
        .finally(function () {
          loading = false;
          // 불러온 뒤에도 맨 아래가 화면 안에 있으면(카드가 적어서 스크롤이 안 생기는 경우) 이어서 불러오기
          if (hasNext && isSentinelVisible()) loadNextPage();
        });
    }

    function isSentinelVisible() {
      var rect = sentinel.getBoundingClientRect();
      return rect.top < window.innerHeight;
    }

    var observer = new IntersectionObserver(function (entries) {
      if (entries[0].isIntersecting) loadNextPage();
    });

    observer.observe(sentinel);
    loadNextPage();
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
    // 좌표가 있으면 실제 지도 (없으면 점선 그림 유지)
    if (cardMapObserver && canDrawMap(meeting)) {
      var box = prepareMapBox(card.querySelector(".map-preview"), false);
      box._courseData = meeting;
      cardMapObserver.observe(box);
    }
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
    var meetingClosed = false; // 모집 중(RECRUITING)이 아니면 true (정원 충족 시 서버가 CLOSED로 내려줌)

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
        meetingClosed = meeting.status !== "RECRUITING";
        if (meeting.isHost) {
          renderDisabledButton("HOST", "내가 공유한 모집이에요");
        } else {
          renderApplyButton(meeting.myApplicationStatus);
        }
        detailRoot.hidden = false;
        showDetailMap(meeting);
        loadComments();
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

    // 신청할 수 없는 상태: 안내 문구, 누를 수 없음 (디자인 시스템 Gray = 비활성)
    // - HOST  : 내가 공유한 모집
    // - CLOSED: 모집 마감 (정원 충족 포함), 아직 신청하지 않은 사람
    function renderDisabledButton(mark, text) {
      applyBtn.setAttribute("data-application-status", mark);
      applyBtn.classList.remove("is-done");
      applyBtn.disabled = true;
      applyBtn.style.background = "var(--color-border)";
      applyBtn.style.color = "var(--color-text-muted)";
      applyBtn.style.cursor = "default";
      applyBtn.textContent = text;
    }

    // 신청 상태별 버튼: 없음 → 동행 신청(마감이면 비활성) / PENDING → 신청 완료(다시 누르면 취소) / ACCEPTED·REJECTED → 비활성
    function renderApplyButton(status) {
      if (!status && meetingClosed) {
        renderDisabledButton("CLOSED", "동행 모집 마감");
        return;
      }
      applyBtn.style.background = "";
      applyBtn.style.color = "";
      applyBtn.style.cursor = "";
      applyBtn.setAttribute("data-application-status", status || "");
      applyBtn.classList.toggle("is-done", !!status);
      applyBtn.disabled = status === "ACCEPTED" || status === "REJECTED";

      if (status === "PENDING") applyBtn.textContent = "✓ 신청 완료";
      else if (status === "ACCEPTED") applyBtn.textContent = "수락됨";
      else if (status === "REJECTED") applyBtn.textContent = "거절됨";
      else applyBtn.textContent = "동행 신청";
    }

    /* ---------- 댓글 조회 / 작성 ---------- */
    var commentForm = detailRoot.querySelector("[data-meeting-comment-form]");
    var commentInput = commentForm.querySelector("input");
    var commentBtn = commentForm.querySelector("button");
    var commentList = detailRoot.querySelector("[data-meeting-comment-list]");
    var commentCount = detailRoot.querySelector("[data-meeting-comment-count]");

    function loadComments() {
      fetch("/api/meetings/" + encodeURIComponent(meetingId) + "/comments")
        .then(function (res) {
          if (!res.ok) throw new Error("status " + res.status);
          return res.json();
        })
        .then(function (comments) {
          commentList.replaceChildren.apply(commentList, comments.map(createComment));
          commentCount.textContent = String(comments.length);
        })
        .catch(function (err) {
          console.error("댓글을 불러오지 못했습니다.", err);
          commentList.replaceChildren(el("p", "small text-muted", "댓글을 불러오지 못했어요."));
        });
    }

    function submitComment() {
      if (!loggedIn) {
        window.location.href = "/login";
        return;
      }
      var content = commentInput.value.trim();
      if (!content || commentBtn.disabled) return;

      var headers = { "Content-Type": "application/json" };
      if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;

      commentBtn.disabled = true;
      fetch("/api/meetings/" + encodeURIComponent(meetingId) + "/comments", {
        method: "POST",
        headers: headers,
        body: JSON.stringify({ content: content })
      })
        .then(function (res) {
          if (res.redirected || res.status === 401) {
            window.location.href = "/login";
            return;
          }
          return res.json()
            .catch(function () { return {}; })
            .then(function (body) {
              if (!res.ok) {
                alert(body.message || "댓글을 등록하지 못했어요. 잠시 후 다시 시도해주세요.");
                return;
              }
              commentList.prepend(createComment(body)); // 최신 댓글이 맨 위
              commentCount.textContent = String(parseInt(commentCount.textContent || "0", 10) + 1);
              commentInput.value = "";
              commentInput.focus();
            });
        })
        .catch(function (err) {
          console.error("댓글 등록 실패", err);
          alert("댓글을 등록하지 못했어요. 잠시 후 다시 시도해주세요.");
        })
        .finally(function () {
          commentBtn.disabled = false;
        });
    }

    // 댓글 삭제 (본인 댓글만 버튼이 보임)
    commentList.addEventListener("click", function (e) {
      var btn = e.target.closest("[data-comment-delete]");
      if (!btn || btn.disabled) return;
      if (!confirm("댓글을 삭제할까요?")) return;

      var headers = {};
      if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;

      btn.disabled = true;
      fetch("/api/meetings/" + encodeURIComponent(meetingId) + "/comments/" +
            encodeURIComponent(btn.getAttribute("data-comment-delete")), {
        method: "DELETE",
        headers: headers
      })
        .then(function (res) {
          if (res.redirected || res.status === 401) {
            window.location.href = "/login";
            return;
          }
          if (res.ok) {
            btn.closest(".comment").remove();
            commentCount.textContent = String(Math.max(0, parseInt(commentCount.textContent || "0", 10) - 1));
            return;
          }
          return res.json()
            .catch(function () { return {}; })
            .then(function (body) {
              alert(body.message || "댓글을 삭제하지 못했어요. 잠시 후 다시 시도해주세요.");
              btn.disabled = false;
            });
        })
        .catch(function (err) {
          console.error("댓글 삭제 실패", err);
          alert("댓글을 삭제하지 못했어요. 잠시 후 다시 시도해주세요.");
          btn.disabled = false;
        });
    });

    commentBtn.addEventListener("click", submitComment);
    commentInput.addEventListener("keydown", function (e) {
      // 한글 입력 조합 중 Enter는 무시 (중복 등록 방지)
      if (e.key === "Enter" && !e.isComposing) submitComment();
    });
  }

  // 댓글 한 개 (작성자 아바타 + 이름 · 시간 + 내용)
  function createComment(comment) {
    var item = el("div", "comment");
    // 댓글 구분선 (디자인 시스템 구분선 색)
    item.style.paddingBottom = "14px";
    item.style.borderBottom = "1px solid var(--color-border)";
    item.appendChild(el("div", comment.hostComment ? "avatar avatar-sm" : "avatar avatar-sm avatar-muted",
      comment.authorNickname.charAt(0)));
    var body = document.createElement("div");
    var name = el("span", "name", comment.authorNickname);
    name.appendChild(el("span", "time", "· " + formatRelative(comment.createdAt)));
    // 내가 쓴 댓글에만 삭제 버튼
    if (comment.mine) {
      var del = el("button", "time", "삭제");
      del.type = "button";
      del.setAttribute("data-comment-delete", comment.commentId);
      del.style.cssText = "background:none; border:0; padding:0; cursor:pointer; text-decoration:underline;";
      name.appendChild(del);
    }
    body.appendChild(name);
    body.appendChild(el("p", null, comment.content));
    item.appendChild(body);
    return item;
  }

  // 상세 상단 지도 (좌표가 없으면 점선 그림 유지)
  function showDetailMap(meeting) {
    if (!canDrawMap(meeting)) return;
    var preview = detailRoot.querySelector(".map-preview");
    var box = prepareMapBox(preview, true);
    try {
      drawCourseMap(box, meeting, true);
    } catch (err) {
      console.error("지도 표시 실패", err);
      preview.classList.remove("has-map");
      box.remove();
    }
  }

  // 반려견 프로필 렌더링
  function renderMeetingPets(pets) {
    var section = detailRoot.querySelector("[data-meeting-pets]");
    var list = detailRoot.querySelector("[data-meeting-pet-list]");

    if (!section || !list) return;

    list.replaceChildren();

    if (!Array.isArray(pets) || pets.length === 0) {
      section.hidden = true;
      return;
    }

    pets.forEach(function (pet) {
      var item = el("div", "walk-pet-group-item");

      var photo = el("div", "walk-current-pet-photo");

      if (pet.profileImage) {
        var img = document.createElement("img");
        img.src = pet.profileImage;
        img.alt = pet.name || "반려견";
        photo.appendChild(img);
      } else {
        photo.textContent = "🐾";
      }

      var info = el("div", "walk-current-pet-info");
      info.appendChild(el("strong", null, pet.name || "반려견"));

      var parts = [];
      if (pet.breed) parts.push(pet.breed);
      if (pet.sizeLabel) parts.push(pet.sizeLabel);
      if (pet.ageInYears != null) parts.push(pet.ageInYears + "세");

      if (parts.length) {
        info.appendChild(el("span", null, parts.join(" · ")));
      }

      if (pet.activityLevelLabel) {
        info.appendChild(
          el("span", "walk-current-pet-activity",
            "활동성 " + pet.activityLevelLabel)
        );
      }

      item.appendChild(photo);
      item.appendChild(info);
      list.appendChild(item);
    });

    section.hidden = false;
  }

  function renderDetail(meeting) {
    var q = function (sel) { return detailRoot.querySelector(sel); };

    q("[data-status]").replaceChildren(statusTag(meeting.status));
    q("[data-title]").textContent = meeting.title;
    q("[data-course-name]").textContent = meeting.courseName || "-";
    q("[data-distance]").textContent =
      meeting.distanceM != null ? (meeting.distanceM / 1000).toFixed(1) + "km" : "-";
    q("[data-minutes]").textContent =
      meeting.estimatedMinutes != null ? "약 " + meeting.estimatedMinutes + "분" : "-";
    q("[data-when]").textContent =
      formatMeetingDateTime(meeting.meetingDate, meeting.meetingTime) || "-";

    // 참여자의 반려견 동반 필수 조건 (조건부 class 적용)
    var petCondition = q("[data-pet]");
    petCondition.textContent = meeting.petRequired ? "필수" : "선택";
    petCondition.classList.toggle("meeting-pet-required", meeting.petRequired);

    // 참여 조건
    var condition = q("[data-condition]");

    if (meeting.participationCondition) {
      condition.textContent = meeting.participationCondition;
    } else {
      condition.textContent = "없음";
    }

    // 설명 영역
    var descriptionSection = q("[data-description-section]");
    var description = q("[data-description]");

    if (descriptionSection && description) {
      if (meeting.description) {
        description.textContent = meeting.description;
        descriptionSection.hidden = false;
      } else {
        descriptionSection.hidden = true;
      }
    }

    renderMeetingPets(meeting.pets);

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
