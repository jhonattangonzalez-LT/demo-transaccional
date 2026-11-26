package paylink;

import net.serenitybdd.annotations.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pasos de la tienda y del checkout de PayLink. Cada método es un paso que Serenity
 * registra en el informe con su captura cuando falla.
 */
public class TiendaSteps {

    static final String BASE = "https://demo-transaccional.vercel.app";
    static final String API_KEY = "demo-api-key-123";

    private WebDriver driver;

    /** El navegador lo administra la prueba (Serenity) y se entrega aquí antes de cada escenario. */
    public void usarNavegador(WebDriver navegador) {
        this.driver = navegador;
    }

    private WebDriverWait esperar() {
        return new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    /** Escribe en un campo y comprueba que quedó el valor; si no, lo fija con JavaScript. */
    private void escribir(WebElement campo, String texto) {
        campo.clear();
        campo.sendKeys(texto);
        if (!texto.equals(campo.getAttribute("value"))) {
            ((org.openqa.selenium.JavascriptExecutor) driver)
                    .executeScript("arguments[0].value = arguments[1];", campo, texto);
        }
    }

    @Step("Reiniciar los datos de prueba de la demo")
    public void reiniciarDatosDemo() {
        narrar("Preparar: se reinician los datos de la demo");
        // Cada escenario empieza sin sesión: la del escenario anterior (cookies del banco) rompe el siguiente.
        driver.get(BASE);
        driver.manage().deleteAllCookies();
        // La demo a veces responde 500 al reiniciar (se observó 2 de 3 llamadas): se reintenta hasta 3 veces.
        int codigo = -1;
        try {
            for (int intento = 1; intento <= 3 && codigo != 200; intento++) {
                HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/api/v1/demo/reset"))
                        .header("X-Api-Key", API_KEY)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
                codigo = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
                if (codigo != 200) pausa(3000);
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo reiniciar la demo", e);
        }
        assertTrue(codigo == 200, "reset de la demo respondió " + codigo + " en 3 intentos");
    }

    @Step("Abrir la tienda e iniciar un pago por {0} COP ({1})")
    public void iniciarPagoEnTienda(String monto, String descripcion) {
        driver.get(BASE);
        narrar("1 · Tienda: el comercio pone el valor y la descripción del pago");
        // Esperar a que el formulario esté listo antes de escribir (la tienda se carga en el servidor).
        WebElement valor = esperar().until(ExpectedConditions.visibilityOfElementLocated(By.id("amount")));
        // El navegador en configuración colombiana no acepta el punto decimal en un campo numérico:
        // la demo trabaja en pesos enteros, así que se envía solo la parte entera.
        // Campo numérico: sendKeys no siempre escribe tras un flujo previo (el valor por defecto es 150000.00).
        // Se fija el valor con JavaScript y se avisa a la página del cambio.
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "arguments[0].value = ''; arguments[0].value = arguments[1];"
                + " arguments[0].dispatchEvent(new Event('input', {bubbles: true}));"
                + " arguments[0].dispatchEvent(new Event('change', {bubbles: true}));",
                valor, monto.split("\\.")[0]);
        escribir(driver.findElement(By.id("description")), descripcion);
        driver.findElement(By.xpath("//button[contains(., 'Pagar con PayLink')]")).click();
        // La tienda vive en Vercel y a veces tarda en responder: se espera hasta 45 s.
        new WebDriverWait(driver, Duration.ofSeconds(45))
                .until(ExpectedConditions.urlContains("/checkout/"));
    }

    @Step("Elegir banco {0}, documento {1} {2} en el checkout")
    public void elegirBancoYDocumento(String banco, String tipoDocumento, String numero) {
        narrar("2 · Checkout: el pagador elige su banco y su documento");
        enviarCheckout(banco, tipoDocumento, numero);
        esperar().until(ExpectedConditions.urlContains("/bank/"));
    }

    @Step("Enviar el checkout con banco {0}, documento {1} {2}")
    public void enviarCheckout(String banco, String tipoDocumento, String numero) {
        narrar("Checkout: se llenan banco " + banco + ", documento " + tipoDocumento + " " + (numero.isEmpty() ? "(vacío)" : numero));
        new Select(driver.findElement(By.id("bankCode"))).selectByValue(banco);
        new Select(driver.findElement(By.id("documentType"))).selectByValue(tipoDocumento);
        escribir(driver.findElement(By.id("documentNumber")), numero);
        driver.findElement(By.xpath("//button[contains(., 'Ir a mi banco')]")).click();
    }

    @Step("Enviar el checkout como {1}: banco {0}, documento {2} {3}")
    public void enviarCheckoutCompleto(String banco, String tipoPersona, String tipoDocumento, String numero) {
        new Select(driver.findElement(By.id("bankCode"))).selectByValue(banco);
        // Los radios de tipo de persona están ocultos dentro de su etiqueta: se marcan por script.
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='personType'][value='\" + arguments[0] + \"']\").click();", tipoPersona);
        new Select(driver.findElement(By.id("documentType"))).selectByValue(tipoDocumento);
        escribir(driver.findElement(By.id("documentNumber")), numero);
        driver.findElement(By.xpath("//button[contains(., 'Ir a mi banco')]")).click();
    }

    @Step("Verificar que el navegador no envía el checkout y marca el documento como obligatorio")
    public void verificarDocumentoObligatorio() {
        narrar("Verificación: el documento vacío no se envía");
        pausa(2000);
        assertTrue(driver.getCurrentUrl().contains("/checkout/"),
                "El checkout se envió con el documento vacío: " + driver.getCurrentUrl());
        Boolean faltante = (Boolean) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "return document.getElementById('documentNumber').validity.valueMissing;");
        assertTrue(Boolean.TRUE.equals(faltante), "El campo de documento no está marcado como obligatorio");
    }

    @Step("Verificar que el banco {0} recibe el documento {1} en la pantalla de ingreso")
    public void verificarDocumentoEnLoginBanco(String banco, String numero) {
        narrar("Verificación: el banco " + banco + " recibe el documento " + numero);
        esperar().until(ExpectedConditions.urlContains("/bank/" + banco + "/login"));
        String valor = driver.findElement(By.id("documentNumber")).getAttribute("value");
        assertTrue(numero.equals(valor), "El banco muestra el documento «" + valor + "» y se esperaba «" + numero + "»");
    }

    @Step("Intentar pagar en la tienda con el campo de valor vacío")
    public void intentarPagarSinValor() {
        narrar("Tienda: se intenta pagar con el valor vacío");
        driver.get(BASE);
        WebElement valor = esperar().until(ExpectedConditions.visibilityOfElementLocated(By.id("amount")));
        valor.clear();
        driver.findElement(By.xpath("//button[contains(., 'Pagar con PayLink')]")).click();
    }

    @Step("Verificar que el campo de valor queda marcado como obligatorio y vacío")
    public void verificarValorObligatorio() {
        narrar("Verificación: el valor vacío queda marcado como obligatorio");
        Boolean faltante = (Boolean) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "return document.getElementById('amount').validity.valueMissing;");
        assertTrue(Boolean.TRUE.equals(faltante), "El campo de valor no está marcado como obligatorio");
    }

    @Step("Verificar que la tienda no avanza al checkout")
    public void verificarQueNoAvanzaAlCheckout() {
        // Prueba negativa: no hay un evento que esperar, así que se da un margen corto para comprobar que no avanza.
        pausa(2000);
        assertTrue(!driver.getCurrentUrl().contains("/checkout/"),
                "La tienda avanzó al checkout con el valor vacío: " + driver.getCurrentUrl());
    }

    @Step("Verificar que la pantalla muestra el mensaje «{0}»")
    public void verificarMensaje(String mensaje) {
        narrar("Verificación: debe aparecer el mensaje «" + mensaje + "»");
        esperar().until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), mensaje));
        assertTrue(driver.getPageSource().contains(mensaje), "No se muestra el mensaje «" + mensaje + "»");
    }

    @Step("Ingresar al banco con la clave incorrecta «{0}»")
    public void ingresarConClaveIncorrecta(String clave) {
        narrar("Banco: se ingresa la clave incorrecta «" + clave + "»");
        ingresarAlBanco(clave);
    }

    @Step("Verificar que el banco no deja avanzar a la autorización")
    public void verificarQueSigueEnLogin() {
        narrar("Verificación: el banco no deja avanzar");
        assertTrue(driver.getCurrentUrl().contains("/login"),
                "El banco dejó avanzar con clave incorrecta: " + driver.getCurrentUrl());
        assertTrue(!driver.getPageSource().contains("Autorizar") && !driver.getPageSource().contains("Aprobar"),
                "La pantalla de autorización apareció con clave incorrecta");
    }

    /** Muestra un rótulo en Chrome (para ver el caso como presentación). En modo demo (-Ddemo=true) se detiene para leerlo. */
    public void narrar(String texto) {
        // Para el panel: cada paso se agrega a un archivo que el panel lee mientras corre.
        String ruta = System.getProperty("demo.log");
        if (ruta != null && !ruta.isBlank()) {
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of(ruta), texto + "\n",
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception e) {
                // El panel es opcional: si no se puede escribir, la prueba sigue.
            }
        }
        try {
            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "var id='narrador';var d=document.getElementById(id);if(!d){d=document.createElement('div');d.id=id;"
                + "d.style.cssText='position:fixed;left:0;right:0;top:0;z-index:99999;background:#0b3d6e;color:#fff;"
                + "font:600 20px system-ui,sans-serif;padding:16px 24px;box-shadow:0 4px 12px #0004';document.body.appendChild(d);}"
                + "d.textContent=arguments[0];", texto);
        } catch (Exception e) {
            // Si la página no permite el rótulo, la prueba sigue igual.
        }
    }

    /** Cierre del caso en modo demo: muestra el resultado en Chrome y lo deja visible unos segundos. */
    public void cierreDemo(String resultado) {
        if (!"true".equals(System.getProperty("demo"))) return;
        narrar(resultado);
        pausa(5000);
    }

    /** En modo demo (-Ddemo=true) se espera un momento después de cada acción para poder leer el rótulo. */
    private void pausaDemo() {
        if ("true".equals(System.getProperty("demo"))) pausa(3500);
    }

    private void pausa(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Step("Ingresar al banco con la clave simulada")
    public void ingresarAlBanco(String clave) {
        narrar("3 · Banco: el pagador ingresa su clave");
        WebElement pass = driver.findElement(By.id("password"));
        pass.sendKeys(clave);
        driver.findElement(By.xpath("//button[contains(., 'Ingresar')]")).click();
    }

    @Step("{0} el débito en el banco")
    public void autorizar(boolean aprobar) {
        String boton = aprobar ? "Aprobar" : "Rechazar";
        narrar("4 · Banco: el pagador " + (aprobar ? "aprueba" : "rechaza") + " el débito");
        // Esperar a la pantalla de autorización (no solo al botón) antes de hacer clic.
        esperar().until(ExpectedConditions.urlContains("/authorize"));
        esperar().until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(., '" + boton + "')]")));
        driver.findElement(By.xpath("//button[contains(., '" + boton + "')]")).click();
        pausaDemo();
        // El resultado es una página nueva (/result/): se espera la dirección, no un texto de la página anterior.
        esperar().until(ExpectedConditions.urlContains("/result/"));
    }

    @Step("Verificar que la pantalla de resultado muestra «{0}»")
    public void verificarResultado(String textoEsperado) {
        narrar("5 · Resultado: la tienda muestra el estado del pago (" + textoEsperado + ")");
        esperar().until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), textoEsperado));
        assertTrue(driver.getPageSource().contains(textoEsperado),
                "La pantalla de resultado no muestra «" + textoEsperado + "»");
    }
}
