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
@RequiredArgsConstructor
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weatherService;

    /**
     * GET /api/weather?city=Tashkent&date=2025-10-05
     */
    @GetMapping
    public WeatherResponse getWeather(
            @RequestParam String city,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        try {
            // 🔹 1. Koordinata olish
            var location = weatherService.geocodeCity(city);
            if (location == null) {
                throw new RuntimeException("Shahar topilmadi: " + city);
            }

            double lat = location.lat();
            double lon = location.lon();

            // 🔹 2. Hozirgi, tarixiy yoki kelajak ob-havo tanlash
            WeatherResponse response;
            if (date == null || date.isEqual(LocalDate.now())) {
                response = weatherService.fetchWeather(lat, lon);
            } else if (date.isBefore(LocalDate.now())) {
                response = weatherService.fetchWeatherByDate(lat, lon, date);
            } else {
                response = weatherService.fetchFutureWeather(lat, lon, date);
            }

            // 🔹 3. Maslahat generatsiya qilish (bitta API orqali)
            String advice = weatherService.generateAdvice(
                    response.temp(),
                    response.windSpeed(),
                    response.pressure(),
                    response.feelsLike(),
                    response.humidity()
            );

            // 🔹 4. Maslahatni ob-havo javobiga qo‘shib qaytaramiz
            return new WeatherResponse(
                    response.temp(),
                    response.feelsLike(),
                    response.pressure(),
                    response.humidity(),
                    response.windSpeed(),
                    response.main(),
                    response.description(),
                    advice
            );

        } catch (Exception e) {
            throw new RuntimeException("Xatolik yuz berdi: " + e.getMessage());
        }
    }
}
