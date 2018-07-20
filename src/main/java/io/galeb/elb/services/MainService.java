package io.galeb.elb.services;

import java.util.LinkedHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MainService {

    private final MessageService messageService;

    private final UpdateService updateService;

    private final AlbService albService;

    private final DnsSyncService dnsSyncService;

    @Autowired
    public MainService(
        MessageService messageService,
        UpdateService updateService,
        AlbService albService,
        DnsSyncService dnsSyncService) {

        this.messageService = messageService;
        this.updateService = updateService;
        this.albService = albService;
        this.dnsSyncService = dnsSyncService;
    }

    @SuppressWarnings("unused")
    public String process(LinkedHashMap<String, Object> ignore) {
        return albService.sync();
    }
}
