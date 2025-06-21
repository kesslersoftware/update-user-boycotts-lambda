package com.boycottpro.userboycotts.models;

public class CurrentReason {

    private String company_cause_id;
    private boolean personal_reason;
    private boolean remove;

    public CurrentReason() {
    }

    public CurrentReason(String company_cause_id, boolean personal_reason, boolean remove) {
        this.company_cause_id = company_cause_id;
        this.personal_reason = personal_reason;
        this.remove = remove;
    }

    public String getCompany_cause_id() {
        return company_cause_id;
    }

    public void setCompany_cause_id(String company_cause_id) {
        this.company_cause_id = company_cause_id;
    }

    public boolean isPersonal_reason() {
        return personal_reason;
    }

    public void setPersonal_reason(boolean personal_reason) {
        this.personal_reason = personal_reason;
    }

    public boolean isRemove() {
        return remove;
    }

    public void setRemove(boolean remove) {
        this.remove = remove;
    }
}
