/**
 * Adds a book to the live Library API, reads it back by ID, then deletes it.
 * The book details come from fixtures/book.json; the API calls are custom commands in support/commands.js.
 * The server is shared, so each run uses a unique ISBN.
 */
describe('Library API', () => {
  beforeEach(() => {
    // The fixture holds the fixed details; add a unique ISBN, since the server rejects duplicate IDs
    cy.fixture('book').then((bookData) => {
      cy.wrap({ ...bookData, isbn: `cy${Date.now().toString(36)}` }).as('book');
    });
  });

  afterEach(() => {
    // Safety net: delete the book only when the test failed before it deleted it.
    // Aliases don't reach afterEach reliably, so the test records the ID in Cypress.env as well.
    const addedBookId = Cypress.env('addedBookId');
    if (addedBookId) {
      cy.deleteBook(addedBookId, { failOnStatusCode: false });
      Cypress.env('addedBookId', null);
    }
  });

  // function () rather than an arrow, so this.book reads the alias set in beforeEach
  it('TC82: a book added through Addbook.php is returned with the same details by GetBook.php?ID, then deleted', function () {
    const book = this.book;

    // -- Step 1: add the book, and record its ID before asserting so cleanup still runs if an assertion fails --
    cy.addBook(book).then((addResponse) => {
      Cypress.env('addedBookId', addResponse.body.ID);
      cy.log(`Added book ID: ${addResponse.body.ID}`);

      // -- Step 2: the book was added, and the server built its ID from isbn + aisle --
      expect(addResponse.status, 'Addbook.php status').to.eq(200);
      expect(addResponse.body.Msg, 'Msg').to.eq('successfully added');
      expect(addResponse.body.ID, 'ID').to.eq(book.isbn + book.aisle);

      // Save the ID as an alias for the later steps (wrapped here, since cy.log above changes the subject)
      cy.wrap(addResponse.body.ID).as('bookId');
    });

    // -- Step 3: get the book back by its ID; GetBook.php answers with an array of books --
    cy.get('@bookId')
      .then((bookId) => cy.getBookById(bookId))
      .then((getResponse) => {
        expect(getResponse.status, 'GetBook.php status').to.eq(200);

        // -- Step 4: exactly one book matches the ID, with the same details that were added --
        expect(getResponse.body, 'books matching the ID').to.have.length(1);
        const [returned] = getResponse.body;
        expect(returned.book_name, 'book_name').to.eq(book.name);
        expect(returned.isbn, 'isbn').to.eq(book.isbn);
        expect(returned.aisle, 'aisle').to.eq(book.aisle);
        expect(returned.author, 'author').to.eq(book.author);
      });

    // -- Step 5: delete the book that was added --
    cy.get('@bookId')
      .then((bookId) => cy.deleteBook(bookId))
      .then((deleteResponse) => {
        expect(deleteResponse.status, 'DeleteBook.php status').to.eq(200);
        expect(deleteResponse.body.msg, 'msg').to.eq('book is successfully deleted');
        Cypress.env('addedBookId', null);
      });

    // -- Step 6: the deleted book can no longer be found by its ID --
    cy.get('@bookId')
      .then((bookId) => cy.getBookById(bookId, { failOnStatusCode: false }))
      .then((lookupResponse) => {
        expect(lookupResponse.status, 'GetBook.php status after delete').to.eq(404);
        expect(lookupResponse.body.msg, 'msg').to.eq('The book by requested bookid / author name does not exists!');
      });
  });
});
