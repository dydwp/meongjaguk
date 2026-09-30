package com.mungjaguk.app.service;

import com.mungjaguk.app.dto.MeetingRequestView;
import com.mungjaguk.app.dto.MyCompanionRequestView;
import com.mungjaguk.app.dto.MySharedMeetingView;
import com.mungjaguk.app.repository.MyPageQueryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyPageService {

    private final MyPageQueryRepository myPageQueryRepository;

    public MyPageService(MyPageQueryRepository myPageQueryRepository) {
        this.myPageQueryRepository = myPageQueryRepository;
    }

    @Transactional(readOnly = true)
    public List<MySharedMeetingView> getMySharedMeetings(Long userId) {
        return myPageQueryRepository.findMySharedMeetings(userId);
    }

    @Transactional(readOnly = true)
    public List<MeetingRequestView> getMeetingRequests(Long userId) {
        return myPageQueryRepository.findRequestsForMyMeetings(userId);
    }

    @Transactional(readOnly = true)
    public List<MyCompanionRequestView> getMyCompanionRequests(Long userId) {
        return myPageQueryRepository.findMyCompanionRequests(userId);
    }
}