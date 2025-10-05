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
@RestController
@RequestMapping("/api/weather")
public class WeatherController {


    private final WeatherService weatherService;

    @Autowired
    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /**
     * 📍 GET /api/weather?city=Tashkent&date=2025-10-05
     * - Agar sana kiritilmasa → hozirgi ob-havo
     * - Agar sana o‘tgan bo‘lsa → tarixiy
     * - Agar sana kelajakda bo‘lsa → forecast
     */
    @GetMapping
    public WeatherResponse getWeather(
            @RequestParam String city,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        try {
            // 1️⃣ Shahar koordinatalarini olish
            LocationDto location = weatherService.geocodeCity(city);
            if (location == null) {
                throw new RuntimeException("❌ Shahar topilmadi: " + city);
            }

            double lat = location.lat();
            double lon = location.lon();
            LocalDate today = LocalDate.now();

            // 2️⃣ Hozirgi, tarixiy yoki kelajak ob-havo
            WeatherResponse response;
            if (date == null || date.isEqual(today)) {
                response = weatherService.fetchWeather(city, lat, lon);
            } else if (date.isBefore(today)) {
                response = weatherService.fetchWeatherByDate(city, lat, lon, date);
            } else {
                response = weatherService.fetchFutureWeather(city, lat, lon, date);
            }

            return response;

        } catch (Exception e) {
            throw new RuntimeException("❌ Xatolik yuz berdi: " + e.getMessage());
        }
    }
}
