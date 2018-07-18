package io.galeb.elb.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MainService {

    private final MessageService messageService;

    @Autowired
    public MainService(MessageService messageService) {
        this.messageService = messageService;
    }

    public String process(String text) {
        return messageService.hi(text);
    }
}
