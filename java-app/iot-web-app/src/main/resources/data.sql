-- Xóa dữ liệu cũ nếu có
DELETE FROM sensors;
DELETE FROM devices;

-- Thêm thiết bị (Devices)
INSERT INTO devices (id, device_name, device_type, location, status, created_at) VALUES
(1, 'Máy đo độ ẩm đất', 'Soil Sensor', 'Khu vực A - Nhà kính', 'Active', NOW()),
(2, 'Cảm biến nhiệt độ phòng', 'Temperature Sensor', 'Phòng khách', 'Active', NOW()),
(3, 'Hệ thống tưới nước tự động', 'Irrigation System', 'Sân sau', 'Active', NOW());

-- Thêm cảm biến (Sensors) cho thiết bị 1: Máy đo độ ẩm đất
INSERT INTO sensors (sensor_name, sensor_type, value, unit, timestamp, device_id) VALUES
('Độ ẩm đất', 'Humidity', 65.5, '%', NOW(), 1),
('Nhiệt độ đất', 'Temperature', 28.3, '°C', NOW(), 1);

-- Thêm cảm biến cho thiết bị 2: Cảm biến nhiệt độ phòng
INSERT INTO sensors (sensor_name, sensor_type, value, unit, timestamp, device_id) VALUES
('Nhiệt độ', 'Temperature', 24.5, '°C', NOW(), 2),
('Độ ẩm không khí', 'Humidity', 45.2, '%', NOW(), 2),
('Áp suất không khí', 'Pressure', 1013.25, 'hPa', NOW(), 2);

-- Thêm cảm biến cho thiết bị 3: Hệ thống tưới nước
INSERT INTO sensors (sensor_name, sensor_type, value, unit, timestamp, device_id) VALUES
('Tốc độ lưu lượng', 'Flow Rate', 12.5, 'L/min', NOW(), 3),
('Áp lực nước', 'Pressure', 2.8, 'bar', NOW(), 3);
