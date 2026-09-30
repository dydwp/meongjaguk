package com.meongjaguk.app.support;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통합 테스트 공통 설정
 * - 전체 스프링 + H2 메모리 DB (application-test.properties)
 * - 테스트마다 트랜잭션을 롤백해서 서로 데이터가 섞이지 않음
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestSupport {

    @Autowired
    protected TestDataFactory data;

    @Autowired
    protected EntityManager em;

    /** 변경 내용을 DB에 반영하고 1차 캐시를 비움 (JDBC 조회 / 다시 읽기 전에 사용) */
    protected void flushAndClear() {
        em.flush();
        em.clear();
    }
}
