package com.eventhub.tests;

import com.eventhub.api.ApiClient;
import com.eventhub.api.ApiClient.Booking;
import com.eventhub.api.ApiClient.Event;
import com.eventhub.base.BaseTest;
import com.eventhub.config.Config;
import com.eventhub.pages.BookingDetailPage;
import com.eventhub.pages.BookingsPage;
import com.eventhub.pages.Toast;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Critical booking-management E2E paths: TC-001 to TC-003 from docs/test-strategy.md. */
public class BookingFlowTest extends BaseTest {
    private static final int QUANTITY = 2;

    private Event event;
    private Booking booking;

    /** Runs after BaseTest.setUp: every test starts with one fresh booking on a static event. */
    @BeforeMethod
    public void createBooking() {
        event   = api.findStaticEventWithSeats(token, QUANTITY);
        booking = api.createBooking(token, event.id(), QUANTITY);
        createdBookingIds.add(booking.id());
        System.out.printf("Created booking %s (#%d) for \"%s\"%n", booking.bookingRef(), booking.id(), event.title());
    }

    /** Same formatting the app uses for prices: USD, no decimals. */
    private static String usd(double amount) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.US);
        f.setMaximumFractionDigits(0);
        return f.format(amount);
    }

    @Test(description = "TC-001: bookings list shows a card with the booking details")
    public void tc001_bookingsListShowsCard() {
        // -- Step 1: Open My Bookings from the navbar --
        BookingsPage bookings = new BookingsPage(driver).openFromNavbar();
        Assert.assertTrue(bookings.isHeadingVisible(), "\"My Bookings\" heading should be visible");

        // -- Step 2: Find this booking's card and check its content --
        String card = bookings.cardText(booking.bookingRef());
        SoftAssert soft = new SoftAssert();
        soft.assertEquals(bookings.cardBookingId(booking.bookingRef()), "#" + booking.id(), "booking id");
        soft.assertTrue(card.contains("confirmed"), "status badge");
        soft.assertTrue(card.contains(event.title()), "event title");
        soft.assertTrue(card.contains(QUANTITY + " tickets"), "ticket count");
        soft.assertTrue(card.contains(event.city()), "city");
        soft.assertTrue(card.contains(usd(event.price() * QUANTITY)), "total price " + usd(event.price() * QUANTITY));
        soft.assertTrue(bookings.cardHasViewDetails(booking.bookingRef()), "View Details link");
        soft.assertTrue(bookings.cardHasCancelButton(booking.bookingRef()), "Cancel Booking button");
        soft.assertAll();
    }

    @Test(description = "TC-002: booking detail page shows all sections")
    public void tc002_detailPageShowsAllSections() {
        // -- Step 1: Open the detail page from the list --
        BookingDetailPage detail = new BookingsPage(driver).open().viewDetails(booking.bookingRef());
        Assert.assertEquals(detail.currentUrl(), Config.BASE_URL + "/bookings/" + booking.id());

        // -- Step 2: Breadcrumb and header --
        String breadcrumb = detail.breadcrumbText(booking.bookingRef());
        Assert.assertTrue(breadcrumb.contains("My Bookings") && breadcrumb.contains(booking.bookingRef()),
                "Breadcrumb should read \"My Bookings / " + booking.bookingRef() + "\" but was: " + breadcrumb);
        Assert.assertEquals(detail.title(), event.title());

        // -- Step 3: Every section is rendered --
        for (String section : List.of("Event Details", "Customer Details", "Payment Summary", "Refund", "Booking Information")) {
            Assert.assertTrue(detail.isSectionVisible(section), section + " section should be visible");
        }

        // -- Step 4: Section values match the booking --
        String main = detail.mainText();
        SoftAssert soft = new SoftAssert();
        soft.assertTrue(main.contains(event.venue()), "venue");
        soft.assertTrue(main.contains(event.category()), "category");
        soft.assertTrue(main.contains(ApiClient.CUSTOMER_NAME), "customer name");
        soft.assertTrue(main.contains(ApiClient.CUSTOMER_EMAIL), "customer email");
        soft.assertTrue(main.contains(ApiClient.CUSTOMER_PHONE), "customer phone");
        soft.assertTrue(main.contains(usd(event.price() * QUANTITY)), "total paid");
        soft.assertTrue(main.contains("#" + booking.id()), "booking id");
        soft.assertTrue(detail.isRefundCheckVisible(), "refund check button");
        soft.assertAll();
    }

    @Test(description = "TC-003: cancelling from the detail page shows a toast, redirects and deletes the booking")
    public void tc003_cancelFromDetailPage() {
        // -- Step 1: Open the detail page --
        BookingDetailPage detail = new BookingDetailPage(driver).open(booking.id());
        Assert.assertEquals(detail.title(), event.title());

        // -- Step 2: Open the cancel dialog --
        String dialog = detail.openCancelDialog();
        Assert.assertTrue(dialog.contains("Cancel this booking?"), "Dialog title missing: " + dialog);
        Assert.assertTrue(dialog.contains("Cancelling " + booking.bookingRef() + " will release " + QUANTITY + " seat(s)"),
                "Dialog description should name the ref and seat count: " + dialog);

        // -- Step 3: Confirm; check the toast straight away (it auto-dismisses after 4.5s) --
        detail.confirmCancel();
        Assert.assertTrue(new Toast(driver).isShown("Booking cancelled successfully"), "Success toast should be visible");

        // -- Step 4: Redirected to the list; the booking is gone from the list and the API --
        BookingsPage bookings = new BookingsPage(driver).waitForLoaded();
        Assert.assertTrue(bookings.waitForCardGone(booking.bookingRef()), "Cancelled booking should not be listed");
        Assert.assertEquals(api.bookingStatus(token, booking.id()), 404, "GET /bookings/:id after cancel");
    }
}
