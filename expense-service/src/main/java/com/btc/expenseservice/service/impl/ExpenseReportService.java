package com.btc.expenseservice.service.impl;

import com.btc.expenseservice.dto.ExpenseSummaryDto;
import com.btc.expenseservice.dto.TripSpendDto;
import com.btc.expenseservice.entity.Expense;
import com.btc.expenseservice.exception.InvalidRequestException;
import com.btc.expenseservice.repository.ExpenseRepository;
import com.btc.expenseservice.security.CurrentUser;
import com.btc.expenseservice.service.ExpenseService;
import com.btc.expenseservice.web.CsvWriter;
import com.btc.expenseservice.web.DateRange;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Company-wide expense reporting, for administrators only. All aggregation happens in the database. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseReportService {

    static final int MAX_EXPORT_ROWS = 50_000;
    private static final List<String> CSV_HEADER =
            List.of("expense_id", "expense_date", "owner_id", "trip_id", "claim_id", "category", "title", "amount");

    private final ExpenseRepository expenseRepository;
    private final ExpenseService expenseService;

    public ExpenseSummaryDto summary(CurrentUser actor, Long ownerId, LocalDate from, LocalDate to) {
        actor.requireAdmin();
        return expenseService.getSummary(actor, ownerId, null, from, to);
    }

    public Page<TripSpendDto> byTrip(CurrentUser actor, LocalDate from, LocalDate to, Pageable pageable) {
        actor.requireAdmin();
        DateRange range = DateRange.resolve(from, to);
        return expenseRepository.totalsByTrip(range.from(), range.to(), pageable);
    }

    /** One row per expense in the (bounded) range; refused when it would exceed MAX_EXPORT_ROWS. */
    public String exportCsv(CurrentUser actor, LocalDate from, LocalDate to) {
        actor.requireAdmin();
        DateRange range = DateRange.resolve(from, to);
        long rows = expenseRepository.countByExpenseDateBetween(range.from(), range.to());
        if (rows > MAX_EXPORT_ROWS) {
            throw new InvalidRequestException("The export would contain " + rows + " rows; narrow the date range to at most "
                    + MAX_EXPORT_ROWS + " rows");
        }
        StringBuilder csv = new StringBuilder(CsvWriter.row(CSV_HEADER));
        for (Expense expense : expenseRepository.findAllByExpenseDateBetweenOrderByExpenseDateAscIdAsc(range.from(), range.to())) {
            csv.append(CsvWriter.row(Arrays.asList(expense.getId(), expense.getExpenseDate(), expense.getOwnerId(),
                    expense.getTripId(), expense.getClaimId(), expense.getCategory(), expense.getTitle(),
                    expense.getAmount().toPlainString())));
        }
        return csv.toString();
    }
}
