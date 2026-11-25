package com.quind.paylink.controller.web;

import com.quind.paylink.dto.BankAuthenticateRequest;
import com.quind.paylink.exception.ApiException;
import com.quind.paylink.model.Bank;
import com.quind.paylink.model.DocumentType;
import com.quind.paylink.model.Transaction;
import com.quind.paylink.model.TransactionStatus;
import com.quind.paylink.service.BankService;
import com.quind.paylink.service.PaymentService;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Portal simulado de cada banco ficticio: login y autorización del débito. */
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
                          HttpSession httpSession,
                          Model model) {
        Bank bank = bankService.getBank(bankCode);
        try {
            BankService.BankSession session = bankService.authenticate(bank.getCode(),
                    new BankAuthenticateRequest(txId, documentType, documentNumber, password));
            httpSession.setAttribute(sessionKey(txId), session.token());
            httpSession.setAttribute(sessionKey(txId) + ".holder", session.holderName());
            return "redirect:/bank/" + bank.getCode() + "/authorize?tx=" + txId;
        } catch (ApiException ex) {
            model.addAttribute("error", ex.getMessage());
            return renderLogin(bank, paymentService.getPayment(txId), model);
        }
    }

    /** Pantalla 3: confirmar o rechazar el débito. */
    @GetMapping("/bank/{bankCode}/authorize")
    public String authorize(@PathVariable String bankCode, @RequestParam("tx") UUID txId,
                            HttpSession httpSession, Model model) {
        Bank bank = bankService.getBank(bankCode);
        Transaction tx = paymentService.getPayment(txId);
        if (tx.getStatus() != TransactionStatus.PENDING) {
            return "redirect:/result/" + txId;
        }
        if (httpSession.getAttribute(sessionKey(txId)) == null) {
            return "redirect:/bank/" + bank.getCode() + "/login?tx=" + txId;
        }
        model.addAttribute("bank", bank);
        model.addAttribute("tx", tx);
        model.addAttribute("holderName", httpSession.getAttribute(sessionKey(txId) + ".holder"));
        model.addAttribute("step", 3);
        return "bank-authorize";
    }

    @PostMapping("/bank/{bankCode}/authorize")
    public String doAuthorize(@PathVariable String bankCode, @RequestParam("tx") UUID txId,
                              @RequestParam boolean approve, HttpSession httpSession) {
        String token = (String) httpSession.getAttribute(sessionKey(txId));
        try {
            bankService.authorize(bankCode, txId, token, approve);
        } finally {
            httpSession.removeAttribute(sessionKey(txId));
            httpSession.removeAttribute(sessionKey(txId) + ".holder");
        }
        return "redirect:/result/" + txId;
    }

    private String renderLogin(Bank bank, Transaction tx, Model model) {
        model.addAttribute("bank", bank);
        model.addAttribute("tx", tx);
        model.addAttribute("documentTypes", DocumentType.values());
        model.addAttribute("step", 2);
        return "bank-login";
    }

    private static String sessionKey(UUID txId) {
        return "bankSession." + txId;
    }
}
