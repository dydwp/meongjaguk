package com.meongjaguk.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.meongjaguk.app.dto.PetCardView;
import com.meongjaguk.app.dto.PetEditView;
import com.meongjaguk.app.dto.PetRequestDto;
import com.meongjaguk.app.security.LoginUser;
import com.meongjaguk.app.service.PetService;

@Controller
@RequestMapping("/api/pet-profile")
public class PetController {
  private final PetService service;

  public PetController(PetService service) {
    this.service = service;
  }

  @GetMapping("/pets")
  public ResponseEntity<List<PetCardView>> getPetFindList(@AuthenticationPrincipal LoginUser loginUser) {
    return ResponseEntity.ok(service.getMyPets(loginUser.getUserId()));
  }

  @GetMapping("/pets/{petId}")
  public ResponseEntity<PetEditView> getPet(@PathVariable Long petId,
      @AuthenticationPrincipal LoginUser loginUser) {
    return ResponseEntity.ok(service.getPetEditView(petId, loginUser.getUserId()));
  }

  @PostMapping("/pets")
  public ResponseEntity<Void> postPetAdd(@AuthenticationPrincipal LoginUser loginUser,
      @RequestPart("petInfo") PetRequestDto petInfo,
      @RequestPart(value = "image", required = false) MultipartFile image) {
    service.postPetInfoAdd(petInfo, image, loginUser.getUserId());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PutMapping("/pets/{petId}")
  public ResponseEntity<Void> updatePet(@PathVariable Long petId,
      @AuthenticationPrincipal LoginUser loginUser,
      @RequestPart("petInfo") PetRequestDto petInfo,
      @RequestPart(value = "image", required = false) MultipartFile image,
      @RequestParam(value = "removeImage", defaultValue = "false") boolean removeImage) {
    service.updatePet(petId, petInfo, image, removeImage, loginUser.getUserId());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/pets/{petId}")
  public ResponseEntity<Void> deletePet(@PathVariable Long petId,
      @AuthenticationPrincipal LoginUser loginUser) {
    service.deletePet(petId, loginUser.getUserId());
    return ResponseEntity.noContent().build();
  }
}
