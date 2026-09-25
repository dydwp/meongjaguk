package com.mungjaguk.app.service;

import com.mungjaguk.app.entity.WalkApplication;
import com.mungjaguk.app.repository.WalkApplicationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalkApplicationService {

    private final WalkApplicationRepository walkApplicationRepository;

    public WalkApplicationService(WalkApplicationRepository walkApplicationRepository) {
        this.walkApplicationRepository = walkApplicationRepository;
    }

    @Transactional(readOnly = true)
    public List<WalkApplication> getMyApplications(Long userId) {
        return walkApplicationRepository.findByApplicant_UserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<WalkApplication> getApplicationsForMyMeetings(List<Long> meetingIds) {
        if (meetingIds == null || meetingIds.isEmpty()) {
            return List.of();
        }

        return walkApplicationRepository.findByMeetingIdInOrderByCreatedAtDesc(meetingIds);
    }

    @Transactional
    public void accept(Long applicationId) {
        WalkApplication application = walkApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("동행 신청을 찾을 수 없습니다."));

        application.accept();
    }

    @Transactional
    public void reject(Long applicationId) {
        WalkApplication application = walkApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("동행 신청을 찾을 수 없습니다."));

        application.reject();
    }
}