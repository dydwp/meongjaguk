package com.mungjaguk.app.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.mungjaguk.app.dto.PetRequestDto;
import com.mungjaguk.app.entity.Pet;
import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.repository.PetRepositroy;
import com.mungjaguk.app.repository.UserRepository;

import jakarta.transaction.Transactional;

@Service
public class PetService {
  private final PetRepositroy repositroy;
  private final UserRepository userRepository;
  private final PetImageStorage imageStorage;

  public PetService(PetRepositroy repositroy, UserRepository userRepository, PetImageStorage imageStorage) {
    this.repositroy = repositroy;
    this.userRepository = userRepository;
    this.imageStorage = imageStorage;
  }

  @Transactional
  public List<Pet> getPetList() {
    return repositroy.findAll();
  }

  @Transactional
  public Pet getPetInfo(String pet_id) {
    return repositroy.findByPetId(pet_id);
  }

  @Transactional
  public void postPetInfoAdd(PetRequestDto request, MultipartFile image, Long userId) {
    Pet pet = new Pet();
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

    pet.setUser(user);
    pet.setName(request.name());
    pet.setBreed(request.breed());
    pet.setSize(request.size());
    pet.setProfileImage(imageStorage.save(image));

    repositroy.save(pet);

  }
}
