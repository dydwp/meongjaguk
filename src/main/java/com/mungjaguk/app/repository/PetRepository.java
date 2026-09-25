package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.Pet;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetRepository extends JpaRepository<Pet, Long> {

    List<Pet> findByOwner_UserIdOrderByIdAsc(Long userId);

}