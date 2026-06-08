package com.duc.iot.iot_web_app.service;

import com.duc.iot.iot_web_app.repository.SensorReadingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class DataCleanupService {

    @Autowired
    private SensorReadingRepository readingRepository;

    // Run every day at midnight
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanupOldData() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        readingRepository.deleteByRecordedAtBefore(thirtyDaysAgo);
        System.out.println("Cleaning up sensor readings older than: " + thirtyDaysAgo);
    }
}
