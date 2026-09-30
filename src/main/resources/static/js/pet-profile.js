(() => {
  const form = document.querySelector("#pet-profile-form");
  if (!form) return;

  const submitButton = document.querySelector("#pet-submit");
  const deleteButton = document.querySelector("#pet-delete");
  const formTitle = document.querySelector("#pet-form-title");
  const status = document.querySelector("#pet-form-status");
  const photoInput = document.querySelector("#pet-photo");
  const photoButton = document.querySelector("#pet-photo-button");
  const photoPreview = document.querySelector("#pet-photo-preview");
  const photoPlaceholder = document.querySelector("#pet-photo-placeholder");
  const photoRemove = document.querySelector("#pet-photo-remove");
  const photoChange = document.querySelector("#pet-photo-change");
  const petId = new URLSearchParams(window.location.search).get("id");
  const editMode = petId !== null;

  let selectedImage = null;
  let originalImage = null;
  let removeImage = false;
  let previewUrl = null;
  let submitting = false;

  class PetNotice extends Error {}

  function csrfHeaders() {
    const headers = { Accept: "application/json" };
    if (form.dataset.csrfHeader && form.dataset.csrfToken) {
      headers[form.dataset.csrfHeader] = form.dataset.csrfToken;
    }
    return headers;
  }

  function showPhoto(src) {
    photoPreview.src = src;
    photoPreview.hidden = false;
    photoPlaceholder.hidden = true;
    photoRemove.hidden = false;
    photoChange.hidden = false;
    photoButton.setAttribute("aria-label", "반려견 사진 변경");
  }

  function clearPhoto(markRemoved = true) {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = null;
    selectedImage = null;
    removeImage = markRemoved && editMode && originalImage !== null;
    photoInput.value = "";
    photoPreview.removeAttribute("src");
    photoPreview.hidden = true;
    photoPlaceholder.hidden = false;
    photoRemove.hidden = true;
    photoChange.hidden = true;
    photoButton.setAttribute("aria-label", "반려견 사진 업로드");
  }

  function selectPill(field, value) {
    if (!value) return;
    const group = form.querySelector(`[data-pet-field="${field}"]`);
    const button = group?.querySelector(`.pill[data-value="${value}"]`);
    if (!button) return;
    group.querySelectorAll(".pill").forEach((pill) => {
      const selected = pill === button;
      pill.classList.toggle("selected", selected);
      pill.setAttribute("aria-pressed", String(selected));
    });
  }

  async function loadPet() {
    if (!editMode) return;
    if (!/^\d+$/.test(petId)) {
      status.hidden = false;
      status.textContent = "잘못된 반려견 정보입니다.";
      submitButton.disabled = true;
      return;
    }

    formTitle.textContent = "반려견 프로필 수정";
    submitButton.textContent = "수정하기";
    deleteButton.hidden = false;
    status.hidden = false;
    status.textContent = "반려견 정보를 불러오고 있습니다.";

    try {
      const response = await fetch(`${form.action}/${petId}`, {
        headers: csrfHeaders(),
        credentials: "same-origin",
      });
      if (response.status === 401) throw new PetNotice("로그인 후 다시 시도해주세요.");
      if (response.status === 404) throw new PetNotice("반려견 정보를 찾을 수 없습니다.");
      if (!response.ok) throw new Error(`반려견 조회 실패: ${response.status}`);

      const pet = await response.json();
      form.elements.namedItem("name").value = pet.name ?? "";
      form.elements.namedItem("breed").value = pet.breed ?? "";
      form.elements.namedItem("age").value = pet.age ?? "";
      selectPill("size", pet.size);
      selectPill("activityLevel", pet.activityLevel);

      originalImage = pet.profileImage || null;
      if (originalImage) showPhoto(originalImage);
      status.hidden = true;
    } catch (error) {
      console.error("반려견 정보 조회 실패:", error);
      status.hidden = false;
      status.textContent = error instanceof PetNotice ? error.message : "반려견 정보를 불러오지 못했습니다.";
      submitButton.disabled = true;
      deleteButton.disabled = true;
    }
  }

  photoButton.addEventListener("click", () => photoInput.click());
  photoRemove.addEventListener("click", () => clearPhoto(true));
  photoInput.addEventListener("change", () => {
    const file = photoInput.files[0];
    if (!file) return;
    if (!["image/jpeg", "image/png"].includes(file.type) || file.size > 5 * 1024 * 1024) {
      photoInput.value = "";
      status.hidden = false;
      status.textContent = "5MB 이하의 JPG 또는 PNG 사진을 선택해주세요.";
      return;
    }

    if (previewUrl) URL.revokeObjectURL(previewUrl);
    selectedImage = file;
    removeImage = false;
    previewUrl = URL.createObjectURL(file);
    showPhoto(previewUrl);
    status.hidden = true;
  });

  photoPreview.addEventListener("error", () => {
    clearPhoto(false);
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
    const selected = (key) => form.querySelector(`[data-pet-field="${key}"] .pill[aria-pressed="true"]`)?.dataset.value ?? null;
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
    deleteButton.disabled = true;
    submitButton.textContent = editMode ? "수정 중…" : "등록 중…";
    form.setAttribute("aria-busy", "true");
    status.textContent = editMode ? "반려견 정보를 수정하고 있습니다." : "반려견 정보를 등록하고 있습니다.";

    try {
      const body = new FormData();
      body.append("petInfo", new Blob([JSON.stringify(pet)], { type: "application/json" }));
      if (selectedImage) body.append("image", selectedImage);
      if (editMode) body.append("removeImage", String(removeImage));

      const response = await fetch(editMode ? `${form.action}/${petId}` : form.action, {
        method: editMode ? "PUT" : "POST",
        headers: csrfHeaders(),
        credentials: "same-origin",
        body,
      });

      if (response.redirected || response.status === 401) throw new PetNotice("로그인 후 다시 시도해주세요.");
      if (response.status === 403) throw new PetNotice("저장을 완료하지 못했어요. 새로고침 후 다시 시도해주세요.");
      if (response.status === 404) throw new PetNotice("반려견 정보를 찾을 수 없습니다.");
      if (response.status === 413) throw new PetNotice("사진 용량이 커서 저장하지 못했어요. 더 작은 사진을 선택해주세요.");
      if (!response.ok) throw new Error(`반려견 저장 실패: ${response.status}`);

      status.textContent = editMode ? "반려견 정보가 수정되었습니다." : "반려견이 등록되었습니다.";
      window.location.assign(form.dataset.successUrl);
    } catch (error) {
      console.error(editMode ? "반려견 수정 실패:" : "반려견 등록 실패:", error);
      status.textContent = error instanceof PetNotice ? error.message : "반려견 정보를 저장하지 못했어요. 잠시 후 다시 시도해주세요.";
    } finally {
      submitting = false;
      submitButton.disabled = false;
      photoButton.disabled = false;
      photoRemove.disabled = false;
      deleteButton.disabled = false;
      submitButton.textContent = editMode ? "수정하기" : "저장하기";
      form.setAttribute("aria-busy", "false");
    }
  });

  deleteButton.addEventListener("click", async () => {
    if (!editMode || submitting) return;
    if (!window.confirm("이 반려견 프로필을 삭제하시겠습니까?")) return;

    submitting = true;
    submitButton.disabled = true;
    deleteButton.disabled = true;
    status.hidden = false;
    status.textContent = "반려견 프로필을 삭제하고 있습니다.";

    try {
      const response = await fetch(`${form.action}/${petId}`, {
        method: "DELETE",
        headers: csrfHeaders(),
        credentials: "same-origin",
      });
      if (response.status === 401) throw new PetNotice("로그인 후 다시 시도해주세요.");
      if (response.status === 404) throw new PetNotice("반려견 정보를 찾을 수 없습니다.");
      if (!response.ok) throw new Error(`반려견 삭제 실패: ${response.status}`);
      window.location.assign(form.dataset.successUrl);
    } catch (error) {
      console.error("반려견 삭제 실패:", error);
      status.textContent = error instanceof PetNotice ? error.message : "반려견 프로필을 삭제하지 못했습니다.";
      submitting = false;
      submitButton.disabled = false;
      deleteButton.disabled = false;
    }
  });

  loadPet();
})();
