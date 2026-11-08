package com.example.sqlite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocationMapsLinksTest {
    @Test public void acceptsCoordinateBoundariesAndRejectsInvalidValues() {
        assertTrue(LocationMapsLinks.isValidCoordinate(-90, -180));
        assertTrue(LocationMapsLinks.isValidCoordinate(90, 180));
        assertFalse(LocationMapsLinks.isValidCoordinate(90.01, 0));
        assertFalse(LocationMapsLinks.isValidCoordinate(0, Double.NaN));
    }

    @Test public void createsGoogleMapsSearchLinkFromSavedCoordinates() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=-30.604589%2C-71.204762",
                LocationMapsLinks.mapUrl(-30.6045891, -71.2047616));
    }

    @Test public void directionsLinkCanUseDeviceOriginAndSavedDestination() {
        assertEquals("https://www.google.com/maps/dir/?api=1&origin=-33.448900%2C-70.669300"
                        + "&destination=-30.604589%2C-71.204762",
                LocationMapsLinks.directionsUrl(-33.4489, -70.6693,
                        -30.6045891, -71.2047616));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidMapCoordinates() {
        LocationMapsLinks.mapUrl(91, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankAddressDirections() {
        LocationMapsLinks.directionsUrl("  ");
    }
}
