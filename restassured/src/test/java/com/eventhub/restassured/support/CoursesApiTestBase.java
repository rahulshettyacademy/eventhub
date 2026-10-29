package com.eventhub.restassured.support;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import java.io.IOException;

/** Starts a mock courses API per test class and resets it to the untouched sample before every test. */
public abstract class CoursesApiTestBase {
    private static final String COURSES_PATH = "/courses";

    protected MockCoursesServer server;
    protected RequestSpecification spec;

    @BeforeClass
    public void startServer() throws IOException {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        server = new MockCoursesServer(COURSES_PATH, CoursesSample.json());
        spec = new RequestSpecBuilder().setBaseUri(server.baseUri()).setBasePath(COURSES_PATH).build();
    }

    @BeforeMethod
    public void serveSample() {
        server.setBody(CoursesSample.json());
    }

    /** Replaces the response body for the rest of the current test. */
    protected void serve(String body) {
        server.setBody(body);
    }

    @AfterClass(alwaysRun = true)
    public void stopServer() {
        if (server != null) server.close();
    }
}
