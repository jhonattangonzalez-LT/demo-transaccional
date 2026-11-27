package paylink;

import net.serenitybdd.annotations.Steps;
import net.serenitybdd.annotations.Title;
import net.serenitybdd.junit5.SerenityJUnit5Extension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Escenarios de la tienda demo, con los datos de docs/DATOS_DE_PRUEBA.md de PayLink.
 * Cada escenario parte de datos reiniciados y usa el pagador de ese escenario.
 */
@ExtendWith(SerenityJUnit5Extension.class)
class PagoTiendaTest {

    WebDriver navegador;

    @Steps
    TiendaSteps tienda;

    // Un navegador nuevo por escenario: en la misma sesión, el segundo envío de la tienda no avanzaba.
    @BeforeEach
    void datosLimpios() {
        ChromeOptions opciones = new ChromeOptions();
        if (!"false".equals(System.getProperty("ventana"))) opciones.addArguments("--headless=new");
        opciones.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--window-size=1366,900");
        navegador = new ChromeDriver(opciones);
        tienda.usarNavegador(navegador);
        tienda.reiniciarDatosDemo();
    }

    @AfterEach
    void cerrarNavegador() {
        // En modo demo el resultado queda visible en Chrome unos segundos antes de cerrarlo.
        tienda.cierreDemo("fin del caso (el resultado de la prueba está en la terminal y en el informe)");
        if (navegador != null) navegador.quit();
    }

    @Test
    @Title("Pago aprobado: Ana (Banco Andino) paga $150.000")
    void pagoAprobado() {
        tienda.iniciarPagoEnTienda("150000.00", "Compra aprobada");
        tienda.elegirBancoYDocumento("ANDINO", "CC", "1000000001");
        tienda.ingresarAlBanco("1234");
        tienda.autorizar(true);
        tienda.verificarResultado("APPROVED");
    }

    @Test
    @Title("Pago rechazado por el usuario: Ana (Banco Andino) cancela $80.000")
    void pagoRechazado() {
        tienda.iniciarPagoEnTienda("80000.00", "Compra rechazada");
        tienda.elegirBancoYDocumento("ANDINO", "CC", "1000000001");
        tienda.ingresarAlBanco("1234");
        tienda.autorizar(false);
        tienda.verificarResultado("REJECTED");
    }

    @Test
    @Title("Fondos insuficientes: Luis (saldo $10.000) intenta pagar $50.000")
    void fondosInsuficientes() {
        tienda.iniciarPagoEnTienda("50000.00", "Compra sin fondos");
        tienda.elegirBancoYDocumento("ANDINO", "CC", "1000000002");
        tienda.ingresarAlBanco("1234");
        tienda.autorizar(true);
        tienda.verificarResultado("INSUFFICIENT_FUNDS");
    }
}
