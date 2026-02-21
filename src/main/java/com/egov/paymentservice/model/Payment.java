package com.egov.paymentservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "payments")
public class Payment {
    @Id
    private String paymentNumber;
    @Indexed(unique = true)
    private String bookingNumber;
    private String busNumber;//??
    private int numSeats;//??
    private LocalDateTime paymentDate;
    private double amount;
}