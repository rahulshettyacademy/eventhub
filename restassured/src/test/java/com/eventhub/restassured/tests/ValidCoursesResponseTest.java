package com.eventhub.restassured.tests;

import com.eventhub.restassured.support.CoursesApiTestBase;
import com.eventhub.restassured.support.CoursesSample;
import com.eventhub.restassured.support.CoursesSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.eventhub.restassured.support.CoursesSample.set;
import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

/** Responses the schema must accept: the sample with one valid change applied. */
public class ValidCoursesResponseTest extends CoursesApiTestBase {

    @Test(description = "TC02: one course per category is enough")
    public void tc02_oneCoursePerCategoryIsValid() {
        // -- Step 1: keep only the first course in each list --
        serveChanged(doc -> doc.get("courses").forEach(list -> {
            ArrayNode courses = (ArrayNode) list;
            while (courses.size() > 1) courses.remove(courses.size() - 1);
        }));

        // -- Step 2: it still matches --
        assertResponseMatchesSchema();
    }

    @Test(description = "TC03: a price with two decimals is valid")
    public void tc03_twoDecimalPriceIsValid() {
        serveChanged(set("/courses/webAutomation/0/price", "49.99"));
        assertResponseMatchesSchema();
    }

    @Test(description = "TC04: a long course list is valid")
    public void tc04_manyCoursesAreValid() {
        // -- Step 1: add 10 more courses to webAutomation --
        serveChanged(doc -> {
            ArrayNode webAutomation = (ArrayNode) doc.at("/courses/webAutomation");
            for (int i = 1; i <= 10; i++) {
                webAutomation.addObject().put("courseTitle", "Extra course " + i).put("price", "30");
            }
        });

        // -- Step 2: it still matches --
        assertResponseMatchesSchema();
    }

    @Test(description = "TC05: key order does not matter")
    public void tc05_shuffledKeysAreValid() {
        // -- Step 1: rebuild the object with its top-level keys in reverse order --
        ObjectNode original = CoursesSample.load();
        List<Map.Entry<String, JsonNode>> fields = new ArrayList<>(original.properties());
        Collections.reverse(fields);
        ObjectNode reversed = JsonNodeFactory.instance.objectNode();
        fields.forEach(field -> reversed.set(field.getKey(), field.getValue()));
        serve(CoursesSample.toJson(reversed));

        // -- Step 2: it still matches --
        assertResponseMatchesSchema();
    }

    @Test(description = "TC43: one-character strings meet minLength 1")
    public void tc43_singleCharacterStringsAreValid() {
        serveChanged(set("/instructor", "R").andThen(set("/courses/api/0/courseTitle", "X")));
        assertResponseMatchesSchema();
    }

    @Test(description = "TC44: a zero price is valid")
    public void tc44_zeroPriceIsValid() {
        serveChanged(set("/courses/mobile/0/price", "0"));
        assertResponseMatchesSchema();
    }

    @Test(description = "TC45: a price with one decimal is valid")
    public void tc45_oneDecimalPriceIsValid() {
        serveChanged(set("/courses/mobile/0/price", "49.5"));
        assertResponseMatchesSchema();
    }

    @Test(description = "TC46: non-ASCII course titles are valid")
    public void tc46_unicodeTitleIsValid() {
        serveChanged(set("/courses/api/1/courseTitle", "Tests d'API avancés – 自動化"));
        assertResponseMatchesSchema();
    }

    private void serveChanged(Consumer<ObjectNode> change) {
        ObjectNode doc = CoursesSample.load();
        change.accept(doc);
        serve(CoursesSample.toJson(doc));
    }

    private void assertResponseMatchesSchema() {
        given(spec)
                .when().get()
                .then().statusCode(200)
                .body(matchesJsonSchemaInClasspath(CoursesSchema.CLASSPATH));
    }
}
