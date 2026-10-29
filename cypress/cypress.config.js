const { defineConfig } = require('cypress');

module.exports = defineConfig({
  e2e: {
    // Live Library API; the specs only use cy.request, so no page is ever visited
    baseUrl: 'http://216.10.245.166',
    specPattern: 'e2e/**/*.cy.js',
    // Registers the custom Library API commands (cy.addBook, cy.getBookById, cy.deleteBook)
    supportFile: 'support/e2e.js',
    fixturesFolder: 'fixtures',
    video: false,
    screenshotOnRunFailure: false,
  },
});
