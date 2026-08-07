package com.talon.ats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Talon ATS")
@SpringBootApplication
public class TalonAtsApplication {

  public static void main(String[] args) {
    SpringApplication.run(TalonAtsApplication.class, args);
  }
}
