package com.nomoneypirate.profiles;

import com.nomoneypirate.config.ConfigLoader;

import java.util.List;

public class PlayerProfile {
    public String name;
    public String locations;
    public List<String> tags; // z.B. "friendly", "likes Redstone", "new Player"

    public PlayerProfile(String name, String locations, List<String> tags) {
        this.name = name;
        this.locations = locations;
        this.tags = tags;
    }

    /** What the LLM gets to see about this player (it is formatted straight into the WHOIS feedback). */
    @Override
    public String toString() {
        if (tags == null || tags.isEmpty()) return ConfigLoader.lang.noProfileEntries;
        return String.join("; ", tags);
    }
}
