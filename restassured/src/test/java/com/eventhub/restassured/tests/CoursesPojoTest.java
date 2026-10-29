package com.eventhub.restassured.tests;

import com.eventhub.restassured.pojo.Course;
import com.eventhub.restassured.pojo.Courses;
import com.eventhub.restassured.pojo.CoursesResponse;
import com.eventhub.restassured.support.CoursesApiTestBase;
import io.restassured.http.ContentType;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

/** Deserializes a successful response into the POJOs in com.eventhub.restassured.pojo and checks its values. */
public class CoursesPojoTest extends CoursesApiTestBase {

    /** GET /courses, expect 200 JSON, and map the body onto CoursesResponse. */
    private CoursesResponse getCourses() {
        return given(spec)
                .when().get()
                .then().statusCode(200).contentType(ContentType.JSON)
                .extract().as(CoursesResponse.class);
    }

    private static List<Course> allCourses(Courses courses) {
        List<Course> all = new ArrayList<>(courses.getWebAutomation());
        all.addAll(courses.getApi());
        all.addAll(courses.getMobile());
        return all;
    }

    @Test(description = "TC77: a successful response maps onto the POJO with the expected top-level values")
    public void tc77_topLevelFields() {
        // -- Step 1: get the response as a CoursesResponse --
        CoursesResponse response = getCourses();

        // -- Step 2: every top-level field has its expected value --
        Assert.assertEquals(response.getInstructor(), "RahulShetty");
        Assert.assertEquals(response.getUrl(), "rahulshettyacademy.com");
        Assert.assertEquals(response.getServices(), "projectSupport");
        Assert.assertEquals(response.getExpertise(), "Automation");
        Assert.assertEquals(response.getLinkedIn(), "https://www.linkedin.com/in/rahul-shetty-trainer/");
        Assert.assertNotNull(response.getCourses(), "courses should be present");
    }

    @Test(description = "TC78: each category lists the expected course titles in order")
    public void tc78_courseTitlesPerCategory() {
        // -- Step 1: get the courses object --
        Courses courses = getCourses().getCourses();

        // -- Step 2: compare each category's titles --
        Map<String, List<String>> expected = Map.of(
                "webAutomation", List.of("Selenium Webdriver Java", "Cypress", "Protractor"),
                "api", List.of("Rest Assured Automation using Java", "SoapUI Webservices testing"),
                "mobile", List.of("Appium-Mobile Automation using Java"));
        Map<String, List<Course>> actual = Map.of(
                "webAutomation", courses.getWebAutomation(),
                "api", courses.getApi(),
                "mobile", courses.getMobile());
        expected.forEach((category, titles) -> Assert.assertEquals(
                actual.get(category).stream().map(Course::getCourseTitle).toList(), titles,
                "Course titles in " + category));
    }

    @Test(description = "TC79: the price of a course can be looked up by its title")
    public void tc79_priceByTitle() {
        // -- Step 1: get the api courses --
        List<Course> apiCourses = getCourses().getCourses().getApi();

        // -- Step 2: find SoapUI by title and check its price --
        String price = apiCourses.stream()
                .filter(course -> course.getCourseTitle().equals("SoapUI Webservices testing"))
                .map(Course::getPrice)
                .findFirst()
                .orElseThrow(() -> new AssertionError("SoapUI Webservices testing not in api courses"));
        Assert.assertEquals(price, "40");
    }

    @Test(description = "TC80: every course has a numeric price and the prices add up to $270")
    public void tc80_totalPriceFromPojo() {
        // -- Step 1: collect the courses of every category --
        List<Course> all = allCourses(getCourses().getCourses());
        Assert.assertEquals(all.size(), 6, "Number of courses across all categories");

        // -- Step 2: add up the prices; BigDecimal fails the test if a price is not a number --
        BigDecimal total = BigDecimal.ZERO;
        for (Course course : all) {
            Assert.assertNotNull(course.getPrice(), course.getCourseTitle() + " has no price");
            total = total.add(new BigDecimal(course.getPrice()));
        }

        // -- Step 3: 50 + 40 + 40 + 50 + 40 + 50 --
        Assert.assertEquals(total, new BigDecimal("270"));
    }

    @Test(description = "TC81: the sum of all course prices, read through the POJO getters, is less than $1000")
    public void tc81_totalPriceUnder1000FromPojo() {
        // -- Step 1: get the response as a CoursesResponse --
        Courses courses = getCourses().getCourses();

        // -- Step 2: add up the price of every course in every category --
        BigDecimal total = BigDecimal.ZERO;
        int courseCount = 0;
        for (List<Course> category : List.of(courses.getWebAutomation(), courses.getApi(), courses.getMobile())) {
            for (Course course : category) {
                Assert.assertNotNull(course.getPrice(), course.getCourseTitle() + " has no price");
                total = total.add(new BigDecimal(course.getPrice()));
                courseCount++;
            }
        }
        System.out.printf("Sum of %d course prices (POJO): $%s%n", courseCount, total);

        // -- Step 3: there are courses, and their sum is below $1000 --
        Assert.assertTrue(courseCount > 0, "Response has no courses to sum");
        Assert.assertTrue(total.compareTo(new BigDecimal("1000")) < 0,
                "Sum of all course prices should be less than $1000 but was $" + total);
    }
}
