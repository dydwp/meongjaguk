package com.meongjaguk.app.service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.meongjaguk.app.dto.PetCardView;
import com.meongjaguk.app.dto.WalkDetailView;
import com.meongjaguk.app.dto.WalkHistoryItemView;
import com.meongjaguk.app.dto.WalkPointView;
import com.meongjaguk.app.entity.Route;
import com.meongjaguk.app.entity.WalkRecord;
import com.meongjaguk.app.repository.CoursePointRepository;
import com.meongjaguk.app.repository.RouteRepository;
import com.meongjaguk.app.repository.PetRepository;
import com.meongjaguk.app.repository.WalkRecordPetRepository;
import com.meongjaguk.app.repository.WalkRecordPointRepository;
import com.meongjaguk.app.repository.WalkRecordPlannedPointRepository;
import com.meongjaguk.app.repository.WalkRecordRepository;

@Service
@Transactional(readOnly = true)
public class WalkRecordService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREA);

    private final WalkRecordRepository walkRecordRepository;
    private final WalkRecordPetRepository walkRecordPetRepository;
    private final WalkRecordPointRepository walkRecordPointRepository;
    private final WalkRecordPlannedPointRepository plannedPointRepository;
    private final PetRepository petRepository;
    private final PetService petService;
    private final RouteRepository routeRepository;
    private final CoursePointRepository coursePointRepository;

    public WalkRecordService(WalkRecordRepository walkRecordRepository,
                            RouteRepository routeRepository,
                            WalkRecordPointRepository walkRecordPointRepository,
                            WalkRecordPlannedPointRepository plannedPointRepository,
                            CoursePointRepository coursePointRepository,
                            WalkRecordPetRepository walkRecordPetRepository,
                            PetRepository petRepository,
                            PetService petService) {
        this.walkRecordRepository = walkRecordRepository;
        this.routeRepository = routeRepository;
        this.walkRecordPointRepository = walkRecordPointRepository;
        this.plannedPointRepository = plannedPointRepository;
        this.coursePointRepository = coursePointRepository;
        this.walkRecordPetRepository = walkRecordPetRepository;
        this.petRepository = petRepository;
        this.petService = petService;
    }

    public List<WalkHistoryItemView> getMyWalkHistory(Long userId) {
        return getMyWalkHistory(userId, null);
    }

    public List<WalkHistoryItemView> getMyWalkHistory(Long userId, Long petId) {
        List<WalkRecord> records = walkRecordRepository.findByUserIdOrderByStartedAtDesc(userId);

        if (petId != null) {
            petRepository.findByPetIdAndUser_UserId(petId, userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "반려견 정보를 찾을 수 없습니다."
                    ));

            Set<Long> walkRecordIds = walkRecordPetRepository.findWalkRecordIdsByPetId(userId, petId);

            records = records.stream()
                    .filter(record -> walkRecordIds.contains(record.getWalkRecordId()))
                    .toList();
        }

        Map<Long, List<String>> petNamesByWalkRecord =
                walkRecordPetRepository.findPetNamesByUserId(userId);

        return records.stream()
                .map(record -> toHistoryItem(
                        record,
                        petNamesByWalkRecord.getOrDefault(record.getWalkRecordId(), List.of())
                ))
                .toList();
    }

    public Optional<WalkDetailView> getDetail(Long walkRecordId, Long userId) {
        return walkRecordRepository.findByWalkRecordIdAndUserId(walkRecordId, userId)
                .map(record -> toDetailView(record, userId));
    }

    private WalkHistoryItemView toHistoryItem(WalkRecord record, List<String> petNames) {
        Optional<Route> route = findRoute(record.getCourseId());

        return new WalkHistoryItemView(
                record.getWalkRecordId(),
                record.getPlannedTitle() != null ? record.getPlannedTitle()
                        : route.map(Route::getCourseName).orElse("자유 산책"),
                "개인 산책",
                String.join(" · ", petNames),
                distanceLabel(record.getDistanceM()),
                minuteDurationLabel(record.getDurationSeconds()),
                record.getStartedAt().format(DATE_FORMAT)
        );
    }

    private WalkDetailView toDetailView(WalkRecord record, Long userId) {
        Optional<Route> route = findRoute(record.getCourseId());

        String title = record.getPlannedTitle() != null ? record.getPlannedTitle()
                : route.map(Route::getCourseName).orElse("자유 산책");
        String description = record.getPlannedTitle() != null ? record.getPlannedDescription()
                : route.map(Route::getDescription).orElse("");
        String plannedDistance = record.getPlannedDistanceM() != null
                ? distanceLabel(record.getPlannedDistanceM())
                : route.map(Route::getDistanceM).map(this::distanceLabel).orElse("-");
        String plannedDuration = record.getPlannedEstimatedMinutes() != null
                ? "약 " + record.getPlannedEstimatedMinutes() + "분"
                : route.map(Route::getEstimatedMinutes)
                    .map(minutes -> "약 " + minutes + "분").orElse("-");
        List<String> petNames = walkRecordPetRepository.findPetNamesByWalkRecordId(record.getWalkRecordId(), userId);
        String petNamesLabel = String.join(" · ", petNames);

        return new WalkDetailView(
                record.getWalkRecordId(),
                title,
                "개인 산책",
                record.getPlannedTitle() != null || route.isPresent(),
                WalkRecord.STATUS_COMPLETED.equals(record.getStatus()),
                description,
                "거리 · " + plannedDistance,
                "예상 소요시간 · " + plannedDuration,
                petNamesLabel,
                distanceLabel(record.getDistanceM()),
                clockDurationLabel(record.getDurationSeconds()),
                record.getEndedAt() == null
                        ? "산책 중"
                        : record.getEndedAt().format(DATE_FORMAT) + " 완료"
        );
    }

    private Optional<Route> findRoute(Long courseId) {
        if (courseId == null || courseId > Integer.MAX_VALUE) {
            return Optional.empty();
        }

        return routeRepository.findById(courseId.intValue());
    }

    private String distanceLabel(Number distanceM) {
        if (distanceM == null) {
            return "0.0km";
        }

        double km = distanceM.doubleValue() / 1000.0;
        return String.format(Locale.KOREA, "%.1fkm", km);
    }

    private String minuteDurationLabel(Integer durationSeconds) {
        if (durationSeconds == null) {
            return "진행 중";
        }

        int minutes = Math.max(1, Math.round(durationSeconds / 60f));
        return "약 " + minutes + "분";
    }

    private String clockDurationLabel(Integer durationSeconds) {
        if (durationSeconds == null) {
            return "00:00";
        }

        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;
        return String.format(Locale.KOREA, "%02d:%02d", minutes, seconds);
    }

    public List<WalkPointView> getWalkPoints(Long walkRecordId, Long userId) {
        if (walkRecordRepository.findByWalkRecordIdAndUserId(walkRecordId, userId).isEmpty()) {
            return List.of();
        }

        return walkRecordPointRepository.findByWalkRecordIdOrderBySequenceNoAsc(walkRecordId).stream()
                .map(point -> new WalkPointView(point.getLatitude(), point.getLongitude()))
                .toList();
    }

    public List<WalkPointView> getPlannedPoints(Long walkRecordId, Long userId) {
        Optional<WalkRecord> record = walkRecordRepository.findByWalkRecordIdAndUserId(walkRecordId, userId);
        if (record.isEmpty()) {
            return List.of();
        }

        List<WalkPointView> points = plannedPointRepository.findByWalkRecordIdOrderBySequenceNoAsc(walkRecordId)
                .stream()
                .map(point -> new WalkPointView(point.getLatitude(), point.getLongitude()))
                .toList();
        if (!points.isEmpty() || record.get().getCourseId() == null
                || record.get().getCourseId() > Integer.MAX_VALUE) {
            return points;
        }
        return coursePointRepository.findByCourse_CourseIdOrderBySequenceNoAsc(
                        record.get().getCourseId().intValue()).stream()
                .map(point -> new WalkPointView(BigDecimal.valueOf(point.getLatitude()),
                        BigDecimal.valueOf(point.getLongitude())))
                .toList();
    }

    // 활동 상세에서 함께 산책한 반려견 프로필 조회 (담당: 최주영)
    public List<PetCardView> getWalkPets(Long walkRecordId, Long userId) {
        List<Long> petIds = walkRecordPetRepository.findPetIdsByWalkRecordId(walkRecordId, userId);
        return petService.getMyPetsByIds(userId, petIds);
    }

    @Transactional
    public void deleteMyCompletedWalkRecord(Long walkRecordId, Long userId) {
        WalkRecord record = walkRecordRepository.findByWalkRecordIdAndUserId(walkRecordId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "산책 기록을 찾을 수 없습니다."
                ));

        if (!WalkRecord.STATUS_COMPLETED.equals(record.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "완료된 산책 기록만 삭제할 수 있습니다."
            );
        }

        plannedPointRepository.deleteByWalkRecordId(walkRecordId);
        walkRecordPointRepository.deleteByWalkRecordId(walkRecordId);
        walkRecordRepository.delete(record);
    }

    public boolean isMyPet(Long userId, Long petId) {
        return petRepository.findByPetIdAndUser_UserId(petId, userId).isPresent();
    }
}
