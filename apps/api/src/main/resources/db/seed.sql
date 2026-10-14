INSERT OR IGNORE INTO profiles VALUES ('user_demo_1','Demo Shopper','demo@example.test','9000000000');
INSERT OR IGNORE INTO addresses (id,user_id,label,address_line,city,state,pin_code) VALUES
('addr_home','user_demo_1','Home','12 Test Street, Indiranagar','Bengaluru','Karnataka','560038'),
('addr_work','user_demo_1','Work','24 Demo Avenue, Koramangala','Bengaluru','Karnataka','560034');
INSERT OR IGNORE INTO products VALUES
('TRAINING-MAT','Everyday training mat','FOUNDATION','A little space to move, stretch and reset.','6 mm cushion · textured grip · sage',1299.0,8,'mat'),
('RESISTANCE-LOOP','Resistance loop set','STRENGTH','Small kit. A little more resistance.','3 resistance levels · woven fabric',499.0,12,'loops'),
('STEEL-BOTTLE','Daily steel bottle','RECOVERY','Keep your water close, wherever you move.','750 ml · stainless steel · mist blue',799.0,6,'bottle'),
('LIFTING-STRAPS','Cotton lifting straps','STRENGTH','A steady grip for your next set.','Cotton weave · sold as a pair',599.0,3,'straps'),
('RUN-CAP','Lightweight run cap','OUTDOORS','For early starts and one more kilometre.','Adjustable fit · breathable panels',699.0,5,'cap');
