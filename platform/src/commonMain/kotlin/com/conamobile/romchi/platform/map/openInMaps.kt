package com.conamobile.romchi.platform.map
/**
 * Open the given geo-location in a navigation app.
 *
 * @param latitude  latitude of the destination
 * @param longitude longitude of the destination
 * @param label     a human-readable name (shown in the map UI)
 */
expect fun openInMaps(latitude: Double, longitude: Double, label: String)