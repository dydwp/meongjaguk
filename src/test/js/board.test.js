// 동행 게시판 목록·상세·동행 신청·댓글 (board.js) - board/list.html, board/detail.html
const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  openPage,
  template,
  response,
  settle,
  localDateTime,
} = require("./support/browser");

const hoursAgo = (n) => localDateTime(n * 3600_000);

function card(id, extra = {}) {
  return {
    meetingId: id,
    title: "모집 " + id,
    hostNickname: "용제",
    createdAt: hoursAgo(3),
    meetingDate: "2099-10-03",
    meetingTime: "19:00:00",
    distanceM: 2600,
    estimatedMinutes: 40,
    currentParticipants: 2,
    maxParticipants: 4,
    status: "RECRUITING",
    startLatitude: 37.544,
    startLongitude: 127.043,
    points: [],
    ...extra,
  };
}

// ---------- 목록 ----------

function openList(pages, kakao = false) {
  return openPage({
    html: template("board/list.html"),
    url: "http://localhost:8081/board",
    scripts: ["board.js"],
    kakao,
    fetch: (url) => {
      const cursor = new URL(url, "http://localhost").searchParams.get(
        "cursor",
      );
      const page = pages[cursor ?? "first"];
      return page instanceof Error ? page : response(page);
    },
  });
}

test("게시판 목록 카드에 제목·상태·작성자·일시·거리·참여 인원을 보여준다", async () => {
  const page = openList({
    first: {
      items: [card(9), card(8, { status: "CLOSED", distanceM: null })],
      hasNext: false,
      nextCursor: 8,
    },
  });
  await settle();

  const cards = page.$$(".board-card");
  assert.equal(cards.length, 2);
  assert.equal(
    cards[0].getAttribute("href"),
    "/course-detail-shared?meetingId=9",
  );
  assert.equal(cards[0].querySelector("h3").textContent, "모집 9 모집 중");
  assert.equal(cards[0].querySelector(".tag-secondary").textContent, "모집 중");
  assert.equal(
    cards[0].querySelector("[data-host]").textContent,
    "용제 · 3시간 전",
  );
  assert.equal(
    cards[0].querySelector("[data-when]").textContent,
    "2099.10.03 19:00",
  );
  assert.equal(
    cards[0].querySelector("[data-distance]").textContent,
    "2.6km · 약 40분",
  );
  assert.equal(cards[0].querySelector(".map-tag").textContent, "참여 2/4");
  assert.equal(cards[1].querySelector(".tag-neutral").textContent, "모집 마감");
  assert.equal(
    cards[1].querySelector("[data-distance]").textContent,
    "약 40분",
  );
  assert.equal(page.$("[data-meeting-status]").hidden, true);
  assert.equal(page.calls.fetch[0].url, "/api/meetings?size=6");
  page.close();
});

test("다음 페이지가 있으면 마지막 게시글 번호(cursor)로 이어서 불러온다", async () => {
  const page = openList({
    first: { items: [card(9), card(8)], hasNext: true, nextCursor: 8 },
    8: { items: [card(7)], hasNext: false, nextCursor: 7 },
  });
  await settle(20);

  assert.deepEqual(
    page.calls.fetch.map((c) => c.url),
    ["/api/meetings?size=6", "/api/meetings?size=6&cursor=8"],
  );
  assert.equal(page.$$(".board-card").length, 3);
  const listObserver = page.observers.find((o) =>
    o.callback.toString().includes("loadNextPage"),
  );
  assert.ok(listObserver.disconnected, "마지막 페이지면 무한스크롤 감시 중단");
  page.close();
});

test("게시글이 없거나 불러오지 못하면 안내 문구를 보여준다", async () => {
  const empty = openList({
    first: { items: [], hasNext: false, nextCursor: null },
  });
  await settle();
  assert.equal(
    empty.$("[data-meeting-status]").textContent,
    "아직 공유된 산책로가 없어요.",
  );
  empty.close();

  const failed = openList({ first: new Error("network") });
  await settle();
  assert.equal(
    failed.$("[data-meeting-status]").textContent,
    "게시글을 불러오지 못했어요. 잠시 후 다시 시도해주세요.",
  );
  failed.close();
});

test("경로 좌표가 있는 카드는 화면에 보일 때 카카오 지도로 경로를 그린다", async () => {
  const points = [
    { sequence: 2, latitude: 37.545, longitude: 127.044 },
    { sequence: 1, latitude: 37.544, longitude: 127.043 },
  ];
  const page = openList(
    { first: { items: [card(9, { points })], hasNext: false, nextCursor: 9 } },
    true,
  );
  await settle();

  const mapBox = page.$(".board-card .walk-map");
  assert.ok(mapBox, "지도 영역 준비");
  assert.equal(
    page.calls.kakao.maps.length,
    0,
    "보이기 전에는 지도를 만들지 않음",
  );

  const cardObserver = page.observers.find((o) => o.targets.has(mapBox));
  cardObserver.trigger(mapBox);

  assert.equal(page.calls.kakao.maps.length, 1);
  assert.equal(page.calls.kakao.maps[0].options.draggable, false); // 목록 카드는 움직이지 않는 지도
  const line = page.calls.kakao.polylines[0];
  assert.deepEqual(
    line.path.map((p) => p.lat),
    [37.544, 37.545],
  ); // sequence 순서
  assert.deepEqual(
    page.calls.kakao.markers.map((m) => m.options.title),
    ["시작", "도착"],
  );
  page.close();
});

// ---------- 상세 ----------

const DETAIL = {
  meetingId: 10,
  title: "저녁 산책",
  description: "같이 걸어요",
  courseName: "서울숲 코스",
  distanceM: 2600,
  estimatedMinutes: 40,
  meetingDate: "2099-10-03",
  meetingTime: "19:00:00",
  petRequired: true,
  participationCondition: "소형견",
  hostNickname: "용제",
  createdAt: "2026-09-20T18:30:00",
  participantNicknames: ["용제", "민준"],
  currentParticipants: 2,
  maxParticipants: 4,
  status: "RECRUITING",
  isHost: false,
  myApplicationStatus: null,
  startLatitude: 37.544,
  startLongitude: 127.043,
  points: [],
};

const COMMENTS = [
  {
    commentId: 2,
    authorNickname: "민준",
    hostComment: false,
    mine: true,
    content: "참여할게요",
    createdAt: hoursAgo(0),
  },
  {
    commentId: 1,
    authorNickname: "용제",
    hostComment: true,
    mine: false,
    content: "환영해요",
    createdAt: hoursAgo(30),
  },
];

function openDetail({
  meeting = DETAIL,
  loggedIn = true,
  routes = {},
  url,
  kakao,
  confirm,
} = {}) {
  return openPage({
    html: template("board/detail.html"),
    url: url || "http://localhost:8081/course-detail-shared?meetingId=10",
    scripts: ["board.js"],
    kakao,
    confirm,
    setup(window) {
      const root = window.document.querySelector("[data-meeting-detail]");
      root.setAttribute("data-logged-in", String(loggedIn));
      root.setAttribute("data-csrf-header", "X-CSRF-TOKEN");
      root.setAttribute("data-csrf-token", "token-123");
    },
    fetch: (u, call) => {
      const key = `${call.method} ${u}`;
      if (routes[key]) return routes[key](call);
      if (key === "GET /api/meetings/10")
        return meeting instanceof Error
          ? meeting
          : response(meeting, { status: meeting ? 200 : 404 });
      if (key === "GET /api/meetings/10/comments") return response(COMMENTS);
      throw new Error("unexpected " + key);
    },
  });
}

test("상세 화면에 모집 정보·참여자·댓글을 그린다", async () => {
  const page = openDetail();
  await settle();

  const text = (s) => page.$(s).textContent;
  assert.equal(page.$("[data-meeting-detail]").hidden, false);
  assert.equal(text("[data-title]"), "저녁 산책");
  assert.equal(text("[data-course-name]"), "서울숲 코스");
  assert.equal(text("[data-distance]"), "2.6km");
  assert.equal(text("[data-minutes]"), "약 40분");
  assert.equal(text("[data-when]"), "2099.10.03 19:00");
  assert.equal(text("[data-pet]"), "필수");
  assert.ok(page.$("[data-pet]").classList.contains("meeting-pet-required"));
  assert.equal(text("[data-condition]"), "소형견");
  assert.equal(page.$("[data-description-section]").hidden, false);
  assert.equal(text("[data-description]"), "같이 걸어요");
  assert.equal(text("[data-shared-at]"), "2026.09.20 공유");
  assert.equal(text("[data-join-count]"), "2/4");
  assert.deepEqual(
    page.$$("[data-participant-avatars] .avatar").map((a) => a.textContent),
    ["용", "민"],
  );
  assert.equal(text("[data-apply-btn]"), "동행 신청");

  assert.equal(text("[data-meeting-comment-count]"), "2");
  const comments = page.$$("[data-meeting-comment-list] .comment");
  assert.equal(comments.length, 2);
  assert.ok(
    comments[0].querySelector("[data-comment-delete='2']"),
    "내 댓글에만 삭제 버튼",
  );
  assert.equal(comments[1].querySelector("[data-comment-delete]"), null);
  assert.ok(
    comments[1].querySelector(".avatar").classList.contains("avatar-sm"),
  );
  assert.ok(
    !comments[1].querySelector(".avatar").classList.contains("avatar-muted"),
    "작성자 댓글 아바타 강조",
  );
  page.close();
});

test("참여 조건이 없으면 '없음', 설명이 없으면 설명 칸을 숨긴다", async () => {
  const page = openDetail({
    meeting: {
      ...DETAIL,
      participationCondition: null,
      description: null,
      petRequired: false,
    },
  });
  await settle();

  assert.equal(page.$("[data-condition]").textContent, "없음");
  assert.equal(page.$("[data-description-section]").hidden, true);
  assert.equal(page.$("[data-pet]").textContent, "선택");
  assert.ok(!page.$("[data-pet]").classList.contains("meeting-pet-required"));
  page.close();
});

test("모집자가 함께할 반려견이 있으면 반려견 카드를 보여준다", async () => {
  const none = openDetail();
  await settle();
  assert.equal(none.$("[data-meeting-pets]").hidden, true);
  none.close();

  const page = openDetail({
    meeting: {
      ...DETAIL,
      pets: [
        {
          id: 1,
          name: "보리",
          breed: "푸들",
          sizeLabel: "소형",
          ageInYears: 3,
          profileImage: null,
          activityLevelLabel: "높음",
        },
      ],
    },
  });
  await settle();
  assert.equal(page.$("[data-meeting-pets]").hidden, false);
  const items = page.$$("[data-meeting-pet-list] .walk-pet-group-item");
  assert.equal(items.length, 1);
  assert.equal(items[0].querySelector("strong").textContent, "보리");
  assert.equal(
    items[0].querySelector(".walk-current-pet-activity").textContent,
    "활동성 높음",
  );
  page.close();
});

test("없는 모집이면 안내 화면을 보여준다", async () => {
  const notFound = openDetail({ meeting: null });
  await settle();
  assert.equal(notFound.$("[data-meeting-detail]").hidden, true);
  assert.equal(notFound.$("[data-meeting-empty]").hidden, false);
  assert.equal(
    notFound.$("[data-meeting-empty] p").textContent,
    "모집 정보를 찾을 수 없어요.",
  );
  notFound.close();

  const noId = openDetail({
    url: "http://localhost:8081/course-detail-shared",
  });
  await settle();
  assert.equal(noId.calls.fetch.length, 0);
  assert.equal(noId.$("[data-meeting-empty]").hidden, false);
  noId.close();
});

test("신청 버튼은 내 상태에 따라 바뀐다 (작성자 / 마감 / 대기 / 수락 / 거절)", async () => {
  const cases = [
    [{ isHost: true }, "내가 공유한 모집이에요", true],
    [{ status: "CLOSED" }, "동행 모집 마감", true],
    [{ myApplicationStatus: "PENDING" }, "✓ 신청 완료", false],
    [{ myApplicationStatus: "ACCEPTED", status: "CLOSED" }, "수락됨", true],
    [{ myApplicationStatus: "REJECTED" }, "거절됨", true],
  ];
  for (const [change, label, disabled] of cases) {
    const page = openDetail({ meeting: { ...DETAIL, ...change } });
    await settle();
    const btn = page.$("[data-apply-btn]");
    assert.equal(btn.textContent, label);
    assert.equal(btn.disabled, disabled, label);
    page.close();
  }
});

test("동행 신청 → 신청 완료, 다시 누르면 신청 취소 (CSRF 토큰 포함)", async () => {
  const page = openDetail({
    routes: {
      "POST /api/meetings/10/applications": () =>
        response({ status: "PENDING" }, { status: 201 }),
      "DELETE /api/meetings/10/applications": () =>
        response(undefined, { status: 204 }),
    },
  });
  await settle();
  const btn = page.$("[data-apply-btn]");

  page.click(btn);
  await settle();
  assert.equal(btn.textContent, "✓ 신청 완료");
  assert.equal(page.calls.fetch.at(-1).headers["X-CSRF-TOKEN"], "token-123");

  page.click(btn);
  await settle();
  assert.equal(page.calls.fetch.at(-1).method, "DELETE");
  assert.equal(btn.textContent, "동행 신청");
  page.close();
});

test("신청이 거부되면 서버가 준 이유를 알려준다", async () => {
  const page = openDetail({
    routes: {
      "POST /api/meetings/10/applications": () =>
        response(
          { message: "정원이 다 차서 신청할 수 없어요." },
          { status: 409 },
        ),
    },
  });
  await settle();

  page.click("[data-apply-btn]");
  await settle();

  assert.deepEqual(page.calls.alerts, ["정원이 다 차서 신청할 수 없어요."]);
  assert.equal(page.$("[data-apply-btn]").textContent, "동행 신청");
  page.close();
});

test("비회원이 신청이나 댓글 등록을 누르면 로그인 화면으로 이동한다", async () => {
  const page = openDetail({ loggedIn: false });
  await settle();

  page.click("[data-apply-btn]");
  page.$("[data-meeting-comment-form] input").value = "안녕하세요";
  page.click("[data-meeting-comment-form] button");

  assert.deepEqual(page.calls.navigations, ["/login", "/login"]);
  assert.equal(page.calls.fetch.filter((c) => c.method !== "GET").length, 0);
  page.close();
});

test("댓글을 등록하면 맨 위에 추가하고 개수를 늘린다 (빈 댓글은 보내지 않음)", async () => {
  const page = openDetail({
    routes: {
      "POST /api/meetings/10/comments": (call) =>
        response(
          {
            commentId: 3,
            authorNickname: "서연",
            hostComment: false,
            mine: true,
            content: JSON.parse(call.body).content,
            createdAt: hoursAgo(0),
          },
          { status: 201 },
        ),
    },
  });
  await settle();
  const input = page.$("[data-meeting-comment-form] input");

  input.value = "   ";
  page.click("[data-meeting-comment-form] button");
  assert.equal(page.calls.fetch.filter((c) => c.method === "POST").length, 0);

  input.value = "  같이 가요  ";
  input.dispatchEvent(
    new page.window.KeyboardEvent("keydown", { key: "Enter" }),
  );
  await settle();

  const post = page.calls.fetch.find((c) => c.method === "POST");
  assert.deepEqual(JSON.parse(post.body), { content: "같이 가요" });
  assert.equal(post.headers["X-CSRF-TOKEN"], "token-123");
  assert.equal(
    page.$("[data-meeting-comment-list] .comment p").textContent,
    "같이 가요",
  );
  assert.equal(page.$("[data-meeting-comment-count]").textContent, "3");
  assert.equal(input.value, "");
  page.close();
});

test("댓글 삭제는 확인 후 요청하고 목록에서 지운다", async () => {
  const page = openDetail({
    routes: {
      "DELETE /api/meetings/10/comments/2": () =>
        response(undefined, { status: 204 }),
    },
  });
  await settle();

  page.click("[data-comment-delete='2']");
  await settle();

  assert.deepEqual(page.calls.confirms, ["댓글을 삭제할까요?"]);
  assert.equal(page.$$("[data-meeting-comment-list] .comment").length, 1);
  assert.equal(page.$("[data-meeting-comment-count]").textContent, "1");
  page.close();
});

test("수정·삭제 버튼은 작성자에게만 보이고, 수정은 수정 화면으로 연결된다", async () => {
  const guest = openDetail();
  await settle();
  assert.equal(guest.$("[data-host-actions]").hidden, true);
  guest.close();

  const host = openDetail({ meeting: { ...DETAIL, isHost: true } });
  await settle();
  assert.equal(host.$("[data-host-actions]").hidden, false);
  assert.equal(
    host.$("[data-edit-link]").getAttribute("href"),
    "/board/edit?meetingId=10",
  );
  host.close();
});

test("글 삭제는 확인 후 CSRF 토큰과 함께 요청하고 게시판으로 이동한다 (취소하면 요청 안 함)", async () => {
  const cancelled = openDetail({
    meeting: { ...DETAIL, isHost: true },
    confirm: false,
  });
  await settle();
  cancelled.click("[data-delete-btn]");
  await settle();
  assert.equal(
    cancelled.calls.fetch.some((c) => c.method === "DELETE"),
    false,
  );
  cancelled.close();

  const page = openDetail({
    meeting: { ...DETAIL, isHost: true },
    routes: {
      "DELETE /api/meetings/10": () => response(undefined, { status: 204 }),
    },
  });
  await settle();
  page.click("[data-delete-btn]");
  await settle();

  assert.match(page.calls.confirms[0], /이 모집 글을 삭제할까요\?/);
  assert.equal(page.calls.fetch.at(-1).headers["X-CSRF-TOKEN"], "token-123");
  assert.deepEqual(page.calls.navigations, ["/board"]);
  page.close();
});

test("글 삭제가 거부되면 이유를 알려주고 다시 누를 수 있게 한다", async () => {
  const page = openDetail({
    meeting: { ...DETAIL, isHost: true },
    routes: {
      "DELETE /api/meetings/10": () =>
        response(
          { message: "본인이 작성한 글만 삭제할 수 있어요." },
          { status: 403 },
        ),
    },
  });
  await settle();
  page.click("[data-delete-btn]");
  await settle();

  assert.deepEqual(page.calls.alerts, ["본인이 작성한 글만 삭제할 수 있어요."]);
  assert.equal(page.$("[data-delete-btn]").disabled, false);
  assert.deepEqual(page.calls.navigations, []);
  page.close();
});

// ---------- 동행 산책 시작 / 종료 (추가: 김환중) ----------

const STARTED = "2026-10-02T18:02:11";
const ENDED = "2026-10-02T18:40:00";

test("동행 산책 영역은 상태와 작성자 여부에 따라 바뀐다", async () => {
  const cases = [
    [{ status: "RECRUITING" }, "모집이 마감되면 동행 산책을 시작할 수 있어요.", "동행 산책 시작", true],
    [{ status: "CLOSED", currentParticipants: 1 }, "수락된 참가자가 있어야 동행 산책을 시작할 수 있어요.", "동행 산책 시작", true],
    [{ status: "CLOSED", currentParticipants: 2 }, "모두 모이면 동행 산책을 시작해주세요.", "동행 산책 시작", false],
    [{ status: "IN_PROGRESS", startedAt: STARTED }, "산책 중 · 18:02 시작", "동행 산책 종료(기록 없이)", false],
    [{ status: "COMPLETED", startedAt: STARTED, endedAt: ENDED }, "산책 완료 · 18:02 ~ 18:40 (38분)", null, null],
  ];
  for (const [change, info, label, disabled] of cases) {
    const host = openDetail({ meeting: { ...DETAIL, isHost: true, ...change } });
    await settle();
    assert.equal(host.$("[data-walk-section]").hidden, false, info);
    assert.equal(host.$("[data-walk-info]").textContent, info);
    if (label) {
      assert.equal(host.$("[data-walk-actions]").hidden, false, info);
      assert.equal(host.$("[data-walk-btn]").textContent, label);
      assert.equal(host.$("[data-walk-btn]").disabled, disabled, info);
    } else {
      assert.equal(host.$("[data-walk-actions]").hidden, true, info);
    }
    host.close();

    const guest = openDetail({ meeting: { ...DETAIL, ...change } });
    await settle();
    const infoOnly = change.status === "IN_PROGRESS" || change.status === "COMPLETED";
    assert.equal(guest.$("[data-walk-section]").hidden, !infoOnly, info);
    assert.equal(guest.$("[data-walk-actions]").hidden, true, info);
    if (infoOnly) assert.equal(guest.$("[data-walk-info]").textContent, info);
    guest.close();
  }
});

test("동행 산책을 시작한 글은 작성자에게도 수정·삭제가 보이지 않는다", async () => {
  for (const status of ["IN_PROGRESS", "COMPLETED"]) {
    const page = openDetail({ meeting: { ...DETAIL, isHost: true, status, startedAt: STARTED, endedAt: ENDED } });
    await settle();
    assert.equal(page.$("[data-host-actions]").hidden, true, status);
    page.close();
  }
});

test("동행 산책 시작·종료는 확인 후 CSRF 토큰과 함께 요청하고, 시작하면 기록 화면으로 이동·종료하면 새로고침한다", async () => {
  const start = openDetail({
    meeting: { ...DETAIL, isHost: true, status: "CLOSED", currentParticipants: 2 },
    routes: { "POST /api/meetings/10/start": () => response(undefined, { status: 204 }) },
  });
  await settle();
  start.click("[data-walk-btn]");
  await settle();
  assert.deepEqual(start.calls.confirms,
    ["동행 산책을 시작할까요?\n시작하면 글 수정·삭제와 동행 신청 수락·거절을 할 수 없어요."]);
  assert.equal(start.calls.fetch.at(-1).url, "/api/meetings/10/start");
  assert.equal(start.calls.fetch.at(-1).method, "POST");
  assert.equal(start.calls.fetch.at(-1).headers["X-CSRF-TOKEN"], "token-123");
  assert.deepEqual(start.calls.navigations, ["/walk-record?meetingId=10"]);
  start.close();

  const end = openDetail({
    meeting: { ...DETAIL, isHost: true, status: "IN_PROGRESS", startedAt: STARTED },
    routes: { "POST /api/meetings/10/end": () => response(undefined, { status: 204 }) },
  });
  await settle();
  end.click("[data-walk-btn]");
  await settle();
  assert.deepEqual(end.calls.confirms, ["기록 화면에서 종료하면 경로와 거리가 저장돼요.\n기록 없이 동행 산책을 종료할까요?"]);
  assert.equal(end.calls.fetch.at(-1).url, "/api/meetings/10/end");
  assert.equal(end.calls.fetch.at(-1).headers["X-CSRF-TOKEN"], "token-123");
  assert.deepEqual(end.calls.navigations, ["reload"]);
  end.close();
});

test("동행 산책 시작 확인을 취소하면 요청하지 않는다", async () => {
  const page = openDetail({
    meeting: { ...DETAIL, isHost: true, status: "CLOSED", currentParticipants: 2 },
    confirm: false,
  });
  await settle();
  page.click("[data-walk-btn]");
  await settle();

  assert.equal(page.calls.fetch.some((c) => c.method === "POST"), false);
  assert.equal(page.$("[data-walk-btn]").disabled, false);
  page.close();
});

test("동행 산책 시작이 거부되면 이유를 알려주고 다시 누를 수 있게 한다", async () => {
  const page = openDetail({
    meeting: { ...DETAIL, isHost: true, status: "CLOSED", currentParticipants: 2 },
    routes: {
      "POST /api/meetings/10/start": () =>
        response({ message: "수락된 참가자가 있어야 산책을 시작할 수 있어요." }, { status: 400 }),
    },
  });
  await settle();
  page.click("[data-walk-btn]");
  await settle();

  assert.deepEqual(page.calls.alerts, ["수락된 참가자가 있어야 산책을 시작할 수 있어요."]);
  assert.equal(page.$("[data-walk-btn]").disabled, false);
  assert.deepEqual(page.calls.navigations, []);
  page.close();
});

// ---------- 동행 산책 GPS 기록 연결 (추가: 김환중) ----------

test("산책 중이면 작성자에게 기록 화면으로 돌아가기와 기록 없이 종료 버튼을 보여준다", async () => {
  const host = openDetail({ meeting: { ...DETAIL, isHost: true, status: "IN_PROGRESS", startedAt: STARTED } });
  await settle();
  assert.equal(host.$("[data-walk-actions]").hidden, false);
  assert.equal(host.$("[data-walk-record-link]").hidden, false);
  assert.equal(host.$("[data-walk-record-link]").getAttribute("href"), "/walk-record?meetingId=10");
  assert.equal(host.$("[data-walk-btn]").hidden, false);
  assert.equal(host.$("[data-walk-btn]").textContent, "동행 산책 종료(기록 없이)");
  assert.equal(host.$("[data-walk-record-view]").hidden, true);
  host.close();

  const member = openDetail({ meeting: { ...DETAIL, status: "IN_PROGRESS", startedAt: STARTED, myApplicationStatus: "ACCEPTED" } });
  await settle();
  assert.equal(member.$("[data-walk-record-link]").hidden, true);
  assert.equal(member.$("[data-walk-actions]").hidden, true);
  member.close();
});

test("완료된 동행 산책의 기록 보기는 작성자와 수락된 참가자에게만 보인다", async () => {
  const done = { ...DETAIL, status: "COMPLETED", startedAt: STARTED, endedAt: ENDED, walkRecordId: 55 };
  const cases = [
    [{ isHost: true }, true],
    [{ myApplicationStatus: "ACCEPTED" }, true],
    [{ myApplicationStatus: "PENDING" }, false],
    [{ myApplicationStatus: "REJECTED" }, false],
    [{}, false],
    [{ isHost: true, walkRecordId: null }, false],
  ];
  for (const [change, shown] of cases) {
    const page = openDetail({ meeting: { ...done, ...change } });
    await settle();
    const label = JSON.stringify(change);
    assert.equal(page.$("[data-walk-record-view]").hidden, !shown, label);
    assert.equal(page.$("[data-walk-actions]").hidden, !shown, label);
    if (shown) {
      assert.equal(page.$("[data-walk-record-view]").getAttribute("href"), "/activity-detail?id=55");
      assert.equal(page.$("[data-walk-btn]").hidden, true, label);
    }
    assert.equal(page.$("[data-walk-record-link]").hidden, true, label);
    assert.equal(page.$("[data-walk-info]").textContent, "산책 완료 · 18:02 ~ 18:40 (38분)");
    page.close();
  }
});

test("상세 지도: 경로가 없으면 출발 지점 마커, 왕복 코스면 '시작 · 도착' 표시", async () => {
  const start = openDetail({ kakao: true });
  await settle();
  assert.equal(start.calls.kakao.polylines.length, 0);
  assert.deepEqual(
    start.calls.kakao.markers.map((m) => m.options.title),
    ["산책로 시작점"],
  );
  start.close();

  const loop = [
    { sequence: 1, latitude: 37.544, longitude: 127.043 },
    { sequence: 2, latitude: 37.545, longitude: 127.044 },
    { sequence: 3, latitude: 37.544, longitude: 127.043 },
  ];
  const round = openDetail({
    kakao: true,
    meeting: { ...DETAIL, points: loop },
  });
  await settle();
  assert.equal(round.calls.kakao.polylines.length, 1);
  assert.deepEqual(
    round.calls.kakao.markers.map((m) => m.options.title),
    ["시작 · 도착"],
  );
  assert.equal(
    round.calls.kakao.overlays[0].options.content.textContent,
    "시작 · 도착",
  );
  round.close();
});
