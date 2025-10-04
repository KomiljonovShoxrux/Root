package org.example.root.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.example.root.dto.LocationDto;
import org.example.root.dto.WeatherResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
public class WeatherService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${owm.api.key}")
    private String apiKey;

    // 🌍 Shahar nomidan koordinata olish
    public LocationDto geocodeCity(String city, String country) {
        try {
            String query = city; // faqat shahar
            String url = String.format(
                    "https://api.openweathermap.org/geo/1.0/direct?q=%s&limit=1&appid=%s",
                    query, apiKey
            );

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode() != HttpStatus.OK) return null;

            var jsonArray = JsonParser.parseString(response.getBody()).getAsJsonArray();
            if (jsonArray.size() == 0) return null;

            var json = jsonArray.get(0).getAsJsonObject();
            double lat = json.get("lat").getAsDouble();
            double lon = json.get("lon").getAsDouble();
            String name = json.get("name").getAsString();

            return new LocationDto(name, lat, lon);

        } catch (Exception e) {
            throw new RuntimeException("Geocodingda xatolik: " + e.getMessage());
        }
    }

    // 🌤️ Hozirgi ob-havo
    public WeatherResponse fetchWeather(double lat, double lon) {
        try {
            String url = String.format(
                    "https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&units=metric&appid=%s",
                    lat, lon, apiKey
            );

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("OpenWeatherMap hozirgi ob-havo bermadi!");
            }

            JsonObject root = JsonParser.parseString(response.getBody()).getAsJsonObject();
            JsonObject main = root.getAsJsonObject("main");
            JsonObject wind = root.getAsJsonObject("wind");
            JsonObject weatherObj = root.getAsJsonArray("weather").get(0).getAsJsonObject();

            return new WeatherResponse(
                    main.get("temp").getAsDouble(),
                    main.get("feels_like").getAsDouble(),
                    main.get("pressure").getAsInt(),
                    main.get("humidity").getAsInt(),
                    wind.get("speed").getAsDouble(),
                    weatherObj.get("main").getAsString(),
                    weatherObj.get("description").getAsString()
            );

        } catch (Exception e) {
            throw new RuntimeException("Ob-havo olishda xatolik: " + e.getMessage());
        }
    }

    // 📅 Tarixiy ob-havo (faqat 5 kun orqaga)
    public WeatherResponse fetchWeatherByDate(double lat, double lon, LocalDate date) {
        try {
            LocalDate now = LocalDate.now(ZoneOffset.UTC);
            long daysBetween = ChronoUnit.DAYS.between(date, now);

            if (daysBetween < 0) {
                throw new RuntimeException("Tanlangan sana kelajakda. Iltimos forecast metodini ishlating.");
            }
            if (daysBetween > 5) {
                throw new RuntimeException("Tarixiy ob-havo faqat so‘nggi 5 kun uchun mavjud.");
            }

            long timestamp = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond();

            String url = String.format(
                    "https://api.openweathermap.org/data/3.0/onecall/timemachine?lat=%f&lon=%f&dt=%d&units=metric&appid=%s",
                    lat, lon, timestamp, apiKey
            );

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("OpenWeatherMap tarixiy ma’lumot bermadi!");
            }

            JsonObject root = JsonParser.parseString(response.getBody()).getAsJsonObject();
            JsonObject current = root.has("data")
                    ? root.getAsJsonArray("data").get(0).getAsJsonObject()
                    : root.getAsJsonObject("current");

            JsonObject weatherObj = current.getAsJsonArray("weather").get(0).getAsJsonObject();

            return new WeatherResponse(
                    current.get("temp").getAsDouble(),
                    current.get("feels_like").getAsDouble(),
                    current.get("pressure").getAsInt(),
                    current.get("humidity").getAsInt(),
                    current.get("wind_speed").getAsDouble(),
                    weatherObj.get("main").getAsString(),
                    weatherObj.get("description").getAsString()
            );

        } catch (Exception e) {
            throw new RuntimeException("Tarixiy ob-havo olishda xatolik: " + e.getMessage());
        }
    }

    // 🔮 Kelajak prognozi (faqat 7 kun oldinga)
    public WeatherResponse fetchFutureWeather(double lat, double lon, LocalDate date) {
        try {
            LocalDate now = LocalDate.now(ZoneOffset.UTC);
            long daysAhead = ChronoUnit.DAYS.between(now, date);

            if (daysAhead < 0) {
                throw new RuntimeException("Bu sana o‘tib ketgan. Tarixiy ob-havo uchun boshqa metodni ishlating.");
            }
            if (daysAhead > 7) {
                throw new RuntimeException("Kelajak prognozi faqat 7 kun oldinga mavjud.");
            }

            String url = String.format(
                    "https://api.openweathermap.org/data/3.0/onecall?lat=%f&lon=%f&units=metric&exclude=current,minutely,hourly,alerts&appid=%s",
                    lat, lon, apiKey
            );

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("OpenWeatherMap prognoz bermadi!");
            }

            JsonObject root = JsonParser.parseString(response.getBody()).getAsJsonObject();
            var daily = root.getAsJsonArray("daily");
            if (daily.size() <= daysAhead) {
                throw new RuntimeException("Ushbu sana uchun prognoz mavjud emas.");
            }

            JsonObject forecast = daily.get((int) daysAhead).getAsJsonObject();
            JsonObject temp = forecast.getAsJsonObject("temp");
            JsonObject feelsLike = forecast.getAsJsonObject("feels_like");
            JsonObject weatherObj = forecast.getAsJsonArray("weather").get(0).getAsJsonObject();

            return new WeatherResponse(
                    temp.get("day").getAsDouble(),
                    feelsLike.get("day").getAsDouble(),
                    forecast.get("pressure").getAsInt(),
                    forecast.get("humidity").getAsInt(),
                    forecast.get("wind_speed").getAsDouble(),
                    weatherObj.get("main").getAsString(),
                    weatherObj.get("description").getAsString()
            );

        } catch (Exception e) {
            throw new RuntimeException("Kelajak prognozi olishda xatolik: " + e.getMessage());
        }
    }
}
