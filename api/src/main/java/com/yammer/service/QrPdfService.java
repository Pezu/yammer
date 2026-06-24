package com.yammer.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Link;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.yammer.entity.EventEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.repository.EventRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.security.AccessGuard;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Builds a printable PDF of QR codes — one per order point of an event. Each QR encodes the
 * customer landing URL {@code {customerBaseUrl}/customer/order-point/{opId}} (the event is embedded
 * in the order point). Uses ZXing for the QR images and iText for the PDF layout.
 */
@Service
@RequiredArgsConstructor
public class QrPdfService {

    private final EventRepository eventRepository;
    private final OrderPointRepository orderPointRepository;
    private final AccessGuard accessGuard;

    /** Public base URL of the customer web app the QR codes point at. */
    @Value("${app.customer-base-url:http://localhost:4200}")
    private String customerBaseUrl;

    private String baseUrl;

    @PostConstruct
    void init() {
        // Trim a trailing slash so URL building is clean.
        this.baseUrl = customerBaseUrl.endsWith("/")
                ? customerBaseUrl.substring(0, customerBaseUrl.length() - 1)
                : customerBaseUrl;
    }

    @Transactional(readOnly = true)
    public byte[] generateOrderPointsQrPdf(UUID eventId) {
        EventEntity event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found: " + eventId));
        accessGuard.requireAccessibleLocation(event.getLocationId()); // tenant gate (404 cross-tenant)

        List<OrderPointEntity> orderPoints = orderPointRepository.findByEventIdOrderByName(eventId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
        Document document = new Document(pdf);
        try {
            document.add(new Paragraph(event.getName())
                    .setFontSize(20).setBold().setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph(" "));

            Table table = new Table(UnitValue.createPercentArray(new float[]{33.33f, 33.33f, 33.33f}));
            table.setWidth(UnitValue.createPercentValue(100));

            for (OrderPointEntity op : orderPoints) {
                String url = baseUrl + "/customer/order-point/" + op.getId();
                Image image = new Image(ImageDataFactory.create(qrPng(url, 300, 300)))
                        .setWidth(150)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER);
                Cell cell = new Cell();
                cell.add(new Paragraph(op.getName())
                        .setFontSize(10).setBold().setTextAlignment(TextAlignment.CENTER));
                cell.add(image);
                // TODO: testing aid — clickable link under each QR; remove for production.
                Link link = new Link(url, PdfAction.createURI(url));
                link.setFontColor(ColorConstants.BLUE);
                cell.add(new Paragraph(link).setFontSize(6).setTextAlignment(TextAlignment.CENTER));
                cell.setTextAlignment(TextAlignment.CENTER);
                table.addCell(cell);
            }
            // pad the last row so the grid stays aligned
            int remainder = orderPoints.size() % 3;
            if (remainder != 0) {
                for (int i = 0; i < 3 - remainder; i++) {
                    table.addCell(new Cell());
                }
            }
            document.add(table);
        } catch (WriterException | java.io.IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate QR PDF: " + e.getMessage(), e);
        } finally {
            document.close();
        }
        return baos.toByteArray();
    }

    private byte[] qrPng(String text, int width, int height) throws WriterException, java.io.IOException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
        return baos.toByteArray();
    }
}
