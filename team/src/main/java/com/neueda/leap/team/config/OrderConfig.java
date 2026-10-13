package com.neueda.leap.team.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({OrderProperties.class, FauxnanceProperties.class, TradingCalendarProperties.class})
public class OrderConfig {}
