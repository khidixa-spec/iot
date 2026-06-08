package com.duc.iot.iot_web_app.controller;

import com.duc.iot.iot_web_app.model.Device;
import com.duc.iot.iot_web_app.model.Sensor;
import com.duc.iot.iot_web_app.model.SensorReading;
import com.duc.iot.iot_web_app.repository.DeviceRepository;
import com.duc.iot.iot_web_app.repository.FirmwareVersionRepository;
import com.duc.iot.iot_web_app.repository.SensorReadingRepository;
import com.duc.iot.iot_web_app.repository.SensorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Controller
public class IotController {

    @Autowired private SensorRepository sensorRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private SensorReadingRepository readingRepository;
    @Autowired private FirmwareVersionRepository firmwareRepository;

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
        if (deviceOpt.isEmpty()) return "redirect:/devices";
        Device device = deviceOpt.get();
        List<Sensor> sensors = device.getSensors() != null ? device.getSensors() : new ArrayList<>();
        Map<String, Double> latestReadings = new LinkedHashMap<>();
        for (Sensor s : sensors) {
            List<SensorReading> readings = readingRepository
                .findTop10BySensorIdOrderByRecordedAtDesc(s.getId());
            if (!readings.isEmpty()) {
                latestReadings.put(s.getSensorName(), readings.get(0).getRawValue());
            }
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
        if (deviceOpt.isEmpty()) return "redirect:/devices";
        Device device = deviceOpt.get();
        List<Sensor> sensors = device.getSensors() != null ? device.getSensors() : new ArrayList<>();
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
        if (deviceOpt.isEmpty()) return "Error: Invalid Access Token!";
        Device device = deviceOpt.get();
        Long deviceId = device.getId();

        Set<String> systemFields = Set.of(
            "hardware_version", "free_heap", "wifi_rssi", "uptime",
            "reboot_count", "last_reboot_reason", "mqtt_connected"
        );

        data.forEach((key, value) -> {
            if (value == null) return;

            switch (key) {
                case "hardware_version" -> device.setHardwareVersion(value.toString());
                case "free_heap" -> device.setFreeHeap(((Number) value).intValue());
                case "wifi_rssi" -> device.setWifiRssi(((Number) value).intValue());
                case "uptime" -> device.setUptime(((Number) value).longValue());
                case "reboot_count" -> device.setRebootCount(((Number) value).intValue());
                case "last_reboot_reason" -> device.setLastRebootReason(value.toString());
                case "mqtt_connected" -> device.setMqttConnected(Boolean.parseBoolean(value.toString()));
                default -> {
                    // Xử lý sensor readings
                    double numVal;
                    try {
                        numVal = value instanceof Number
                            ? ((Number) value).doubleValue()
                            : Double.parseDouble(value.toString());
                    } catch (Exception e) {
                        return; // bỏ qua chuỗi không phải số
                    }

                    Sensor sensor = sensorRepository.findBySensorNameAndDevice_Id(key, deviceId)
                        .orElseGet(() -> {
                            Sensor s = new Sensor();
                            s.setSensorName(key);
                            s.setDevice(device);
                            s.setSensorType(Sensor.SensorType.CUSTOM);
                            if (key.toLowerCase().contains("temp")) s.setSensorType(Sensor.SensorType.TEMPERATURE);
                            else if (key.toLowerCase().contains("humi")) s.setSensorType(Sensor.SensorType.HUMIDITY);
                            return sensorRepository.save(s);
                        });

                    SensorReading reading = new SensorReading();
                    reading.setSensor(sensor);
                    reading.setRawValue(numVal);
                    reading.setFilteredValue(numVal);
                    readingRepository.save(reading);
                }
            }
        });

        device.setLastSeen(LocalDateTime.now());
        device.setStatus(Device.Status.ONLINE);
        deviceRepository.save(device);

        data.put("deviceId", deviceId);
        System.out.println("Broadcasting to /topic/telemetry-updates: " + data);
        messagingTemplate.convertAndSend("/topic/telemetry-updates", (Object) data);

        return "OK";
    }
}