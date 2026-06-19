package com.yammer.service;

import com.yammer.dto.CustomerImportResult;
import com.yammer.dto.CustomerRequest;
import com.yammer.dto.CustomerResponse;
import com.yammer.dto.PagedResponse;
import com.yammer.entity.CustomerEntity;
import com.yammer.repository.CustomerRepository;
import com.yammer.util.Strings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public List<CustomerResponse> list() {
        return customerRepository.findAll(Sort.by("lastName", "firstName")).stream()
                .map(CustomerResponse::from)
                .toList();
    }

    /** One page of customers (ordered by last then first name) plus the total count. */
    public PagedResponse<CustomerResponse> listPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastName", "firstName"));
        Page<CustomerEntity> result = customerRepository.findAll(pageable);
        List<CustomerResponse> content = result.getContent().stream().map(CustomerResponse::from).toList();
        return new PagedResponse<>(content, result.getTotalElements(), page, size);
    }

    public CustomerResponse create(CustomerRequest request) {
        CustomerEntity entity = new CustomerEntity();
        apply(entity, request);
        return CustomerResponse.from(customerRepository.save(entity));
    }

    public CustomerResponse update(UUID id, CustomerRequest request) {
        CustomerEntity entity = customerRepository.findById(id).orElseThrow(() -> notFound(id));
        apply(entity, request);
        return CustomerResponse.from(customerRepository.save(entity));
    }

    public void delete(UUID id) {
        if (!customerRepository.existsById(id)) {
            throw notFound(id);
        }
        customerRepository.deleteById(id);
    }

    /**
     * Bulk-imports customers from an uploaded .xlsx. The first row is a header that
     * must contain (case-insensitively) the columns {@code firstName} and {@code lastName};
     * {@code phone} and {@code email} are optional. Each data row is validated independently:
     * fully blank rows are ignored, rows missing a required name (or with an invalid email)
     * are skipped and reported, and the valid rows are inserted in one batch.
     */
    private static final DataFormatter CELL_FORMATTER = new DataFormatter();

    public CustomerImportResult importFromXlsx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file was uploaded.");
        }

        List<String> errors = new ArrayList<>();
        List<CustomerEntity> toSave = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The spreadsheet has no sheets.");
            }

            Iterator<Row> rows = sheet.rowIterator();
            if (!rows.hasNext()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The spreadsheet is empty.");
            }

            Map<String, Integer> columns = mapColumns(rows.next());
            requireColumn(columns, "firstname");
            requireColumn(columns, "lastname");

            while (rows.hasNext()) {
                Row row = rows.next();
                int rowNumber = row.getRowNum() + 1; // 1-based for human-readable messages

                String firstName = cell(row, columns.get("firstname"));
                String lastName = cell(row, columns.get("lastname"));
                String phone = cell(row, columns.get("phone"));
                String email = cell(row, columns.get("email"));

                if (firstName.isEmpty() && lastName.isEmpty() && phone.isEmpty() && email.isEmpty()) {
                    continue; // blank padding row — ignore silently
                }
                if (firstName.isEmpty() || lastName.isEmpty()) {
                    errors.add("Row " + rowNumber + ": first and last name are required.");
                    continue;
                }
                if (!email.isEmpty() && !email.contains("@")) {
                    errors.add("Row " + rowNumber + ": invalid email \"" + email + "\".");
                    continue;
                }

                CustomerEntity entity = new CustomerEntity();
                entity.setFirstName(firstName);
                entity.setLastName(lastName);
                entity.setPhone(Strings.trimToNull(phone));
                entity.setEmail(Strings.trimToNull(email));
                toSave.add(entity);
            }
        } catch (ResponseStatusException e) {
            throw e; // our own validation failures (missing column, empty sheet) — keep the message
        } catch (IOException | RuntimeException e) {
            // POI throws unchecked exceptions (e.g. NotOfficeXmlFileException) when the upload
            // isn't a real .xlsx — surface all parse failures as a 400, not a 500.
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Could not read the file — is it a valid .xlsx?");
        }

        customerRepository.saveAll(toSave);
        return new CustomerImportResult(toSave.size(), errors.size(), errors);
    }

    /** Maps each non-blank header label (lower-cased, trimmed) to its column index. */
    private Map<String, Integer> mapColumns(Row header) {
        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : header) {
            String label = CELL_FORMATTER.formatCellValue(cell).trim().toLowerCase();
            if (!label.isEmpty()) {
                columns.putIfAbsent(label, cell.getColumnIndex());
            }
        }
        return columns;
    }

    private void requireColumn(Map<String, Integer> columns, String key) {
        if (!columns.containsKey(key)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Missing required column \"" + key + "\". Expected columns: firstName, lastName, phone, email.");
        }
    }

    /** Reads the cell at the given column index as trimmed text ("" when the column/cell is absent). */
    private String cell(Row row, Integer columnIndex) {
        if (columnIndex == null) {
            return "";
        }
        Cell cell = row.getCell(columnIndex);
        return cell == null ? "" : CELL_FORMATTER.formatCellValue(cell).trim();
    }

    private void apply(CustomerEntity entity, CustomerRequest request) {
        entity.setFirstName(request.firstName().trim());
        entity.setLastName(request.lastName().trim());
        entity.setPhone(Strings.trimToNull(request.phone()));
        entity.setEmail(Strings.trimToNull(request.email()));
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + id);
    }
}
