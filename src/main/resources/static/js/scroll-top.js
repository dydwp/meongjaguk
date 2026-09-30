/**
 * 맨 위로 버튼 - 담당: 박용제
 * 모든 페이지 공통 (layout/default.html). 조금 내려가면 오른쪽 아래에 나타나고, 누르면 맨 위로 이동
 */
(function () {
  "use strict";

  var SHOW_AFTER = 300; // 이만큼(px) 스크롤하면 버튼 표시

  var btn = document.querySelector("[data-scroll-top]");
  if (!btn) return;

  function update() {
    btn.hidden = window.scrollY < SHOW_AFTER;
  }

  btn.addEventListener("click", function () {
    // 모션 줄이기 설정한 사용자는 부드러운 스크롤 없이 바로 이동
    var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    window.scrollTo({ top: 0, behavior: reduce ? "auto" : "smooth" });
  });

  window.addEventListener("scroll", update, { passive: true });
  update();
})();
