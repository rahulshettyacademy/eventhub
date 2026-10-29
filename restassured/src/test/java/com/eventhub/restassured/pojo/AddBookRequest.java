package com.eventhub.restassured.pojo;

/**
 * The Addbook.php request body: {"name": "...", "isbn": "...", "aisle": "227", "author": "..."}.
 * Rest Assured serializes it to JSON through the getters. The API takes the aisle as a string.
 */
public class AddBookRequest {
    private String name;
    private String isbn;
    private String aisle;
    private String author;

    public AddBookRequest() {
    }

    public AddBookRequest(String name, String isbn, String aisle, String author) {
        this.name = name;
        this.isbn = isbn;
        this.aisle = aisle;
        this.author = author;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getAisle() {
        return aisle;
    }

    public void setAisle(String aisle) {
        this.aisle = aisle;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }
}
