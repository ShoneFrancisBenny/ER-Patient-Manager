package com.example.er;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootApplication
@RestController
@RequestMapping("/api")
public class ErApplication {
    private final AtomicLong ids = new AtomicLong(1);
    private long arrivalOrder = 0;

    public static class Patient {
        public long id;
        public String name;
        public int severity;
        public long arrivalOrder;

        public Patient(long id, String name, int severity, long arrivalOrder) {
            this.id = id;
            this.name = name;
            this.severity = severity;
            this.arrivalOrder = arrivalOrder;
        }
    }

    // Severity 5 is highest. Equal severity is handled FIFO by arrivalOrder.
    private final Comparator<Patient> patientComparator =
            Comparator.comparingInt((Patient p) -> p.severity)
                    .reversed()
                    .thenComparingLong(p -> p.arrivalOrder);

    private final PriorityQueue<Patient> waiting =
            new PriorityQueue<>(patientComparator);

    // The most recently treated patient is at the top.
    private final Deque<Patient> history = new ArrayDeque<>();

    public static void main(String[] args) {
        SpringApplication.run(ErApplication.class, args);
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        List<Patient> waitingList = new ArrayList<>(waiting);
        waitingList.sort(patientComparator);

        return Map.of(
                "waiting", waitingList,
                "history", new ArrayList<>(history),
                "waitingCount", waiting.size(),
                "treatedCount", history.size()
        );
    }

    @PostMapping("/patient")
    public Map<String, Object> addPatient(@RequestBody Map<String, Object> data) {
        Object rawName = data.get("name");
        String name = rawName == null ? "" : rawName.toString().trim();

        if (name.isEmpty() || name.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Name must contain 1–100 characters.");
        }

        int severity;
        try {
            severity = Integer.parseInt(String.valueOf(data.get("severity")));
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Severity must be a number from 1 to 5.");
        }

        if (severity < 1 || severity > 5) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Severity must be between 1 and 5.");
        }

        Patient patient = new Patient(
                ids.getAndIncrement(), name, severity, arrivalOrder++);
        waiting.offer(patient);

        return Map.of("message", "Patient admitted successfully.", "patient", patient);
    }

    @PostMapping("/treat")
    public Map<String, Object> treatNext() {
        Patient patient = waiting.poll();

        if (patient == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "No patients are waiting.");
        }

        history.push(patient);
        return Map.of("message", "Patient treated successfully.", "patient", patient);
    }

    @PostMapping("/undo")
    public Map<String, Object> undoTreatment() {
        if (history.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "No treatment available to undo.");
        }

        Patient patient = history.pop();
        waiting.offer(patient);
        return Map.of(
                "message", "Last treatment undone. Patient returned to waiting list.",
                "patient", patient
        );
    }
}
