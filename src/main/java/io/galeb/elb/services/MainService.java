package io.galeb.elb.services;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MainService {

    private final ElbService albService;

    @Autowired
    public MainService(ElbService albService) {
        this.albService = albService;
    }

    @SuppressWarnings("unused")
    public Map<String, Object> process(LinkedHashMap<String, Object> ignore) {
        return albService.sync();
    }
}
