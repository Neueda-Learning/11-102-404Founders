package com.Project.PaymentProcessingSystem.model;

public class TransactionTicketRequest {

    private Long userId;
    private String title;
    private String description;
    private TicketType issueType;
    private TicketPriority priority;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TicketType getIssueType() {
        return issueType;
    }

    public void setIssueType(TicketType issueType) {
        this.issueType = issueType;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }
}

