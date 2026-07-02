package com.duc.iot.iot_web_app.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.duc.iot.iot_web_app.model.Device;
import com.duc.iot.iot_web_app.model.Sensor;
import com.duc.iot.iot_web_app.model.SensorReading;
import com.duc.iot.iot_web_app.repository.DeviceRepository;
import com.duc.iot.iot_web_app.repository.FirmwareVersionRepository;
import com.duc.iot.iot_web_app.repository.SensorReadingRepository;
import com.duc.iot.iot_web_app.repository.SensorRepository;

@Controller
public class IotController {

    @Autowired
    private SensorRepository sensorRepository;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private SensorReadingRepository readingRepository;
    @Autowired
    private FirmwareVersionRepository firmwareRepository;

    // --- TRANG WEB ---
    @GetMapping("/")
    public String home(Model model) {
        List<Device> devices = deviceRepository.findAll();
        long onlineCount = devices.stream().filter(d -> d.getStatus() == Device.Status.ONLINE).count();
        model.addAttribute("devices", devices);
        model.addAttribute("totalDevices", devices.size());
        model.addAttribute("onlineCount", onlineCount);
        model.addAttribute("offlineCount", devices.size() - onlineCount);
        model.addAttribute("firmwares", firmwareRepository.findAll());
        return "dashboard";
    }

    @GetMapping("/devices")
    public String devices(Model model) {
        model.addAttribute("devices", deviceRepository.findAll());
        model.addAttribute("firmwares", firmwareRepository.findAll());
        return "devices";
    }

    @GetMapping("/dashboard/{id}")
    public String dashboard(@PathVariable Long id, Model model) {
        Optional<Device> deviceOpt = deviceRepository.findById(id);
        if (deviceOpt.isEmpty()) {
            return "redirect:/devices";
        }
        Device device = deviceOpt.get();
        List<Sensor> sensors = device.getSensors() != null ? device.getSensors() : new ArrayList<>();
        Map<String, Double> latestReadings = new LinkedHashMap<>();
        for (Sensor s : sensors) {
            SensorReading reading = readingRepository
                    .findFirstBySensorIdOrderByRecordedAtDesc(s.getId());
            if (reading != null) {
                latestReadings.put(s.getSensorName(), reading.getRawValue());
            }
        }
        
        // Lấy dữ liệu thật cho biểu đồ Activity
        List<String> chartLabels = new ArrayList<>();
        List<Double> chartData = new ArrayList<>();
        
        if (!sensors.isEmpty()) {
            // Dùng sensor đầu tiên (thường là Nhiệt độ) làm mốc cho biểu đồ mini
            Sensor firstSensor = sensors.get(0);
            List<SensorReading> recentReadings = readingRepository.findTop50BySensorIdOrderByRecordedAtDesc(firstSensor.getId());
            // Đảo ngược để có thứ tự thời gian tăng dần
            for (int i = recentReadings.size() - 1; i >= 0; i--) {
                SensorReading r = recentReadings.get(i);
                chartLabels.add(r.getRecordedAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                chartData.add(r.getRawValue());
            }
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            model.addAttribute("chartLabels", mapper.writeValueAsString(chartLabels));
            model.addAttribute("chartData", mapper.writeValueAsString(chartData));
        } catch (Exception e) {
            model.addAttribute("chartLabels", "[]");
            model.addAttribute("chartData", "[]");
        }

        model.addAttribute("device", device);
        model.addAttribute("sensors", sensors);
        model.addAttribute("latestReadings", latestReadings);
        return "home";
    }

    @PostMapping("/devices/add")
    public String addDevice(@RequestParam String deviceName,
            @RequestParam String category,
            @RequestParam String location,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            Device device = new Device();
            String generatedToken = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            device.setDeviceUid(generatedToken);
            device.setDeviceName(deviceName);
            device.setCategory(category);
            device.setLocation(location);
            device.setStatus(Device.Status.OFFLINE);
            device.setCreatedAt(LocalDateTime.now());
            deviceRepository.save(device);
            redirectAttributes.addFlashAttribute("success", "Device " + deviceName + " added successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to add device.");
        }
        return "redirect:/devices";
    }

    @PostMapping("/devices/delete/{id}")
    public String deleteDevice(@PathVariable Long id, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            deviceRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Device deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete device.");
        }
        return "redirect:/devices";
    }

    @PostMapping("/devices/edit/{id}")
    public String editDevice(@PathVariable Long id,
            @RequestParam String deviceName,
            @RequestParam String category,
            @RequestParam String location,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            Optional<Device> deviceOpt = deviceRepository.findById(id);
            if (deviceOpt.isPresent()) {
                Device device = deviceOpt.get();
                device.setDeviceName(deviceName);
                device.setCategory(category);
                device.setLocation(location);
                deviceRepository.save(device);
                redirectAttributes.addFlashAttribute("success", "Device updated successfully.");
            } else {
                redirectAttributes.addFlashAttribute("error", "Device not found.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update device.");
        }
        return "redirect:/devices";
    }

    @GetMapping("/analytics/{id}")
    public String analytics(@PathVariable Long id, Model model) {
        Optional<Device> deviceOpt = deviceRepository.findById(id);
        if (deviceOpt.isEmpty()) {
            return "redirect:/devices";
        }
        Device device = deviceOpt.get();
        List<Sensor> sensors = device.getSensors() != null ? device.getSensors() : new ArrayList<>();

        Map<String, List<Object[]>> historicalData = new LinkedHashMap<>();
        for (Sensor s : sensors) {
            List<SensorReading> readings = readingRepository.findTop200BySensorIdOrderByRecordedAtDesc(s.getId());
            List<Object[]> sensorData = new ArrayList<>();
            // ApexCharts needs oldest first
            for (int i = readings.size() - 1; i >= 0; i--) {
                SensorReading r = readings.get(i);
                long timestamp = r.getRecordedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                sensorData.add(new Object[]{timestamp, r.getRawValue()});
            }
            historicalData.put(s.getSensorName(), sensorData);
        }
        try {
            model.addAttribute("historicalDataJson", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(historicalData));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            model.addAttribute("historicalDataJson", "{}");
        }

        model.addAttribute("device", device);
        model.addAttribute("sensors", sensors);
        return "analytics";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("firmwares", firmwareRepository.findAll());
        model.addAttribute("devices", deviceRepository.findAll());
        return "settings";
    }

    // --- APIs ---
    @GetMapping("/api/status")
    @ResponseBody
    public String checkStatus() {
        return "IoT System Ready!";
    }

    @Autowired
    private com.duc.iot.iot_web_app.service.MqttService mqttService;

    @PostMapping("/api/device/{id}/control")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> controlDevice(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Optional<Device> deviceOpt = deviceRepository.findById(id);
        if (deviceOpt.isPresent()) {
            Device device = deviceOpt.get();
            // Topic expected by ESP32 to receive commands
            String topic = "iot/device/control/" + device.getDeviceUid();
            
            try {
                String jsonPayload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
                mqttService.publishCommand(topic, jsonPayload);
                return org.springframework.http.ResponseEntity.ok().body(Map.of("status", "success", "message", "Command sent"));
            } catch (com.fasterxml.jackson.core.JsonProcessingException | RuntimeException e) {
                return org.springframework.http.ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
            }
        }
        return org.springframework.http.ResponseEntity.notFound().build();
    }

    @GetMapping("/api/devices/all")
    @ResponseBody
    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    @PostMapping("/api/v1/{deviceToken}/telemetry")
    @ResponseBody
    @org.springframework.transaction.annotation.Transactional
    public String receiveTelemetry(@PathVariable String deviceToken, @RequestBody Map<String, Object> data) {
        Optional<Device> deviceOpt = deviceRepository.findByDeviceUid(deviceToken);
        if (deviceOpt.isEmpty()) {
            return "Error: Invalid Access Token!";
        }
        Device device = deviceOpt.get();
        Long deviceId = device.getId();
        LocalDateTime payloadTime = LocalDateTime.now();
        List<SensorReading> newReadings = new ArrayList<>();

        data.forEach((key, value) -> {
            if (value == null) {
                return;
            }

            switch (key) {
                case "hardware_version" ->
                    device.setHardwareVersion(value.toString());
                case "free_heap" ->
                    device.setFreeHeap(((Number) value).intValue());
                case "wifi_rssi" ->
                    device.setWifiRssi(((Number) value).intValue());
                case "uptime" ->
                    device.setUptime(((Number) value).longValue());
                case "reboot_count" ->
                    device.setRebootCount(((Number) value).intValue());
                case "last_reboot_reason" ->
                    device.setLastRebootReason(value.toString());
                case "mqtt_connected" ->
                    device.setMqttConnected(Boolean.valueOf(String.valueOf(value)));
                default -> {
                    // Xử lý sensor readings
                    double numVal;
                    try {
                        numVal = value instanceof Number
                                ? ((Number) value).doubleValue()
                                : Double.parseDouble((String) value);
                    } catch (NumberFormatException | ClassCastException e) {
                        return; // bỏ qua chuỗi không phải số
                    }

                    Sensor sensor = device.getSensors().stream()
                            .filter(s -> s.getSensorName().equals(key))
                            .findFirst()
                            .orElseGet(() -> {
                                Sensor s = new Sensor();
                                s.setSensorName(key);
                                s.setDevice(device);
                                s.setSensorType(Sensor.SensorType.CUSTOM);
                                if (key.toLowerCase().contains("temp")) {
                                    s.setSensorType(Sensor.SensorType.TEMPERATURE); 
                                }else if (key.toLowerCase().contains("humi")) {
                                    s.setSensorType(Sensor.SensorType.HUMIDITY);
                                }
                                s = sensorRepository.save(s);
                                device.getSensors().add(s);
                                return s;
                            });

                    SensorReading reading = new SensorReading();
                    reading.setSensor(sensor);
                    reading.setRawValue(numVal);
                    reading.setFilteredValue(numVal);
                    reading.setRecordedAt(payloadTime);
                    newReadings.add(reading);
                }
            }
        });

        if (!newReadings.isEmpty()) {
            readingRepository.saveAll(newReadings);
        }

        device.setLastSeen(LocalDateTime.now());
        device.setStatus(Device.Status.ONLINE);
        deviceRepository.save(device);

        data.put("deviceId", deviceId);
        data.put("status", "ONLINE");
        System.out.println("Broadcasting to /topic/telemetry-updates: " + data);
        messagingTemplate.convertAndSend("/topic/telemetry-updates", (Object) data);

        return "OK";
    }

    @GetMapping("/api/export/{deviceId}/csv")
    public void exportCsv(@PathVariable Long deviceId, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
        if (deviceOpt.isEmpty()) {
            response.sendError(404, "Device not found");
            return;
        }

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"device_" + deviceId + "_data.csv\"");

        java.io.PrintWriter writer = response.getWriter();
        writer.write('\ufeff'); // UTF-8 BOM for Excel

        // Cấu trúc cột như yêu cầu: Ngày, Thời gian, Tên cảm biến, Độ ẩm đất, Độ ẩm không khí, Nhiệt độ
        writer.println("Ngày,Thời gian,Tên cảm biến,Độ ẩm đất,Độ ẩm không khí,Nhiệt độ");

        Device device = deviceOpt.get();
        if (device.getSensors() != null) {
            // Group readings by time (truncate to seconds to match readings that arrived together)
            java.util.Map<java.time.LocalDateTime, java.util.Map<String, Double>> groupedReadings = new java.util.TreeMap<>(java.util.Collections.reverseOrder());

            for (Sensor s : device.getSensors()) {
                List<SensorReading> readings = readingRepository.findBySensorIdOrderByRecordedAtDesc(s.getId());
                for (SensorReading r : readings) {
                    // Group into 5-second buckets to align slightly off timestamps
                    int secondBucket = (r.getRecordedAt().getSecond() / 5) * 5;
                    java.time.LocalDateTime timeKey = r.getRecordedAt().withNano(0).withSecond(secondBucket);
                    groupedReadings.putIfAbsent(timeKey, new java.util.HashMap<>());
                    groupedReadings.get(timeKey).put(s.getSensorName(), r.getRawValue());
                }
            }

            java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
            String deviceName = device.getDeviceName() != null ? device.getDeviceName() : "Unknown";

            for (java.util.Map.Entry<java.time.LocalDateTime, java.util.Map<String, Double>> entry : groupedReadings.entrySet()) {
                String dateStr = entry.getKey().format(dateFormatter);
                String timeStr = entry.getKey().format(timeFormatter);
                java.util.Map<String, Double> vals = entry.getValue();

                String soil = vals.containsKey("Độ ẩm đất") ? String.valueOf(vals.get("Độ ẩm đất")) : "";
                String hum = vals.containsKey("Độ ẩm không khí") ? String.valueOf(vals.get("Độ ẩm không khí")) : "";
                String temp = vals.containsKey("Nhiệt độ") ? String.valueOf(vals.get("Nhiệt độ")) : "";

                writer.println(dateStr + "," + timeStr + "," + deviceName + "," + soil + "," + hum + "," + temp);
            }
        }
        writer.flush();
    }
}
