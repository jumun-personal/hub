package com.jumunhasyeo.common.Idempotency.db.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotentStatusTest {


    @Test
    @DisplayName("idempotentStatus 를 value로 찾을 수 있다.")
    public void idempotentStatus_를_value로_찾을_수_있다 (){
        IdempotentStatus success = IdempotentStatus.valueOf("SUCCESS");
        assertThat(success).isEqualTo(IdempotentStatus.SUCCESS);
    }

}
