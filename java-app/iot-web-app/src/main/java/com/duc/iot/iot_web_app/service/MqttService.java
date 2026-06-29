package com.duc.iot.iot_web_app.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.duc.iot.iot_web_app.model.Device;
import com.duc.iot.iot_web_app.model.Sensor;
import com.duc.iot.iot_web_app.model.SensorReading;
import com.duc.iot.iot_web_app.repository.DeviceRepository;
import com.duc.iot.iot_web_app.repository.SensorReadingRepository;
import com.duc.iot.iot_web_app.repository.SensorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MqttService implements MqttCallback {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.topic.sensor}")
    private String topicSensor;

    @Value("${mqtt.username:}")
    private String mqttUsername;

    @Value("${mqtt.password:}")
    private String mqttPassword;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SensorRepository sensorRepository;

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private MqttClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            log.info("Connecting to MQTT Broker: {}", brokerUrl);
            client = new MqttClient(brokerUrl, MqttClient.generateClientId());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            
            if (mqttUsername != null && !mqttUsername.isEmpty()) {
                options.setUserName(mqttUsername);
            }
            if (mqttPassword != null && !mqttPassword.isEmpty()) {
                options.setPassword(mqttPassword.toCharArray());
            }

            client.setCallback(this);
            client.connect(options);
            client.subscribe(topicSensor);
            log.info("Connected and subscribed to: {}", topicSensor);
        } catch (MqttException e) {
            log.error("Failed to connect to MQTT Broker", e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Lost connection to MQTT broker: {}", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String payload = new String(message.getPayload());
        log.info("Received message on topic {}: {}", topic, payload);

        try {
            // Extract deviceUid from topic (e.g. iot/sensor/data/61c8310806c14648b912)
            String[] topicParts = topic.split("/");
            if (topicParts.length < 4) {
                log.warn("Topic format invalid, expected iot/sensor/data/{deviceUid}");
                return;
            }
            String deviceUid = topicParts[3];

            Optional<Device> deviceOpt = deviceRepository.findByDeviceUid(deviceUid);
            if (deviceOpt.isEmpty()) {
                log.warn("Device with UID {} not found in database.", deviceUid);
                return;
            }
            Device device = deviceOpt.get();

            JsonNode data = objectMapper.readTree(payload);
            ObjectNode objectNode = objectMapper.createObjectNode();
            LocalDateTime payloadTime = LocalDateTime.now();

            if (data.has("temperature")) {
                saveReading(device, "Nhiệt độ", Sensor.SensorType.TEMPERATURE, data.get("temperature").asDouble(), payloadTime);
                objectNode.put("Nhiệt độ", data.get("temperature").asDouble());
            }
            if (data.has("humidity")) {
                saveReading(device, "Độ ẩm không khí", Sensor.SensorType.HUMIDITY, data.get("humidity").asDouble(), payloadTime);
                objectNode.put("Độ ẩm không khí", data.get("humidity").asDouble());
            }
            if (data.has("soil")) {
                saveReading(device, "Độ ẩm đất", Sensor.SensorType.CUSTOM, data.get("soil").asDouble(), payloadTime);
                objectNode.put("Độ ẩm đất", data.get("soil").asDouble());
            }

            // Update device status and last seen
            device.setStatus(Device.Status.ONLINE);
            device.setLastSeen(LocalDateTime.now());
            deviceRepository.save(device);

            // Push to WebSocket for live feed
            objectNode.put("deviceId", device.getId());
            messagingTemplate.convertAndSend("/topic/telemetry-updates", objectNode.toString());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Error parsing JSON payload from MQTT message", e);
        } catch (RuntimeException e) {
            log.error("Error processing MQTT message", e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Not used
    }

    public void publishCommand(String topic, String payload) {
        try {
            if (client != null && client.isConnected()) {
                MqttMessage message = new MqttMessage(payload.getBytes());
                message.setQos(1);
                client.publish(topic, message);
                log.info("Published command to {}: {}", topic, payload);
            } else {
                log.warn("MQTT client not connected, cannot publish command");
            }
        } catch (MqttException e) {
            log.error("Failed to publish command", e);
        }
    }

    private void saveReading(Device device, String sensorName, Sensor.SensorType type, double value, LocalDateTime timestamp) {
        Optional<Sensor> sensorOpt = sensorRepository.findBySensorNameAndDevice_Id(sensorName, device.getId());
        Sensor sensor;
        if (sensorOpt.isEmpty()) {
            sensor = new Sensor();
            sensor.setSensorName(sensorName);
            sensor.setSensorType(type);
            sensor.setDevice(device);
            sensor.setIsEnabled(true);
            sensor = sensorRepository.save(sensor);
        } else {
            sensor = sensorOpt.get();
        }

        SensorReading reading = new SensorReading();
        reading.setSensor(sensor);
        reading.setRawValue(value);
        reading.setFilteredValue(value);
        reading.setRecordedAt(timestamp);
        sensorReadingRepository.save(reading);
    }
}
