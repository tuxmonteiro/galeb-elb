package io.galeb.elb.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.galeb.elb.config.SpringConfig;
import io.galeb.elb.services.MainService;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;


public class MainHandler
    implements RequestHandler<LinkedHashMap<String, Object>, String> {

    private static final Logger LOG = Logger.getLogger(MainHandler.class);

    private final MainService mainService;

    public MainHandler() {
        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(SpringConfig.class);
        this.mainService = applicationContext.getBean(MainService.class);
    }

    public String handleRequest(LinkedHashMap<String, Object> request, Context context)
        throws RuntimeException {

        return mainService.process(request.entrySet().stream()
            .map(e -> e.getKey() + e.getValue())
            .collect(Collectors.joining()));
    }

}
