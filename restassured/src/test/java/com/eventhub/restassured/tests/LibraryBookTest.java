package com.eventhub.restassured.tests;

import com.eventhub.restassured.pojo.AddBookRequest;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Adds a book to the live Library API, reads it back by ID, then deletes it.
 * Unlike the course tests this calls a real server, so each run uses a unique ISBN.
 */
public class LibraryBookTest {
    private static final String BASE_URI = "http://216.10.245.166";

    private RequestSpecification spec;
    /** The ID of the book this test added, so it can be deleted even when an assertion fails. */
    private String addedBookId;

    @BeforeClass
    public void setUp() {
        spec = new RequestSpecBuilder()
                .setBaseUri(BASE_URI)
                .setContentType(ContentType.JSON)
                .build();
    }

    @Test(description = "TC82: a book added through Addbook.php is returned with the same details by GetBook.php?ID, then deleted")
    public void tc82_addGetAndDeleteBook() {
        // -- Step 1: build a book with a unique ISBN, since the server is shared and rejects duplicate IDs --
        String isbn = "ra" + UUID.randomUUID().toString().substring(0, 8);
        AddBookRequest book = new AddBookRequest("Learn Appium Automation with Java", isbn, "227", "John foe");

        // -- Step 2: add the book, and keep its ID before asserting so cleanup still runs if an assertion fails --
        Response addResponse = given(spec)
                .body(book)
                .when().post("/Library/Addbook.php");
        JsonPath added = addResponse.jsonPath();
        addedBookId = added.getString("ID");
        System.out.println("Added book ID: " + addedBookId);

        // -- Step 3: the book was added, and the server built its ID from isbn + aisle --
        Assert.assertEquals(addResponse.statusCode(), 200, "Addbook.php status");
        Assert.assertEquals(added.getString("Msg"), "successfully added", "Msg");
        Assert.assertEquals(addedBookId, book.getIsbn() + book.getAisle(), "ID");

        // -- Step 4: get the book back by its ID; GetBook.php answers with an array of books --
        JsonPath getResponse = given(spec)
                .queryParam("ID", addedBookId)
                .when().get("/Library/GetBook.php")
                .then().statusCode(200).contentType(ContentType.JSON)
                .extract().jsonPath();

        // -- Step 5: exactly one book matches the ID, with the same details that were added --
        Assert.assertEquals(getResponse.getInt("size()"), 1, "Exactly one book should match the ID");
        Assert.assertEquals(getResponse.getString("[0].book_name"), book.getName(), "book_name");
        Assert.assertEquals(getResponse.getString("[0].isbn"), book.getIsbn(), "isbn");
        Assert.assertEquals(getResponse.getString("[0].aisle"), book.getAisle(), "aisle");
        Assert.assertEquals(getResponse.getString("[0].author"), book.getAuthor(), "author");

        // -- Step 6: delete the book that was added --
        given(spec)
                .body(Map.of("ID", addedBookId))
                .when().post("/Library/DeleteBook.php")
                .then().statusCode(200)
                .body("msg", equalTo("book is successfully deleted"));
        String deletedBookId = addedBookId;
        addedBookId = null;

        // -- Step 7: the deleted book can no longer be found by its ID --
        given(spec)
                .queryParam("ID", deletedBookId)
                .when().get("/Library/GetBook.php")
                .then().statusCode(404)
                .body("msg", equalTo("The book by requested bookid / author name does not exists!"));
    }

    /** Safety net: deletes the book only when the test failed before Step 6 deleted it. */
    @AfterMethod(alwaysRun = true)
    public void deleteBookLeftByFailedTest() {
        if (addedBookId == null) {
            return;
        }
        given(spec)
                .body(Map.of("ID", addedBookId))
                .when().post("/Library/DeleteBook.php");
        addedBookId = null;
    }
}
