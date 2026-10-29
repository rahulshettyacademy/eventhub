package com.eventhub.restassured.tests;

import com.eventhub.restassured.support.CoursesApiTestBase;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.restassured.RestAssured.given;

/** Checks on the course prices in the response. */
public class CoursePricesTest extends CoursesApiTestBase {
    /** Every course in every category: courses is a map of category -> list of courses, so flatten its values. */
    private static final String ALL_COURSES = "courses.values().flatten()";

    @Test(description = "TC76: the sum of all course prices is less than $1000")
    public void tc76_totalPriceUnder1000() {
        // -- Step 1: get the raw response body --
        String body = given(spec)
                .when().get()
                .then().statusCode(200).contentType(ContentType.JSON)
                .extract().asString();

        // -- Step 2: parse it with JsonPath and read every course's price in one expression --
        JsonPath jsonPath = new JsonPath(body);
        int courseCount = jsonPath.getInt(ALL_COURSES + ".size()");
        List<String> prices = jsonPath.getList(ALL_COURSES + ".price", String.class);

        // -- Step 3: add up the prices --
        BigDecimal total = BigDecimal.ZERO;
        for (String price : prices) {
            total = total.add(new BigDecimal(price));
        }
        System.out.printf("Sum of %d course prices: $%s%n", courseCount, total);

        // -- Step 4: every course has a price, and the sum is below $1000 --
        Assert.assertTrue(courseCount > 0, "Response has no courses to sum");
        Assert.assertEquals(prices.size(), courseCount, "Every course should have a price");
        Assert.assertTrue(total.compareTo(new BigDecimal("1000")) < 0,
                "Sum of all course prices should be less than $1000 but was $" + total);
    }
}
