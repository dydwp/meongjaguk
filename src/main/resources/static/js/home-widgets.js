/**
 * 메인 하단 카드 - 담당: 박용제
 *  1) 오늘의 산책 날씨 [data-weather]
 *     Open-Meteo(키 없는 무료 API)로 기온·날씨·미세먼지를 조회하고,
 *     오늘 남은 시간 중 산책하기 좋은 2시간을 추천합니다.
 *     위치는 소수점 2자리(약 1km)로 줄여서 보내고, 위치 권한이 없으면 서울시청 기준으로 조회합니다.
 *  2) 나가기 전 체크 [data-walk-checklist]
 *     체크 상태를 브라우저(localStorage)에 날짜별로 저장 → 다음 날이면 초기화
 *     산책 종료(app.js의 walk:stopped 신호) 시에도 모두 해제 (저장값은 app.js가 지움)
 */
(function () {
  "use strict";

  var DEFAULT_CENTER = { lat: 37.5665, lng: 126.978 }; // 위치를 모를 때: 서울시청 (walk-map.js와 같음)
  var WEATHER_CACHE_KEY = "mungjaguk-weather";
  var WEATHER_CACHE_MS = 30 * 60 * 1000;               // 30분 동안은 다시 조회하지 않음
  var CHECKLIST_KEY = "mungjaguk-walk-checklist";

  function pad2(n) { return n < 10 ? "0" + n : String(n); }
  function todayText() {
    var d = new Date();
    return d.getFullYear() + "-" + pad2(d.getMonth() + 1) + "-" + pad2(d.getDate());
  }

  /* ---------- 1) 오늘의 산책 날씨 ---------- */
  (function initWeather() {
    var box = document.querySelector("[data-weather]");
    if (!box) return;
    var statusEl = box.querySelector("[data-weather-status]");
    var bodyEl = box.querySelector("[data-weather-body]");

    function show(view) {
      box.querySelector("[data-weather-icon]").textContent = view.icon;
      box.querySelector("[data-weather-temp]").textContent = view.temp;
      box.querySelector("[data-weather-grade]").textContent = view.grade;
      box.querySelector("[data-weather-detail]").textContent = view.detail;
      statusEl.hidden = true;
      bodyEl.hidden = false;
    }

    function fail() {
      statusEl.textContent = "날씨 정보를 불러오지 못했어요. 잠시 후 다시 확인해주세요.";
    }

    // WMO 날씨 코드 → 아이콘
    function weatherIcon(code) {
      if (code === 0) return "☀️";
      if (code <= 2) return "🌤️";
      if (code === 3) return "☁️";
      if (code === 45 || code === 48) return "🌫️";
      if ((code >= 71 && code <= 77) || code === 85 || code === 86) return "❄️";
      if (code >= 95) return "⛈️";
      return "🌧️"; // 51~67, 80~82: 이슬비·비·소나기
    }
    function isWet(code) {
      return code >= 51;
    }

    // 미세먼지 등급 (환경부 기준, PM10/PM2.5 중 나쁜 쪽) 0 좋음 ~ 3 매우 나쁨
    var DUST_NAMES = ["좋음", "보통", "나쁨", "매우 나쁨"];
    function dustLevel(pm10, pm25) {
      function level(value, limits) {
        if (typeof value !== "number") return -1;
        for (var i = 0; i < limits.length; i++) {
          if (value <= limits[i]) return i;
        }
        return 3;
      }
      return Math.max(level(pm10, [30, 80, 150]), level(pm25, [15, 35, 75]));
    }

    function walkGrade(temp, code, dust) {
      if (isWet(code) || temp < -5 || temp > 31 || dust >= 2) return "산책 주의";
      if (temp >= 5 && temp <= 27) return "산책 좋음";
      return "산책 보통";
    }

    // 17 → "오후 5시", 12 → "오후 12시"
    function hourText(h) {
      if (h < 12) return "오전 " + h + "시";
      return "오후 " + (h === 12 ? 12 : h - 12) + "시";
    }
    function rangeText(start, end) {
      if ((start < 12) === (end < 12)) {
        return hourText(start).replace("시", "") + "~" + hourText(end).split(" ")[1];
      }
      return hourText(start) + "~" + hourText(end);
    }

    // 오늘 남은 시간(6시~21시) 중 비 올 확률이 낮고 기온이 18도에 가까운 연속 2시간
    function recommendTime(hourly, nowHour) {
      var best = null;
      for (var i = 0; i + 1 < hourly.time.length; i++) {
        var h = Number(hourly.time[i].slice(11, 13));
        if (h < Math.max(6, nowHour + 1) || h > 19) continue;
        var score = 0;
        for (var j = i; j <= i + 1; j++) {
          score += (hourly.precipitation_probability[j] || 0)
            + Math.abs(hourly.temperature_2m[j] - 18) * 3
            + (isWet(hourly.weather_code[j]) ? 100 : 0);
        }
        if (!best || score < best.score) best = { hour: h, score: score };
      }
      return best ? rangeText(best.hour, best.hour + 2) + " 추천" : "오늘 남은 추천 시간이 없어요";
    }

    function getJson(url) {
      var controller = new AbortController();
      var timer = setTimeout(function () { controller.abort(); }, 8000);
      return fetch(url, { signal: controller.signal })
        .then(function (res) {
          if (!res.ok) throw new Error("Weather request failed: " + res.status);
          return res.json();
        })
        .finally(function () { clearTimeout(timer); });
    }

    function load(lat, lng) {
      var q = "latitude=" + lat.toFixed(2) + "&longitude=" + lng.toFixed(2) + "&timezone=auto";
      var forecast = getJson("https://api.open-meteo.com/v1/forecast?" + q
        + "&current=temperature_2m,weather_code"
        + "&hourly=temperature_2m,precipitation_probability,weather_code&forecast_days=1");
      // 미세먼지는 실패해도 날씨는 보여줌
      var air = getJson("https://air-quality-api.open-meteo.com/v1/air-quality?" + q
        + "&current=pm10,pm2_5")
        .catch(function (err) {
          console.warn("미세먼지 조회 실패:", err);
          return null;
        });

      Promise.all([forecast, air])
        .then(function (results) {
          var weather = results[0];
          var current = weather.current;
          var temp = current.temperature_2m;
          var code = current.weather_code;
          var dust = results[1] && results[1].current
            ? dustLevel(results[1].current.pm10, results[1].current.pm2_5)
            : -1;
          var view = {
            icon: weatherIcon(code),
            temp: Math.round(temp) + "°",
            grade: walkGrade(temp, code, dust),
            detail: (dust >= 0 ? "미세먼지 " + DUST_NAMES[dust] : "미세먼지 정보 없음")
              + " · " + recommendTime(weather.hourly, Number(current.time.slice(11, 13)))
          };
          show(view);
          try {
            sessionStorage.setItem(WEATHER_CACHE_KEY, JSON.stringify({ savedAt: Date.now(), view: view }));
          } catch (e) { /* 저장 못 해도 화면에는 표시됨 */ }
        })
        .catch(function (err) {
          console.error("날씨 조회 실패:", err);
          fail();
        });
    }

    try {
      var cached = JSON.parse(sessionStorage.getItem(WEATHER_CACHE_KEY));
      if (cached && Date.now() - cached.savedAt < WEATHER_CACHE_MS && cached.view) {
        show(cached.view);
        return;
      }
    } catch (e) { /* 캐시가 없거나 깨졌으면 새로 조회 */ }

    if (!navigator.geolocation) {
      load(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng);
      return;
    }
    navigator.geolocation.getCurrentPosition(
      function (position) { load(position.coords.latitude, position.coords.longitude); },
      function () { load(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng); },
      { enableHighAccuracy: false, maximumAge: 10 * 60 * 1000, timeout: 5000 }
    );
  })();

  /* ---------- 2) 나가기 전 체크 ---------- */
  (function initChecklist() {
    var box = document.querySelector("[data-walk-checklist]");
    if (!box) return;
    var inputs = box.querySelectorAll('input[type="checkbox"]');

    var checked = [];
    try {
      var saved = JSON.parse(localStorage.getItem(CHECKLIST_KEY));
      if (saved && saved.date === todayText() && Array.isArray(saved.checked)) checked = saved.checked;
    } catch (e) { /* 저장된 값이 없으면 모두 해제 상태 */ }

    inputs.forEach(function (input) {
      input.checked = checked.indexOf(input.value) !== -1;
      input.addEventListener("change", function () {
        var values = [];
        inputs.forEach(function (i) { if (i.checked) values.push(i.value); });
        try {
          localStorage.setItem(CHECKLIST_KEY, JSON.stringify({ date: todayText(), checked: values }));
        } catch (e) { /* 저장 못 해도 체크는 화면에 유지 */ }
      });
    });

    // 산책을 마치면 다음 산책을 위해 체크 모두 해제
    window.addEventListener("walk:stopped", function () {
      inputs.forEach(function (input) { input.checked = false; });
    });
  })();
})();
