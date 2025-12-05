package com.apps.deen_sa.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionController {

    @GetMapping("/transaction")
    public ResponseEntity<String> getTransactionStatus() {

        return ResponseEntity.ok("Transaction is successful");
    }
}
