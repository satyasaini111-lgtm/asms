package com.asms.helpbot.domain;

import java.util.List;

public record FaqEntry(List<String> keywords, String answer) {
    public boolean matches(String query) {
        String lowerQuery = query.toLowerCase();
        return keywords.stream().anyMatch(k -> lowerQuery.contains(k.toLowerCase()));
    }
}
