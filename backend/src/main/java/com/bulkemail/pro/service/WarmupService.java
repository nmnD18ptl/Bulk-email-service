package com.bulkemail.pro.service;

import com.bulkemail.pro.model.entity.WarmupPlan;
import com.bulkemail.pro.repository.WarmupPlanRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WarmupService {

    private final WarmupPlanRepository warmupPlanRepository;
    private final ObjectMapper objectMapper;

    // Warmup schedule: day -> volume
    private static final int[] WARMUP_SCHEDULE = {
        50, 100, 200, 400, 700, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000
    };

    public List<Map<String, Object>> generateSchedule(int targetVolume) {
        List<Map<String, Object>> schedule = new ArrayList<>();
        int days = 14;
        double step = (double) targetVolume / days;

        for (int i = 0; i < days; i++) {
            int dayVolume = Math.min((int) (step * (i + 1)), targetVolume);
            // Use predefined schedule if within range
            if (i < WARMUP_SCHEDULE.length) {
                dayVolume = Math.min(WARMUP_SCHEDULE[i], targetVolume);
            }
            Map<String, Object> day = new HashMap<>();
            day.put("day", i + 1);
            day.put("volume", dayVolume);
            day.put("date", LocalDate.now().plusDays(i).toString());
            schedule.add(day);
        }
        return schedule;
    }

    public WarmupPlan createPlan(WarmupPlan plan) {
        List<Map<String, Object>> schedule = generateSchedule(plan.getTargetDailyVolume());
        try {
            plan.setScheduleJson(objectMapper.writeValueAsString(schedule));
        } catch (Exception e) {
            log.error("Failed to serialize schedule", e);
        }
        plan.setTotalStages(schedule.size());
        plan.setCurrentStage(1);
        plan.setEstimatedCompletionDate(LocalDate.now().plusDays(schedule.size()));
        return warmupPlanRepository.save(plan);
    }

    public WarmupPlan startPlan(Long id) {
        WarmupPlan plan = warmupPlanRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Warmup plan not found"));
        plan.setStatus(WarmupPlan.WarmupStatus.ACTIVE);
        plan.setStartDate(LocalDate.now());
        return warmupPlanRepository.save(plan);
    }

    public WarmupPlan pausePlan(Long id) {
        WarmupPlan plan = warmupPlanRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Warmup plan not found"));
        plan.setStatus(WarmupPlan.WarmupStatus.PAUSED);
        return warmupPlanRepository.save(plan);
    }

    public WarmupPlan resumePlan(Long id) {
        WarmupPlan plan = warmupPlanRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Warmup plan not found"));
        plan.setStatus(WarmupPlan.WarmupStatus.ACTIVE);
        return warmupPlanRepository.save(plan);
    }

    @Scheduled(cron = "0 0 6 * * *") // Run daily at 6 AM
    public void advanceWarmupPlans() {
        List<WarmupPlan> activePlans = warmupPlanRepository.findByStatus(WarmupPlan.WarmupStatus.ACTIVE);
        for (WarmupPlan plan : activePlans) {
            try {
                if (plan.getLastRunDate() != null && plan.getLastRunDate().equals(LocalDate.now())) {
                    continue; // Already ran today
                }

                int nextStage = plan.getCurrentStage() + 1;
                if (nextStage > plan.getTotalStages()) {
                    plan.setStatus(WarmupPlan.WarmupStatus.COMPLETED);
                } else {
                    plan.setCurrentStage(nextStage);
                    plan.setLastRunDate(LocalDate.now());

                    // Get today's volume from schedule
                    if (plan.getScheduleJson() != null) {
                        List<Map<String, Object>> schedule = objectMapper.readValue(
                            plan.getScheduleJson(), new TypeReference<>() {});
                        if (nextStage - 1 < schedule.size()) {
                            int volume = (int) schedule.get(nextStage - 1).get("volume");
                            plan.setCurrentDayVolume(volume);
                        }
                    }
                }
                warmupPlanRepository.save(plan);
            } catch (Exception e) {
                log.error("Error advancing warmup plan {}: {}", plan.getId(), e.getMessage());
            }
        }
    }
}
