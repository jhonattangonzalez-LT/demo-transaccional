package com.quind.paylink.service;

import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Restablece los datos demo (saldos iniciales, sin transacciones ni movimientos). Solo para el mockup. */
@Service
public class DemoDataService {

    private final DataSource dataSource;

    public DemoDataService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Transactional
    public void reset() {
        new ResourceDatabasePopulator(new ClassPathResource("demo-reset.sql")).execute(dataSource);
    }
}
