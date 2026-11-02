package com.swiftdrop.delivery.web;

import com.swiftdrop.delivery.domain.Parcel;

/** An amount as a string such as {@code "650.00"}, so clients never round it (contract §3.1). */
record MoneyResponse(String amount, String currency) {

    static MoneyResponse feeOf(Parcel parcel) {
        return new MoneyResponse(parcel.getFeeAmount().toPlainString(), parcel.getFeeCurrency());
    }
}
