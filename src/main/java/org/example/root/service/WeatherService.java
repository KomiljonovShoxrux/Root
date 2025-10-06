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

    public LocationDto geocodeCity(String city) {
        try {
            String url = String.format(
                    "https://api.openweathermap.org/geo/1.0/direct?q=%s&limit=1&appid=%s",
                    city, apiKey
            );

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            System.out.println("API javobi: " + response.getBody());

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("Geocoding so‘rovi muvaffaqiyatsiz!");
            }

            var jsonArray = JsonParser.parseString(response.getBody()).getAsJsonArray();
            if (jsonArray.size() == 0) return null;

            var json = jsonArray.get(0).getAsJsonObject();
            double lat = json.get("lat").getAsDouble();
            double lon = json.get("lon").getAsDouble();
            String name = json.get("name").getAsString();

            return new LocationDto(name, lat, lon);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Geocodingda xatolik: " + e.getMessage());
        }
    }

    public WeatherResponse fetchWeather(String city, double lat, double lon) {
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
            JsonObject wind = root.has("wind") ? root.getAsJsonObject("wind") : new JsonObject();
            JsonObject weatherObj = root.getAsJsonArray("weather").get(0).getAsJsonObject();

            double temp = main.get("temp").getAsDouble();
            double feelsLike = main.has("feels_like") ? main.get("feels_like").getAsDouble() : temp;
            int pressure = main.get("pressure").getAsInt();
            int humidity = main.get("humidity").getAsInt();
            double windSpeed = wind.has("speed") ? wind.get("speed").getAsDouble() : 0.0;
            String mainDesc = weatherObj.get("main").getAsString();
            String desc = weatherObj.get("description").getAsString();

            String advice = generateAdvice(temp, feelsLike, humidity, windSpeed, pressure);

            return new WeatherResponse(
                    city,
                    LocalDate.now(ZoneOffset.UTC),
                    temp,
                    feelsLike,
                    pressure,
                    humidity,
                    windSpeed,
                    mainDesc,
                    desc,
                    advice
            );

        } catch (Exception e) {
            throw new RuntimeException("Ob-havo olishda xatolik: " + e.getMessage(), e);
        }
    }

    public WeatherResponse fetchWeatherByDate(String city, double lat, double lon, LocalDate date) {
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
                    "https://api.openweathermap.org/data/2.5/forecast?lat=%f&lon=%f&units=metric&lang=uz&appid=%s",
                    lat, lon, apiKey
            );

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("OpenWeatherMap tarixiy ma’lumot bermadi!");
            }

            JsonObject root = JsonParser.parseString(response.getBody()).getAsJsonObject();

            JsonObject current = null;
            if (root.has("data") && root.getAsJsonArray("data").size() > 0) {
                current = root.getAsJsonArray("data").get(0).getAsJsonObject();
            } else if (root.has("current")) {
                current = root.getAsJsonObject("current");
            }

            if (current == null) {
                throw new RuntimeException("Tarixiy ma'lumot topilmadi (timemachine).");
            }

            JsonObject weatherObj = current.getAsJsonArray("weather").get(0).getAsJsonObject();

            double temp = current.has("temp") ? current.get("temp").getAsDouble() : 0.0;
            double feelsLike = current.has("feels_like") ? current.get("feels_like").getAsDouble() : temp;
            int pressure = current.has("pressure") ? current.get("pressure").getAsInt() : 0;
            int humidity = current.has("humidity") ? current.get("humidity").getAsInt() : 0;
            double windSpeed = current.has("wind_speed") ? current.get("wind_speed").getAsDouble() : 0.0;
            String main = weatherObj.get("main").getAsString();
            String desc = weatherObj.get("description").getAsString();

            String advice = generateAdvice(temp, feelsLike, humidity, windSpeed, pressure);

            return new WeatherResponse(
                    city,
                    date,
                    temp,
                    feelsLike,
                    pressure,
                    humidity,
                    windSpeed,
                    main,
                    desc,
                    advice
            );

        } catch (Exception e) {
            throw new RuntimeException("Tarixiy ob-havo olishda xatolik: " + e.getMessage(), e);
        }
    }


    public WeatherResponse fetchFutureWeather(String city, double lat, double lon, LocalDate date) {
        try {
            LocalDate now = LocalDate.now(ZoneOffset.UTC);
            long daysAhead = ChronoUnit.DAYS.between(now, date);

            if (daysAhead < 0) {
                throw new RuntimeException("Bu sana o'tib ketgan. Tarixiy ob-havo uchun boshqa metodni ishlating.");
            }
            if (daysAhead > 5) {
                throw new RuntimeException("Kelajak prognozi faqat 5 kun oldinga mavjud (3 soatlik kesimda).");
            }

            String url = String.format(
                    "https://api.openweathermap.org/data/2.5/forecast?lat=%f&lon=%f&units=metric&lang=uz&appid=%s",
                    lat, lon, apiKey
            );

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("OpenWeatherMap prognoz bermadi!");
            }

            JsonObject root = JsonParser.parseString(response.getBody()).getAsJsonObject();
            JsonArray list = root.getAsJsonArray("list");

            if (list == null || list.size() == 0) {
                throw new RuntimeException("Prognoz topilmadi.");
            }

            JsonObject forecast = null;
            for (var el : list) {
                JsonObject obj = el.getAsJsonObject();
                String dtTxt = obj.get("dt_txt").getAsString().substring(0, 10);
                if (dtTxt.equals(date.toString())) {
                    forecast = obj;
                    break;
                }
            }

            if (forecast == null) {
                throw new RuntimeException("Ushbu sana uchun prognoz mavjud emas.");
            }

            JsonObject main = forecast.getAsJsonObject("main");
            JsonObject wind = forecast.has("wind") ? forecast.getAsJsonObject("wind") : new JsonObject();
            JsonObject weatherObj = forecast.getAsJsonArray("weather").get(0).getAsJsonObject();

            double temp = main.get("temp").getAsDouble();
            double feelsLike = main.has("feels_like") ? main.get("feels_like").getAsDouble() : temp;
            int pressure = main.get("pressure").getAsInt();
            int humidity = main.get("humidity").getAsInt();
            double windSpeed = wind.has("speed") ? wind.get("speed").getAsDouble() : 0.0;
            String mainDesc = weatherObj.get("main").getAsString();
            String desc = weatherObj.get("description").getAsString();

            String advice = generateAdvice(temp, feelsLike, humidity, windSpeed, pressure);

            return new WeatherResponse(
                    city,
                    date,
                    temp,
                    feelsLike,
                    pressure,
                    humidity,
                    windSpeed,
                    mainDesc,
                    desc,
                    advice
            );

        } catch (Exception e) {
            throw new RuntimeException("Kelajak prognozi olishda xatolik: " + e.getMessage(), e);
        }
    }


    private String generateAdvice(double temp, double feelsLike, int humidity, double windSpeed, int pressure) {
        StringBuilder sb = new StringBuilder();

        // Harorat
        if (temp <= 0) sb.append("❄️ Juda sovuq! Issiq kiyining, qo‘lqop va shapka taqing. ");
        else if (temp <= 10) sb.append("🧥 Salqin — kurtka yoki palto kiying. ");
        else if (temp <= 20) sb.append("🧶 Yengil sviter yoki ko'ylak yetarli. ");
        else if (temp <= 30) sb.append("😎 Iliq — yengil kiyimda yuring va suv iching. ");
        else sb.append("🔥 Juda issiq — quyoshdan himoyalaning, ko‘p suv iching. ");

        // Shamol
        if (windSpeed > 10) sb.append("🌬 Kuchli shamol — sharf yoki qo'lqop oling. ");
        else if (windSpeed > 5) sb.append("💨 Yengil shamol — salqin hissi beradi. ");

        // Namlik
        if (humidity > 80) sb.append("💦 Namlik yuqori — havoda og'irlik bo'lishi mumkin. ");
        else if (humidity < 30) sb.append("🌵 Havo quruq — teri va lablarni namlang, suv iching. ");

        // Bosim
        if (pressure < 1000) sb.append("⚠ Past bosim — bosh og'rig‘i mumkin, dam oling. ");
        else if (pressure > 1030) sb.append("⛰ Yuqori bosim — yurak kasalligi borlar ehtiyot bo'lsin. ");

        return sb.toString().trim();
    }

}
