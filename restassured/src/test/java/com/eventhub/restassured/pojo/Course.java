package com.eventhub.restassured.pojo;

/** One course in a category list: {"courseTitle": "...", "price": "50"}. The API sends the price as a string. */
public class Course {
    private String courseTitle;
    private String price;

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }
}
