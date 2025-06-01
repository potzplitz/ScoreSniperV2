package tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Mod {
	
    private static final Map<String, Integer> modMap = new HashMap<>();
    
    static {
        modMap.put("NF", 1);
        modMap.put("EZ", 2);
        modMap.put("TD", 4);
        modMap.put("HD", 8);
        modMap.put("HR", 16);
        modMap.put("SD", 32);
        modMap.put("DT", 64);
        modMap.put("RX", 128);
        modMap.put("HT", 256);
        modMap.put("NC", 512);
        modMap.put("FL", 1024);
        modMap.put("Autoplay", 2048);
        modMap.put("SO", 4096);
        modMap.put("AP", 8192);
        modMap.put("PF", 16384);
        modMap.put("4K", 32768);
        modMap.put("5K", 65536);
        modMap.put("6K", 131072);
        modMap.put("7K", 262144);
        modMap.put("8K", 524288);
        modMap.put("FI", 1048576);
        modMap.put("RD", 2097152);
        modMap.put("CN", 4194304);
        modMap.put("TP", 8388608);
        modMap.put("9K", 16777216);
        modMap.put("CO", 33554432);
        modMap.put("1K", 67108864);
        modMap.put("3K", 134217728);
        modMap.put("2K", 268435456);
        modMap.put("ScoreV2", 536870912);
        modMap.put("MR", 1073741824);
        modMap.put("CL", 0);
    }
	
    public static int convertModsToBitmask(List<String> modAcronyms) {
        int bitmask = 0;
        for (String mod : modAcronyms) {
            if (mod == null || mod.isBlank()) {
                continue;
            }

            Integer value = modMap.get(mod);
            if (value != null) {
                bitmask |= value;
            } else {
                System.out.println("Unbekanntes Mod-Akronym: " + mod);
            }
        }

        if (modAcronyms.contains("NC")) {
            bitmask &= ~64;
        }
        if (modAcronyms.contains("PF")) {
            bitmask &= ~32;
        }

        return bitmask;
    }


}
