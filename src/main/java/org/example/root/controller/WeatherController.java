package org.example.root.controller;

import lombok.RequiredArgsConstructor;
import org.example.root.dto.LocationDto;
import org.example.root.dto.WeatherResponse;
import org.example.root.service.WeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weatherService;

    @Autowired
    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /**
     * GET /api/weather?city=Tashkent&date=2025-10-05
     */
    @GetMapping
    public ResponseEntity<WeatherResponse> getWeather(
            @RequestParam String city,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        try {
            // 1️⃣ Shahar koordinatalarini olish
            var location = weatherService.geocodeCity(city);
            if (location == null) {
                throw new RuntimeException("Shahar topilmadi: " + city);
            }

            double lat = location.lat();
            double lon = location.lon();

            // 2️⃣ Qaysi turdagi ma’lumotni olishni aniqlaymiz
            var now = LocalDate.now();
            WeatherResponse baseResponse;

            if (date == null || date.isEqual(now)) {
                baseResponse = weatherService.fetchWeather(city, lat, lon);
            } else if (date.isBefore(now)) {
                baseResponse = weatherService.fetchWeatherByDate(lat, lon, date);
            } else {
                baseResponse = weatherService.fetchFutureWeather(lat, lon, date);
            }

            // 3️⃣ Maslahat generatsiya qilish
            String advice = weatherService.generateAdvice(
                    baseResponse.temp(),
                    baseResponse.feelsLike(),
                    baseResponse.pressure(),
                    baseResponse.windSpeed(),
                    baseResponse.humidity()
            );

            // 4️⃣ To‘liq javobni yig‘ib qaytaramiz (city + date bilan)
            WeatherResponse fullResponse = new WeatherResponse(
                    city,
                    (date != null ? date : now),
                    baseResponse.temp(),
                    baseResponse.feelsLike(),
                    baseResponse.pressure(),
                    baseResponse.humidity(),
                    baseResponse.windSpeed(),
                    baseResponse.main(),
                    baseResponse.description(),
                    advice
            );

            return ResponseEntity.ok(fullResponse);

        } catch (Exception e) {
            throw new RuntimeException("Xatolik yuz berdi: " + e.getMessage());
        }
    }
}
