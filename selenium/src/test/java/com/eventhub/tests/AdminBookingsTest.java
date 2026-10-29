package com.eventhub.tests;

import com.eventhub.api.ApiClient;
import com.eventhub.api.ApiClient.Booking;
import com.eventhub.api.ApiClient.Event;
import com.eventhub.base.BaseTest;
import com.eventhub.pages.AdminBookingsPage;
import com.eventhub.pages.Toast;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Admin → Bookings: TC-010 from docs/test-strategy.md (table, detail modal, cancel from the table). */
public class AdminBookingsTest extends BaseTest {

    /** Same formatting the app uses for prices: USD, no decimals. */
    private static String usd(double amount) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.US);
        f.setMaximumFractionDigits(0);
        return f.format(amount);
    }

    @Test(description = "TC-010: admin bookings table lists the user's bookings, the modal shows one booking, and cancelling removes its row")
    public void tc010_adminTableModalAndCancel() {
        // -- Step 1: Two bookings with different quantities --
        Event event = api.findStaticEventWithSeats(token, 4);
        Booking toCancel = api.createBooking(token, event.id(), 3);
        createdBookingIds.add(toCancel.id());
        Booking toKeep = api.createBooking(token, event.id(), 1);
        createdBookingIds.add(toKeep.id());
        System.out.printf("Created bookings %s (#%d) and %s (#%d) for \"%s\"%n", toCancel.bookingRef(), toCancel.id(),
                toKeep.bookingRef(), toKeep.id(), event.title());

        // -- Step 2: The page header counts this user's bookings, and the table has the expected columns --
        AdminBookingsPage admin = new AdminBookingsPage(driver).open();
        int total = api.totalBookings(token);
        Assert.assertTrue(admin.waitForTotal(total), "Subtitle should read \"" + total + " total bookings\"");
        Assert.assertEquals(admin.columnHeaders(),
                List.of("REF", "CUSTOMER", "EVENT", "QTY", "TOTAL", "STATUS", "DATE", "ACTIONS"), "Table columns");

        // -- Step 3: The row for the booking shows its data --
        List<String> cells = admin.rowCells(toCancel.bookingRef());
        SoftAssert soft = new SoftAssert();
        soft.assertEquals(cells.get(0), toCancel.bookingRef(), "Ref");
        soft.assertTrue(cells.get(1).contains(ApiClient.CUSTOMER_NAME) && cells.get(1).contains(ApiClient.CUSTOMER_EMAIL),
                "Customer name and email: " + cells.get(1));
        soft.assertEquals(cells.get(2), event.title(), "Event");
        soft.assertEquals(cells.get(3), "3", "Qty");
        soft.assertEquals(cells.get(4), usd(event.price() * 3), "Total");
        soft.assertEquals(cells.get(5), "confirmed", "Status");
        soft.assertAll();

        // -- Step 4: "View" opens a modal for that booking --
        String modalTitle = admin.openDetails(toCancel.bookingRef());
        Assert.assertEquals(modalTitle, "Booking — " + toCancel.bookingRef(), "Modal title");
        String modal = admin.modalText();
        soft = new SoftAssert();
        soft.assertTrue(modal.contains(event.title()), "event title in modal");
        soft.assertTrue(modal.contains(ApiClient.CUSTOMER_PHONE), "customer phone in modal");
        soft.assertTrue(modal.contains(usd(event.price() * 3)), "total in modal");
        soft.assertAll();
        admin.closeModal();

        // -- Step 5: Cancel from the table and confirm; check the toast straight away --
        String dialog = admin.openCancelDialog(toCancel.bookingRef());
        Assert.assertTrue(dialog.contains("Cancel this booking?"), "Dialog title missing: " + dialog);
        admin.confirmCancel();
        Assert.assertTrue(new Toast(driver).isShown("Booking cancelled"), "\"Booking cancelled\" toast should be visible");

        // -- Step 6: Only that row disappears, the count drops by one, and the API agrees --
        Assert.assertTrue(admin.waitForRowGone(toCancel.bookingRef()), "Cancelled row should disappear");
        Assert.assertTrue(admin.isRowShown(toKeep.bookingRef()), "Other booking's row stays");
        Assert.assertTrue(admin.waitForTotal(total - 1), "Subtitle should drop to " + (total - 1) + " total bookings");
        Assert.assertEquals(api.bookingStatus(token, toCancel.id()), 404, "GET /bookings/:id after cancel");
    }
}
