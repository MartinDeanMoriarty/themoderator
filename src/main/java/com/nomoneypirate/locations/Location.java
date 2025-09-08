package com.nomoneypirate.locations;

public class Location {
    public String name;
    public String dim;
    public int x;
    public int z;

    public Location() {} // Für Gson

    public Location(String name, String dim, int x, int z) {
        this.name = name;
        this.dim = dim;
        this.x = x;
        this.z = z;
    }
}

