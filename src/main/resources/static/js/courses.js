// 추천 산책로 리스트 조회
async function loadCourses() {
  try {
    const response = await fetch("/api/courses/routes");

    if (!response.ok) {
      throw new Error(`코스 조회 실패 : ${response.status}`);
    }

    const courses = await response.json();

    console.log("받은 데이터:", courses);
    console.log("배열인지:", Array.isArray(courses));
    console.log("목록 요소:", document.querySelector("#course-list"));

    if (courses.length === 0) {
      courseList.textContent = "등록된 추천 산책로가 없습니다.";
      return;
    }

    const cards = courses.map(createCoursesCard);
    courseList.replaceChildren(...cards);
  } catch (error) {
    console.error("코스를 불러오지 못했습니다.", error);
  }
}

const courseList = document.querySelector("#course-list");

if (courseList) {
  loadCourses();
}

function createCoursesCard(courses) {
  const card = document.createElement("a");

  card.className = "route-item";

  card.href = `/course-detail?coursesId=${encodeURIComponent(courses.courseId)}`;

  card.innerHTML = `
      <div class="route-thumb">🐾</div>
      <div>
        <h3>한강 벚꽃길 코스 <span class="tag">추천</span></h3>
        <p>
          강변을 따라 걷는 평탄한 코스예요. 소형견도 부담 없이 걸을 수 있어요.
        </p>
        <span class="meta">2.3km · 약 35분</span>
      </div>
  `;

  card.querySelector("h3").textContent = courses.courseName;
  card.querySelector("p").textContent = courses.description ?? "";

  const distance =
    courses.distanceM != null
      ? `${(courses.distanceM / 1000).toFixed(1)}km`
      : "";

  const minutes =
    courses.estimatedMinutes != null ? `약 ${courses.estimatedMinutes}분` : "";

  card.querySelector(".meta").textContent = [distance, minutes]
    .filter(Boolean)
    .join(" · ");

  return card;
}
