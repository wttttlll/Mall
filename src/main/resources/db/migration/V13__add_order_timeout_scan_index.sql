-- 订单超时扫描：按状态和创建时间定位待支付超时订单。
ALTER TABLE order_info
    ADD KEY idx_order_info_status_created (status, created_at);
