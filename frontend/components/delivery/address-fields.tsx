"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Building2, Check, ChevronDown, MapPin, Search } from "lucide-react";
import { cn } from "cn";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { AddressInput, Zone } from "@/lib/delivery";
import type { District } from "@/lib/districts";

/** Native select styled like the shadcn Input, kept for other delivery forms that import it. */
export const SELECT_CLASS =
  "h-8 w-full min-w-0 rounded-lg border border-input bg-transparent px-2.5 py-1 text-base outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive md:text-sm dark:bg-input/30";

export const EMPTY_ADDRESS: AddressInput = {
  contactName: "",
  phone: "",
  line1: "",
  line2: "",
  city: "",
  zoneId: "",
  postalCode: "",
};

export interface AddressFieldsProps {
  /** Prefix for element IDs and the legend, for example "pickup". */
  id: string;
  legend?: string;
  description?: string;
  value: AddressInput;
  onChange: (value: AddressInput) => void;
  zones: Zone[];
  /** Cataloged Sri Lankan districts from admin (/api/v1/districts). */
  districts?: District[];
  /** Backend field names that failed validation, for example "pickup.phone". */
  invalidFields?: string[];
  disabled?: boolean;
}

export function zonesForDistrict(district: District, zones: Zone[]): Zone[] {
  const prefix = district.code.toUpperCase();
  const distName = district.name.trim().toLowerCase();
  const postalCodes = new Set(district.zones.map((pz) => pz.code.toUpperCase()));
  const postalNames = new Set(district.zones.map((pz) => pz.name.trim().toLowerCase()));

  return zones.filter((z) => {
    const zCode = z.code.toUpperCase();
    const zName = z.name.trim().toLowerCase();
    return (
      zCode === prefix ||
      zCode.startsWith(`${prefix}-`) ||
      zCode.startsWith(`${prefix}_`) ||
      postalCodes.has(zCode) ||
      postalNames.has(zName) ||
      zName === distName ||
      zName.startsWith(`${distName} `) ||
      zName.startsWith(`${distName}-`)
    );
  });
}

/** One pickup or drop-off address; limits match the backend (contract §3.2). */
export function AddressFields({
  id,
  legend,
  description,
  value,
  onChange,
  zones,
  districts,
  invalidFields = [],
  disabled,
}: AddressFieldsProps) {
  const [selectedDistrictCode, setSelectedDistrictCode] = useState<string>("");
  const hasDistrictCatalog = districts !== undefined;
  const districtList = useMemo(() => districts ?? [], [districts]);

  const set = (field: keyof AddressInput) => (event: { target: { value: string } }) =>
    onChange({ ...value, [field]: event.target.value });
  const invalid = (field: string) => invalidFields.includes(`${id}.${field}`) || undefined;

  const activeDistrict = useMemo(() => {
    if (selectedDistrictCode) {
      return districtList.find((d) => d.code === selectedDistrictCode) ?? null;
    }
    if (value.city.trim()) {
      return (
        districtList.find(
          (d) => d.name.toLowerCase() === value.city.trim().toLowerCase()
        ) ?? null
      );
    }
    return null;
  }, [districtList, selectedDistrictCode, value.city]);

  // Filter zones to the selected district when chosen
  const districtZones = useMemo(() => {
    if (!activeDistrict) return hasDistrictCatalog ? [] : zones;
    return zonesForDistrict(activeDistrict, zones);
  }, [activeDistrict, hasDistrictCatalog, zones]);

  function handleDistrictSelect(district: District) {
    setSelectedDistrictCode(district.code);
    const matchingZones = zonesForDistrict(district, zones);
    const currentStillMatches = matchingZones.some((z) => z.id === value.zoneId);
    const nextZone =
      matchingZones.length === 1
        ? matchingZones[0]
        : currentStillMatches
          ? matchingZones.find((z) => z.id === value.zoneId) ?? null
          : null;

    const matchedPostalZone = nextZone
      ? district.zones.find((pz) => pz.code.toUpperCase() === nextZone.code.toUpperCase())
      : undefined;

    onChange({
      ...value,
      city: district.name,
      postalCode: matchedPostalZone?.postalCode || district.postalCode || "",
      zoneId: nextZone?.id ?? "",
    });
  }

  function handleZoneSelect(zone: Zone) {
    const zPrefix = zone.code.split(/[-_]/)[0].toUpperCase();
    const matchedDistrict =
      activeDistrict ??
      districtList.find((d) => d.code.toUpperCase() === zPrefix) ??
      null;
    const matchedPostalZone = matchedDistrict?.zones.find(
      (pz) => pz.code.toUpperCase() === zone.code.toUpperCase()
    );

    if (matchedDistrict) {
      setSelectedDistrictCode(matchedDistrict.code);
    }

    onChange({
      ...value,
      zoneId: zone.id,
      city: matchedDistrict?.name || value.city || zone.name.split(" - ")[0].trim(),
      postalCode:
        matchedPostalZone?.postalCode ||
        value.postalCode ||
        matchedDistrict?.postalCode ||
        "",
    });
  }

  return (
    <fieldset className="space-y-4" disabled={disabled}>
      {(legend || description) && (
        <div className="space-y-0.5">
          {legend && <legend className="font-heading text-sm font-semibold text-foreground">{legend}</legend>}
          {description && (
            <p className="text-xs text-muted-foreground">{description}</p>
          )}
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-12">
        <div className="space-y-1.5 sm:col-span-6">
          <Label htmlFor={`${id}-name`} className="text-xs font-semibold text-foreground">
            Contact name
          </Label>
          <Input
            id={`${id}-name`}
            required
            maxLength={200}
            autoComplete="name"
            placeholder="Full name"
            value={value.contactName}
            onChange={set("contactName")}
            aria-invalid={invalid("contactName")}
            className="h-9"
          />
        </div>

        <div className="space-y-1.5 sm:col-span-6">
          <Label htmlFor={`${id}-phone`} className="text-xs font-semibold text-foreground">
            Phone
          </Label>
          <Input
            id={`${id}-phone`}
            required
            type="tel"
            inputMode="tel"
            placeholder="+94771234567"
            pattern="\+[1-9][0-9]{7,14}"
            title="International format, for example +94771234567"
            autoComplete="tel"
            value={value.phone}
            onChange={set("phone")}
            aria-invalid={invalid("phone")}
            className="h-9 font-mono"
          />
        </div>

        <div className="space-y-1.5 sm:col-span-12">
          <Label htmlFor={`${id}-line1`} className="text-xs font-semibold text-foreground">
            Address line 1
          </Label>
          <Input
            id={`${id}-line1`}
            required
            maxLength={200}
            autoComplete="address-line1"
            placeholder="Street address, house or building number"
            value={value.line1}
            onChange={set("line1")}
            aria-invalid={invalid("line1")}
            className="h-9"
          />
        </div>

        <div className="space-y-1.5 sm:col-span-12">
          <Label htmlFor={`${id}-line2`} className="text-xs font-semibold text-foreground">
            Address line 2 <span className="font-normal text-muted-foreground">(optional)</span>
          </Label>
          <Input
            id={`${id}-line2`}
            maxLength={200}
            autoComplete="address-line2"
            placeholder="Apartment, floor, or landmark"
            value={value.line2 ?? ""}
            onChange={set("line2")}
            aria-invalid={invalid("line2")}
            className="h-9"
          />
        </div>

        {/* Step 1: Select District -> Step 2: Select Delivery Zone -> Auto-filled Postal Code */}
        <div className="space-y-1.5 sm:col-span-5">
          <Label htmlFor={`${id}-district`} className="text-xs font-semibold text-foreground">
            District
          </Label>
          {hasDistrictCatalog ? (
            <DistrictCombobox
              id={`${id}-district`}
              districts={districtList}
              selectedCode={activeDistrict?.code ?? ""}
              onSelect={handleDistrictSelect}
              invalid={Boolean(invalid("city"))}
              disabled={disabled}
            />
          ) : (
            <Input
              id={`${id}-district`}
              required
              maxLength={100}
              autoComplete="address-level2"
              placeholder="e.g. Colombo"
              value={value.city}
              onChange={set("city")}
              aria-invalid={invalid("city")}
              className="h-9"
            />
          )}
        </div>

        <div className="space-y-1.5 sm:col-span-4">
          <Label htmlFor={`${id}-zone`} className="text-xs font-semibold text-foreground">
            Delivery zone
          </Label>
          <ZoneCombobox
            id={`${id}-zone`}
            zones={districtZones}
            hasDistrict={Boolean(activeDistrict) || !hasDistrictCatalog}
            value={value.zoneId}
            onSelect={handleZoneSelect}
            invalid={Boolean(invalid("zoneId"))}
            disabled={disabled}
          />
        </div>

        <div className="space-y-1.5 sm:col-span-3">
          <Label htmlFor={`${id}-postal`} className="text-xs font-semibold text-foreground">
            Postal code <span className="font-normal text-muted-foreground">(optional)</span>
          </Label>
          <Input
            id={`${id}-postal`}
            maxLength={10}
            autoComplete="postal-code"
            placeholder="Auto-filled"
            value={value.postalCode ?? ""}
            onChange={set("postalCode")}
            aria-invalid={invalid("postalCode")}
            className="h-9 font-mono"
          />
        </div>
      </div>
    </fieldset>
  );
}

function DistrictCombobox({
  id,
  districts,
  selectedCode,
  onSelect,
  invalid,
  disabled,
}: {
  id: string;
  districts: District[];
  selectedCode: string;
  onSelect: (district: District) => void;
  invalid?: boolean;
  disabled?: boolean;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [open, setOpen] = useState(false);
  const [openUpward, setOpenUpward] = useState(false);
  const [search, setSearch] = useState("");

  const selected = useMemo(
    () => districts.find((d) => d.code === selectedCode) ?? null,
    [districts, selectedCode]
  );

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return districts;
    return districts.filter(
      (d) =>
        d.name.toLowerCase().includes(q) ||
        d.code.toLowerCase().includes(q) ||
        d.province.toLowerCase().includes(q)
    );
  }, [districts, search]);

  useEffect(() => {
    if (!open) return;

    function handlePointerDown(event: MouseEvent | TouchEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setOpen(false);
      }
    }

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("touchstart", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("touchstart", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  function toggleOpen() {
    if (disabled || districts.length === 0) return;
    if (!open && containerRef.current) {
      const rect = containerRef.current.getBoundingClientRect();
      const spaceBelow = window.innerHeight - rect.bottom;
      const spaceAbove = rect.top;
      setOpenUpward(spaceBelow < 280 && spaceAbove > spaceBelow);
    }
    setOpen((prev) => !prev);
  }

  return (
    <div ref={containerRef} className="relative">
      <input
        tabIndex={-1}
        aria-hidden="true"
        aria-invalid={invalid || undefined}
        required
        value={selectedCode}
        onChange={() => {}}
        className="sr-only"
      />
      <button
        id={id}
        type="button"
        disabled={disabled || districts.length === 0}
        aria-expanded={open}
        onClick={toggleOpen}
        className={cn(
          "flex h-9 w-full items-center justify-between gap-2 rounded-lg border border-input bg-transparent px-2.5 py-1 text-left text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-input/30",
          invalid && "border-destructive",
          !selected && "text-muted-foreground"
        )}
      >
        {selected ? (
          <span className="flex min-w-0 items-center gap-1.5 truncate">
            <span className="shrink-0 rounded bg-muted px-1.5 py-0.5 font-mono text-[10px] font-semibold uppercase text-foreground">
              {selected.code}
            </span>
            <span className="truncate font-medium text-foreground">{selected.name}</span>
          </span>
        ) : (
          <span className="truncate">
            {districts.length === 0 ? "No active hub districts" : "Select a district..."}
          </span>
        )}
        <ChevronDown
          className={cn("size-4 shrink-0 opacity-50 transition-transform", open && "rotate-180")}
        />
      </button>

      {open && (
        <div
          className={cn(
            "absolute z-50 flex w-full min-w-[260px] flex-col overflow-hidden rounded-xl border border-border bg-popover text-popover-foreground shadow-lg animate-in fade-in-0 zoom-in-95 duration-100",
            openUpward ? "bottom-full mb-1.5" : "top-full mt-1.5"
          )}
        >
          <div className="shrink-0 border-b border-border/60 bg-popover p-1.5">
            <div className="flex items-center gap-1.5 rounded-lg border border-input bg-background px-2 py-1.5">
              <Search className="size-3.5 shrink-0 text-muted-foreground" />
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search district or province..."
                className="w-full bg-transparent text-xs text-foreground outline-none placeholder:text-muted-foreground"
                autoFocus
              />
            </div>
          </div>

          <div className="max-h-56 overflow-y-auto p-1">
            {filtered.length === 0 ? (
              <p className="p-3 text-center text-xs text-muted-foreground">No matching district.</p>
            ) : (
              filtered.map((district) => {
                const isSelected = district.code === selectedCode;
                return (
                  <button
                    key={district.code}
                    type="button"
                    onClick={() => {
                      onSelect(district);
                      setOpen(false);
                      setSearch("");
                    }}
                    className={cn(
                      "flex w-full items-center justify-between gap-2 rounded-lg px-2.5 py-1.5 text-left text-xs transition-colors",
                      isSelected
                        ? "bg-accent font-medium text-accent-foreground"
                        : "hover:bg-accent/70 hover:text-accent-foreground"
                    )}
                  >
                    <span className="flex min-w-0 items-center gap-2">
                      <Building2 className="size-3.5 shrink-0 text-primary" />
                      <span className="truncate font-medium text-foreground">{district.name}</span>
                      <span className="shrink-0 font-mono text-[10px] text-muted-foreground">
                        ({district.code})
                      </span>
                    </span>
                    {isSelected && <Check className="size-3.5 shrink-0 text-primary" />}
                  </button>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function ZoneCombobox({
  id,
  zones,
  hasDistrict,
  value,
  onSelect,
  invalid,
  disabled,
}: {
  id: string;
  zones: Zone[];
  hasDistrict: boolean;
  value: string;
  onSelect: (zone: Zone) => void;
  invalid?: boolean;
  disabled?: boolean;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [open, setOpen] = useState(false);
  const [openUpward, setOpenUpward] = useState(false);
  const [search, setSearch] = useState("");

  const selectedZone = useMemo(
    () => zones.find((z) => z.id === value) ?? null,
    [zones, value]
  );

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return zones;
    return zones.filter(
      (z) =>
        z.name.toLowerCase().includes(q) ||
        z.code.toLowerCase().includes(q)
    );
  }, [zones, search]);

  useEffect(() => {
    if (!open) return;

    function handlePointerDown(event: MouseEvent | TouchEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setOpen(false);
      }
    }

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("touchstart", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("touchstart", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  function toggleOpen() {
    if (disabled || !hasDistrict || zones.length === 0) return;
    if (!open && containerRef.current) {
      const rect = containerRef.current.getBoundingClientRect();
      const spaceBelow = window.innerHeight - rect.bottom;
      const spaceAbove = rect.top;
      setOpenUpward(spaceBelow < 280 && spaceAbove > spaceBelow);
    }
    setOpen((prev) => !prev);
  }

  const placeholderText = !hasDistrict
    ? "Select district first..."
    : zones.length > 0
      ? "Choose a delivery zone..."
      : "No active zones in district";

  return (
    <div ref={containerRef} className="relative">
      <input
        tabIndex={-1}
        aria-hidden="true"
        aria-invalid={invalid || undefined}
        required
        value={value}
        onChange={() => {}}
        className="sr-only"
      />
      <button
        id={id}
        type="button"
        disabled={disabled || !hasDistrict || zones.length === 0}
        aria-expanded={open}
        onClick={toggleOpen}
        className={cn(
          "flex h-9 w-full items-center justify-between gap-2 rounded-lg border border-input bg-transparent px-2.5 py-1 text-left text-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-input/30",
          invalid && "border-destructive",
          !selectedZone && "text-muted-foreground"
        )}
      >
        {selectedZone ? (
          <span className="flex min-w-0 items-center gap-1.5 truncate">
            <span className="shrink-0 rounded bg-muted px-1.5 py-0.5 font-mono text-[10px] font-semibold uppercase text-foreground">
              {selectedZone.code}
            </span>
            <span className="truncate font-medium text-foreground">{selectedZone.name}</span>
          </span>
        ) : (
          <span className="truncate">{placeholderText}</span>
        )}
        <ChevronDown
          className={cn("size-4 shrink-0 opacity-50 transition-transform", open && "rotate-180")}
        />
      </button>

      {open && (
        <div
          className={cn(
            "absolute z-50 flex w-full min-w-[260px] flex-col overflow-hidden rounded-xl border border-border bg-popover text-popover-foreground shadow-lg animate-in fade-in-0 zoom-in-95 duration-100",
            openUpward ? "bottom-full mb-1.5" : "top-full mt-1.5"
          )}
        >
          <div className="shrink-0 border-b border-border/60 bg-popover p-1.5">
            <div className="flex items-center gap-1.5 rounded-lg border border-input bg-background px-2 py-1.5">
              <Search className="size-3.5 shrink-0 text-muted-foreground" />
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search zone or code..."
                className="w-full bg-transparent text-xs text-foreground outline-none placeholder:text-muted-foreground"
                autoFocus
              />
            </div>
          </div>

          <div className="max-h-56 overflow-y-auto p-1">
            {filtered.length === 0 ? (
              <p className="p-3 text-center text-xs text-muted-foreground">
                No matching delivery zone.
              </p>
            ) : (
              filtered.map((zone) => {
                const isSelected = zone.id === value;
                return (
                  <button
                    key={zone.id}
                    type="button"
                    onClick={() => {
                      onSelect(zone);
                      setOpen(false);
                      setSearch("");
                    }}
                    className={cn(
                      "flex w-full items-center justify-between gap-2 rounded-lg px-2.5 py-1.5 text-left text-xs transition-colors",
                      isSelected
                        ? "bg-accent font-medium text-accent-foreground"
                        : "hover:bg-accent/70 hover:text-accent-foreground"
                    )}
                  >
                    <span className="flex min-w-0 items-center gap-2">
                      <MapPin className="size-3.5 shrink-0 text-primary" />
                      <span className="truncate font-medium text-foreground">{zone.name}</span>
                      <span className="shrink-0 font-mono text-[10px] text-muted-foreground">
                        ({zone.code})
                      </span>
                    </span>
                    {isSelected && <Check className="size-3.5 shrink-0 text-primary" />}
                  </button>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
}
