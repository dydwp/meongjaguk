package com.meongjaguk.app.controller;

import com.meongjaguk.app.dto.PetCardView;
import com.meongjaguk.app.dto.PetEditView;
import com.meongjaguk.app.dto.PetRequestDto;
import com.meongjaguk.app.service.PetService;
import com.meongjaguk.app.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PetController.class)
class PetControllerTest extends WebTestSupport {

    @MockitoBean
    PetService petService;

    private static final PetRequestDto BORI = new PetRequestDto("보리", "말티즈", 3, "SMALL", "HIGH");

    private static MockMultipartFile petInfo() {
        return new MockMultipartFile("petInfo", "", MediaType.APPLICATION_JSON_VALUE,
                """
                {"name": "보리", "breed": "말티즈", "age": 3, "size": "SMALL", "activityLevel": "HIGH"}
                """.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void myPetsAreListed() throws Exception {
        when(petService.getMyPets(USER_ID)).thenReturn(List.of(
                new PetCardView(1L, "보리", "말티즈", "소형견", 3, null, "높음")));

        mvc.perform(get("/api/pet-profile/pets").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("보리"))
                .andExpect(jsonPath("$[0].sizeLabel").value("소형견"));
    }

    @Test
    void petDetailForEditing() throws Exception {
        when(petService.getPetEditView(1L, USER_ID))
                .thenReturn(new PetEditView(1L, "보리", "말티즈", 3, "SMALL", "HIGH", null));

        mvc.perform(get("/api/pet-profile/pets/1").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.age").value(3));
    }

    @Test
    void othersPetIs404() throws Exception {
        when(petService.getPetEditView(1L, USER_ID)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mvc.perform(get("/api/pet-profile/pets/1").with(login()))
                .andExpect(status().isNotFound());
    }

    @Test
    void guestCannotSeePets() throws Exception {
        mvc.perform(get("/api/pet-profile/pets")).andExpect(status().is3xxRedirection());
    }

    @Test
    void addPetWithImage() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "bori.png", "image/png", new byte[]{1, 2});

        mvc.perform(multipart("/api/pet-profile/pets").file(petInfo()).file(image).with(login()).with(csrf()))
                .andExpect(status().isCreated());

        verify(petService).postPetInfoAdd(eq(BORI), any(), eq(USER_ID));
    }

    @Test
    void addPetWithoutImage() throws Exception {
        mvc.perform(multipart("/api/pet-profile/pets").file(petInfo()).with(login()).with(csrf()))
                .andExpect(status().isCreated());

        verify(petService).postPetInfoAdd(eq(BORI), isNull(), eq(USER_ID));
    }

    @Test
    void updatePetCanRemoveImage() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/pet-profile/pets/1").file(petInfo())
                        .param("removeImage", "true").with(login()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(petService).updatePet(eq(1L), eq(BORI), isNull(), eq(true), eq(USER_ID));
    }

    @Test
    void deletePet() throws Exception {
        mvc.perform(delete("/api/pet-profile/pets/1").with(login()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(petService).deletePet(1L, USER_ID);
    }

    @Test
    void deletingOthersPetIs404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(petService).deletePet(1L, USER_ID);

        mvc.perform(delete("/api/pet-profile/pets/1").with(login()).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
