package com.eventhub.tests;

import com.eventhub.api.ApiClient.Booking;
import com.eventhub.api.ApiClient.Event;
import com.eventhub.base.BaseTest;
import com.eventhub.base.DriverFactory;
import com.eventhub.config.Config;
import com.eventhub.pages.BookingDetailPage;
import com.eventhub.pages.BookingsPage;
import com.eventhub.pages.LoginPage;
import com.eventhub.pages.Navbar;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.util.ArrayList;
import java.util.List;

/** Security journeys: TC-200 (sandbox isolation in the UI) and TC-206 (AuthGuard) from docs/test-strategy.md. */
public class AccessControlTest extends BaseTest {
    private String otherToken;
    private final List<Long> otherUserBookingIds = new ArrayList<>();

    /** Runs before BaseTest.tearDown: removes bookings made with the second account, using its own token. */
    @AfterMethod(alwaysRun = true)
    public void deleteOtherUserBookings() {
        if (otherToken != null) api.deleteBookings(otherToken, otherUserBookingIds);
        otherUserBookingIds.clear();
        otherToken = null;
    }

    @Test(description = "TC-200: another user's booking shows Access Denied and none of its data, while its owner can see it")
    public void tc200_otherUsersBookingShowsAccessDenied() {
        // -- Step 1: User A (second account) owns a booking --
        otherToken = api.login(Config.OTHER_USER_EMAIL, Config.OTHER_USER_PASSWORD);
        Event event = api.findStaticEventWithSeats(otherToken, 1);
        Booking theirs = api.createBooking(otherToken, event.id(), 1);
        otherUserBookingIds.add(theirs.id());
        System.out.printf("Other user's booking %s (#%d)%n", theirs.bookingRef(), theirs.id());

        // -- Step 2: The owner can open it in their own browser --
        WebDriver ownerBrowser = DriverFactory.create();
        try {
            loginInBrowser(ownerBrowser, otherToken);
            Assert.assertEquals(new BookingDetailPage(ownerBrowser).open(theirs.id()).title(), event.title(),
                    "Owner should see their own booking");
        } finally {
            ownerBrowser.quit();
        }

        // -- Step 3: User B (this test's main account) opens the same URL --
        BookingDetailPage detail = new BookingDetailPage(driver);
        String errorTitle = detail.openExpectingError(theirs.id());

        // -- Step 4: Access Denied, with no trace of User A's booking --
        SoftAssert soft = new SoftAssert();
        soft.assertEquals(errorTitle, "Access Denied", "error title");
        soft.assertEquals(detail.errorDescription(), "You are not authorized to view this booking.", "error description");
        soft.assertTrue(detail.hasViewMyBookingsButton(), "View My Bookings button");
        soft.assertFalse(detail.pageSource().contains(theirs.bookingRef()), "page must not show the other user's ref");
        soft.assertFalse(detail.hasSection("Booking Information"), "page must not render booking sections");
        soft.assertAll();
        Assert.assertEquals(api.bookingStatus(token, theirs.id()), 403, "API returns 403 for another user's booking");

        // -- Step 5: "View My Bookings" shows User B's own list, which doesn't include User A's booking --
        BookingsPage bookings = detail.clickViewMyBookings();
        Assert.assertFalse(bookings.refsInOrder().contains(theirs.bookingRef()),
                "User A's booking must not appear in User B's list");
    }

    @Test(description = "TC-206: after logging out, booking pages redirect to /login; signing in again restores access")
    public void tc206_loggedOutUserIsRedirectedToLogin() {
        // -- Step 1: A booking exists, and the user is logged in --
        Event event = api.findStaticEventWithSeats(token, 1);
        Booking mine = api.createBooking(token, event.id(), 1);
        createdBookingIds.add(mine.id());
        Navbar navbar = new Navbar(driver);
        Assert.assertEquals(navbar.loggedInEmail(), Config.USER_EMAIL, "Logged-in user in the navbar");

        // -- Step 2: Log out through the navbar; the token is removed --
        LoginPage login = navbar.logout();
        Assert.assertNull(((JavascriptExecutor) driver).executeScript("return localStorage.getItem('eventhub_token');"),
                "Token should be cleared on logout");

        // -- Step 3: The bookings list redirects to /login --
        driver.get(Config.BASE_URL + "/bookings");
        login.waitForLoaded();
        Assert.assertEquals(login.currentUrl(), Config.BASE_URL + "/login", "/bookings while logged out");

        // -- Step 4: A booking detail URL redirects too, without rendering the booking --
        driver.get(Config.BASE_URL + "/bookings/" + mine.id());
        login.waitForLoaded();
        Assert.assertEquals(login.currentUrl(), Config.BASE_URL + "/login", "/bookings/:id while logged out");
        Assert.assertFalse(driver.getPageSource().contains(mine.bookingRef()), "Booking ref must not be rendered");

        // -- Step 5: Signing in through the form lands on home, and the booking is reachable again --
        login.loginAs(Config.USER_EMAIL, Config.USER_PASSWORD);
        Assert.assertEquals(driver.getCurrentUrl(), Config.BASE_URL + "/", "Login lands on the home page");
        BookingsPage bookings = new BookingsPage(driver).openFromNavbar();
        Assert.assertTrue(bookings.cardText(mine.bookingRef()).contains(mine.bookingRef()), "Own booking is listed after login");
    }
}
