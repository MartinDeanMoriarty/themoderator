package com.nomoneypirate.profiles;

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
}

