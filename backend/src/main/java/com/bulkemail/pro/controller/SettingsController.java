package com.bulkemail.pro.controller;

import com.bulkemail.pro.model.entity.AppSetting;
import com.bulkemail.pro.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final AppSettingRepository settingRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<List<AppSetting>> list(
            @RequestParam(required = false) String category) {
        if (category != null) {
            return ResponseEntity.ok(settingRepository.findByCategory(category));
        }
        return ResponseEntity.ok(settingRepository.findAll());
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<AppSetting> getByKey(@PathVariable String key) {
        return settingRepository.findBySettingKey(key)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<AppSetting> update(@PathVariable String key, @RequestBody Map<String, String> body) {
        AppSetting setting = settingRepository.findBySettingKey(key)
            .orElseGet(() -> {
                AppSetting s = new AppSetting();
                s.setSettingKey(key);
                return s;
            });
        setting.setSettingValue(body.get("value"));
        return ResponseEntity.ok(settingRepository.save(setting));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<List<AppSetting>> bulkUpdate(@RequestBody Map<String, String> settings) {
        settings.forEach((key, value) -> {
            AppSetting setting = settingRepository.findBySettingKey(key)
                .orElseGet(() -> {
                    AppSetting s = new AppSetting();
                    s.setSettingKey(key);
                    return s;
                });
            setting.setSettingValue(value);
            settingRepository.save(setting);
        });
        return ResponseEntity.ok(settingRepository.findAll());
    }
}
