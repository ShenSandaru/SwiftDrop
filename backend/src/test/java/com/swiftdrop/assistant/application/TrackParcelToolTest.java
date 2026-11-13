package com.swiftdrop.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.PublicStatusEvent;
import com.swiftdrop.delivery.api.PublicTrackingView;

class TrackParcelToolTest {

    private ParcelQueries parcels;
    private TrackParcelTool tool;

    @BeforeEach
    void setUp() {
        parcels = mock(ParcelQueries.class);
        tool = new TrackParcelTool(parcels);
    }

    @Test
    @SuppressWarnings("unchecked")
    void describesStatusRouteAndHistoryOfAKnownParcel() {
        when(parcels.findPublicTrackingView("SD123")).thenReturn(Optional.of(new PublicTrackingView(
                "SD123", ParcelStatus.AT_HUB, "Colombo", "Kandy",
                List.of(new PublicStatusEvent(ParcelStatus.CREATED, Instant.parse("2026-11-12T08:00:00Z")),
                        new PublicStatusEvent(ParcelStatus.AT_HUB, Instant.parse("2026-11-12T12:00:00Z"),
                                "Colombo Central", null)))));

        Map<String, Object> result = tool.execute(Map.of("trackingNumber", " SD123 "));

        assertThat(result).containsEntry("found", true).containsEntry("status", "AT_HUB")
                .containsEntry("from", "Colombo").containsEntry("to", "Kandy");
        List<Map<String, Object>> history = (List<Map<String, Object>>) result.get("history");
        assertThat(history).hasSize(2);
        assertThat(history.get(0)).containsEntry("status", "CREATED").doesNotContainKey("hub");
        assertThat(history.get(1)).containsEntry("hub", "Colombo Central");
    }

    @Test
    void reportsAnUnknownTrackingNumberWithoutAnError() {
        when(parcels.findPublicTrackingView("NOPE")).thenReturn(Optional.empty());

        assertThat(tool.execute(Map.of("trackingNumber", "NOPE"))).containsEntry("found", false);
    }

    @Test
    void refusesMissingBlankOrOversizedInputWithoutQueryingDelivery() {
        assertThat(tool.execute(Map.of())).containsKey("error");
        assertThat(tool.execute(Map.of("trackingNumber", "  "))).containsKey("error");
        assertThat(tool.execute(Map.of("trackingNumber", 42))).containsKey("error");
        assertThat(tool.execute(Map.of("trackingNumber", "X".repeat(41)))).containsKey("error");

        verifyNoInteractions(parcels);
    }

    @Test
    void exposesNothingBeyondThePublicView() {
        when(parcels.findPublicTrackingView("SD1")).thenReturn(Optional.of(
                new PublicTrackingView("SD1", ParcelStatus.CREATED, "Galle", "Jaffna", List.of())));

        assertThat(tool.execute(Map.of("trackingNumber", "SD1")).keySet())
                .containsExactlyInAnyOrder("found", "trackingNumber", "status", "from", "to", "history");
    }
}
