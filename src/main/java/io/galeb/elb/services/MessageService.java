package io.galeb.elb.services;

import org.springframework.stereotype.Service;

@Service
public class MessageService {

    public String hi(String text) {
        return "Hi " + text;
    }
}
