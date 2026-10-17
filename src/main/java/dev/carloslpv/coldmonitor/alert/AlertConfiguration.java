package dev.carloslpv.coldmonitor.alert;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AlertProperties.class)
class AlertConfiguration {

}
