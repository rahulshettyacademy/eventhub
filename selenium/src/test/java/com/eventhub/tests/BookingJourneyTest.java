package com.eventhub.tests;

import com.eventhub.api.ApiClient;
import com.eventhub.api.ApiClient.Booking;
import com.eventhub.api.ApiClient.Event;
import com.eventhub.base.BaseTest;
import com.eventhub.config.Config;
import com.eventhub.pages.BookingsPage;
import com.eventhub.pages.EventDetailPage;
import com.eventhub.pages.Toast;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Multi-page booking journeys: TC-006 and TC-004 from docs/test-strategy.md, each
 * combined with TC-512 (list and seat counts refresh after booking / cancelling).
 */
public class BookingJourneyTest extends BaseTest {

    /** Same formatting the app uses for prices: USD, no decimals. */
    private static String usd(double amount) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.US);
        f.setMaximumFractionDigits(0);
        return f.format(amount);
    }

    private Booking createApiBooking(Event event, int quantity) {
        Booking booking = api.createBooking(token, event.id(), quantity);
        createdBookingIds.add(booking.id());
        System.out.printf("Created booking %s (#%d) for \"%s\"%n", booking.bookingRef(), booking.id(), event.title());
        return booking;
    }

    @Test(description = "TC-006 + TC-512: booking 3 tickets through the UI lists the new booking above older ones and lowers the seat count")
    public void tc006_bookThroughUiThenViewMyBookings() {
        final int quantity = 3;
        Event event = api.findStaticEventWithSeats(token, quantity);

        // -- Step 1: An older booking already exists, so "newest first" can be checked on a shared account --
        Booking older = createApiBooking(event, 1);
        int seatsBefore = api.availableSeats(token, event.id());

        // -- Step 2: Open the event; the page shows this user's seats --
        EventDetailPage eventPage = new EventDetailPage(driver).open(event.id());
        Assert.assertEquals(eventPage.title(), event.title());
        Assert.assertEquals(eventPage.availableSeats(), seatsBefore, "Seats shown before booking");

        // -- Step 3: Choose 3 tickets; the price summary follows the quantity --
        eventPage.selectTickets(quantity);
        Assert.assertEquals(eventPage.ticketCount(), quantity, "Ticket counter");
        Assert.assertEquals(eventPage.priceLine(), usd(event.price()) + " × " + quantity + " tickets", "Price line");
        Assert.assertEquals(eventPage.formTotal(), usd(event.price() * quantity), "Form total");

        // -- Step 4: Fill the customer form and confirm --
        eventPage.fillCustomer(ApiClient.CUSTOMER_NAME, ApiClient.CUSTOMER_EMAIL, ApiClient.CUSTOMER_PHONE)
                 .confirmBooking();
        String ref = eventPage.confirmedBookingRef();
        // Record the UI-created booking straight away so teardown deletes it even if a later assert fails
        Booking created = api.findBookingByRef(token, ref);
        createdBookingIds.add(created.id());
        System.out.printf("Booked through the UI: %s (#%d)%n", ref, created.id());

        // -- Step 5: The confirmation card shows a valid ref and the right details --
        String expectedPrefix = Character.toUpperCase(event.title().charAt(0)) + "-";
        Assert.assertTrue(ref.matches("[A-Z]-[A-Z0-9]{6}"), "Ref format [LETTER]-[6 alphanumerics]: " + ref);
        Assert.assertTrue(ref.startsWith(expectedPrefix), "Ref " + ref + " should start with " + expectedPrefix);
        String confirmation = eventPage.confirmationText();
        SoftAssert soft = new SoftAssert();
        soft.assertTrue(confirmation.contains(ApiClient.CUSTOMER_NAME), "customer on confirmation");
        soft.assertTrue(confirmation.contains("Tickets\n" + quantity) || confirmation.contains("Tickets " + quantity),
                "ticket count on confirmation: " + confirmation);
        soft.assertTrue(confirmation.contains(usd(event.price() * quantity)), "total on confirmation");
        soft.assertEquals(created.quantity(), quantity, "quantity stored by the API");
        soft.assertAll();

        // -- Step 6: "View My Bookings" lists the new booking above the older one, without a reload --
        BookingsPage bookings = eventPage.viewMyBookings();
        Assert.assertEquals(bookings.currentUrl(), Config.BASE_URL + "/bookings");
        String card = bookings.cardText(ref);
        Assert.assertTrue(card.contains(quantity + " tickets"), "New card should show " + quantity + " tickets: " + card);
        List<String> refs = bookings.refsInOrder();
        Assert.assertTrue(refs.indexOf(ref) >= 0 && refs.indexOf(ref) < refs.indexOf(older.bookingRef()),
                "New booking " + ref + " should be listed above older " + older.bookingRef() + ": " + refs);

        // -- Step 7: The event's seat count went down by exactly the booked quantity --
        Assert.assertEquals(new EventDetailPage(driver).open(event.id()).availableSeats(), seatsBefore - quantity,
                "Seats after booking " + quantity + " tickets");
    }

    @Test(description = "TC-004 + TC-512: cancelling from a list card removes only that card, keeps the user on the list and restores the seats")
    public void tc004_cancelFromCardKeepsOtherBookings() {
        Event event = api.findStaticEventWithSeats(token, 3);

        // -- Step 1: Two bookings on the same event --
        Booking toCancel = createApiBooking(event, 2);
        Booking toKeep   = createApiBooking(event, 1);
        int seatsBefore = api.availableSeats(token, event.id());

        // -- Step 2: Both cards are listed --
        BookingsPage bookings = new BookingsPage(driver).open();
        bookings.cardFor(toCancel.bookingRef());
        bookings.cardFor(toKeep.bookingRef());

        // -- Step 3: Cancel one from its card; the dialog names that booking and its seats --
        String dialog = bookings.openCancelDialog(toCancel.bookingRef());
        Assert.assertTrue(dialog.contains("Cancel this booking?"), "Dialog title missing: " + dialog);
        Assert.assertTrue(dialog.contains("This will cancel " + toCancel.bookingRef() + " and release 2 seat(s)"),
                "Dialog should name the ref and seat count: " + dialog);

        // -- Step 4: Confirm; check the toast straight away (it auto-dismisses after 4.5s) --
        bookings.confirmCancel();
        Assert.assertTrue(new Toast(driver).isShown("Booking cancelled successfully"), "Success toast should be visible");

        // -- Step 5: Still on the list, dialog closed, only the cancelled card is gone (no reload) --
        Assert.assertTrue(bookings.waitForDialogClosed(), "Dialog should close after cancelling");
        Assert.assertTrue(bookings.waitForCardGone(toCancel.bookingRef()), "Cancelled card should disappear");
        Assert.assertEquals(bookings.currentUrl(), Config.BASE_URL + "/bookings", "User stays on the list");
        Assert.assertTrue(bookings.cardText(toKeep.bookingRef()).contains(toKeep.bookingRef()), "Other booking stays listed");

        // -- Step 6: The backend agrees, and the 2 seats are released back to the event --
        Assert.assertEquals(api.bookingStatus(token, toCancel.id()), 404, "GET /bookings/:id after cancel");
        Assert.assertEquals(api.bookingStatus(token, toKeep.id()), 200, "Kept booking still exists");
        Assert.assertEquals(new EventDetailPage(driver).open(event.id()).availableSeats(), seatsBefore + 2,
                "Seats after cancelling a 2-ticket booking");
    }
}
