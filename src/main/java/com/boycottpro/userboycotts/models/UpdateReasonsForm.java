package com.boycottpro.userboycotts.models;

import java.util.List;

public class UpdateReasonsForm {

    private String user_id;
    private String company_id;
    private String company_name;
    private List<CurrentReason> currentReasons;
    private List<NewReason> newReasons;
    private String currentPersonalReason;

    public UpdateReasonsForm() {
    }

    public UpdateReasonsForm(String user_id, String company_id, String company_name,
        List<CurrentReason> currentReasons, List<NewReason> newReasons, String currentPersonalReason) {
        this.user_id = user_id;
        this.company_id = company_id;
        this.company_name = company_name;
        this.currentReasons = currentReasons;
        this.newReasons = newReasons;
        this.currentPersonalReason = currentPersonalReason;
    }

    public String getUser_id() {
        return user_id;
    }

    public void setUser_id(String user_id) {
        this.user_id = user_id;
    }

    public String getCompany_id() {
        return company_id;
    }

    public void setCompany_id(String company_id) {
        this.company_id = company_id;
    }

    public List<CurrentReason> getCurrentReasons() {
        return currentReasons;
    }

    public void setCurrentReasons(List<CurrentReason> currentReasons) {
        this.currentReasons = currentReasons;
    }

    public List<NewReason> getNewReasons() {
        return newReasons;
    }

    public void setNewReasons(List<NewReason> newReasons) {
        this.newReasons = newReasons;
    }

    public String getCurrentPersonalReason() {
        return currentPersonalReason;
    }

    public void setCurrentPersonalReason(String currentPersonalReason) {
        this.currentPersonalReason = currentPersonalReason;
    }

    public String getCompany_name() {
        return company_name;
    }

    public void setCompany_name(String company_name) {
        this.company_name = company_name;
    }
}
