package com.example.freelancera.model;

public class UserSettings {
    private double hourlyRate;
    private int paymentDueDays;
    private String companyName;
    private String address;
    private String nip;
    private String bankAccount;
    private String email;
    private String phone;
    private String preferredLanguage;
    private String projectTool;
    private String timeTrackingTool;
    private String invoiceTool;

    public UserSettings() {}

    // --- GETTERY I SETTERY ---
    public double getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(double hourlyRate) { this.hourlyRate = hourlyRate; }
    public int getPaymentDueDays() { return paymentDueDays; }
    public void setPaymentDueDays(int paymentDueDays) { this.paymentDueDays = paymentDueDays; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getNip() { return nip; }
    public void setNip(String nip) { this.nip = nip; }
    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public String getProjectTool() { return projectTool; }
    public void setProjectTool(String projectTool) { this.projectTool = projectTool; }
    public String getTimeTrackingTool() { return timeTrackingTool; }
    public void setTimeTrackingTool(String timeTrackingTool) { this.timeTrackingTool = timeTrackingTool; }
    public String getInvoiceTool() { return invoiceTool; }
    public void setInvoiceTool(String invoiceTool) { this.invoiceTool = invoiceTool; }
}