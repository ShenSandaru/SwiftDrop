package com.swiftdrop.assistant.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.swiftdrop.pricing.api.ServiceZone;
import com.swiftdrop.pricing.api.ZoneDirectory;

/** The areas SwiftDrop delivers between. */
@Component
class ListServiceAreasTool implements AssistantTool {

    static final String NAME = "list_service_areas";

    private final ZoneDirectory zones;

    ListServiceAreasTool(ZoneDirectory zones) {
        this.zones = zones;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(NAME, "List the areas (cities) SwiftDrop delivers to and from.",
                Map.of("type", "object", "properties", Map.of()));
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        List<String> names = zones.findActiveZones().stream().map(ServiceZone::name).toList();
        return Map.of("areas", names);
    }
}
