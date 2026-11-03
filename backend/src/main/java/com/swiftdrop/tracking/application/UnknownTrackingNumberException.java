package com.swiftdrop.tracking.application;

/** No parcel has this tracking number, or it is malformed; answered like the public read (404). */
public class UnknownTrackingNumberException extends RuntimeException {

    UnknownTrackingNumberException() {
        super("Parcel not found");
    }
}
