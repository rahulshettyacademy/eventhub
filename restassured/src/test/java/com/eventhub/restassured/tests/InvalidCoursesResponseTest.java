package com.eventhub.restassured.tests;

import com.eventhub.restassured.support.CoursesApiTestBase;
import com.eventhub.restassured.support.CoursesSample;
import com.eventhub.restassured.support.CoursesSchema;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static com.eventhub.restassured.support.CoursesSample.remove;
import static com.eventhub.restassured.support.CoursesSample.set;
import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.not;

/**
 * Responses the schema must reject. Each case changes the valid sample, then checks that the response fails
 * the schema on exactly the expected rules.
 */
public class InvalidCoursesResponseTest extends CoursesApiTestBase {

    /** A change to the sample and the schema keywords it should break. toString() names the case in reports. */
    record InvalidCase(String id, String scenario, Consumer<ObjectNode> change, Set<String> expectedKeywords) {
        InvalidCase(String id, String scenario, Consumer<ObjectNode> change, String... expectedKeywords) {
            this(id, scenario, change, Set.of(expectedKeywords));
        }

        @Override
        public String toString() {
            return id + ": " + scenario;
        }
    }

    /** A whole response body (not a change to the sample) and the keyword it should break. */
    record InvalidBody(String id, String scenario, String body, String expectedKeyword) {
        @Override
        public String toString() {
            return id + ": " + scenario;
        }
    }

    private static final Map<String, String> EXTRA_COURSE = Map.of("courseTitle", "JMeter", "price", "40");

    @DataProvider(name = "invalidResponses")
    public Object[][] invalidResponses() {
        List<InvalidCase> cases = List.of(
                // -- Missing required fields --
                new InvalidCase("TC06", "no instructor", remove("/instructor"), "required"),
                new InvalidCase("TC07", "no url", remove("/url"), "required"),
                new InvalidCase("TC08", "no services", remove("/services"), "required"),
                new InvalidCase("TC09", "no expertise", remove("/expertise"), "required"),
                new InvalidCase("TC10", "no courses", remove("/courses"), "required"),
                new InvalidCase("TC11", "no linkedIn", remove("/linkedIn"), "required"),
                new InvalidCase("TC12", "no webAutomation", remove("/courses/webAutomation"), "required"),
                new InvalidCase("TC13", "no api", remove("/courses/api"), "required"),
                new InvalidCase("TC14", "no mobile", remove("/courses/mobile"), "required"),
                new InvalidCase("TC15", "no courseTitle", remove("/courses/api/0/courseTitle"), "required"),
                new InvalidCase("TC16", "no price", remove("/courses/mobile/0/price"), "required"),

                // -- Wrong data types --
                new InvalidCase("TC17", "instructor is a number", set("/instructor", 123), "type"),
                new InvalidCase("TC18", "instructor is null", set("/instructor", null), "type"),
                new InvalidCase("TC19", "courses is an array", set("/courses", List.of()), "type"),
                new InvalidCase("TC20", "course list is an object", set("/courses/api", Map.of()), "type"),
                new InvalidCase("TC21", "course item is a string", set("/courses/mobile", List.of("Appium")), "type"),
                new InvalidCase("TC22", "price is a number", set("/courses/webAutomation/0/price", 50), "type"),
                new InvalidCase("TC23", "courseTitle is a boolean", set("/courses/webAutomation/0/courseTitle", true), "type"),
                new InvalidCase("TC24", "linkedIn is a number", set("/linkedIn", 12345), "type"),
                new InvalidCase("TC50", "course item is null", set("/courses/api", Arrays.asList((Object) null)), "type"),
                new InvalidCase("TC51", "courses is null", set("/courses", null), "type"),
                new InvalidCase("TC52", "url is a boolean", set("/url", false), "type"),

                // -- Lengths, patterns and formats --
                new InvalidCase("TC25", "empty instructor", set("/instructor", ""), "minLength"),
                new InvalidCase("TC26", "empty courseTitle", set("/courses/api/0/courseTitle", ""), "minLength"),
                new InvalidCase("TC27", "empty course list", set("/courses/mobile", List.of()), "minItems"),
                new InvalidCase("TC28", "price is not a number", set("/courses/api/0/price", "fifty"), "pattern"),
                new InvalidCase("TC29", "negative price", set("/courses/api/0/price", "-50"), "pattern"),
                new InvalidCase("TC30", "price with 3 decimals", set("/courses/api/0/price", "50.999"), "pattern"),
                new InvalidCase("TC31", "price with currency sign", set("/courses/api/0/price", "$50"), "pattern"),
                new InvalidCase("TC32", "empty price", set("/courses/api/0/price", ""), "pattern"),
                new InvalidCase("TC33", "price with spaces", set("/courses/api/0/price", " 50 "), "pattern"),
                new InvalidCase("TC34", "linkedIn is not a URI", set("/linkedIn", "not a url"), "format"),
                new InvalidCase("TC53", "price with trailing dot", set("/courses/api/0/price", "50."), "pattern"),
                new InvalidCase("TC54", "price with no whole part", set("/courses/api/0/price", ".50"), "pattern"),
                new InvalidCase("TC55", "price in exponent form", set("/courses/api/0/price", "1e2"), "pattern"),
                new InvalidCase("TC56", "price with thousands separator", set("/courses/api/0/price", "1,000"), "pattern"),
                new InvalidCase("TC57", "empty url", set("/url", ""), "minLength"),
                new InvalidCase("TC58", "empty services", set("/services", ""), "minLength"),
                new InvalidCase("TC59", "empty expertise", set("/expertise", ""), "minLength"),

                // -- Extra fields (additionalProperties: false) --
                new InvalidCase("TC35", "extra top-level field", set("/email", "x@y.com"), "additionalProperties"),
                new InvalidCase("TC36", "new course category", set("/courses/performance", List.of(EXTRA_COURSE)), "additionalProperties"),
                new InvalidCase("TC37", "extra field on a course", set("/courses/api/0/duration", "10h"), "additionalProperties"),

                // -- Several problems at once --
                // The validator skips an object's children once the object itself fails, so a missing top-level
                // field hides a bad price further down until the field is restored.
                new InvalidCase("TC60", "missing field hides a bad price below it",
                        remove("/instructor").andThen(set("/courses/api/0/price", "abc")), "required"),
                // Problems in separate branches are all reported.
                new InvalidCase("TC61", "wrong type and extra field together",
                        set("/expertise", 7).andThen(set("/courses/mobile/0/level", "basic")), "type", "additionalProperties"));

        return cases.stream().map(c -> new Object[] {c}).toArray(Object[][]::new);
    }

    @Test(dataProvider = "invalidResponses", description = "A changed response fails the schema on exactly the expected rules")
    public void invalidResponseFailsSchema(InvalidCase testCase) {
        // -- Step 1: serve the sample with the change applied --
        ObjectNode doc = CoursesSample.load();
        testCase.change().accept(doc);
        String body = CoursesSample.toJson(doc);
        serve(body);

        // -- Step 2: the response no longer matches the schema --
        assertResponseFailsSchema();

        // -- Step 3: it fails on the expected rules, and only those --
        Set<String> failed = CoursesSchema.failedKeywords(body);
        Assert.assertEquals(failed, testCase.expectedKeywords(),
                testCase + " should break exactly " + testCase.expectedKeywords() + " but broke " + failed);
    }

    @DataProvider(name = "invalidBodies")
    public Object[][] invalidBodies() {
        List<InvalidBody> cases = List.of(
                new InvalidBody("TC62", "root is an array", "[]", "type"),
                new InvalidBody("TC63", "root is a string", "\"courses\"", "type"),
                new InvalidBody("TC64", "root is null", "null", "type"),
                new InvalidBody("TC65", "empty object", "{}", "required"));
        return cases.stream().map(c -> new Object[] {c}).toArray(Object[][]::new);
    }

    @Test(dataProvider = "invalidBodies", description = "A body of the wrong shape fails the schema at the root")
    public void wrongShapeFailsSchema(InvalidBody testCase) {
        // -- Step 1: serve the body as is --
        serve(testCase.body());

        // -- Step 2: it fails the schema on the expected rule --
        assertResponseFailsSchema();
        Set<String> failed = CoursesSchema.failedKeywords(testCase.body());
        Assert.assertEquals(failed, Set.of(testCase.expectedKeyword()), testCase + " broke " + failed);
    }

    private void assertResponseFailsSchema() {
        given(spec)
                .when().get()
                .then().statusCode(200)
                .body(not(matchesJsonSchemaInClasspath(CoursesSchema.CLASSPATH)));
    }
}
