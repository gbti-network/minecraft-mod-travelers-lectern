package com.gbti.travelerslectern;

import com.gbti.travelerslectern.utils.LecternObject;
import com.google.common.reflect.TypeToken;
import com.google.gson.*;

import java.util.*;
import net.minecraft.nbt.NbtElement;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileManager {

    private static final Logger LOGGER = LogManager.getLogger("TravelersLectern");

    private static final GsonBuilder gsonBuilder = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(NbtElement.class,
                    (JsonSerializer<NbtElement>) (nbtElement, typeOfSrc, context) -> new JsonPrimitive(nbtElement.asString()))
            .registerTypeAdapter(LecternObject.class,
                    (JsonSerializer<LecternObject>) (lectern, type, context) -> {
                        JsonObject json = new JsonObject();
                        json.addProperty("lastTimeUsed", lectern.getLastTimeUsed());
                        json.addProperty("cooldown", lectern.getCooldown());
                        json.add("worldKey", context.serialize(lectern.getWorldKey()));
                        json.addProperty("item", lectern.getItem().asString());
                        return json;
                    })
            .registerTypeAdapter(LecternObject.class,
                    (JsonDeserializer<LecternObject>) (json, typeOfT, context) -> {
                        try {
                            JsonObject jsonObject = json.getAsJsonObject();
                            TravelersLectern.logDebug("[TL Debug] Lectern JSON: {}", jsonObject);
                            
                            long lastTimeUsed = jsonObject.get("lastTimeUsed").getAsLong();
                            int cooldown = jsonObject.get("cooldown").getAsInt();
                            TravelersLectern.logDebug("[TL Debug] Parsed time and cooldown: {} {}", lastTimeUsed, cooldown);
                            
                            RegistryKey<World> worldKey = context.deserialize(jsonObject.get("worldKey"), new TypeToken<RegistryKey<World>>(){}.getType());
                            TravelersLectern.logDebug("[TL Debug] Parsed worldKey: {}", worldKey);
                            
                            // Get the raw NBT string from the item field
                            JsonElement itemElement = jsonObject.get("item");
                            String nbtString;
                            
                            if (itemElement.isJsonPrimitive()) {
                                // Direct string format
                                nbtString = itemElement.getAsString();
                            } else {
                                TravelersLectern.logDebug("[TC Error] Unexpected item format. Expected string but got: {}", itemElement.getClass().getSimpleName());
                                throw new JsonParseException("Lectern item must be a string containing NBT data");
                            }
                            
                            TravelersLectern.logDebug("[TL Debug] Parsing NBT string: {}", nbtString);
                            NbtElement item = net.minecraft.nbt.StringNbtReader.parse(nbtString);
                            
                            return new LecternObject(lastTimeUsed, cooldown, worldKey, item);
                        } catch (Exception e) {
                            TravelersLectern.logDebug("[TC Error] Failed to parse lectern: {}", e.getMessage());
                            TravelersLectern.logDebug("[TC Error] Full stack trace:", e);
                            throw new JsonParseException("Error parsing lectern data: " + e.getMessage(), e);
                        }
                    })
            .enableComplexMapKeySerialization()
            .serializeNulls();

   
    public static Map<String, String> COLORS = new HashMap<>();
    public static List<String> joinList = new ArrayList<>();
    private static final String CONFIG_DIR = "config/travelers-lectern";
   
    public static void readFiles() {

        try {
            File directory = new File(CONFIG_DIR);
            directory.mkdir();

            // Config file
            File configFile = new File("config/travelers-lectern/travelers_lectern_config.txt");
            if(configFile.createNewFile()) {
                BufferedWriter writer = new BufferedWriter(new FileWriter(configFile));
                writer.write("debug_logging=false");
                writer.close();
            } else {
                Scanner reader = new Scanner(configFile);
                while (reader.hasNextLine()) {
                    String line = reader.nextLine();

                    if(line.startsWith("debug_logging=")) {
                        // Get everything after the equals sign
                        String debugValue = line.substring(line.indexOf('=') + 1).trim();
                    
                        // Use direct logging for debug setting since logDebug isn't ready yet
                        TravelersLectern.debugLoggingEnabled = debugValue.equals("true");
                        LOGGER.info("[TL] Set debugLoggingEnabled to: {}", TravelersLectern.debugLoggingEnabled);
                    }
                }
                reader.close();
            }


        } catch (Exception e) {
            TravelersLectern.logDebug("[TL] An error occurred: {}", e.getMessage());
            e.printStackTrace();
        }

    }

    public static void saveLecterns() {
        File file = new File("config/travelers-lectern/travelers_lecterns.json");
        try {
            file.createNewFile();
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            Gson gson = gsonBuilder.create();
            String json = gson.toJson(TravelersLectern.lecterns);
            TravelersLectern.logDebug("[TL] Saving lecterns JSON: {}", json);
            writer.write(json);
            writer.close();
        } catch(Exception e) {
            TravelersLectern.logDebug("[TL] Error saving lecterns: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void loadLecterns() {
        try {
            File file = new File("config/travelers-lectern/travelers_lecterns.json");
            if(!file.createNewFile()) {
                Scanner reader = new Scanner(file);
                String str = "";
                while (reader.hasNextLine()) {
                    str += reader.nextLine();
                };
                TravelersLectern.logDebug("[TL] Loaded lecterns JSON: {}", str);
                TravelersLectern.lecterns = gsonBuilder.create().fromJson(str, new TypeToken<Map<Long, LecternObject>>(){}.getType());
                if(TravelersLectern.lecterns == null) TravelersLectern.lecterns = new HashMap<>();
                reader.close();
            }

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}
