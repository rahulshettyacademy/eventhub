// Library API (http://216.10.245.166), not EventHub: add a book, get it back by ID, then delete it.
// The libraryApi fixture deletes the book on teardown if the test fails before Step 5.
const { test, expect, buildBook } = require('./fixtures/library-api');

test.describe('Library API', () => {
  test('TC82: a book added through Addbook.php is returned with the same details by GetBook.php?ID, then deleted', async ({ libraryApi }) => {
    // Step 1: build the request body with a unique ISBN
    const book = buildBook();

    // Step 2: add the book; the server builds its ID from isbn + aisle
    const added = await libraryApi.addBook(book);
    expect(added.response).toBeOK();
    expect(added.body).toEqual({ Msg: 'successfully added', ID: book.isbn + book.aisle });
    const bookId = added.body.ID;

    // Step 3: get the book back by its ID; GetBook.php answers with an array of books
    const fetched = await libraryApi.getBookById(bookId);
    expect(fetched.response).toBeOK();

    // Step 4: exactly one book matches the ID, with the same details that were added
    expect(fetched.body).toEqual([
      { book_name: book.name, isbn: book.isbn, aisle: book.aisle, author: book.author },
    ]);

    // Step 5: delete the book that was added
    const deleted = await libraryApi.deleteBook(bookId);
    expect(deleted.response).toBeOK();
    expect(deleted.body.msg).toBe('book is successfully deleted');

    // Step 6: the deleted book can no longer be found by its ID
    const lookup = await libraryApi.getBookById(bookId);
    expect(lookup.response.status()).toBe(404);
    expect(lookup.body.msg).toBe('The book by requested bookid / author name does not exists!');
  });
});
