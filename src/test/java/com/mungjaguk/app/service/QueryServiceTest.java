package com.mungjaguk.app.service;

import com.mungjaguk.app.dto.MeetingRequestView;
import com.mungjaguk.app.dto.MyCompanionRequestView;
import com.mungjaguk.app.dto.MySharedMeetingView;
import com.mungjaguk.app.entity.Route;
import com.mungjaguk.app.repository.MyPageQueryRepository;
import com.mungjaguk.app.repository.RouteRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.mungjaguk.app.support.Fixtures.route;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 조회만 전달하는 서비스: MyPageService, RouteService */
class QueryServiceTest {

    @Test
    void myPageServicePassesUserIdToQueries() {
        MyPageQueryRepository repository = mock(MyPageQueryRepository.class);
        MyPageService service = new MyPageService(repository);
        LocalDate date = LocalDate.of(2026, 10, 3);
        LocalTime time = LocalTime.of(9, 0);
        List<MySharedMeetingView> shared = List.of(
                new MySharedMeetingView(1L, "모집", "코스", date, time, 1, 4, "RECRUITING"));
        List<MeetingRequestView> received = List.of(
                new MeetingRequestView(2L, 1L, "모집", "민준", null, date, time, "PENDING"));
        List<MyCompanionRequestView> sent = List.of(
                new MyCompanionRequestView(3L, 5L, "다른 모집", "코스", "서연", date, time, "ACCEPTED"));
        when(repository.findMySharedMeetings(7L)).thenReturn(shared);
        when(repository.findRequestsForMyMeetings(7L)).thenReturn(received);
        when(repository.findMyCompanionRequests(7L)).thenReturn(sent);

        assertSame(shared, service.getMySharedMeetings(7L));
        assertSame(received, service.getMeetingRequests(7L));
        assertSame(sent, service.getMyCompanionRequests(7L));
    }

    @Test
    void routeServiceReturnsCourses() {
        RouteRepository repository = mock(RouteRepository.class);
        RouteService service = new RouteService(repository);
        Route course = route(3, "한강 코스");
        when(repository.findAll()).thenReturn(List.of(course));
        when(repository.findByCourseId(3L)).thenReturn(course);

        assertEquals(List.of(course), service.getCoursesList());
        assertSame(course, service.getCourseInfo(3L));
        assertNull(service.getCourseInfo(4L));
    }
}
