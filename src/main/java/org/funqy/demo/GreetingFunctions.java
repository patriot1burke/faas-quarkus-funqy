package org.funqy.demo;

import io.quarkus.funqy.Funq;
import org.jboss.logging.Logger;

import javax.inject.Inject;

public class GreetingFunctions {
    private static final Logger log = Logger.getLogger("funqy.greeting");
    @Inject
    GreetingService service;

    @Funq
    public Greeting greet(Identity name) {
        log.info("*** In greeting service ***");
        String message = service.hello(name.getName());
        log.info("Sending back: " + message);
        Greeting greeting = new Greeting();
        greeting.setMessage(message);
        greeting.setName(name.getName());
        return greeting;
    }

}
