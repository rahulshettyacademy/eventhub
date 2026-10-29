"""Library API: add a book, get it back by ID, then delete it."""
from library_api import Book


def test_tc82_add_get_and_delete_book(library_api):
    # Step 1: build the request body; Book() fills in the details and a unique ISBN
    book = Book()

    # Step 2: add the book; the server builds its ID from isbn + aisle
    add_response = library_api.add_book(book)
    assert add_response.status_code == 200
    assert add_response.json() == {"Msg": "successfully added", "ID": book.expected_id}

    # Step 3: get the book back by its ID; GetBook.php answers with an array of books
    get_response = library_api.get_book_by_id(book.expected_id)
    assert get_response.status_code == 200

    # Step 4: exactly one book matches the ID, with the same details that were added
    assert get_response.json() == [
        {"book_name": book.name, "isbn": book.isbn, "aisle": book.aisle, "author": book.author}
    ]

    # Step 5: delete the book that was added
    delete_response = library_api.delete_book(book.expected_id)
    assert delete_response.status_code == 200
    assert delete_response.json()["msg"] == "book is successfully deleted"

    # Step 6: the deleted book can no longer be found by its ID
    lookup_response = library_api.get_book_by_id(book.expected_id)
    assert lookup_response.status_code == 404
    assert lookup_response.json()["msg"] == "The book by requested bookid / author name does not exists!"
