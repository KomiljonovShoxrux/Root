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
    public LocationDto geocodeCity(String city) {
        try {
            String url = String.format(
                    "http://api.openweathermap.org/geo/1.0/direct?q=%s&limit=1&appid=%s",
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
            JsonObject wind = root.getAsJsonObject("wind");
            JsonObject weatherObj = root.getAsJsonArray("weather").get(0).getAsJsonObject();

            double temp = main.get("temp").getAsDouble();
            double feelsLike = main.get("feels_like").getAsDouble();
            int pressure = main.get("pressure").getAsInt();
            int humidity = main.get("humidity").getAsInt();
            double windSpeed = wind.get("speed").getAsDouble();
            String mainDesc = weatherObj.get("main").getAsString();
            String desc = weatherObj.get("description").getAsString();

            String advice = generateAdvice(temp, feelsLike, pressure, windSpeed, humidity);

            // 🌍 Bugungi sana + shahar nomi bilan to‘liq javob qaytaramiz
            return new WeatherResponse(
                    city,
                    LocalDate.now(),
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
            throw new RuntimeException("Ob-havo olishda xatolik: " + e.getMessage());
        }
    }


    // 📅 Tarixiy ob-havo (so‘nggi 5 kun)
    public WeatherResponse fetchWeatherByDate(double lat, double lon, LocalDate date) {
        try {
            LocalDate now = LocalDate.now(ZoneOffset.UTC);
            long daysBetween = ChronoUnit.DAYS.between(date, now);

            if (daysBetween < 0)
                throw new RuntimeException("Tanlangan sana kelajakda. Iltimos forecast metodini ishlating.");
            if (daysBetween > 5)
                throw new RuntimeException("Tarixiy ob-havo faqat so‘nggi 5 kun uchun mavjud.");

            long timestamp = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond();

            String url = String.format(
                    "https://api.openweathermap.org/data/3.0/onecall/timemachine?lat=%f&lon=%f&dt=%d&units=metric&appid=%s",
                    lat, lon, timestamp, apiKey
            );

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode() != HttpStatus.OK)
                throw new RuntimeException("OpenWeatherMap tarixiy ma’lumot bermadi!");

            JsonObject root = JsonParser.parseString(response.getBody()).getAsJsonObject();
            JsonObject current = root.has("data")
                    ? root.getAsJsonArray("data").get(0).getAsJsonObject()
                    : root.getAsJsonObject("current");

            JsonObject weatherObj = current.getAsJsonArray("weather").get(0).getAsJsonObject();

            double temp = current.get("temp").getAsDouble();
            double feelsLike = current.get("feels_like").getAsDouble();
            int pressure = current.get("pressure").getAsInt();
            int humidity = current.get("humidity").getAsInt();
            double windSpeed = current.get("wind_speed").getAsDouble();
            String main = weatherObj.get("main").getAsString();
            String desc = weatherObj.get("description").getAsString();

            String advice = generateAdvice(temp, feelsLike, humidity, windSpeed, pressure);

            return new WeatherResponse(temp, feelsLike, pressure, humidity, windSpeed, main, desc, advice);

        } catch (Exception e) {
            throw new RuntimeException("Tarixiy ob-havo olishda xatolik: " + e.getMessage());
        }
    }

    // 🔮 Kelajak prognozi (7 kun oldinga)
    public WeatherResponse fetchFutureWeather(double lat, double lon, LocalDate date) {
        try {
            LocalDate now = LocalDate.now(ZoneOffset.UTC);
            long daysAhead = ChronoUnit.DAYS.between(now, date);

            if (daysAhead < 0)
                throw new RuntimeException("Bu sana o‘tib ketgan. Tarixiy ob-havo uchun boshqa metodni ishlating.");
            if (daysAhead > 7)
                throw new RuntimeException("Kelajak prognozi faqat 7 kun oldinga mavjud.");

            String url = String.format(
                    "https://api.openweathermap.org/data/3.0/onecall?lat=%f&lon=%f&units=metric&exclude=current,minutely,hourly,alerts&appid=%s",
                    lat, lon, apiKey
            );

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode() != HttpStatus.OK)
                throw new RuntimeException("OpenWeatherMap prognoz bermadi!");

            JsonObject root = JsonParser.parseString(response.getBody()).getAsJsonObject();
            var daily = root.getAsJsonArray("daily");
            if (daily.size() <= daysAhead)
                throw new RuntimeException("Ushbu sana uchun prognoz mavjud emas.");

            JsonObject forecast = daily.get((int) daysAhead).getAsJsonObject();
            JsonObject temp = forecast.getAsJsonObject("temp");
            JsonObject feelsLike = forecast.getAsJsonObject("feels_like");
            JsonObject weatherObj = forecast.getAsJsonArray("weather").get(0).getAsJsonObject();

            double dayTemp = temp.get("day").getAsDouble();
            double dayFeels = feelsLike.get("day").getAsDouble();
            int pressure = forecast.get("pressure").getAsInt();
            int humidity = forecast.get("humidity").getAsInt();
            double windSpeed = forecast.get("wind_speed").getAsDouble();
            String main = weatherObj.get("main").getAsString();
            String desc = weatherObj.get("description").getAsString();

            String advice = generateAdvice(dayTemp, dayFeels, humidity, windSpeed, pressure);

            return new WeatherResponse(dayTemp, dayFeels, pressure, humidity, windSpeed, main, desc, advice);

        } catch (Exception e) {
            throw new RuntimeException("Kelajak prognozi olishda xatolik: " + e.getMessage());
        }
    }

    // 🧠 Aqlli maslahat generatori
    public String generateAdvice(double temp, double windSpeed, int pressure, double feelsLike, int humidity) {
        StringBuilder advice = new StringBuilder();

        // 🌡 Harorat asosida
        if (temp <= 0) advice.append("Juda sovuq — iliq kiyining, sharf va qo‘lqop taqing. 🧣 ");
        else if (temp <= 10) advice.append("Salqin havo — kurtka kiying. 🧥 ");
        else if (temp <= 20) advice.append("Yengil kurtka yoki sviter yetarli bo‘ladi. 🧶 ");
        else if (temp <= 30) advice.append("Yoqqin havo — yengil kiyim mos keladi. 👕 ");
        else advice.append("Juda issiq — suyuqlik ko‘p iching va soyada turing. ☀️ ");

        // 🌬 Shamol asosida
        if (windSpeed > 10) advice.append("Shamol kuchli — bosh kiyim kiying. 💨 ");
        else if (windSpeed > 5) advice.append("Yengil shamol esmoqda. 🌬 ");

        // 💧 Namlik asosida
        if (humidity > 80) advice.append("Namlik yuqori — yomg‘ir ehtimoli bor, soyabon oling. ☔ ");
        else if (humidity < 30) advice.append("Havo quruq — suv ichishni unutmang. 💧 ");

        // 🔽 Bosim asosida
        if (pressure < 1000) advice.append("Bosim past — boshingiz og‘risa, tinch joyda dam oling. 💤 ");
        else if (pressure > 1020) advice.append("Bosim yuqori — yurak bilan bog‘liq muammosi bo‘lganlar ehtiyot bo‘lsin. ❤️ ");

        return advice.toString().trim();
    }

}
