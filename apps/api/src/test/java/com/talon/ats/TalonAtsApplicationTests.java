package com.talon.ats;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = TalonAtsApplication.class,
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
    })
class TalonAtsApplicationTests {

  @Test
  void applicationContextStarts() {}
}
