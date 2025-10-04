package org.example.root.controller;

import org.example.root.dto.LocationDto;
import org.example.root.dto.WeatherResponse;
import org.example.root.service.WeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    @GetMapping
    public Map<String, Object> getWeather(
            @RequestParam String city,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            LocationDto location = weatherService.geocodeCity(city, country);
            if (location == null) {
                result.put("error", "Manzil topilmadi: " + city);
                return result;
            }

            WeatherResponse weather;
            if (date == null) {
                // Hozirgi ob-havo
                weather = weatherService.fetchWeather(location.lat(), location.lon());
            } else {
                LocalDate now = LocalDate.now(ZoneOffset.UTC);
                if (date.isBefore(now)) {
                    weather = weatherService.fetchWeatherByDate(location.lat(), location.lon(), date);
                } else if (date.isEqual(now)) {
                    weather = weatherService.fetchWeather(location.lat(), location.lon());
                } else {
                    weather = weatherService.fetchFutureWeather(location.lat(), location.lon(), date);
                }
            }

            result.put("location", location);
            result.put("weather", weather);
            return result;

        } catch (Exception e) {
            result.put("error", "Xatolik: " + e.getMessage());
            return result;
        }
    }
}
