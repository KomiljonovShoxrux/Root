package org.example.root.dto;

public record WeatherResponse(  double temp,
                               double feelsLike,
                               int pressure,
                               int humidity,
                               double windSpeed,
                               String main,
                               String description) {
}
