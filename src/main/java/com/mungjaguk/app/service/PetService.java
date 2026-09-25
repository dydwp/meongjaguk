package com.mungjaguk.app.service;

import com.mungjaguk.app.dto.PetCardView;
import com.mungjaguk.app.entity.Pet;
import com.mungjaguk.app.entity.PetSize;
import com.mungjaguk.app.repository.PetRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PetService {

    private final PetRepository petRepository;

    public PetService(PetRepository petRepository) {
        this.petRepository = petRepository;
    }

    public List<PetCardView> getMyPets(Long userId) {
        return petRepository.findByOwner_UserIdOrderByIdAsc(userId).stream()
                .map(this::toCardView)
                .toList();
    }

    private PetCardView toCardView(Pet pet) {
        return new PetCardView(
                pet.getId(),
                pet.getName(),
                pet.getBreed() == null ? "" : pet.getBreed(),
                sizeLabel(pet.getSize()),
                pet.ageInYears()
        );
    }

    private String sizeLabel(PetSize size) {
        if (size == null) {
            return "";
        }

        return switch (size) {
            case SMALL -> "소형견";
            case MEDIUM -> "중형견";
            case LARGE -> "대형견";
        };
    }
}