package dev.carloslpv.coldmonitor;

import org.springframework.boot.SpringApplication;

public class TestColdMonitorBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(ColdMonitorBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
