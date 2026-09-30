package com.mungjaguk.app.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.mungjaguk.app.dto.PetCardView;
import com.mungjaguk.app.dto.PetEditView;
import com.mungjaguk.app.dto.PetRequestDto;
import com.mungjaguk.app.entity.Pet;
import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.repository.PetRepository;
import com.mungjaguk.app.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class PetService {

  private final PetRepository petRepository;
  private final UserRepository userRepository;
  private final PetImageStorage imageStorage;

  public PetService(PetRepository petRepository, UserRepository userRepository, PetImageStorage imageStorage) {
    this.petRepository = petRepository;
    this.userRepository = userRepository;
    this.imageStorage = imageStorage;
  }

  public List<PetCardView> getMyPets(Long userId) {
    return petRepository.findByUser_UserIdOrderByPetIdAsc(userId).stream()
        .map(this::toCardView)
        .toList();
  }

  public PetEditView getPetEditView(Long petId, Long userId) {
    Pet pet = findMyPet(petId, userId);
    return new PetEditView(
        pet.getPetId(),
        pet.getName(),
        pet.getBreed(),
        pet.getBirthDate() == null ? null : pet.ageInYears(),
        pet.getSize(),
        pet.getActivityLevel(),
        pet.getProfileImage());
  }

  @Transactional
  public void postPetInfoAdd(PetRequestDto request, MultipartFile image, Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

    Pet pet = new Pet();
    pet.setUser(user);
    applyPetInfo(pet, request);
    pet.setProfileImage(imageStorage.save(image));
    petRepository.save(pet);
  }

  @Transactional
  public void updatePet(Long petId, PetRequestDto request, MultipartFile image, boolean removeImage, Long userId) {
    Pet pet = findMyPet(petId, userId);
    String previousImage = pet.getProfileImage();

    applyPetInfo(pet, request);

    if (removeImage) {
      pet.setProfileImage(null);
      imageStorage.deleteAfterCommit(previousImage);
    } else if (image != null && !image.isEmpty()) {
      pet.setProfileImage(imageStorage.save(image));
      imageStorage.deleteAfterCommit(previousImage);
    }
  }

  @Transactional
  public void deletePet(Long petId, Long userId) {
    Pet pet = findMyPet(petId, userId);
    String profileImage = pet.getProfileImage();
    petRepository.delete(pet);
    imageStorage.deleteAfterCommit(profileImage);
  }

  private Pet findMyPet(Long petId, Long userId) {
    return petRepository.findByPetIdAndUser_UserId(petId, userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "반려견 정보를 찾을 수 없습니다."));
  }

  private void applyPetInfo(Pet pet, PetRequestDto request) {
    pet.setName(request.name());
    pet.setBreed(request.breed());
    pet.setSize(request.size());
    pet.setActivityLevel(request.activityLevel());
    pet.setBirthDate(request.age() == null ? null : LocalDate.now().minusYears(request.age()));
  }

  private PetCardView toCardView(Pet pet) {
    return new PetCardView(
        pet.getPetId(),
        pet.getName(),
        pet.getBreed() == null ? "" : pet.getBreed(),
        sizeLabel(pet.getSize()),
        pet.ageInYears(),
        pet.getProfileImage(),
        activityLevelLabel(pet.getActivityLevel()));
  }

  public List<PetCardView> getMyPetsByIds(Long userId, List<Long> petIds) {
    if (petIds == null || petIds.isEmpty()) {
        return List.of();
    }

    return getMyPets(userId).stream()
            .filter(pet -> petIds.contains(pet.id()))
            .toList();
  }

  private String activityLevelLabel(String activityLevel) {
    if (activityLevel == null || activityLevel.isBlank()) {
      return "";
    }

    return switch (activityLevel) {
      case "LOW" -> "낮음";
      case "MEDIUM" -> "보통";
      case "HIGH" -> "높음";
      default -> activityLevel;
    };
  }

  private String sizeLabel(String size) {
    if (size == null || size.isBlank()) {
      return "";
    }

    return switch (size) {
      case "SMALL" -> "소형견";
      case "MEDIUM" -> "중형견";
      case "LARGE" -> "대형견";
      default -> size;
    };
  }
}