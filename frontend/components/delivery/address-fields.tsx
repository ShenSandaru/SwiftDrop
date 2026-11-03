"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { AddressInput, Zone } from "@/lib/delivery";

/** Native select styled like the shadcn Input, so no extra component package is needed. */
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
  legend: string;
  value: AddressInput;
  onChange: (value: AddressInput) => void;
  zones: Zone[];
  /** Backend field names that failed validation, for example "pickup.phone". */
  invalidFields?: string[];
  disabled?: boolean;
}

/** One pickup or drop-off address; limits match the backend (contract §3.2). */
export function AddressFields({ id, legend, value, onChange, zones, invalidFields = [], disabled }: AddressFieldsProps) {
  const set = (field: keyof AddressInput) => (event: { target: { value: string } }) =>
    onChange({ ...value, [field]: event.target.value });
  const invalid = (field: string) => invalidFields.includes(`${id}.${field}`) || undefined;

  return (
    <fieldset className="space-y-3" disabled={disabled}>
      <legend className="text-sm font-semibold text-foreground">{legend}</legend>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-1">
          <Label htmlFor={`${id}-name`}>Contact name</Label>
          <Input id={`${id}-name`} required maxLength={200} autoComplete="name" value={value.contactName}
            onChange={set("contactName")} aria-invalid={invalid("contactName")} />
        </div>
        <div className="space-y-1">
          <Label htmlFor={`${id}-phone`}>Phone</Label>
          <Input id={`${id}-phone`} required type="tel" inputMode="tel" placeholder="+94771234567"
            pattern="\+[1-9][0-9]{7,14}" title="International format, for example +94771234567" autoComplete="tel"
            value={value.phone} onChange={set("phone")} aria-invalid={invalid("phone")} />
        </div>
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor={`${id}-line1`}>Address line 1</Label>
          <Input id={`${id}-line1`} required maxLength={200} autoComplete="address-line1" value={value.line1}
            onChange={set("line1")} aria-invalid={invalid("line1")} />
        </div>
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor={`${id}-line2`}>Address line 2 <span className="text-muted-foreground">(optional)</span></Label>
          <Input id={`${id}-line2`} maxLength={200} autoComplete="address-line2" value={value.line2 ?? ""}
            onChange={set("line2")} aria-invalid={invalid("line2")} />
        </div>
        <div className="space-y-1">
          <Label htmlFor={`${id}-city`}>City</Label>
          <Input id={`${id}-city`} required maxLength={100} autoComplete="address-level2" value={value.city}
            onChange={set("city")} aria-invalid={invalid("city")} />
        </div>
        <div className="space-y-1">
          <Label htmlFor={`${id}-zone`}>Delivery zone</Label>
          <select id={`${id}-zone`} required className={SELECT_CLASS} value={value.zoneId} onChange={set("zoneId")}
            aria-invalid={invalid("zoneId")}>
            <option value="" disabled>
              {zones.length ? "Choose a zone" : "No zones available"}
            </option>
            {zones.map((zone) => (
              <option key={zone.id} value={zone.id}>
                {zone.name}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <Label htmlFor={`${id}-postal`}>Postal code <span className="text-muted-foreground">(optional)</span></Label>
          <Input id={`${id}-postal`} maxLength={10} autoComplete="postal-code" value={value.postalCode ?? ""}
            onChange={set("postalCode")} aria-invalid={invalid("postalCode")} />
        </div>
      </div>
    </fieldset>
  );
}
