package com.meongjaguk.app.service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
import com.meongjaguk.app.entity.ApplicationStatus;
import com.meongjaguk.app.entity.Board;
import com.meongjaguk.app.entity.Route;
import com.meongjaguk.app.entity.WalkRecord;
import com.meongjaguk.app.repository.BoardRepository;
import com.meongjaguk.app.repository.CompanionRequestRepository;
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
    private final CompanionRequestRepository companionRequestRepository; // 동행 산책 참가자 열람 확인 (추가: 김환중)
    private final BoardRepository boardRepository; // 동행 산책 모집글 제목 (추가: 김환중)

    public WalkRecordService(WalkRecordRepository walkRecordRepository,
                            RouteRepository routeRepository,
                            WalkRecordPointRepository walkRecordPointRepository,
                            WalkRecordPlannedPointRepository plannedPointRepository,
                            CoursePointRepository coursePointRepository,
                            WalkRecordPetRepository walkRecordPetRepository,
                            PetRepository petRepository,
                            PetService petService,
                            CompanionRequestRepository companionRequestRepository,
                            BoardRepository boardRepository) {
        this.walkRecordRepository = walkRecordRepository;
        this.routeRepository = routeRepository;
        this.walkRecordPointRepository = walkRecordPointRepository;
        this.plannedPointRepository = plannedPointRepository;
        this.coursePointRepository = coursePointRepository;
        this.walkRecordPetRepository = walkRecordPetRepository;
        this.petRepository = petRepository;
        this.petService = petService;
        this.companionRequestRepository = companionRequestRepository;
        this.boardRepository = boardRepository;
    }

    public List<WalkHistoryItemView> getMyWalkHistory(Long userId) {
        return getMyWalkHistory(userId, null);
    }

    public List<WalkHistoryItemView> getMyWalkHistory(Long userId, Long petId) {
        // 내 기록(개인 + 내가 개최한 동행) + 내가 수락된 동행 모집의 기록, 시작 시각 최신순 (추가: 김환중)
        List<WalkRecord> records = mergeByStartedAtDesc(
                walkRecordRepository.findByUserIdOrderByStartedAtDesc(userId),
                findAcceptedMeetingRecords(userId));

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
        Map<Long, String> meetingTitles = findMeetingTitles(records); // 동행 산책 모집글 제목 (추가: 김환중)

        return records.stream()
                .map(record -> toHistoryItem(
                        record,
                        petNamesByWalkRecord.getOrDefault(record.getWalkRecordId(), List.of()),
                        meetingTitles.get(record.getMeetingId())
                ))
                .toList();
    }

    /** 내가 수락(ACCEPTED)된 동행 모집에 연결된 기록 (추가: 김환중) */
    private List<WalkRecord> findAcceptedMeetingRecords(Long userId) {
        List<Long> meetingIds = companionRequestRepository
                .findMeetingIdsByApplicantAndStatus(userId, ApplicationStatus.ACCEPTED);
        if (meetingIds == null || meetingIds.isEmpty()) {
            return List.of();
        }
        return walkRecordRepository.findByMeetingIdIn(meetingIds);
    }

    /** 두 목록을 합쳐 같은 기록은 한 번만, 시작 시각 최신순 (시각이 같으면 원래 순서) (추가: 김환중) */
    private static List<WalkRecord> mergeByStartedAtDesc(List<WalkRecord> mine, List<WalkRecord> joined) {
        Map<Long, WalkRecord> byId = new LinkedHashMap<>();
        mine.forEach(record -> byId.putIfAbsent(record.getWalkRecordId(), record));
        joined.forEach(record -> byId.putIfAbsent(record.getWalkRecordId(), record));

        List<WalkRecord> merged = new ArrayList<>(byId.values());
        merged.sort(Comparator.comparing(WalkRecord::getStartedAt).reversed());
        return merged;
    }

    /** 동행 산책 기록의 모집글 제목: 모집글 번호 → 제목 (추가: 김환중) */
    private Map<Long, String> findMeetingTitles(List<WalkRecord> records) {
        List<Long> meetingIds = records.stream()
                .map(WalkRecord::getMeetingId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> titles = new HashMap<>();
        if (!meetingIds.isEmpty()) {
            boardRepository.findAllById(meetingIds)
                    .forEach(board -> titles.put(board.getMeetingId(), board.getTitle()));
        }
        return titles;
    }

    public Optional<WalkDetailView> getDetail(Long walkRecordId, Long userId) {
        return findViewableRecord(walkRecordId, userId)
                .map(record -> toDetailView(record, userId));
    }

    /**
     * 볼 수 있는 산책 기록 (추가: 김환중)
     * - 내 기록이면 그대로
     * - 아니면 동행 산책 기록이고, 조회자가 그 모집에 수락(ACCEPTED)된 신청자일 때만
     *   (대기·거절 신청자, 무관한 사용자는 볼 수 없음)
     */
    private Optional<WalkRecord> findViewableRecord(Long walkRecordId, Long userId) {
        Optional<WalkRecord> mine = walkRecordRepository.findByWalkRecordIdAndUserId(walkRecordId, userId);
        if (mine.isPresent() || walkRecordId == null || userId == null) {
            return mine;
        }

        return walkRecordRepository.findById(walkRecordId)
                .filter(record -> record.getMeetingId() != null)
                .filter(record -> companionRequestRepository
                        .findByMeetingIdAndApplicant_UserId(record.getMeetingId(), userId)
                        .map(request -> request.getStatus() == ApplicationStatus.ACCEPTED)
                        .orElse(false));
    }

    private WalkHistoryItemView toHistoryItem(WalkRecord record, List<String> petNames, String meetingTitle) {
        Optional<Route> route = findRoute(record.getCourseId());

        return new WalkHistoryItemView(
                record.getWalkRecordId(),
                // 동행 산책이면 모집글 제목, 아니면 기존 규칙 (추가: 김환중)
                meetingTitle != null ? meetingTitle
                        : record.getPlannedTitle() != null ? record.getPlannedTitle()
                        : route.map(Route::getCourseName).orElse("자유 산책"),
                record.getMeetingId() != null ? "동행 산책" : "개인 산책", // (추가: 김환중)
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
        // 동행 산책이면 모집글 제목 (추가: 김환중)
        if (record.getMeetingId() != null) {
            title = boardRepository.findById(record.getMeetingId()).map(Board::getTitle).orElse(title);
        }
        String description = record.getPlannedTitle() != null ? record.getPlannedDescription()
                : route.map(Route::getDescription).orElse("");
        String plannedDistance = record.getPlannedDistanceM() != null
                ? distanceLabel(record.getPlannedDistanceM())
                : route.map(Route::getDistanceM).map(this::distanceLabel).orElse("-");
        String plannedDuration = record.getPlannedEstimatedMinutes() != null
                ? "약 " + record.getPlannedEstimatedMinutes() + "분"
                : route.map(Route::getEstimatedMinutes)
                    .map(minutes -> "약 " + minutes + "분").orElse("-");
        // 반려견은 조회자가 아니라 기록 주인 기준 (추가: 김환중)
        List<String> petNames = walkRecordPetRepository.findPetNamesByWalkRecordId(record.getWalkRecordId(), record.getUserId());
        String petNamesLabel = String.join(" · ", petNames);
        boolean meetingWalk = record.getMeetingId() != null; // 동행 산책 기록 (추가: 김환중)

        return new WalkDetailView(
                record.getWalkRecordId(),
                title,
                meetingWalk ? "동행 산책" : "개인 산책",
                record.getPlannedTitle() != null || route.isPresent(),
                // 기록 주인의 완료된 개인 산책만 삭제 가능 (추가: 김환중)
                record.getUserId().equals(userId) && !meetingWalk
                        && WalkRecord.STATUS_COMPLETED.equals(record.getStatus()),
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
        if (findViewableRecord(walkRecordId, userId).isEmpty()) {
            return List.of();
        }

        return walkRecordPointRepository.findByWalkRecordIdOrderBySequenceNoAsc(walkRecordId).stream()
                .map(point -> new WalkPointView(point.getLatitude(), point.getLongitude()))
                .toList();
    }

    public List<WalkPointView> getPlannedPoints(Long walkRecordId, Long userId) {
        Optional<WalkRecord> record = findViewableRecord(walkRecordId, userId);
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
        // 볼 수 있는 기록만, 반려견은 기록 주인 기준 (추가: 김환중)
        Optional<WalkRecord> record = findViewableRecord(walkRecordId, userId);
        if (record.isEmpty()) {
            return List.of();
        }
        Long ownerId = record.get().getUserId();
        List<Long> petIds = walkRecordPetRepository.findPetIdsByWalkRecordId(walkRecordId, ownerId);
        return petService.getMyPetsByIds(ownerId, petIds);
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
        // 동행 산책 기록은 참가자도 함께 보므로 삭제 불가 (추가: 김환중)
        if (record.getMeetingId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "동행 산책 기록은 삭제할 수 없습니다."
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
