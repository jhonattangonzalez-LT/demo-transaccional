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
 * Pruebas funcionales de ingreso de datos en los formularios de la tienda, el checkout y el banco.
 * Cada escenario usa un navegador nuevo.
 */
@ExtendWith(SerenityJUnit5Extension.class)
class DatosFormularioTest {

    WebDriver navegador;

    @Steps
    TiendaSteps tienda;

    @BeforeEach
    void abrirNavegador() {
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
    @Title("Tienda: no deja pagar con el valor vacío")
    void valorVacioNoAvanza() {
        tienda.intentarPagarSinValor();
        tienda.verificarValorObligatorio();
        tienda.verificarQueNoAvanzaAlCheckout();
    }

    @Test
    @Title("Checkout: documento que no existe en el banco elegido muestra error")
    void documentoEnBancoEquivocado() {
        tienda.iniciarPagoEnTienda("150000", "Prueba de documento");
        tienda.enviarCheckout("CONDOR", "CC", "1000000001");
        tienda.verificarMensaje("No existe una cuenta en Banco Cóndor para el documento CC 1000000001");
    }

    @Test
    @Title("Checkout: documento vacío no se envía")
    void documentoVacio() {
        tienda.iniciarPagoEnTienda("150000", "Prueba documento vacío");
        tienda.enviarCheckout("ANDINO", "CC", "");
        tienda.verificarDocumentoObligatorio();
    }

    @Test
    @Title("Checkout: persona jurídica con NIT llega al banco Pacífico con su documento")
    void personaJuridicaNit() {
        tienda.iniciarPagoEnTienda("150000", "Prueba persona jurídica");
        tienda.enviarCheckoutCompleto("PACIFICO", "JURIDICA", "NIT", "800000001");
        tienda.verificarDocumentoEnLoginBanco("PACIFICO", "800000001");
    }

    @Test
    @Title("Banco: clave incorrecta no deja avanzar a la autorización")
    void claveIncorrecta() {
        tienda.iniciarPagoEnTienda("150000", "Prueba de clave");
        tienda.elegirBancoYDocumento("ANDINO", "CC", "1000000001");
        tienda.ingresarConClaveIncorrecta("0000");
        tienda.verificarMensaje("Credenciales bancarias inválidas");
        tienda.verificarQueSigueEnLogin();
    }
}
