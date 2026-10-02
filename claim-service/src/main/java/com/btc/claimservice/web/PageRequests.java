package com.btc.claimservice.web;

import com.btc.claimservice.exception.InvalidRequestException;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

/**
 * Paging for list endpoints. Lists stay JSON arrays (unchanged contract); the total row count is returned
 * in the X-Total-Count header. Sorting is restricted to known fields and always ends with id, so pages
 * are stable even when the sort field has duplicates.
 */
public final class PageRequests {

    public static final String TOTAL_COUNT_HEADER = "X-Total-Count";
    public static final String DEFAULT_PAGE = "0";
    public static final String DEFAULT_SIZE = "100";
    public static final String DEFAULT_SORT = "id,asc";
    public static final int MAX_SIZE = 500;

    private PageRequests() {
    }

    public static Pageable of(int page, int size, String sort, Set<String> sortableFields) {
        if (page < 0) {
            throw new InvalidRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_SIZE);
        }
        String[] parts = sort.split(",", -1);
        String field = parts[0].trim();
        if (parts.length > 2 || !sortableFields.contains(field)) {
            throw new InvalidRequestException("sort must be one of " + sortableFields + ", optionally followed by ,asc or ,desc");
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            direction = switch (parts[1].trim().toLowerCase(Locale.ROOT)) {
                case "asc" -> Sort.Direction.ASC;
                case "desc" -> Sort.Direction.DESC;
                default -> throw new InvalidRequestException("sort direction must be asc or desc");
            };
        }
        Sort order = Sort.by(direction, field);
        if (!"id".equals(field)) {
            order = order.and(Sort.by(Sort.Direction.ASC, "id"));
        }
        return PageRequest.of(page, size, order);
    }

    public static <T> ResponseEntity<java.util.List<T>> toResponse(Page<T> page) {
        return ResponseEntity.ok()
                .header(TOTAL_COUNT_HEADER, String.valueOf(page.getTotalElements()))
                .body(page.getContent());
    }
}
