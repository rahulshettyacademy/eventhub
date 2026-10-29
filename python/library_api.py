"""Payload model and client for the live Library API at http://216.10.245.166 (not an EventHub API)."""
import uuid
from dataclasses import asdict, dataclass, field

import requests

LIBRARY_API_URL = "http://216.10.245.166"


def _unique_isbn() -> str:
    # The server is shared and rejects duplicate IDs, so every book gets its own ISBN
    return "py" + uuid.uuid4().hex[:8]


@dataclass
class Book:
    """The Addbook.php request body. The API takes the aisle as a string."""

    name: str = "Learn Appium Automation with Java"
    isbn: str = field(default_factory=_unique_isbn)
    aisle: str = "227"
    author: str = "John foe"

    @property
    def expected_id(self) -> str:
        """The server builds a book's ID from isbn + aisle."""
        return self.isbn + self.aisle


class LibraryApiClient:
    """One method per Library API endpoint. Each returns the raw requests.Response,
    and the client remembers which books it added so leftovers can be deleted."""

    def __init__(self, base_url: str = LIBRARY_API_URL):
        self.base_url = base_url
        self.session = requests.Session()
        self.added_book_ids: set[str] = set()

    def add_book(self, book: Book) -> requests.Response:
        response = self.session.post(f"{self.base_url}/Library/Addbook.php", json=asdict(book), timeout=15)
        book_id = response.json().get("ID")
        if book_id:
            self.added_book_ids.add(book_id)
        return response

    def get_book_by_id(self, book_id: str) -> requests.Response:
        return self.session.get(f"{self.base_url}/Library/GetBook.php", params={"ID": book_id}, timeout=15)

    def delete_book(self, book_id: str) -> requests.Response:
        response = self.session.post(f"{self.base_url}/Library/DeleteBook.php", json={"ID": book_id}, timeout=15)
        if response.ok:
            self.added_book_ids.discard(book_id)
        return response

    def delete_leftover_books(self) -> None:
        """Deletes every book this client added that a test didn't delete itself."""
        for book_id in list(self.added_book_ids):
            self.delete_book(book_id)

    def close(self) -> None:
        self.session.close()
