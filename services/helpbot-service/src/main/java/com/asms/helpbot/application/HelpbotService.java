package com.asms.helpbot.application;

import com.asms.helpbot.domain.FaqEntry;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HelpbotService {

    // Chain of Responsibility + FAQ matching — rule-based, no external LLM
    private final List<FaqEntry> faqDatabase = List.of(
            new FaqEntry(List.of("maintenance", "charges", "fees"),
                    "Monthly maintenance charges are INR 2500 + water charges. " +
                    "Contact RWA for adjustments. Pay via ASMS portal by 10th of each month."),
            new FaqEntry(List.of("visitor", "gate", "entry", "guest"),
                    "To allow a visitor: Security logs the request → you receive a notification → " +
                    "approve/reject within 5 minutes via ASMS app."),
            new FaqEntry(List.of("ticket", "complaint", "issue", "problem"),
                    "To raise a support ticket: Login → Support → New Ticket → " +
                    "select category and priority → submit. You will receive updates via notifications."),
            new FaqEntry(List.of("amenity", "pool", "gym", "clubhouse", "book", "booking"),
                    "Book amenities via ASMS portal: Amenities → Select → Choose slot → Confirm. " +
                    "Min booking: 1 hour. Cancellation: 2 hours before slot."),
            new FaqEntry(List.of("parking", "vehicle"),
                    "Parking slots are assigned during unit registration. " +
                    "Contact admin for additional or visitor parking allocation."),
            new FaqEntry(List.of("emergency", "fire", "help", "urgent"),
                    "EMERGENCY: Call Society Security: 1800-XXX-XXXX. " +
                    "Fire: 101. Police: 100. Ambulance: 108. Society emergency line: ext 999."),
            new FaqEntry(List.of("payment", "pay", "invoice"),
                    "Pay invoices via ASMS portal: Payments → Pending Invoices → Pay Now. " +
                    "Accepted: UPI, Credit/Debit Card, Net Banking."),
            new FaqEntry(List.of("password", "login", "account", "access"),
                    "For login issues: Use 'Forgot Password' on login screen. " +
                    "Contact admin@asms.io if issue persists."),
            new FaqEntry(List.of("owner", "tenant", "transfer", "rent"),
                    "Unit transfers or new tenant registration: Contact Admin with required documents. " +
                    "Processing time: 2-3 business days."),
            new FaqEntry(List.of("electricity", "power", "outage"),
                    "Electricity issues: Check society transformer status on ASMS dashboard. " +
                    "Report outage via Support ticket category: ELECTRICAL.")
    );

    private static final String DEFAULT_RESPONSE =
            "I couldn't find a specific answer for your query. " +
            "Please raise a Support Ticket for personalized assistance, " +
            "or contact admin@asms.io / 1800-XXX-XXXX.";

    public String answer(String query) {
        if (query == null || query.isBlank()) {
            return "Please enter a question to get help.";
        }
        return faqDatabase.stream()
                .filter(faq -> faq.matches(query))
                .map(FaqEntry::answer)
                .findFirst()
                .orElse(DEFAULT_RESPONSE);
    }
}
