package com.egov.paymentservice.service;


import com.egov.paymentservice.exception.PaymentNotFoundException;
import com.egov.paymentservice.model.Payment;
import com.egov.paymentservice.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;


    private static final String CIRCUIT_BREAKER = "paymentService";

    public Payment getPaymentById(String id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + id));
    }

    // Listen for booking.created events
    @KafkaListener(topics = "booking.created", groupId = "payment-group")
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "paymentFallback")
    public void processPayment(Map<String, Object> eventMap) {
        String bookingNumber = (String) eventMap.get("bookingNumber");
        logger.info("Processing payment for booking {}", bookingNumber);

        try {
            if (paymentRepository.existsByBookingNumber(bookingNumber)) {
                logger.warn("Payment already processed for booking {}", bookingNumber);
                return;
            }

            Payment payment = objectMapper.convertValue(eventMap, Payment.class);
            payment.setPaymentNumber(UUID.randomUUID().toString());
            payment.setPaymentDate(LocalDateTime.now());

            Object seatsObj = eventMap.get("numberOfSeats");
            int numSeats = seatsObj instanceof Number ? ((Number) seatsObj).intValue() : 1;
            payment.setNumSeats(numSeats);

            double pricePerSeat = eventMap.get("pricePerSeat") instanceof Number
                    ? ((Number) eventMap.get("pricePerSeat")).doubleValue() : 0.0;

            // Mock payment processing
            boolean paymentSuccess = mockPaymentGateway(eventMap);

            if (paymentSuccess) {
                if (payment.getAmount() <= 0) {
                    payment.setAmount(payment.getNumSeats() * pricePerSeat);
                }
                paymentRepository.save(payment);

                Map<String, Object> paymentEventMap = objectMapper.convertValue(payment, Map.class);
                kafkaTemplate.send("payment.completed", paymentEventMap);

                logger.info("Payment processed successfully for booking {} with amount {}", bookingNumber, payment.getAmount());

            } else {
                Map<String, Object> failedEventMap = Map.of(
                        "bookingNumber", bookingNumber,
                        "reason", "Payment processing failed"
                );
                kafkaTemplate.send("booking.failed", failedEventMap);
                logger.warn("Payment failed for booking {}", bookingNumber);
            }

        } catch (Exception ex) {
            logger.error("Error processing payment for booking {}: {}", bookingNumber, ex.getMessage(), ex);
            throw ex; // Circuit breaker will handle and call fallback
        }
    }

    private boolean mockPaymentGateway(Map<String, Object> event) {
        // Replace with real payment gateway logic
        return true;
    }


    // Circuit breaker fallback
    public void paymentFallback(Map<String, Object> eventMap, Throwable t) {
        String bookingNumber = eventMap != null ? (String) eventMap.get("bookingNumber") : "UNKNOWN";
        logger.error("Payment service fallback triggered for booking {}: {}", bookingNumber, t.getMessage(), t);

        Map<String, Object> failedEventMap = Map.of(
                "bookingNumber", bookingNumber,
                "reason", "Payment service unavailable: " + t.getMessage()
        );

        kafkaTemplate.send("booking.failed", failedEventMap);
        logger.info("Published booking.failed event for booking {}", bookingNumber);
    }


    // Handle refund for cancellation
    @KafkaListener(topics = "booking.cancellation.requested", groupId = "payment-group")
    public void handleRefundRequest(Map<String, Object> eventMap) {
        String bookingNumber = eventMap != null ? (String) eventMap.get("bookingNumber") : null;

        if (bookingNumber == null || bookingNumber.isBlank()) {
            logger.warn("No bookingNumber in cancellation request, skipping refund.");
            return;
        }

        try {
            Optional<Payment> paymentOpt = paymentRepository.findByBookingNumber(bookingNumber);

            if (paymentOpt.isEmpty()) {
                logger.warn("No payment found for booking {}, skipping refund.", bookingNumber);
                return;
            }

            Payment payment = paymentOpt.get();
            payment.setAmount(0); // mock refund
            payment.setPaymentDate(LocalDateTime.now());
            paymentRepository.save(payment);

            Map<String, Object> paymentEventMap = objectMapper.convertValue(payment, Map.class);
            kafkaTemplate.send("payment.refunded", paymentEventMap);

            logger.info("Refund processed for booking {}", bookingNumber);

        } catch (Exception ex) {
            logger.error("Error processing refund for booking {}: {}", bookingNumber, ex.getMessage(), ex);
        }
    }

    /*@KafkaListener(topics = "booking.updated", groupId = "payment-group")
    public void handleBookingUpdated(Map<String, Object> event) {

        String bookingNumber = (String) event.get("bookingNumber");
        int newSeats = (Integer) event.get("numberOfSeats");
        String busNumber = (String) event.get("busNumber");

        logger.info("Processing payment update for booking {}", bookingNumber);

        try {
            Payment payment = paymentRepository.findByBookingNumber(bookingNumber)
                    .orElseThrow(() -> new PaymentNotFoundException("Payment not found for booking " + bookingNumber));

            // Example: assume pricePerSeat from event or DB
            double pricePerSeat = event.get("pricePerSeat") != null
                    ? ((Number) event.get("pricePerSeat")).doubleValue()
                    : payment.getAmount() / payment.getNumSeats();

            payment.setNumSeats(newSeats);
            payment.setAmount(newSeats * pricePerSeat);
            paymentRepository.save(payment);

            logger.info("Payment updated for booking {}: new amount {}", bookingNumber, payment.getAmount());
            Map<String, Object> paymentEventMap = objectMapper.convertValue(payment, Map.class);
            kafkaTemplate.send("payment.completed", paymentEventMap);

            logger.info("Payment processed successfully for booking {} with amount {}", bookingNumber, payment.getAmount());

        } catch (Exception ex) {
            logger.error("Error updating payment for booking {}: {}", bookingNumber, ex.getMessage(), ex);
            // You may also publish booking.failed event if needed
            kafkaTemplate.send("booking.failed", Map.of(
                    "bookingNumber", bookingNumber,
                    "reason", "Payment update failed: " + ex.getMessage()
            ));
        }
    }*/
}


