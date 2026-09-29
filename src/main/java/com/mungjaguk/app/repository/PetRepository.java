package com.mungjaguk.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mungjaguk.app.entity.Pet;

public interface PetRepository extends JpaRepository<Pet, Long> {

    List<Pet> findByUser_UserIdOrderByPetIdAsc(Long userId);

    Optional<Pet> findByPetIdAndUser_UserId(Long petId, Long userId);
}