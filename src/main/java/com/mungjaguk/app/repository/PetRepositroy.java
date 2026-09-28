package com.mungjaguk.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mungjaguk.app.entity.Pet;

public interface PetRepositroy extends JpaRepository<Pet, Integer> {

  public Pet findByPetId(String pet_id);
}