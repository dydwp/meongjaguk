package com.mungjaguk.app.service;

import com.mungjaguk.app.dto.PetCardView;
import com.mungjaguk.app.dto.PetEditView;
import com.mungjaguk.app.dto.PetRequestDto;
import com.mungjaguk.app.entity.Pet;
import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.repository.PetRepository;
import com.mungjaguk.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.mungjaguk.app.support.Fixtures.pet;
import static com.mungjaguk.app.support.Fixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PetServiceTest {

    private PetRepository pets;
    private UserRepository users;
    private PetImageStorage storage;
    private PetService service;

    private final User owner = user(7L, "용제");

    @BeforeEach
    void setUp() {
        pets = mock(PetRepository.class);
        users = mock(UserRepository.class);
        storage = mock(PetImageStorage.class);
        service = new PetService(pets, users, storage);
    }

    @Test
    void myPetsAreShownWithKoreanLabels() {
        Pet bori = pet(1L, owner, "보리");
        bori.setBreed("말티즈");
        bori.setSize("SMALL");
        bori.setActivityLevel("HIGH");
        bori.setBirthDate(LocalDate.now().minusYears(3));
        Pet choco = pet(2L, owner, "초코");
        choco.setSize("GIANT");
        when(pets.findByUser_UserIdOrderByPetIdAsc(7L)).thenReturn(List.of(bori, choco));

        List<PetCardView> cards = service.getMyPets(7L);

        assertEquals("소형견", cards.get(0).sizeLabel());
        assertEquals("높음", cards.get(0).activityLevelLabel());
        assertEquals(3, cards.get(0).ageInYears());
        assertEquals("말티즈 · 소형견 · 3세", cards.get(0).summaryLine());
        assertEquals("", cards.get(1).breed());           // 품종 없음
        assertEquals("GIANT", cards.get(1).sizeLabel());  // 모르는 값은 그대로
        assertEquals("", cards.get(1).activityLevelLabel());
        assertEquals(0, cards.get(1).ageInYears());
    }

    @Test
    void editViewHasNullAgeWhenBirthDateUnknown() {
        when(pets.findByPetIdAndUser_UserId(1L, 7L)).thenReturn(Optional.of(pet(1L, owner, "보리")));

        PetEditView view = service.getPetEditView(1L, 7L);

        assertEquals("보리", view.name());
        assertNull(view.age());
    }

    @Test
    void othersPetIsNotFound() {
        when(pets.findByPetIdAndUser_UserId(1L, 8L)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.getPetEditView(1L, 8L));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertThrows(ResponseStatusException.class, () -> service.deletePet(1L, 8L));
        verify(pets, never()).delete(any());
    }

    @Test
    void addPetSavesInfoAndImage() {
        MockMultipartFile image = new MockMultipartFile("image", "bori.png", "image/png", new byte[]{1});
        when(users.findById(7L)).thenReturn(Optional.of(owner));
        when(storage.save(image)).thenReturn("/images/pets/abc.png");

        service.postPetInfoAdd(new PetRequestDto("보리", "말티즈", 3, "SMALL", "LOW"), image, 7L);

        ArgumentCaptor<Pet> captor = ArgumentCaptor.forClass(Pet.class);
        verify(pets).save(captor.capture());
        Pet saved = captor.getValue();
        assertEquals(owner, saved.getUser());
        assertEquals("보리", saved.getName());
        assertEquals(LocalDate.now().minusYears(3), saved.getBirthDate());
        assertEquals("/images/pets/abc.png", saved.getProfileImage());
    }

    @Test
    void addPetForUnknownUserFails() {
        when(users.findById(7L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.postPetInfoAdd(new PetRequestDto("보리", null, null, null, null), null, 7L));
        verify(pets, never()).save(any());
    }

    @Test
    void updateWithRemoveImageClearsAndDeletesOldFile() {
        Pet bori = givenPetWithImage("/images/pets/old.png");

        service.updatePet(1L, new PetRequestDto("보리2", null, null, "MEDIUM", null), null, true, 7L);

        assertEquals("보리2", bori.getName());
        assertNull(bori.getBirthDate());
        assertNull(bori.getProfileImage());
        verify(storage).deleteAfterCommit("/images/pets/old.png");
    }

    @Test
    void updateWithNewImageReplacesOldFile() {
        Pet bori = givenPetWithImage("/images/pets/old.png");
        MockMultipartFile image = new MockMultipartFile("image", "new.png", "image/png", new byte[]{1});
        when(storage.save(image)).thenReturn("/images/pets/new.png");

        service.updatePet(1L, new PetRequestDto("보리", null, 2, null, null), image, false, 7L);

        assertEquals("/images/pets/new.png", bori.getProfileImage());
        verify(storage).deleteAfterCommit("/images/pets/old.png");
    }

    @Test
    void updateWithoutImageKeepsOldFile() {
        Pet bori = givenPetWithImage("/images/pets/old.png");

        service.updatePet(1L, new PetRequestDto("보리", null, null, null, null),
                new MockMultipartFile("image", new byte[0]), false, 7L);

        assertEquals("/images/pets/old.png", bori.getProfileImage());
        verify(storage, never()).save(any());
        verify(storage, never()).deleteAfterCommit(any());
    }

    @Test
    void deletePetRemovesImageToo() {
        Pet bori = givenPetWithImage("/images/pets/old.png");

        service.deletePet(1L, 7L);

        verify(pets).delete(bori);
        verify(storage).deleteAfterCommit("/images/pets/old.png");
    }

    private Pet givenPetWithImage(String image) {
        Pet pet = pet(1L, owner, "보리");
        pet.setProfileImage(image);
        when(pets.findByPetIdAndUser_UserId(1L, 7L)).thenReturn(Optional.of(pet));
        return pet;
    }
}
