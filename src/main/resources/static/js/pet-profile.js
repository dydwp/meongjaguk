(() => {
  const form = document.querySelector("#pet-profile-form");
  if (!form) return;

  const submitButton = document.querySelector("#pet-submit");
  const status = document.querySelector("#pet-form-status");
  const photoInput = document.querySelector("#pet-photo");
  const photoButton = document.querySelector("#pet-photo-button");
  const photoPreview = document.querySelector("#pet-photo-preview");
  const photoPlaceholder = document.querySelector("#pet-photo-placeholder");
  const photoRemove = document.querySelector("#pet-photo-remove");
  const photoChange = document.querySelector("#pet-photo-change");
  let selectedImage = null;
  let previewUrl = null;
  let submitting = false;

  // 화면에는 직접 작성한 안내만 표시하고, 원래 오류는 콘솔에 남깁니다.
  class RegistrationNotice extends Error {}

  function clearPhoto() {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = null;
    selectedImage = null;
    photoInput.value = "";
    photoPreview.removeAttribute("src");
    photoPreview.hidden = true;
    photoPlaceholder.hidden = false;
    photoRemove.hidden = true;
    photoChange.hidden = true;
    photoButton.setAttribute("aria-label", "반려견 사진 업로드");
  }

  photoButton.addEventListener("click", () => photoInput.click());
  photoRemove.addEventListener("click", clearPhoto);
  photoInput.addEventListener("change", () => {
    const file = photoInput.files[0];
    if (!file) return;
    if (
      !["image/jpeg", "image/png"].includes(file.type) ||
      file.size > 5 * 1024 * 1024
    ) {
      photoInput.value = "";
      status.hidden = false;
      status.textContent = "5MB 이하의 JPG 또는 PNG 사진을 선택해주세요.";
      return;
    }
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    selectedImage = file;
    previewUrl = URL.createObjectURL(file);
    photoPreview.src = previewUrl;
    photoPreview.hidden = false;
    photoPlaceholder.hidden = true;
    photoRemove.hidden = false;
    photoChange.hidden = false;
    photoButton.setAttribute("aria-label", "반려견 사진 변경");
    status.hidden = true;
  });
  photoPreview.addEventListener("error", () => {
    clearPhoto();
    status.hidden = false;
    status.textContent = "사진을 읽을 수 없습니다. 다른 사진을 선택해주세요.";
  });
  window.addEventListener("pagehide", () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (submitting || !form.reportValidity()) return;

    const data = new FormData(form);
    const value = (key) => String(data.get(key) ?? "").trim() || null;
    const selected = (key) =>
      form.querySelector(`[data-pet-field="${key}"] .pill[aria-pressed="true"]`)
        ?.dataset.value ?? null;
    const age = value("age");
    const pet = {
      name: value("name"),
      breed: value("breed"),
      age: age === null ? null : Number.parseInt(age, 10),
      size: selected("size"),
      activityLevel: selected("activityLevel"),
    };

    status.hidden = false;
    if (!pet.name) {
      status.textContent = "반려견 이름을 입력해주세요.";
      form.elements.namedItem("name").focus();
      return;
    }
    if (pet.age !== null && (!Number.isSafeInteger(pet.age) || pet.age < 0)) {
      status.textContent = "나이를 올바르게 입력해주세요.";
      return;
    }

    submitting = true;
    submitButton.disabled = true;
    photoButton.disabled = true;
    photoRemove.disabled = true;
    submitButton.textContent = "등록 중…";
    form.setAttribute("aria-busy", "true");
    status.textContent = "반려견 정보를 등록하고 있습니다.";

    try {
      // multipart Content-Type과 boundary는 브라우저가 설정합니다.
      const headers = { Accept: "application/json" };
      if (form.dataset.csrfHeader && form.dataset.csrfToken) {
        headers[form.dataset.csrfHeader] = form.dataset.csrfToken;
      }
      const body = new FormData();
      body.append(
        "petInfo",
        new Blob([JSON.stringify(pet)], { type: "application/json" }),
      );
      if (selectedImage) body.append("image", selectedImage);
      const response = await fetch(form.action, {
        method: "POST",
        headers,
        credentials: "same-origin",
        body,
      });

      if (response.redirected || response.status === 401) {
        throw new RegistrationNotice("로그인 후 다시 등록해주세요.");
      }
      if (response.status === 403) {
        throw new RegistrationNotice(
          "등록을 완료하지 못했어요. 새로고침 후 다시 시도해주세요.",
        );
      }
      if (response.status === 413) {
        throw new RegistrationNotice(
          "사진 용량이 커서 등록하지 못했어요. 더 작은 사진을 선택해주세요.",
        );
      }
      if (!response.ok) {
        throw new Error(
          `반려견 등록에 실패했습니다. (오류 ${response.status})`,
        );
      }
      if ((response.headers.get("Content-Type") ?? "").includes("text/html")) {
        throw new RegistrationNotice("로그인 후 다시 등록해주세요.");
      }

      // 성공 응답 본문이 없는 201/204 응답도 처리합니다.
      status.textContent = "반려견이 등록되었습니다.";
      window.location.assign(form.dataset.successUrl);
    } catch (error) {
      console.error("반려견 등록 실패:", error);
      status.textContent =
        error instanceof RegistrationNotice
          ? error.message
          : "반려견을 등록하지 못했어요. 잠시 후 다시 시도해주세요.";
    } finally {
      submitting = false;
      submitButton.disabled = false;
      photoButton.disabled = false;
      photoRemove.disabled = false;
      submitButton.textContent = "저장하기";
      form.setAttribute("aria-busy", "false");
    }
  });
})();
