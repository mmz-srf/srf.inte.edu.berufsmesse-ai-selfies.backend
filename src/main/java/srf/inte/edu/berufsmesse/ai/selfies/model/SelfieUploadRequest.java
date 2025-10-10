package srf.inte.edu.berufsmesse.ai.selfies.model;

public class SelfieUploadRequest {
    private String name;
    private String config;
    private String imageBase64;

    public String getName() {return name;}

    public void setName(String name) {this.name = name;}

    public String getConfig() {return config;}

    public void setConfig(String config) {this.config = config;}

    public String getImageBase64() {return imageBase64;}

    public void setImageBase64(String imageBase64) {this.imageBase64 = imageBase64;}
}