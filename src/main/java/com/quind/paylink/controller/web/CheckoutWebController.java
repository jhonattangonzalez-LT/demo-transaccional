package com.quind.paylink.controller.web;

import com.quind.paylink.dto.CreatePaymentRequest;
import com.quind.paylink.dto.StartPaymentRequest;
import com.quind.paylink.exception.ApiException;
import com.quind.paylink.model.DocumentType;
import com.quind.paylink.model.PersonType;
import com.quind.paylink.model.Transaction;
import com.quind.paylink.model.TransactionStatus;
import com.quind.paylink.service.BankService;
import com.quind.paylink.service.PaymentService;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Pantallas del comercio demo, del checkout de PayLink y del comprobante. Reutilizan los mismos servicios que la API. */
@Controller
public class CheckoutWebController {

    private final PaymentService paymentService;
    private final BankService bankService;
    private final String demoApiKey;

    public CheckoutWebController(PaymentService paymentService, BankService bankService,
                                 @Value("${paylink.demo-api-key}") String demoApiKey) {
        this.paymentService = paymentService;
        this.bankService = bankService;
        this.demoApiKey = demoApiKey;
    }

    /** Página de inicio: simula la tienda del comercio para crear una orden sin Postman. */
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("merchantName", paymentService.authenticateMerchant(demoApiKey).getName());
        return "index";
    }

    @PostMapping("/demo/orders")
    public String createDemoOrder(@RequestParam BigDecimal amount, @RequestParam String description, Model model) {
        try {
            Transaction tx = paymentService.createPayment(demoApiKey,
                    new CreatePaymentRequest(amount, description, null));
            return "redirect:/checkout/" + tx.getId();
        } catch (ApiException ex) {
            model.addAttribute("error", ex.getMessage());
            return home(model);
        }
    }

    /** Pantalla 1: resumen del pago + selección de banco, tipo de persona y documento. */
    @GetMapping("/checkout/{id}")
    public String checkout(@PathVariable UUID id, Model model) {
        Transaction tx = paymentService.getPayment(id);
        if (tx.getStatus() == TransactionStatus.PENDING) {
            return "redirect:/bank/" + tx.getPayerBankCode() + "/login?tx=" + id;
        }
        if (tx.getStatus() != TransactionStatus.CREATED) {
            return "redirect:/result/" + id;
        }
        return renderCheckout(tx, model);
    }

    @PostMapping("/checkout/{id}")
    public String startPayment(@PathVariable UUID id,
                               @RequestParam String bankCode,
                               @RequestParam PersonType personType,
                               @RequestParam DocumentType documentType,
                               @RequestParam String documentNumber,
                               Model model) {
        try {
            Transaction tx = paymentService.startPayment(id,
                    new StartPaymentRequest(bankCode, personType, documentType, documentNumber));
            return "redirect:/bank/" + tx.getPayerBankCode() + "/login?tx=" + id;
        } catch (ApiException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("selectedBank", bankCode);
            model.addAttribute("documentNumber", documentNumber);
            return renderCheckout(paymentService.getPayment(id), model);
        }
    }

    /** Pantalla 4: comprobante con el estado final. */
    @GetMapping("/result/{id}")
    public String result(@PathVariable UUID id, Model model) {
        Transaction tx = paymentService.getPayment(id);
        model.addAttribute("tx", tx);
        model.addAttribute("bankName", tx.getPayerBankCode() == null ? "-"
                : bankService.getBank(tx.getPayerBankCode()).getName());
        model.addAttribute("step", 4);
        return "result";
    }

    private String renderCheckout(Transaction tx, Model model) {
        model.addAttribute("tx", tx);
        model.addAttribute("banks", bankService.listBanks());
        model.addAttribute("personTypes", PersonType.values());
        model.addAttribute("documentTypes", DocumentType.values());
        model.addAttribute("step", 1);
        return "checkout";
    }
}
