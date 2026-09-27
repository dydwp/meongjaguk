package com.mungjaguk.app.service;

import com.mungjaguk.app.entity.ApplicationStatus;
import com.mungjaguk.app.entity.WalkApplication;
import com.mungjaguk.app.repository.MyPageQueryRepository;
import com.mungjaguk.app.repository.WalkApplicationRepository;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalkApplicationService {

    private final WalkApplicationRepository walkApplicationRepository;
    private final MyPageQueryRepository myPageQueryRepository;

    public WalkApplicationService(WalkApplicationRepository walkApplicationRepository,
                                  MyPageQueryRepository myPageQueryRepository) {
        this.walkApplicationRepository = walkApplicationRepository;
        this.myPageQueryRepository = myPageQueryRepository;
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
        WalkApplication application = findApplication(applicationId);
        application.accept();
    }

    @Transactional
    public void reject(Long applicationId) {
        WalkApplication application = findApplication(applicationId);
        application.reject();
    }

    @Transactional
    public void acceptForHost(Long applicationId, Long hostUserId) {
        WalkApplication application = findAuthorizedApplication(applicationId, hostUserId);

        if (application.getStatus() != ApplicationStatus.PENDING) {
            return;
        }

        if (!myPageQueryRepository.hasAvailableCapacity(applicationId)) {
            throw new IllegalStateException("모집 정원이 가득 찼습니다.");
        }

        application.accept();
    }

    @Transactional
    public void rejectForHost(Long applicationId, Long hostUserId) {
        WalkApplication application = findAuthorizedApplication(applicationId, hostUserId);
        if (application.getStatus() == ApplicationStatus.PENDING) {
            application.reject();
        }
    }

    private WalkApplication findApplication(Long applicationId) {
        return walkApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("동행 신청을 찾을 수 없습니다."));
    }

    private WalkApplication findAuthorizedApplication(Long applicationId, Long hostUserId) {
        if (!myPageQueryRepository.belongsToHost(applicationId, hostUserId)) {
            throw new AccessDeniedException("해당 동행 신청을 처리할 권한이 없습니다.");
        }

        return findApplication(applicationId);
    }
}