package com.mungjaguk.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.mungjaguk.app.dto.PetRequestDto;
import com.mungjaguk.app.entity.Pet;
import com.mungjaguk.app.security.LoginUser;
import com.mungjaguk.app.service.PetService;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequestMapping("/api/pet-profile")
public class PetController {
  private final PetService service;

  public PetController(PetService service) {
    this.service = service;
  }

  @GetMapping("/pets")
  public ResponseEntity<List<Pet>> getPetFindList() {
    List<Pet> pets = service.getPetList();
    return ResponseEntity.ok(pets);
  }

  @PostMapping("/pets")
  public ResponseEntity<Void> postPetAdd(@AuthenticationPrincipal LoginUser loginUser,
      @RequestPart("petInfo") PetRequestDto petInfo,
      @RequestPart(value = "image", required = false) MultipartFile image) {
    service.postPetInfoAdd(petInfo, image, loginUser.getUserId());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

}
