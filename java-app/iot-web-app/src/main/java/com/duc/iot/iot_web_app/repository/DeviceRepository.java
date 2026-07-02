package com.duc.iot.iot_web_app.repository;

import com.duc.iot.iot_web_app.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByDeviceUid(String deviceUid);
    List<Device> findByStatusAndLastSeenBefore(Device.Status status, LocalDateTime time);
}
