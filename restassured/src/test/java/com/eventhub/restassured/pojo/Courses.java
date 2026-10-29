package com.eventhub.restassured.pojo;

import java.util.List;

/** The "courses" object: one list of courses per category. */
public class Courses {
    private List<Course> webAutomation;
    private List<Course> api;
    private List<Course> mobile;

    public List<Course> getWebAutomation() {
        return webAutomation;
    }

    public void setWebAutomation(List<Course> webAutomation) {
        this.webAutomation = webAutomation;
    }

    public List<Course> getApi() {
        return api;
    }

    public void setApi(List<Course> api) {
        this.api = api;
    }

    public List<Course> getMobile() {
        return mobile;
    }

    public void setMobile(List<Course> mobile) {
        this.mobile = mobile;
    }
}
