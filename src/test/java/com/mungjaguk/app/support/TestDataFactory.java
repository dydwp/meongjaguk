package com.mungjaguk.app.support;

import com.mungjaguk.app.entity.Board;
import com.mungjaguk.app.entity.CompanionRequest;
import com.mungjaguk.app.entity.CoursePoint;
import com.mungjaguk.app.entity.Pet;
import com.mungjaguk.app.entity.Route;
import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.entity.WalkRecord;
import com.mungjaguk.app.repository.BoardRepository;
import com.mungjaguk.app.repository.CompanionRequestRepository;
import com.mungjaguk.app.repository.CoursePointRepository;
import com.mungjaguk.app.repository.PetRepository;
import com.mungjaguk.app.repository.RouteRepository;
import com.mungjaguk.app.repository.UserRepository;
import com.mungjaguk.app.repository.WalkRecordRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 통합·시나리오 테스트용 데이터 저장 도우미 (H2 테스트 DB에 실제로 저장)
 */
@Component
public class TestDataFactory {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final UserRepository userRepository;
    private final RouteRepository routeRepository;
    private final CoursePointRepository coursePointRepository;
    private final BoardRepository boardRepository;
    private final CompanionRequestRepository companionRequestRepository;
    private final PetRepository petRepository;
    private final WalkRecordRepository walkRecordRepository;
    private final JdbcTemplate jdbcTemplate;

    public TestDataFactory(UserRepository userRepository, RouteRepository routeRepository,
                           CoursePointRepository coursePointRepository, BoardRepository boardRepository,
                           CompanionRequestRepository companionRequestRepository, PetRepository petRepository,
                           WalkRecordRepository walkRecordRepository, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.routeRepository = routeRepository;
        this.coursePointRepository = coursePointRepository;
        this.boardRepository = boardRepository;
        this.companionRequestRepository = companionRequestRepository;
        this.petRepository = petRepository;
        this.walkRecordRepository = walkRecordRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public User user(String nickname) {
        return userRepository.save(User.create("kakao", "kakao-" + SEQ.incrementAndGet(), nickname, null, null));
    }

    public Route route(String name) {
        Route route = new Route();
        route.setCourseName(name);
        route.setDescription(name + " 설명");
        route.setDistanceM(2600L);
        route.setEstimatedMinutes(40);
        route.setStartLatitude(37.544);
        route.setStartLongitude(127.043);
        route.setCreatedAt(LocalDateTime.now());
        return routeRepository.save(route);
    }

    public void coursePoints(Route route, double... latLng) {
        for (int i = 0; i < latLng.length / 2; i++) {
            coursePointRepository.save(CoursePoint.create(route, i + 1, latLng[i * 2], latLng[i * 2 + 1]));
        }
    }

    /** 모집 글 (저장 시 상태는 RECRUITING) */
    public Board board(User host, Route course, String title, LocalDateTime meetingAt, int maxParticipants) {
        return boardRepository.save(Board.create(host, course, title, "같이 걸어요",
                meetingAt.toLocalDate(), meetingAt.toLocalTime(), maxParticipants, false, null));
    }

    public Board board(User host, String title, LocalDateTime meetingAt, int maxParticipants) {
        return board(host, route(title + " 코스"), title, meetingAt, maxParticipants);
    }

    public CompanionRequest pendingRequest(Board board, User applicant) {
        return companionRequestRepository.save(CompanionRequest.create(board, applicant));
    }

    public CompanionRequest acceptedRequest(Board board, User applicant) {
        CompanionRequest request = CompanionRequest.create(board, applicant);
        request.accept();
        return companionRequestRepository.save(request);
    }

    public CompanionRequest rejectedRequest(Board board, User applicant) {
        CompanionRequest request = CompanionRequest.create(board, applicant);
        request.reject();
        return companionRequestRepository.save(request);
    }

    public Pet pet(User owner, String name) {
        Pet pet = new Pet();
        pet.setUser(owner);
        pet.setName(name);
        pet.setBreed("말티즈");
        pet.setSize("SMALL");
        pet.setActivityLevel("MEDIUM");
        pet.setBirthDate(LocalDate.now().minusYears(3));
        return petRepository.save(pet);
    }

    public WalkRecord walk(User user, LocalDateTime startedAt, int durationSeconds, int distanceM) {
        return walkRecordRepository.save(WalkRecord.completed(user.getUserId(), null,
                startedAt, startedAt.plusSeconds(durationSeconds), durationSeconds, distanceM));
    }

    /** 산책 기록 ↔ 반려견 연결 (엔티티가 없는 walk_record_pets 테이블) */
    public void linkPet(WalkRecord record, Pet pet) {
        jdbcTemplate.update("INSERT INTO walk_record_pets (walk_record_id, pet_id) VALUES (?, ?)",
                record.getWalkRecordId(), pet.getPetId());
    }

    /** 커밋되는 시나리오 테스트 사이에 데이터를 비움 */
    public void cleanAll() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'", String.class);
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            for (String table : tables) {
                jdbcTemplate.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY");
            }
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
}
