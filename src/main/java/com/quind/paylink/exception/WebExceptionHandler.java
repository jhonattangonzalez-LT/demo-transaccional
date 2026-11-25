package com.quind.paylink.exception;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/** Errores de negocio en las pantallas web: se muestran en una página amigable en lugar de JSON. */
@ControllerAdvice(basePackages = "com.quind.paylink.controller.web")
public class WebExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ModelAndView handleApi(ApiException ex) {
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(ex.getStatus());
        mav.addObject("status", ex.getStatus().value());
        mav.addObject("error", ex.getStatus().getReasonPhrase());
        mav.addObject("message", ex.getMessage());
        return mav;
    }
}
