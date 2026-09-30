package com.meongjaguk.app.service;

import com.meongjaguk.app.entity.BoardStatus;
import com.meongjaguk.app.repository.BoardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 모임 시간이 지난 모집글 자동 마감 (1분마다)
 * - 게시판·상세·마이페이지 등 DB 상태를 읽는 모든 화면이 같은 상태를 보도록
 *   모임 일시가 지난 RECRUITING 글을 DB에서 CLOSED로 변경
 * - 서버가 켜질 때 바로 한 번 실행되고, 이후 1분 간격으로 실행
 * - 스케줄링 설정(@EnableScheduling)을 이 클래스에 두어 공용 파일은 수정하지 않음
 */
@Configuration
@EnableScheduling
public class BoardCloseScheduler {

    private static final Logger log = LoggerFactory.getLogger(BoardCloseScheduler.class);

    private final BoardRepository boardRepository;

    public BoardCloseScheduler(BoardRepository boardRepository) {
        this.boardRepository = boardRepository;
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void closeExpiredBoards() {
        LocalDateTime now = LocalDateTime.now();
        int closed = boardRepository.closeExpired(
                BoardStatus.RECRUITING, BoardStatus.CLOSED,
                now.toLocalDate(), now.toLocalTime(), now);
        if (closed > 0) {
            log.info("모임 시간이 지난 모집글 {}개를 모집 마감으로 변경했어요.", closed);
        }
    }
}
