package com.fundacao.aualmoxarifado;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Perfil test: H2 em memória. O perfil default é o de PRODUÇÃO e exige
// PostgreSQL via variáveis de ambiente (falha rápido de propósito).
@SpringBootTest(properties = "spring.profiles.active=test")
class AualmoxarifadoApplicationTests {

    @Test
    void contextLoads() {
    }

}
