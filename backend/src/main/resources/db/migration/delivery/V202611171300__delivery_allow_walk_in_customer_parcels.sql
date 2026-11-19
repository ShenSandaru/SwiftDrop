-- Allow walk-in customer parcels booked at a hub counter by dispatchers/admins without a
-- registered customer account (customer_id IS NULL while source = 'CUSTOMER').
ALTER TABLE parcels DROP CONSTRAINT ck_parcels_owner;

ALTER TABLE parcels ADD CONSTRAINT ck_parcels_owner CHECK (
    (source = 'CUSTOMER' AND merchant_id IS NULL AND merchant_order_reference IS NULL)
    OR (source = 'MERCHANT' AND merchant_id IS NOT NULL
        AND customer_id IS NULL AND merchant_order_reference IS NOT NULL)
);

