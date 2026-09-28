package com.mungjaguk.app.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.mungjaguk.app.dto.PetCardView;
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

  public List<Pet> getPetList() {
    return petRepository.findAll();
  }

  public Pet getPetInfo(String petId) {
    Long id;

    try {
      id = Long.valueOf(petId);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("잘못된 반려견 번호입니다.");
    }

    return petRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("반려견 정보를 찾을 수 없습니다."));
  }

  @Transactional
  public void postPetInfoAdd(PetRequestDto request, MultipartFile image, Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

    Pet pet = new Pet();
    pet.setUser(user);
    pet.setName(request.name());
    pet.setBreed(request.breed());
    pet.setSize(request.size());
    pet.setProfileImage(imageStorage.save(image));

    petRepository.save(pet);
  }

  private PetCardView toCardView(Pet pet) {
    return new PetCardView(
        pet.getPetId(),
        pet.getName(),
        pet.getBreed() == null ? "" : pet.getBreed(),
        sizeLabel(pet.getSize()),
        pet.ageInYears());
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