package com.mungjaguk.app.support;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * H2에는 없는 MySQL 함수를 테스트 DB에 등록하기 위한 구현 (sql/test-schema.sql 에서 CREATE ALIAS)
 */
public final class H2Functions {

    private H2Functions() {}

    /** MySQL TIMESTAMP(date, time): 날짜 + 시간 → 일시 */
    public static Timestamp timestamp(Date date, Time time) {
        if (date == null || time == null) {
            return null;
        }
        return Timestamp.valueOf(date.toLocalDate().atTime(time.toLocalTime()));
    }
}
