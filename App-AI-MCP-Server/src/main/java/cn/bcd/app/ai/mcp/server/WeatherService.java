package cn.bcd.app.ai.mcp.server;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class WeatherService {
    @Tool(description = "Get weather information by city name")
    public String getWeather(@ToolParam(description = "City name") String cityName) {
        return "The weather in " + cityName + " is sunny and warm.";
    }
}