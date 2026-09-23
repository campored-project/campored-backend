package com.campored.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Contexto de la aplicación")
class CamporedBackendApplicationTests {

	@Test
	@DisplayName("Debe cargar el contexto de Spring")
	void contextLoads() {
	}

}
