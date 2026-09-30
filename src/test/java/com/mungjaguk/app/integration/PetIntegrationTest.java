package com.mungjaguk.app.integration;

import com.mungjaguk.app.dto.PetCardView;
import com.mungjaguk.app.dto.PetRequestDto;
import com.mungjaguk.app.entity.Pet;
import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.repository.PetRepository;
import com.mungjaguk.app.repository.UserRepository;
import com.mungjaguk.app.security.OAuthAttributes;
import com.mungjaguk.app.service.PetService;
import com.mungjaguk.app.service.UserService;
import com.mungjaguk.app.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 회원 가입(소셜 로그인)과 반려견 등록·수정·삭제 */
class PetIntegrationTest extends IntegrationTestSupport {

    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired PetService petService;
    @Autowired PetRepository pets;

    @Test
    void socialLoginJoinsOnceAndThenLogsIn() {
        OAuthAttributes kakao = new OAuthAttributes("kakao", "k-100", "용제", null, null);

        User joined = userService.loginOrJoin(kakao);
        User again = userService.loginOrJoin(kakao);
        flushAndClear();

        assertEquals(joined.getUserId(), again.getUserId());
        assertEquals(1, users.findAll().stream().filter(u -> "k-100".equals(u.getProviderId())).count());
        assertNotNull(users.findById(joined.getUserId()).orElseThrow().getCreatedAt());
    }

    @Test
    void sameIdFromDifferentProviderIsDifferentMember() {
        User kakao = userService.loginOrJoin(new OAuthAttributes("kakao", "same", "A", null, null));
        User naver = userService.loginOrJoin(new OAuthAttributes("naver", "same", "B", null, null));

        assertTrue(!kakao.getUserId().equals(naver.getUserId()));
    }

    @Test
    void petLifecycleWithoutPhoto() {
        User me = data.user("용제");

        petService.postPetInfoAdd(new PetRequestDto("보리", "말티즈", 3, "SMALL", "HIGH"), null, me.getUserId());
        flushAndClear();
        List<PetCardView> myPets = petService.getMyPets(me.getUserId());
        assertEquals(1, myPets.size());
        assertEquals("말티즈 · 소형견 · 3세", myPets.get(0).summaryLine());
        Long petId = myPets.get(0).id();

        petService.updatePet(petId, new PetRequestDto("보리", "말티즈", 4, "MEDIUM", "LOW"), null, false, me.getUserId());
        flushAndClear();
        Pet updated = pets.findById(petId).orElseThrow();
        assertEquals(4, updated.ageInYears());
        assertEquals("MEDIUM", updated.getSize());

        petService.deletePet(petId, me.getUserId());
        flushAndClear();
        assertTrue(pets.findById(petId).isEmpty());
    }

    @Test
    void cannotTouchOthersPet() {
        User owner = data.user("주인");
        User stranger = data.user("남");
        Pet bori = data.pet(owner, "보리");

        assertThrows(ResponseStatusException.class, () -> petService.getPetEditView(bori.getPetId(), stranger.getUserId()));
        assertThrows(ResponseStatusException.class, () -> petService.deletePet(bori.getPetId(), stranger.getUserId()));
        assertTrue(pets.findById(bori.getPetId()).isPresent());
    }
}
