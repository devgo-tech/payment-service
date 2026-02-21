package com.egov.paymentservice.repository;

import com.egov.paymentservice.model.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends MongoRepository<Payment, String> {
    boolean existsByBookingNumber(String bookingNumber);

    Optional<Payment> findByBookingNumber(String bookingNumber);
}