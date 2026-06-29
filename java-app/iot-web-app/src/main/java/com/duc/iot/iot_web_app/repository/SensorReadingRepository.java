package com.duc.iot.iot_web_app.repository;

import com.duc.iot.iot_web_app.model.SensorReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {
    List<SensorReading> findBySensorIdOrderByRecordedAtDesc(Long sensorId);
    SensorReading findFirstBySensorIdOrderByRecordedAtDesc(Long sensorId);
    List<SensorReading> findTop10BySensorIdOrderByRecordedAtDesc(Long sensorId);
    List<SensorReading> findTop50BySensorIdOrderByRecordedAtDesc(Long sensorId);
    List<SensorReading> findTop1000BySensorIdOrderByRecordedAtDesc(Long sensorId);
    List<SensorReading> findTop200BySensorIdOrderByRecordedAtDesc(Long sensorId);
    void deleteByRecordedAtBefore(LocalDateTime threshold);
}
