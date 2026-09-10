package dev.shirwac.incidentdetective.adk;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "incident-detective.adk")
public record AdkProperties(boolean enabled) {
}
