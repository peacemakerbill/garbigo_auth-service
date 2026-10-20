package com.garbigo.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String mode = "LOCAL";
    private String root;
    private String applicationsFolder = "collector-applications";
    private boolean restrictPermissions = true;
    private Network network = new Network();

    @Data
    public static class Network {
        private String protocol = "SMB";
        private String host;
        private String share;
        private String mountPath;
        private String directory = "garbigo-auth";
        private boolean requireMount = true;
    }
}