// Playwright fixture for the live Library API (not an EventHub API).
// `libraryApi` wraps the three endpoints, remembers every book it adds,
// and deletes any that a test didn't delete itself, so specs need no afterEach.
const base = require('@playwright/test');

const LIBRARY_API_URL = 'http://216.10.245.166';

/**
 * Builds an Addbook.php request body. The server is shared and rejects duplicate IDs,
 * so every book gets a unique ISBN; pass overrides to change any field.
 * The API takes the aisle as a string.
 */
function buildBook(overrides = {}) {
  return {
    name: 'Learn Appium Automation with Java',
    isbn: `pw${Date.now().toString(36)}`,
    aisle: '227',
    author: 'John foe',
    ...overrides,
  };
}

const test = base.test.extend({
  libraryApi: async ({ playwright }, use) => {
    const request = await playwright.request.newContext({ baseURL: LIBRARY_API_URL });
    const addedBookIds = new Set();

    await use({
      async addBook(book) {
        const response = await request.post('/Library/Addbook.php', { data: book });
        const body = await response.json();
        if (body.ID) {
          addedBookIds.add(body.ID);
        }
        return { response, body };
      },
      async getBookById(id) {
        const response = await request.get('/Library/GetBook.php', { params: { ID: id } });
        return { response, body: await response.json() };
      },
      async deleteBook(id) {
        const response = await request.post('/Library/DeleteBook.php', { data: { ID: id } });
        const body = await response.json();
        if (response.ok()) {
          addedBookIds.delete(id);
        }
        return { response, body };
      },
    });

    // Teardown: delete any book the test added but didn't delete (e.g. it failed part-way)
    for (const id of addedBookIds) {
      await request.post('/Library/DeleteBook.php', { data: { ID: id } });
    }
    await request.dispose();
  },
});

module.exports = { test, expect: base.expect, buildBook };
