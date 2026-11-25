package com.quind.paylink.controller.web;

import com.quind.paylink.dto.BankAuthenticateRequest;
import com.quind.paylink.exception.ApiException;
import com.quind.paylink.model.Bank;
import com.quind.paylink.model.BankSession;
import com.quind.paylink.model.DocumentType;
import com.quind.paylink.model.Transaction;
import com.quind.paylink.model.TransactionStatus;
import com.quind.paylink.service.BankService;
import com.quind.paylink.service.PaymentService;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Portal simulado de cada banco ficticio: login y autorización del débito.
 * No usa HttpSession: el token de sesión bancaria (guardado en BD, de un solo uso) viaja en la
 * URL/formulario, así el flujo funciona aunque cada petición la atienda una instancia distinta.
 */
@Controller
public class BankWebController {

    private final BankService bankService;
    private final PaymentService paymentService;

    public BankWebController(BankService bankService, PaymentService paymentService) {
        this.bankService = bankService;
        this.paymentService = paymentService;
    }

    /** Pantalla 2: login simulado del banco. */
    @GetMapping("/bank/{bankCode}/login")
    public String login(@PathVariable String bankCode, @RequestParam("tx") UUID txId, Model model) {
        Bank bank = bankService.getBank(bankCode);
        Transaction tx = paymentService.getPayment(txId);
        if (tx.getStatus() != TransactionStatus.PENDING) {
            return "redirect:/result/" + txId;
        }
        paymentService.requireBank(tx, bank.getCode());
        return renderLogin(bank, tx, model);
    }

    @PostMapping("/bank/{bankCode}/login")
    public String doLogin(@PathVariable String bankCode,
                          @RequestParam("tx") UUID txId,
                          @RequestParam DocumentType documentType,
                          @RequestParam String documentNumber,
                          @RequestParam String password,
                          Model model) {
        Bank bank = bankService.getBank(bankCode);
        try {
            BankSession session = bankService.authenticate(bank.getCode(),
                    new BankAuthenticateRequest(txId, documentType, documentNumber, password));
            return "redirect:/bank/" + bank.getCode() + "/authorize?tx=" + txId + "&session=" + session.getToken();
        } catch (ApiException ex) {
            model.addAttribute("error", ex.getMessage());
            return renderLogin(bank, paymentService.getPayment(txId), model);
        }
    }

    /** Pantalla 3: confirmar o rechazar el débito. */
    @GetMapping("/bank/{bankCode}/authorize")
    public String authorize(@PathVariable String bankCode, @RequestParam("tx") UUID txId,
                            @RequestParam(name = "session", required = false) String sessionToken, Model model) {
        Bank bank = bankService.getBank(bankCode);
        Transaction tx = paymentService.getPayment(txId);
        if (tx.getStatus() != TransactionStatus.PENDING) {
            return "redirect:/result/" + txId;
        }
        BankSession session;
        try {
            session = bankService.getValidSession(sessionToken, txId, bank.getCode());
        } catch (ApiException ex) {
            return "redirect:/bank/" + bank.getCode() + "/login?tx=" + txId;
        }
        model.addAttribute("bank", bank);
        model.addAttribute("tx", tx);
        model.addAttribute("holderName", session.getHolderName());
        model.addAttribute("sessionToken", session.getToken());
        model.addAttribute("step", 3);
        return "bank-authorize";
    }

    @PostMapping("/bank/{bankCode}/authorize")
    public String doAuthorize(@PathVariable String bankCode, @RequestParam("tx") UUID txId,
                              @RequestParam("session") String sessionToken, @RequestParam boolean approve) {
        bankService.authorize(bankCode, txId, sessionToken, approve);
        return "redirect:/result/" + txId;
    }

    private String renderLogin(Bank bank, Transaction tx, Model model) {
        model.addAttribute("bank", bank);
        model.addAttribute("tx", tx);
        model.addAttribute("documentTypes", DocumentType.values());
        model.addAttribute("step", 2);
        return "bank-login";
    }
}
