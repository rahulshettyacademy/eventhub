// Library API calls, one command per endpoint. Each takes extra cy.request options,
// e.g. { failOnStatusCode: false } to assert on a 404 instead of failing the test.

Cypress.Commands.add('addBook', (book, options = {}) =>
  cy.request({ method: 'POST', url: '/Library/Addbook.php', body: book, ...options }),
);

Cypress.Commands.add('getBookById', (id, options = {}) =>
  cy.request({ method: 'GET', url: '/Library/GetBook.php', qs: { ID: id }, ...options }),
);

Cypress.Commands.add('deleteBook', (id, options = {}) =>
  cy.request({ method: 'POST', url: '/Library/DeleteBook.php', body: { ID: id }, ...options }),
);
