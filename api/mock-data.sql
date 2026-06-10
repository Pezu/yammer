SET search_path TO yammer;

DO $$
DECLARE
  c_orders       int       := 3000;          -- number of orders
  c_max_products int       := 5;             -- max distinct products per order
  t_start        timestamp := (current_date - 1) + time '13:00';   -- yesterday 13:00
  t_end          timestamp := localtimestamp;                       -- now
  t_pay_start    timestamp := (current_date - 1) + time '19:00';   -- payments start 19:00
  t_protocol     timestamp := (current_date - 1) + time '23:00';   -- protocol settle 23:00
  v_ops          uuid[];
  v_menus        uuid[];
  v_count        int;
  i              int;
  v_idx          int;
  v_op           uuid;
  v_menu         uuid;
  v_order_id     uuid;
  v_created      timestamp;
  v_nprod        int;
  v_pay_id       uuid;
  v_total_pay    int;
  v_row          int := 0;
  r              record;
BEGIN
  SELECT array_agg(id ORDER BY name), array_agg(menu_id ORDER BY name)
    INTO v_ops, v_menus
  FROM order_point;
  v_count := array_length(v_ops, 1);

  -- 1) Orders + random products (13:00 yesterday -> now)
  FOR i IN 1..c_orders LOOP
    v_idx     := 1 + floor(random() * v_count)::int;
    v_op      := v_ops[v_idx];
    v_menu    := v_menus[v_idx];
    v_created := t_start + (random() * extract(epoch from (t_end - t_start))) * interval '1 second';
    v_order_id := gen_random_uuid();

    INSERT INTO orders (id, order_point_id, created_by, created_at, status)
    VALUES (v_order_id, v_op, 'mock', v_created, 'DELIVERED');

    v_nprod := 1 + floor(random() * c_max_products)::int;     -- 1..max
    INSERT INTO order_item (id, order_id, menu_item_id, name, price, quantity)
    SELECT gen_random_uuid(), v_order_id, mi.id, mi.name, mi.price, 1 + floor(random() * 3)::int
    FROM (
      SELECT id, name, price FROM menu_item
      WHERE menu_id = v_menu AND orderable AND price IS NOT NULL
      ORDER BY random() LIMIT v_nprod
    ) mi;
  END LOOP;

  -- 2) Payments (cash/card) for non-protocol order points, evenly 19:00 -> now
  SELECT count(*) INTO v_total_pay
  FROM orders o JOIN order_point op ON op.id = o.order_point_id
  WHERE o.created_by = 'mock' AND op.protocol = false;

  FOR r IN
    SELECT o.id AS oid, o.order_point_id AS opid,
           COALESCE(sum(oi.price * oi.quantity), 0) AS amt, o.created_at AS cat
    FROM orders o
    JOIN order_point op ON op.id = o.order_point_id
    JOIN order_item oi ON oi.order_id = o.id
    WHERE o.created_by = 'mock' AND op.protocol = false
    GROUP BY o.id, o.order_point_id, o.created_at
    ORDER BY o.created_at
  LOOP
    v_row    := v_row + 1;
    v_pay_id := gen_random_uuid();
    INSERT INTO payment (id, order_point_id, amount, tip, method, created_by, created_at, fiscal_status, receipt_number)
    VALUES (
      v_pay_id, r.opid, r.amt,
      round((random() * 10)::numeric, 2),
      CASE WHEN random() < 0.5 THEN 'CASH' ELSE 'CARD' END,
      'mock',
      t_pay_start + ((v_row::numeric / GREATEST(v_total_pay, 1)) * extract(epoch from (t_end - t_pay_start))) * interval '1 second',
      'SUCCESS',
      'R' || lpad(v_row::text, 6, '0')
    );
    UPDATE order_item SET payment_id = v_pay_id WHERE order_id = r.oid;
  END LOOP;

  -- 3) Protocol settlement: one payment per protocol order point at 23:00, settling all its items
  FOR v_op IN SELECT id FROM order_point WHERE protocol = true LOOP
    v_pay_id := gen_random_uuid();
    INSERT INTO payment (id, order_point_id, amount, tip, method, created_by, created_at, fiscal_status)
    VALUES (v_pay_id, v_op, 0, 0, 'PROTOCOL', 'mock', t_protocol, 'PROTOCOL');

    UPDATE order_item oi SET payment_id = v_pay_id
    FROM orders o
    WHERE oi.order_id = o.id AND o.order_point_id = v_op AND o.created_by = 'mock';
  END LOOP;

  RAISE NOTICE 'Mock data: % orders, % cash/card payments, protocol settled.', c_orders, v_total_pay;
END $$;
