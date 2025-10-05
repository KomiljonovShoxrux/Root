package org.example.root.dto;

import java.time.LocalDate;

public record WeatherResponse(
        String city,        // 🌆 Shahar nomi
        LocalDate date,
        double temp,
                               double feelsLike,
                               int pressure,
                               int humidity,
                               double windSpeed,
                               String main,
                               String description,
                                String advice) {
}
