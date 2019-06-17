package org.mozilla.iot.webthing.example;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.iot.webthing.Action;
import org.mozilla.iot.webthing.Event;
import org.mozilla.iot.webthing.Property;
import org.mozilla.iot.webthing.Thing;
import org.mozilla.iot.webthing.Value;
import org.mozilla.iot.webthing.WebThingServer;
import org.mozilla.iot.webthing.errors.PropertyError;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MultipleThings {
    public static void main(String[] args) {
        // Create a thing that represents a dimmable light
        Thing light = new ExampleDimmableLight();

        // Create a thing that represents a humidity sensor
        Thing sensor = new FakeGpioHumiditySensor();

        try {
            List<Thing> things = new ArrayList<>();
            things.add(light);
            things.add(sensor);

            // If adding more than one thing, use MultipleThings() with a name.
            // In the single thing case, the thing's name will be broadcast.
            WebThingServer server =
                    new WebThingServer(new WebThingServer.MultipleThings(things,
                                                                         "LightAndTempDevice"),
                                       8888);

            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    server.stop();
                }
            });

            server.start(false);
        } catch (IOException e) {
            System.out.println(e);
            System.exit(1);
        }
    }

    /**
     * A dimmable light that logs received commands to std::out.
     */
    public static class ExampleDimmableLight extends Thing {
        public ExampleDimmableLight() {
            super("杨子祺的台灯",
                  new JSONArray(Arrays.asList("OnOffSwitch", "Light")),
                  "A web connected lamp");

            JSONObject onDescription = new JSONObject();
            onDescription.put("@type", "OnOffProperty");
            onDescription.put("title", "On/Off");
            onDescription.put("type", "boolean");
            onDescription.put("description", "Whether the lamp is turned on");

            Value<Boolean> on = new Value<>(true,
                                            // Here, you could send a signal to
                                            // the GPIO that switches the lamp
                                            // off
                                            v -> System.out.printf(
                                                    "On-State is now %s\n",
                                                    v));

            this.addProperty(new Property(this, "on", on, onDescription));

            JSONObject brightnessDescription = new JSONObject();
            brightnessDescription.put("@type", "BrightnessProperty");
            brightnessDescription.put("title", "Brightness");
            brightnessDescription.put("type", "integer");
            brightnessDescription.put("description",
                                      "The level of light from 0-100");
            brightnessDescription.put("minimum", 0);
            brightnessDescription.put("maximum", 100);
            brightnessDescription.put("unit", "percent");

            Value<Integer> brightness = new Value<>(50,
                                                    // Here, you could send a signal
                                                    // to the GPIO that controls the
                                                    // brightness
                                                    l -> System.out.printf(
                                                            "Brightness is now %s\n",
                                                            l));

            this.addProperty(new Property(this,
                                          "brightness",
                                          brightness,
                                          brightnessDescription));

            JSONObject fadeMetadata = new JSONObject();
            JSONObject fadeInput = new JSONObject();
            JSONObject fadeProperties = new JSONObject();
            JSONObject fadeBrightness = new JSONObject();
            JSONObject fadeDuration = new JSONObject();
            fadeMetadata.put("title", "Fade");
            fadeMetadata.put("description", "Fade the lamp to a given level");
            fadeInput.put("type", "object");
            fadeInput.put("required",
                          new JSONArray(Arrays.asList("brightness",
                                                      "duration")));
            fadeBrightness.put("type", "integer");
            fadeBrightness.put("minimum", 0);
            fadeBrightness.put("maximum", 100);
            fadeBrightness.put("unit", "percent");
            fadeDuration.put("type", "integer");
            fadeDuration.put("minimum", 1);
            fadeDuration.put("unit", "milliseconds");
            fadeProperties.put("brightness", fadeBrightness);
            fadeProperties.put("duration", fadeDuration);
            fadeInput.put("properties", fadeProperties);
            fadeMetadata.put("input", fadeInput);
            this.addAvailableAction("fade", fadeMetadata, FadeAction.class);

            JSONObject overheatedMetadata = new JSONObject();
            overheatedMetadata.put("description",
                                   "The lamp has exceeded its safe operating temperature");
            overheatedMetadata.put("type", "number");
            overheatedMetadata.put("unit", "degree celsius");
            this.addAvailableEvent("overheated", overheatedMetadata);
        }

        public static class OverheatedEvent extends Event {
            public OverheatedEvent(Thing thing, int data) {
                super(thing, "overheated", data);
            }
        }

        public static class FadeAction extends Action {
            public FadeAction(Thing thing, JSONObject input) {
                super(UUID.randomUUID().toString(), thing, "fade", input);
            }

            @Override
            public void performAction() {
                Thing thing = this.getThing();
                JSONObject input = this.getInput();
                try {
                    Thread.sleep(input.getInt("duration"));
                } catch (InterruptedException e) {
                }

                try {
                    thing.setProperty("brightness", input.getInt("brightness"));
                    thing.addEvent(new OverheatedEvent(thing, 102));
                } catch (PropertyError e) {
                }
            }
        }
    }

    /**
     * A humidity sensor which updates its measurement every few seconds.
     */
    public static class FakeGpioHumiditySensor extends Thing {
        private final Value<Double> temperature;
        private final Value<Double> humidity;
        private final Value<Double> pm2p5CC;
        private final Value<Double> pm10CC;
        private final Value<Double> VOCH2S;
        private final Value<Double> CH20NH3;

        //数据结构体变量，存储传感器的信息
        /**
         * { temperature: 277,
         *   humidity: 398,
         *   pm2p5CC: 6,
         *   pm10CC: 8,
         *   VOCH2S: 5,
         *   CH20NH3: 0 }
         */
        private Map<String, Double> dataStucture;


        public FakeGpioHumiditySensor() {
            super("房间传感器",
                  new JSONArray(Arrays.asList("Monitor")),
                  "A web connected air sensor");
            //创建传感器结构体变量
            dataStucture = new HashMap<>();
            dataStucture.put("temperature", 0.0);
            dataStucture.put("humidity", 0.0);
            dataStucture.put("pm2p5CC", 0.0);
            dataStucture.put("pm10CC", 0.0);
            dataStucture.put("VOCH2S", 0.0);
            dataStucture.put("CH20NH3", 0.0);
            //1、温度
            JSONObject temperatureDescription = new JSONObject();
            temperatureDescription.put("@type", "TemperatureProperty");
            temperatureDescription.put("title", "Temperature");
            temperatureDescription.put("type", "number");
            temperatureDescription.put("description", "The current temperature in %");
            temperatureDescription.put("minimum", -100);
            temperatureDescription.put("maximum", 100);
//            temperatureDescription.put("unit", "percent");
            temperatureDescription.put("readOnly", true);
            //从结构体中拿到温度值
            this.temperature = new Value<>(dataStucture.get("temperature"));
            this.addProperty(new Property(this,
                                          "temperature",
                                          temperature,
                                          temperatureDescription));
            //2、湿度
            JSONObject humidityDescription = new JSONObject();
            humidityDescription.put("@type", "LevelProperty");
            humidityDescription.put("title", "Humidity");
            humidityDescription.put("type", "number");
            humidityDescription.put("description", "The current humidity in %");
            humidityDescription.put("minimum", 0);
            humidityDescription.put("maximum", 100);
            humidityDescription.put("unit", "percent");
            humidityDescription.put("readOnly", true);
            //从结构体中拿到湿度值
            this.humidity = new Value<>(dataStucture.get("humidity"));
            this.addProperty(new Property(this,
                                          "humidity",
                                          humidity,
                                          humidityDescription));
            //3、pm2p5CC
            JSONObject pm2p5CCDescription = new JSONObject();
            pm2p5CCDescription.put("@type", "LevelProperty");
            pm2p5CCDescription.put("title", "pm2p5CC");
            pm2p5CCDescription.put("type", "number");
            pm2p5CCDescription.put("description", "The current pm2p5CC in %");
            pm2p5CCDescription.put("minimum", 0);
            pm2p5CCDescription.put("maximum", 100);
//            pm2p5CCDescription.put("unit", "percent");
            pm2p5CCDescription.put("readOnly", true);
            //从结构体中拿到pm2p5CC
            this.pm2p5CC = new Value<>(dataStucture.get("pm2p5CC"));
            this.addProperty(new Property(this,
                                          "pm2p5CC",
                                          pm2p5CC,
                                          pm2p5CCDescription));
            //4、pm10CC
            JSONObject pm10CCDescription = new JSONObject();
            pm10CCDescription.put("@type", "LevelProperty");
            pm10CCDescription.put("title", "pm10CC");
            pm10CCDescription.put("type", "number");
            pm10CCDescription.put("description", "The current pm10CC in %");
            pm10CCDescription.put("minimum", 0);
            pm10CCDescription.put("maximum", 100);
            //            pm10CCDescription.put("unit", "percent");
            pm2p5CCDescription.put("readOnly", true);
            //从结构体中拿到pm10CC
            this.pm10CC = new Value<>(dataStucture.get("pm10CC"));
            this.addProperty(new Property(this,
                                          "pm10CC",
                                          pm10CC,
                                          pm10CCDescription));
            //5、VOCH2S
            JSONObject VOCH2SDescription = new JSONObject();
            VOCH2SDescription.put("@type", "LevelProperty");
            VOCH2SDescription.put("title", "VOCH2S");
            VOCH2SDescription.put("type", "number");
            VOCH2SDescription.put("description", "The current VOCH2S in %");
            VOCH2SDescription.put("minimum", 0);
            VOCH2SDescription.put("maximum", 100);
            //            VOCH2SDescription.put("unit", "percent");
            VOCH2SDescription.put("readOnly", true);
            //从结构体中拿到VOCH2S
            this.VOCH2S = new Value<>(dataStucture.get("VOCH2S"));
            this.addProperty(new Property(this,
                                          "VOCH2S",
                                          VOCH2S,
                                          VOCH2SDescription));
            //6、CH20NH3
            JSONObject CH20NH3Description = new JSONObject();
            CH20NH3Description.put("@type", "LevelProperty");
            CH20NH3Description.put("title", "CH20NH3");
            CH20NH3Description.put("type", "number");
            CH20NH3Description.put("description", "The current CH20NH3 in %");
            CH20NH3Description.put("minimum", 0);
            CH20NH3Description.put("maximum", 100);
            //            CH20NH3Description.put("unit", "percent");
            CH20NH3Description.put("readOnly", true);
            //从结构体中拿到CH20NH3
            this.CH20NH3 = new Value<>(dataStucture.get("CH20NH3"));
            this.addProperty(new Property(this,
                                          "CH20NH3",
                                          CH20NH3,
                                          CH20NH3Description));
            // Start a thread that polls the sensor reading every 3 seconds
            new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(3000);
                        // Update the underlying value, which in turn notifies
                        // all listeners
                        double[] newDataStructure = this.readFromGPIO();
                        System.out.printf("setting new temperature level: %f\n",
                                          newDataStructure[0]);
                        this.temperature.notifyOfExternalUpdate(newDataStructure[0]);

                        System.out.printf("setting new humidity level: %f\n",
                                          newDataStructure[1]);
                        this.humidity.notifyOfExternalUpdate(newDataStructure[1]);

                        System.out.printf("setting new pm2p5CC level: %f\n",
                                          newDataStructure[2]);
                        this.pm2p5CC.notifyOfExternalUpdate(newDataStructure[2]);

                        System.out.printf("setting new pm10CC level: %f\n",
                                          newDataStructure[3]);
                        this.pm10CC.notifyOfExternalUpdate(newDataStructure[3]);

                        System.out.printf("setting new VOCH2S level: %f\n",
                                          newDataStructure[4]);
                        this.VOCH2S.notifyOfExternalUpdate(newDataStructure[4]);

                        System.out.printf("setting new CH20NH3 level: %f\n",
                                          newDataStructure[5]);
                        this.CH20NH3.notifyOfExternalUpdate(newDataStructure[5]);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }).start();
        }

        /**
         * Mimic an actual sensor updating its reading every couple seconds.
         */
        private double[] readFromGPIO() {
//            return Math.abs(70.0d * Math.random() * (-0.5 + Math.random()));
            return new double[]{Math.abs(70.0d * Math.random() * (-0.5 + Math.random())),
                                Math.abs(70.0d * Math.random() * (-0.5 + Math.random())),
                                Math.abs(70.0d * Math.random() * (-0.5 + Math.random())),
                                Math.abs(70.0d * Math.random() * (-0.5 + Math.random())),
                                Math.abs(70.0d * Math.random() * (-0.5 + Math.random())),
                                Math.abs(70.0d * Math.random() * (-0.5 + Math.random()))};
        }

        //设置传感器结构体
        /**
         * { temperature: 277,
         *   humidity: 398,
         *   pm2p5CC: 6,
         *   pm10CC: 8,
         *   VOCH2S: 5,
         *   CH20NH3: 0 }
         */
        private void setDataStructure(double temperature, double humidity, double pm2p5CC,
                                      double pm10CC, double VOCH2S, double CH20NH3) {
            dataStucture.put("temperature",temperature);
            dataStucture.put("humidity",humidity);
            dataStucture.put("pm2p5CC",pm2p5CC);
            dataStucture.put("pm10CC",pm10CC);
            dataStucture.put("VOCH2S",VOCH2S);
            dataStucture.put("CH20NH3",CH20NH3);
        }
    }
}
