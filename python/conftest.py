import pytest

from library_api import LibraryApiClient


@pytest.fixture
def library_api():
    """A Library API client for one test. After the test, even a failed one,
    it deletes any book the test added but didn't delete."""
    client = LibraryApiClient()
    yield client
    client.delete_leftover_books()
    client.close()
