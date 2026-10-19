package com.frietsync.backend.config.call;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JanusConfig.class)
public class JanusConfiguration {
}