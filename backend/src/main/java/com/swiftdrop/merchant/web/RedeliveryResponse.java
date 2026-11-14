package com.swiftdrop.merchant.web;

import java.util.UUID;

/** Answer to an accepted manual redelivery: the record is queued for the sender in a new series. */
record RedeliveryResponse(UUID deliveryId, int series, String status) {
}
