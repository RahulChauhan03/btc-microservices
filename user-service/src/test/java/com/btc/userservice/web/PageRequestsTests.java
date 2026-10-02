package com.btc.userservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.btc.userservice.exception.InvalidRequestException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageRequestsTests {

    private static final Set<String> FIELDS = Set.of("id", "name");

    @Test
    void sortAlwaysEndsWithIdForStablePages() {
        Pageable pageable = PageRequests.of(2, 25, "name,desc", FIELDS);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(25);
        assertThat(pageable.getSort()).containsExactly(Sort.Order.desc("name"), Sort.Order.asc("id"));
        assertThat(PageRequests.of(0, 10, "id,desc", FIELDS).getSort()).containsExactly(Sort.Order.desc("id"));
    }

    @Test
    void invalidPagingAndSortingIsRejected() {
        assertThatThrownBy(() -> PageRequests.of(-1, 10, "id", FIELDS)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> PageRequests.of(0, 0, "id", FIELDS)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> PageRequests.of(0, PageRequests.MAX_SIZE + 1, "id", FIELDS))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> PageRequests.of(0, 10, "passwordHash", FIELDS))
                .as("only allow-listed fields").isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> PageRequests.of(0, 10, "name,sideways", FIELDS)).isInstanceOf(InvalidRequestException.class);
    }
}
